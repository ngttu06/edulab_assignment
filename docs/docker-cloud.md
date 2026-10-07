# Docker — EduLab + MySQL cloud

## Thành phần

- `Dockerfile`: multi-stage Maven/Java 21, executable WAR với embedded Tomcat/JSP,
  chạy bằng user UID 10001 (không phải root). Runtime có curl cho healthcheck.
- `compose.cloud.yml`: **không có MySQL service**. Hai container dùng cùng image:
  `migrate` là job kiểm tra/chuẩn bị DB chạy trước, `app` là web chạy lâu dài.
- `.dockerignore`: chỉ gửi `pom.xml`, `src/main`, entrypoint và Dockerfile tới builder.
  Không gửi `.env`, `.tools`, Git, backup DB, upload, test fixture hoặc secret.
- `docker-compose.yml` cũ chỉ dành cho MySQL local, không dùng cho triển khai cloud.
- Healthcheck `GET /edulab/actuator/health` trả `{"status":"UP"}` khi DB truy vấn được,
  HTTP 503 `DOWN` nếu lỗi. Không lộ lỗi SQL/credentials. Restart policy không tự sửa
  lỗi DB; Docker chỉ tự restart khi process kết thúc, không khi chỉ unhealthy.

## 1. Cấu hình một lần

Trong workspace `/Users/ngttu/Workspace/project_asm_prj`, **giữ nguyên** `.env.remote`
đang có. Máy mới: copy `.env.remote.example` sang `.env.remote`, điền biến rồi
`chmod 600 .env.remote`. Docker Compose đọc file này; không cần `source` vào shell.
Chỉ dùng cú pháp dotenv `KEY=value` (không dùng shell command/export).

Biến cần có:

| Biến | Nội dung |
|---|---|
| `EDULAB_DB_URL` | JDBC MySQL cloud, `sslMode=VERIFY_IDENTITY`, `serverTimezone=UTC`, timeout |
| `EDULAB_DB_USERNAME` / `EDULAB_DB_PASSWORD` | Tài khoản MySQL, không phải mật khẩu đăng nhập web |
| `EDULAB_JWT_SECRET` | Secret mạnh đã cấu hình, giữ riêng từng môi trường |
| `EDULAB_AI_ENCRYPTION_KEY` | Khóa mã hóa AI **đúng với ciphertext trong DB** |
| `EDULAB_STORAGE_ENCRYPTION_KEY` | Khóa mã hóa R2 **đúng với ciphertext trong DB** |
| `EDULAB_VIDEO_ALLOWED_HOSTS` | Domain HTTPS chính xác của HLS manifest/segments/keys nếu dùng m3u8 |
| `EDULAB_CLAMAV_HOST` / `EDULAB_CLAMAV_PORT` | ClamAV INSTREAM trên mạng tin cậy nếu dùng upload |

Không sinh lại khóa mã hóa khi triển khai/restart: sẽ không đọc được key AI/R2 đã
lưu. Nếu MySQL dùng CA riêng, mount truststore read-only và cấu hình Java Connector/J
theo nhà cung cấp; không hạ TLS sang REQUIRED/DISABLED. Không dùng `localhost` cho
cloud và không đưa password vào JDBC URL. Firewall cần cho phép IP outbound của máy chạy.
Docker không đóng gói/migrate dữ liệu cloud, không thay cấu hình R2/AI trong DB.

## 2. Schema và migration

Cloud lần xác nhận trước ở V14; source hiện có V1–V16. Sao lưu cloud, kiểm tra
history/checksum và diễn tập staging trước khi nâng cấp. Không sửa file migration
đã áp dụng. MySQL DDL không bảo đảm rollback nguyên transaction; backup là bắt buộc.

Job `migrate` mặc định **chỉ validate**, không migrate. Nếu có pending migrations,
nó dừng với thông báo; app không khởi động để tránh chạy trên schema cũ. Để duyệt:

```dotenv
EDULAB_MIGRATE_ON_DEPLOY=true
```

Đặt trong `.env.remote` **sau khi** đã backup/review. Lệnh start sẽ chạy Flyway riêng
bằng thư viện cùng version với ứng dụng, chỉ áp dụng migration pending. Không chạy
repair/clean/baseline, không import hoặc seed một DB cloud mới. DB phải đã được
provision với Flyway history; tài khoản demo phải đổi mật khẩu/khóa theo deployment
guard hiện có. Khi xong, đặt lại `false` cho lần triển khai sau.

Tài khoản job cần DDL. Để tách quyền, chạy job một lần bằng file môi trường DBA riêng:

```bash
cd /Users/ngttu/Workspace/project_asm_prj
EDULAB_ENV_FILE=/absolute/private/migration.env \
  docker compose -f compose.cloud.yml run --rm --no-deps migrate
docker compose -f compose.cloud.yml up -d --no-build --wait
```

File DBA cần `EDULAB_MIGRATE_ON_DEPLOY=true`; file app giữ `false` và user quyền
tối thiểu. Compose sẽ chạy lại job validate bằng user app trước web. Spring Flyway
vẫn tắt; không chuyển sang profile local để lách guard. Trong lần đóng gói này,
**không chạy job hoặc app lên cloud**, nên không khẳng định đã nâng cloud tới V16.

## 3. Build và chạy

```bash
cd /Users/ngttu/Workspace/project_asm_prj
docker compose -f compose.cloud.yml up -d --build --wait
docker compose -f compose.cloud.yml ps -a
docker compose -f compose.cloud.yml logs --tail=100 migrate app
```

Mặc định image `edulab-khtn8:latest`, port **127.0.0.1:8080**, context **/edulab**.
Nếu build lâu/startup chậm, thêm `--wait-timeout 180`. Lần đầu cần internet tải base
images và Maven dependencies. Build không chạy test hoặc kết nối cloud; test chạy
riêng trên DB thử. `app-storage` giữ dữ liệu cục bộ cần bền vững qua recreate.
File upload chính vẫn ở R2, video vẫn stream từ YouTube/CDN, không copy vào image.

Trang học và AI dùng lại dữ liệu/cấu hình cloud hiện có. Bản PlayerJS Builder hợp
lệ phải đặt vào `src/main/resources/static/js/vendor/playerjs.js` **trước build**;
image không tự tải PlayerJS demo. Xem [hướng dẫn video](lesson-video-learning.md).
Nếu chưa cài thì YouTube vẫn dùng được, m3u8 báo chưa cài player.
Không có ClamAV thì file upload tiếp tục cách ly, không tự bật scan bypass.

## 4. HTTPS và reverse proxy

Ứng dụng cố định profile `remote`, cookie Secure, scan bypass false. Truy cập HTTP
chỉ phù hợp healthcheck; **đăng nhập web cần HTTPS**. Dùng reverse proxy HTTPS hiện
có trên host, upstream `http://127.0.0.1:8080`, giữ prefix `/edulab`, gửi `Host` và
`X-Forwarded-Proto: https`. Proxy phải ghi đè header forwarded từ client.

Nếu proxy đó tin cậy và cần app nhận đúng scheme để redirect, chạy:

```bash
EDULAB_FORWARD_HEADERS_STRATEGY=framework \
  docker compose -f compose.cloud.yml up -d --no-build --wait
```

Mặc định `none` không tin header forwarded. Không mở port web ra internet rồi bật
framework tùy tiện. Có thể đổi port/bind qua `EDULAB_HTTP_PORT`/`EDULAB_BIND_ADDRESS`
trong shell hoặc file `.env` của Compose, hoặc `--env-file` của Compose; khác với
`.env.remote` dùng cấp secrets cho container. Proxy ở Docker network riêng cần
cấu hình networking phù hợp thay vì coi localhost container là host.
Cập nhật CORS R2 cho origin HTTPS chính xác (không có `/edulab`). Image không kèm
certificate/domain/reverse proxy và không thay thiết lập HTTPS hiện có của host.

## 5. Chuyển image sang máy khác, không cần source/Java/Maven

```bash
cd /Users/ngttu/Workspace/project_asm_prj
docker image save edulab-khtn8:latest | gzip > target/edulab-khtn8-docker.tar.gz
```

Workspace này đã có archive tại
`/Users/ngttu/Workspace/project_asm_prj/target/edulab-khtn8-docker.tar.gz`
và checksum `.sha256` tương ứng, nên không cần build/export lại nếu source chưa đổi.

Chuyển archive, `compose.cloud.yml` và `.env.remote` (qua kênh riêng tư) sang cùng
một thư mục tuyệt đối trên host mới; file env không nằm trong archive. Tại host:

```bash
docker image load -i /absolute/deployment/edulab-khtn8-docker.tar.gz
docker compose --project-directory /absolute/deployment \
  -f /absolute/deployment/compose.cloud.yml up -d --no-build --wait
```

Image build mặc định theo kiến trúc máy build; archive trên workspace này là
`linux/amd64`. Với ARM64, build lại trên host hoặc dùng
`docker buildx build --platform linux/arm64 --load -t edulab-khtn8:latest /absolute/source`.
Không có registry/push tự động. Sau khi có image, restart không cần internet tải
Maven nhưng vẫn cần mạng tới MySQL, YouTube/HLS, AI/R2 đang bật.

## Vận hành

- Dừng: `docker compose -f /absolute/deployment/compose.cloud.yml down`.
- Restart: `docker compose -f /absolute/deployment/compose.cloud.yml restart app`.
- Nâng source/image: build mới rồi `up -d --wait`; job chuẩn bị DB chạy trước.
- Không dùng `down -v` nếu cần giữ volume storage. Backup volume và DB/khóa riêng.
- Secrets truyền qua env không nằm trong image nhưng người có quyền Docker vẫn
  đọc được container env. Không gửi output `docker inspect`/Compose config chưa
  che secrets, không log `.env.remote`, không dùng `set -x` khi vận hành.

## Kết quả xác nhận — 07/10/2026

- Maven `verify -Dtest=!MysqlIntegrationTest`: **179 test, 0 failure/error/skip**;
  gồm MySQL test loopback, healthcheck public/503 và policy migration. Docker
  Testcontainers test cũ không nằm trong lần chạy này.
- Docker image `edulab-khtn8:latest` build thật từ source, platform `linux/amd64`.
  Archive gzip khoảng **212 MiB**, image không chứa secret được cấu hình.
- Compose kiểm thử trên **MySQL 8.4 riêng tại localhost:3308**, CA/hostname xác minh
  bằng `sslMode=VERIFY_IDENTITY`. DB ban đầu V14, khóa tài khoản demo; job chặn khi
  chưa duyệt, chỉ áp dụng V15/V16 khi được duyệt. App remote khởi động thành công,
  user 10001, healthcheck healthy; volume giữ file qua force-recreate.
- Browser Chrome thật qua reverse proxy HTTPS **test-only trên loopback**: login/API,
  Secure HttpOnly cookie, admin, quản lý video, thư viện, profile, màn hình 390px đạt,
  không page error. Không dùng/tải PlayerJS giả trong image.
- Không đọc/ghi database cloud trong lượt đóng gói; không thay `.env.remote`, không
  khẳng định cloud đã nâng V16 hoặc HTTPS công khai đã được cấu hình. Build PlayerJS
  thật, ClamAV, nguồn HLS và cấu hình proxy của host là điều kiện vận hành riêng.