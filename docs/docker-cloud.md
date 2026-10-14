# Docker — EduLab + MySQL cloud

## Thành phần

- `Dockerfile`: multi-stage Maven/Java 21, executable WAR với embedded Tomcat/JSP,
  chạy bằng user UID 10001 (không phải root). Runtime có curl cho healthcheck.
- `docker-compose.yml` mặc định include `compose.cloud.yml`: **không có MySQL
  service**. Hai container dùng cùng image: `migrate` là job kiểm tra/chuẩn bị DB
  chạy trước, `app` là web chạy lâu dài với tên container cố định **`edulab`**.
  Host/database/user cloud được cấu hình rõ trong Compose cho cả hai service;
  `.env.remote` cấp mật khẩu/JWT/khóa mã hóa, không chọn lại DB local.
- `.dockerignore`: chỉ gửi `pom.xml`, `src/main`, entrypoint và Dockerfile tới builder.
  Không gửi `.env`, `.tools`, Git, backup DB, upload, test fixture hoặc secret.
- Không còn `compose.local.yml` hoặc `run-local.sh`: deployment chỉ dùng MySQL
  cloud; app không có datasource fallback về MySQL local. Bộ kiểm thử vẫn có thể
  dùng MySQL dùng một lần ở loopback nếu được chỉ định rõ và không phải deployment.
- Cần Docker Compose **2.20+** để hỗ trợ `include`; cả file mặc định và file cloud
  dùng cùng project `edulab`, không tạo hai bộ container khi đổi cách gọi lệnh.
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
| `EDULAB_VIDEO_ALLOWED_HOSTS` | Tùy chọn: trống hoặc `*` cho phép mọi domain HTTPS cho HLS/MP4; danh sách domain chính xác phân cách dấu phẩy để giới hạn lại (gồm manifest/segments/keys) |
| `EDULAB_CLAMAV_HOST` / `EDULAB_CLAMAV_PORT` | ClamAV INSTREAM trên mạng tin cậy nếu dùng upload |

Không sinh lại khóa mã hóa khi triển khai/restart: sẽ không đọc được key AI/R2 đã
lưu. Nếu MySQL dùng CA riêng, mount truststore read-only và cấu hình Java Connector/J
theo nhà cung cấp; không hạ TLS sang REQUIRED/DISABLED. Không dùng `localhost` cho
cloud và không đưa password vào JDBC URL. Firewall cần cho phép IP outbound của máy chạy.
Docker không đóng gói/migrate dữ liệu cloud, không thay cấu hình R2/AI trong DB.

## 2. Schema và migration

Cloud `mysql.ngttu.com/edulab_assignment` hiện có **V25** sau lượt assessment ngày
14/10/2026: backup/restore tách biệt, diễn tập và chỉ áp dụng V25 từ V24 bằng job riêng;
schema/dữ liệu bảng cũ được giữ nguyên. Xem kết quả và giới hạn nhập AI tại
`/Users/ngttu/Workspace/project_asm_prj/docs/assessment.md`. Mỗi lần nâng schema tiếp theo cần sao lưu cloud,
kiểm tra history/checksum và diễn tập staging trước khi nâng cấp. Không sửa file migration
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
vẫn tắt; không chuyển sang profile local để lách guard. Lượt đóng gói ban đầu không
chạy migration cloud; lượt khắc phục khởi động sau đó đã được chủ sở hữu duyệt và
áp dụng V15/V16, không import dữ liệu local vào cloud.

## 3. Build và chạy

```bash
cd /Users/ngttu/Workspace/project_asm_prj
docker compose up -d --no-build --wait
docker compose ps -a
docker logs --tail=100 edulab
```

Mặc định image `edulab-khtn8:latest`, port **127.0.0.1:8088**, context **/edulab**.
Workspace đã có image, container `edulab` được tạo sẵn với đủ biến runtime. Máy
chưa có image hoặc source đã đổi thì thay `--no-build` bằng `--build`. Không dùng
Docker Desktop chạy image trần mà bỏ env: nó không có secret mặc định bên trong.
Container hiện **healthy** với schema cloud V25; trạng thái validation mới nhất nằm ở
mục bản assessment bên dưới. Dùng Compose để khởi
động các lần sau, không `docker start edulab` riêng để bỏ qua kiểm tra dependency.
Nếu build lâu/startup chậm, thêm `--wait-timeout 180`. Lần đầu cần internet tải base
images và Maven dependencies. Build không chạy test hoặc kết nối cloud; test chạy
riêng trên DB thử. `app-storage` giữ dữ liệu cục bộ cần bền vững qua recreate.
File upload chính vẫn ở R2, video vẫn stream từ YouTube/CDN, không copy vào image.

Trang học và AI dùng lại dữ liệu/cấu hình cloud hiện có. Bản PlayerJS Builder hợp
lệ phải đặt vào `src/main/resources/static/js/vendor/playerjs.js` **trước build**;
image không tự tải PlayerJS demo. Xem [hướng dẫn video](lesson-video-learning.md).
MP4 và HLS đều dùng PlayerJS chính thức; nếu thiếu bundle thì cả hai báo chưa cài player.
YouTube vẫn dùng player độc lập. Cài đặt AI có prompt riêng cho bài học/video và hỗ trợ,
cùng nút lấy model qua Base URL + `/models`; xem [hướng dẫn AI](ai-settings.md).
V21 thêm prompt, V22–V24 bổ sung curriculum/video và V25 bổ sung assessment.
Cloud hiện đã áp dụng V25; những lần khởi động tiếp theo chỉ validate mặc định.
Nếu môi trường khác còn migration pending, cần backup/review trước job migration riêng.
Không thay khóa AI, provider/model hoặc hạn mức khi migrate.
Không có ClamAV thì file upload tiếp tục cách ly, không tự bật scan bypass.

### Bản assessment — 14/10/2026

- Container `edulab` hiện chạy `edulab-khtn8:assessment-v25-20261014`, Compose pin
  và `latest` cùng image. Docker healthy, health UP, restart count 0; job validate exit 0.
- Cloud đã backup, restore khớp trên MySQL tách biệt và diễn tập Flyway; job chỉ
  áp dụng V25. Dữ liệu/schema cũ được giữ, FK/CHECK TABLE đạt. Approval chỉ dùng
  một lần; app Flyway tắt, approval persistent false, `.env.remote` không đổi.
- 303 test Java đạt (gồm Docker/Testcontainers, không skip), 6 test Compose đạt;
  assessment/teacher/video browser regression và GET-only live desktop/mobile đạt.
- Giữ port `127.0.0.1:8088`, storage volume, runtime secrets/khóa, cookie Secure,
  scan bypass false. ClamAV chưa cấu hình và PDF capability tắt: nhập tài liệu
  bị chặn an toàn, chưa test AI/ClamAV thật; không đổi provider/model/cấu hình cloud.
- Backup/report riêng tư:
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/assessment-v25-20261014/`.
  Image trước được giữ nhưng thiếu V25 cho job validate; không dùng restore cloud
  hay bỏ guard để rollback, không `down -v`.

## 4. HTTPS và reverse proxy

Ứng dụng cố định profile `remote`, cookie Secure, scan bypass false. Truy cập HTTP
chỉ phù hợp healthcheck; **đăng nhập web cần HTTPS**. Dùng reverse proxy HTTPS hiện
có trên host, upstream `http://127.0.0.1:8088`, giữ prefix `/edulab`, gửi `Host` và
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

### Upload R2 bị CORS ở cổng 8088

Browser gửi raw `PUT` trực tiếp lên R2, nên `Access-Control-Allow-Origin` phải do
**R2 trả về**. Thêm CORS trong Spring, tăng EventEmitter listeners, dùng `no-cors`
hay restart container không sửa được preflight ở storage.

1. Trong Cloudflare → R2 → bucket đang cấu hình → Settings → CORS, thêm rule từ
   `/Users/ngttu/Workspace/project_asm_prj/docker/r2-cors.json`. Cho phép đúng
   `http://127.0.0.1:8088` và `http://localhost:8088`, method `PUT`, header
   `Content-Type`. Giữ rule/origin cũ, thêm origin HTTPS thật khi dùng reverse proxy;
   không dùng `*`, không mở public bucket, không thêm `/edulab` hoặc dấu `/` cuối.
2. Kiểm tra preflight (không cần URL có chữ ký, không ghi object):

   ```bash
   curl -i --max-time 15 -X OPTIONS \
     'https://c9da795e23235545fa54e8c7ee61d3cf.r2.cloudflarestorage.com/edulab/edulab/staging/cors-check' \
     -H 'Origin: http://127.0.0.1:8088' \
     -H 'Access-Control-Request-Method: PUT' \
     -H 'Access-Control-Request-Headers: content-type'
   ```

   Kỳ vọng HTTP 200/204, `Access-Control-Allow-Origin` đúng origin và method/header
   được cho phép. `403` không có allow-origin cho thấy cần kiểm tra bucket/rule/origin.
   Policy có thể cần tới 30 giây để cập nhật; thử lại bằng phiên browser mới.
3. Cấp **URL mới** rồi upload lại, hoặc dùng nút kiểm tra PUT/CORS trong admin.
   URL upload hết hạn sau 300 giây; không tái sử dụng URL từ log cũ, không log URL
   mới vì đây là bearer token. Browser tự đặt `Content-Length` đã ký, JS chỉ đặt
   `Content-Type`; không gửi app cookie/JWT/CSRF sang R2.
4. CORS thông không có nghĩa là file đã CLEAN. File diễn đàn/AI thiếu ClamAV vẫn
   phải cách ly. Riêng ảnh mới qua luồng Cộng đồng được chủ sở hữu yêu cầu bỏ quét
   antivirus ngày 09/10/2026; xem `/Users/ngttu/Workspace/project_asm_prj/docs/social-community.md`.
   Không bật scan bypass toàn ứng dụng để thực hiện ngoại lệ này.

Các cảnh báo `contentscript.js`, `ObjectMultiplex`, `app-init-liveness` hoặc
`background-liveness` không có trong source ứng dụng. Nếu xuất hiện, kiểm tra URL
script trong DevTools: `chrome-extension://...` thuộc extension (thường là ví
trình duyệt). Thử profile Chrome sạch/không extension hoặc tắt extension trên site;
cập nhật extension nếu cần. Không dùng `setMaxListeners()` trong ứng dụng để che
cảnh báo từ extension.

Kiểm thử XHR không cần DB/mạng:

```bash
node /Users/ngttu/Workspace/project_asm_prj/src/test/browser/file-upload.cjs
```

Test kiểm tra upload liên tiếp không tích lũy listener, raw PUT không credentials,
progress và cleanup khi thành công/lỗi mạng/timeout/hủy/lỗi gửi đồng bộ. Thông báo
lỗi mạng nêu đúng `window.location.origin` để operator cấu hình R2.

## 5. Chuyển image sang máy khác, không cần source/Java/Maven

```bash
cd /Users/ngttu/Workspace/project_asm_prj
docker image save edulab-khtn8:latest | gzip > target/edulab-khtn8-docker.tar.gz
```

Workspace này đã có archive tại
`/Users/ngttu/Workspace/project_asm_prj/target/edulab-khtn8-docker.tar.gz`
và checksum `.sha256` tương ứng, nên không cần build/export lại nếu source chưa đổi.

Chuyển archive, `docker-compose.yml`, `compose.cloud.yml` và `.env.remote` (qua kênh riêng tư) sang cùng
một thư mục tuyệt đối trên host mới; file env không nằm trong archive. Tại host:

```bash
docker image load -i /absolute/deployment/edulab-khtn8-docker.tar.gz
docker compose --project-directory /absolute/deployment \
  -f /absolute/deployment/docker-compose.yml up -d --no-build --wait
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

### Cấu hình runtime cloud theo yêu cầu tiếp theo — 07/10/2026

- Compose mặc định giờ là cloud-only; cố định hostname `mysql.ngttu.com`, database
  `edulab_assignment`, user `ngttu`, profile `remote`, TLS `VERIFY_IDENTITY`.
  `.env.remote` được bổ sung các default runtime còn thiếu (migration false,
  allowlist/API path/scanner), giữ nguyên mật khẩu/JWT/khóa mã hóa và quyền 0600.
- Container **`edulab` đã tạo sẵn** với biến từ `.env.remote`, command `app`, UID
  10001, bind loopback **8088** vì 8080 đang được tiến trình khác dùng. Image không
  cần build lại cho thay đổi Compose/runtime này; archive image vẫn hợp lệ.
- Job Flyway với `EDULAB_MIGRATE_ON_DEPLOY=false` đã kết nối **cloud thật** và kiểm
  tra history/checksum: còn **2 pending migration**. Không migrate/repair/baseline,
  không ghi dữ liệu; web chưa được khởi động trên schema thiếu. Cần backup và duyệt
  V15/V16 trước `docker compose up -d --no-build --wait`.
- **5 test Compose đạt** bằng fixture env giả, không truy cập DB: cloud override
  datasource local/insecure settings, chuyển đủ secrets từ env, đúng container/port,
  dependency migration và fixture local chỉ hoạt động khi chỉ định file riêng.
  Chạy lại: `python3 /Users/ngttu/Workspace/project_asm_prj/src/test/docker/cloud-config.py -v`.

### Khắc phục `migrate exit 1` và triển khai cloud — 07/10/2026

- Lỗi Docker Desktop `service "migrate" didn't complete successfully: exit 1`
  được xác nhận do **2 pending migration**, approval `false`; không phải mất kết
  nối, sai mật khẩu hoặc checksum. Chủ sở hữu đã cho phép backup/nâng V15–V16.
- Backup riêng tư (quyền 0600/0700), SHA-256, manifest và report tại
  `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-db-backups/v16-20261007T021951Z/`.
  Dump `cloud-before.sql.gz` phục hồi thành công trên MySQL container cô lập;
  số dòng/hash/schema khớp, diễn tập SQL V15/V16 giữ nguyên dữ liệu cũ. Tài khoản
  cloud không có quyền EVENT, nên dump không bao gồm event definitions không thể
  đọc; schema ứng dụng không có event. Dump bao gồm các bảng/dữ liệu/routines/triggers
  tài khoản có quyền đọc, không phải backup toàn bộ MySQL server.
- Job riêng đã áp dụng **chỉ V15/V16** lên cloud, history/checksum V1–V16 hợp lệ.
  Snapshot trước có **40 bảng / 267 dòng**. Trước chạy web, dữ liệu nghiệp vụ/tài
  khoản được đối chiếu trên các cột gốc và giữ nguyên; riêng Flyway history thêm
  đúng 2 dòng V15/V16. Cloud có **44 bảng** sau nâng cấp. Các cột video mới là null;
  kiểm tra khóa ngoại và `CHECK TABLE` đều đạt. Không repair/clean/baseline, không
  đổi mật khẩu web/MySQL, không nhập đè dữ liệu từ DB local.
- `edulab` chạy profile remote/DB cloud, healthcheck `UP`, bind loopback 8088.
  Job `migrate` thường trực approval **false**, exit **0**, chỉ validate schema đã
  đủ. `docker compose start --wait` được chạy lại thành công (đúng luồng đã báo lỗi).
  Public JSP/catalog/login và video assets trả 200; 5 test Compose đạt. Chrome
  kiểm tra public pages/readiness trên desktop 1440px và mobile 390px đều đạt,
  không tràn ngang/page error, không login/upload/gọi AI trong lượt này. Khi app đã
  chạy, scheduled jobs học tập/cleanup hoạt động bình thường trên cloud.
- Docker Desktop Start **không nạp lại env** nếu bạn vừa sửa `.env.remote`;
  dùng `docker compose up -d --no-build --wait` để reconcile/recreate khi cần.
  Không bật approval lâu dài hoặc bỏ job kiểm tra để che lỗi schema/network.

### Triển khai teacher workspace V17 — 08/10/2026

- Được chủ sở hữu phê duyệt sao lưu/nâng V17 trên cloud hiện tại. Tạm dừng app
  trong thời gian backup/nâng cấp để tránh scheduled jobs ghi dữ liệu.
- Backup riêng tư và report tại
  `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-db-backups/v17-20261008T020217Z/`.
  SHA-256 hợp lệ; phục hồi trên MySQL cô lập khớp schema/số dòng/hash của 44 bảng.
  Diễn tập SQL V17 giữ nguyên dữ liệu cũ. Không gồm event definitions do tài khoản
  không có quyền EVENT; đây không phải backup toàn bộ MySQL server.
- Flyway chỉ áp dụng V17, history V1–V17 hợp lệ. Đối chiếu toàn bộ dữ liệu trên
  các cột cũ trước khi chạy lại app đều khớp; kiểm tra khóa ngoại và CHECK TABLE đạt.
  Schema có 46 bảng sau nâng cấp. Không repair/clean/baseline/import dữ liệu cloud.
- Image `edulab-khtn8:teacher-v17` (đồng thời tag `latest`) đã chạy trong container
  `edulab`, bind `127.0.0.1:8088`, healthcheck `UP`/Docker `healthy`. Job `migrate`
  thường trực exit 0; approval và Spring startup migration vẫn `false`.
  Giữ nguyên `.env.remote`, khóa mã hóa và volume storage. Image cũ được giữ bằng
  tag `edulab-khtn8:before-v17`; rollback schema không được tự động thực hiện.
- Archive image Linux/amd64 khoảng 212 MiB:
  `/Users/ngttu/Workspace/project_asm_prj/target/edulab-khtn8-v17-docker.tar.gz`,
  kèm `.sha256` đã kiểm tra. Archive không chứa database/volume/secrets runtime.
- HTTP/JSP và JavaScript teacher trả 200; kiểm tra WAR đang chạy khớp migration V17,
  authoring JS và roster JSP hiện tại. Browser public pages desktop/mobile đạt,
  không lỗi JavaScript hoặc tràn ngang; 5 test Docker Compose đạt. Không chạy luồng
  ghi dữ liệu teacher/student, upload hoặc AI trên cloud trong smoke test này.
- Cookie Secure vẫn bật; truy cập đăng nhập thực tế cần HTTPS proxy tin cậy.
  Cổng loopback HTTP dùng kiểm tra nội bộ, không phải public deployment endpoint.

### Triển khai teacher workspace V18 — 08/10/2026

- Theo yêu cầu cập nhật container của chủ sở hữu, đã dừng app trong thời gian
  backup/nâng cấp, phục hồi thử trên MySQL cô lập và diễn tập V18 thành công.
- Backup riêng tư, SHA-256, manifest và report:
  `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-db-backups/v18-20261008T060817Z/`.
  Bản phục hồi khớp schema/số dòng/hash trước nâng cấp. Không gồm event definitions
  do tài khoản không có quyền EVENT; không phải backup toàn bộ MySQL server.
- Flyway chỉ áp dụng V18; history/checksum V1–V18 hợp lệ. Dữ liệu trên toàn bộ
  cột gốc được đối chiếu và giữ nguyên trước khi chạy app; khóa ngoại/CHECK TABLE đạt.
  Không repair/clean/baseline hoặc nhập đè database cloud.
- Container `edulab` chạy `edulab-khtn8:teacher-v18`, image cũng được gắn tag
  `latest` để lệnh Compose mặc định không quay về V17. Image ID:
  `sha256:9c225a5bf98c1a6cdcc49cc836fd638b56c82e23c08654f603cc8bd8db9b37c0`.
  Bind `127.0.0.1:8088`, health `UP`/`healthy`, job migrate exit 0.
- Giữ nguyên `.env.remote`, các khóa runtime và volume `edulab_app-storage`.
  Approval migration và Spring startup migration vẫn `false`.
  Image trước nâng cấp giữ tại `edulab-khtn8:before-v18` và `teacher-v17`;
  không tự động hạ schema hoặc phục hồi đè cloud khi rollback ứng dụng.
- HTTP public/JSP và JavaScript workspace trả 200; 5 test Docker Compose đạt.
  Các luồng CRUD được kiểm thử trên database dùng một lần, không tạo fixture
  teacher/student trên cloud trong đợt triển khai này.

### Triển khai Cộng đồng tách biệt V19 — 08/10/2026

- `docker-compose.yml` chỉ có `app` + job `migrate`, datasource cố định tại
  `mysql.ngttu.com` qua `sslMode=VERIFY_IDENTITY`. Đã gỡ `run-local.sh`,
  `compose.local.yml` và mọi giá trị mặc định MySQL local trong cấu hình app.
  Không còn container/volume MySQL local chạy kèm deployment; bộ test loopback
  dùng database tạm thời riêng và không nằm trong Compose cloud.
- Đã dừng web để backup V18, dump/kiểm SHA-256, phục hồi thử trên container MySQL
  **cô lập dùng một lần**, đối chiếu 47 bảng và diễn tập V19. Container thử đã
  được xóa. Bản backup/report riêng tư nằm tại
  `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-db-backups/v19-20261008T131112Z/`.
  Do tài khoản cloud không có quyền EVENT, dump không gồm event definitions;
  không phải bản sao lưu toàn bộ MySQL server.
- Job Flyway riêng đã áp dụng **chỉ V19**. History V1–V19 hợp lệ, 5 bảng cộng đồng
  mới được thêm, toàn bộ dữ liệu trên các cột/bảng cũ giữ nguyên; kiểm tra khóa
  ngoại và `CHECK TABLE` đạt. Không repair/clean/baseline/import database cloud.
- Container `edulab` dùng image `edulab-khtn8:community-v19` (đồng thời tag
  `latest`), image ID `sha256:e23e8076b8072123c86ec9d6e5bd2930a3a55da104ea8c271e43e392e2a215d3`.
  Image trước nâng cấp giữ tại `edulab-khtn8:before-community-v19`; volume
  `edulab_app-storage` và `.env.remote` được giữ nguyên. Bind `127.0.0.1:8088`,
  `health=UP`/Docker `healthy`, migration job mặc định exit 0 và approval
  `false`; startup migration vẫn tắt.
- Các tài nguyên JS/CSS, trang public và trang riêng yêu cầu đăng nhập trả đúng
  HTTP; không đăng bài/tải ảnh thử trên cloud. 5 bài kiểm thử Compose đạt.
  **Lưu ý upload ảnh:** Storage cloud bật nhưng `EDULAB_CLAMAV_HOST` chưa cấu
  hình, nên ảnh mới vẫn cách ly `PENDING_SCAN`, chưa đăng lên bảng tin được.
  Cần triển khai ClamAV INSTREAM thật trên mạng tin cậy rồi cấu hình host/port
  trước khi cho phép ảnh người dùng; không bật `EDULAB_DEV_SCAN_BYPASS`.

### Cập nhật giao diện Cộng đồng — 08/10/2026

- Bỏ banner, giữ bố cục Trang chủ và cột tiện ích; composer/bảng tin theo kiểu
  mạng xã hội với menu ba chấm, avatar tròn và hàng Thích/Bình luận/Chia sẻ.
- Image đang chạy: `edulab-khtn8:community-feed-v19` (cũng tag `latest`), ID
  `sha256:f09931c8f1427bdc3b0ece552e68e5e7c0b5c52b877507716165f0d6f2c69087`.
  Rollback: `edulab-khtn8:before-community-feed-v19`, ID
  `sha256:97ee607420864d5c4609a278e82f170d009c2a936259c88be7c1b596cd81f5e2`.
- So sánh nội dung WAR với bản đang chạy trước cập nhật: chỉ thay 4 file
  `WEB-INF/jsp/community/list.jsp`, CSS/JS cộng đồng và `icons.svg`.
  Không thay Java backend hoặc migration; giữ nguyên biến môi trường,
  volume và bind `127.0.0.1:8088`.
- Đã chạy 3 test `SocialCommunitySecurityTest` trong
  `maven:3.9.9-eclipse-temurin-21`; test JSP thật `SocialCommunityViewTest`
  và browser test API giả lập đạt (7 viewport 320–1440 px). Chi tiết tại
  `/Users/ngttu/Workspace/project_asm_prj/docs/social-community.md`.
- Chỉ recreate `app` với `--no-deps --no-build --wait`. Sau triển khai:
  Docker `healthy`, health `UP`, HTTP CSS/JS/icon và JSP trong WAR khớp
  byte-for-byte với nguồn. Không đăng bài/upload thử, không chạy migration;
  `SPRING_FLYWAY_ENABLED`, `EDULAB_MIGRATE_ON_DEPLOY` và scan bypass đều `false`.

Rollback chỉ giao diện/ứng dụng, không khôi phục database:

```bash
EDULAB_IMAGE=edulab-khtn8:before-community-feed-v19 docker compose \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
```

Giữ `EDULAB_IMAGE` tương ứng cho lần triển khai sau rollback; mặc định
`latest` hiện trỏ bản giao diện mới.

## Phát hành bình luận/cảm xúc V20

- Phạm vi nguồn gồm workspace giáo viên, Cộng đồng độc lập, motion UI và 7 cảm xúc.
  Nâng schema từ V19 chỉ thêm `community_likes.reaction_type` với default `LIKE`
  và CHECK constraint; không sửa migration đã chạy hoặc xóa dữ liệu cũ.
- Kiểm tra phát hành ngày 08/10/2026: `mvn -B -ntp package` với JDK 21 và MySQL
  dùng một lần đạt **221 test, 0 lỗi, 0 bỏ qua**, tạo WAR thành công. Hai assertion
  tổng migration trong bộ tích hợp đã cập nhật từ 19 lên 20. 5 test Compose,
  browser motion và UI cộng đồng 7 viewport đều đạt.
- Quy trình: build image gắn revision Git; giữ image `community-feed-v19` để
  rollback; dừng app; backup DB với SHA-256; phục hồi trên MySQL cô lập và diễn tập
  V20; áp dụng đúng một migration bằng job riêng có approval một lần; kiểm tra dữ
  liệu trên các cột gốc và lượt thích; recreate app rồi kiểm tra health/assets.
- Không ghi approval lâu dài vào `.env.remote`. `SPRING_FLYWAY_ENABLED`,
  `EDULAB_MIGRATE_ON_DEPLOY` và `EDULAB_DEV_SCAN_BYPASS` phải giữ `false`.
  Backup/report vận hành riêng tư lưu dưới
  `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-db-backups/`, không vào Git/image.
  Dump không gồm event definitions do quyền tài khoản; không phải backup toàn server.
- Không chạy app V20 trên schema V19. Rollback app có thể giữ cột additive V20;
  app v19 chỉ hiển thị các loại cảm xúc như lượt thích, không tự hạ schema.

Rollback riêng ứng dụng về image đã xác nhận, giữ nguyên database/volume:

```bash
EDULAB_IMAGE=edulab-khtn8:community-feed-v19 docker compose \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
```

Sau rollback, tiếp tục chỉ định image này cho các lệnh Compose; không tự dùng
`latest` nếu tag đó đã trỏ V20. Xem report của lần phát hành để biết image ID,
commit và trạng thái triển khai thực tế. ClamAV và dọn ảnh R2 mồ côi vẫn là
công việc riêng, không bật scan bypass để thay thế.

## Triển khai cổng admin — 09/10/2026 (+07)

Lượt triển khai được chủ sở hữu phê duyệt, release timestamp UTC 08/10/2026:

- Image release: `edulab-khtn8:admin-20261008t170809z-12e2d93359c6`, đồng thời tag `latest`.
  Image ID: `sha256:b941bcf8234d402234c987b9237030f0bb2b65d27829a758d80928ce0e95f34a`.
- Rollback: `edulab-khtn8:before-admin-20261008t170809z`, image ID
  `sha256:eece8fdd2ef5684351d61e83c8c3c24ac0a5399493b87095c0701d150706eebd`.
  Đây là phát hành từ worktree có thay đổi chưa commit; nhãn revision Git là base
  commit, không đại diện riêng toàn bộ nguồn release. Report lưu source hash và
  đối chiếu byte-for-byte 132 file JSP/static/migration trong WAR.
- Cloud MySQL vẫn dùng TLS `VERIFY_IDENTITY`; probe kết nối thật có TLS cipher.
  Validate 20 migration thành công, không pending migration. Job Compose `migrate`
  chỉ validate, exit 0; không áp dụng migration/repair/clean/baseline/import.
- `edulab` dùng UID 10001, bind `127.0.0.1:8088`, Docker `healthy` và health `UP`.
  Biến môi trường, `.env.remote` (0600), secrets/khóa mã hóa, volume
  `edulab_app-storage`, port và cấu hình giảm quyền đều giữ nguyên.
- Snapshot container trước và bản sao `.env.remote` được bảo vệ riêng tư; **không
  tạo dump database mới trong lượt không đổi schema này**. Fingerprint trước–sau
  khớp 10 bảng lớp học/nội dung đã chọn, không chứng minh toàn bộ DB bất biến.
  Scheduled jobs vẫn chạy; một lần login thất bại có thể ghi auth/audit metadata.
- Đã xác nhận bằng chứng Maven **240 tests, 0 failures/errors/skipped** và browser
  admin **24 phân hệ, 1440/768/390/320px** trên DB dùng một lần. Chạy lại 5 test
  Compose, syntax check 26 file JavaScript và `git diff --check` đều đạt. Public
  browser smoke trên cloud đạt ở 1440/390px, không lỗi JavaScript/tràn ngang.
- **Admin cloud chưa được kiểm chứng sau đăng nhập:** thông tin đăng nhập lưu cũ
  trả 401; probe chỉ đọc xác nhận tài khoản ADMIN vẫn enabled nhưng mật khẩu lưu
  không khớp hash cloud. Không đổi/reset mật khẩu, không tạo tài khoản, không thử
  CRUD/upload/AI, không logout để tránh thu hồi các phiên quản trị đang dùng.
  Cookie authenticated, dashboard, navigation và detail/form admin cloud chưa xác nhận.
  Bản mới được giữ vì health/public checks đạt và lỗi đã xác định là credential cũ,
  không phải bằng chứng lỗi release. Chủ sở hữu cần kiểm tra bằng mật khẩu hiện tại
  qua HTTPS trước khi coi authenticated smoke hoàn tất.
- Proxy HTTPS test-only trên loopback đã tắt; không cấu hình domain/certificate
  hoặc HTTPS công khai mới. Cookie Secure và scan bypass false vẫn giữ nguyên.
- Bằng chứng, report và lệnh rollback riêng tư:
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/admin-20261008T170809Z/`.
  Không đưa thư mục này, backup env hoặc output inspect/config chưa che secrets vào Git.

Rollback **chỉ ứng dụng**, không hạ schema hoặc khôi phục đè DB/volume:

```bash
EDULAB_IMAGE=edulab-khtn8:before-admin-20261008t170809z docker compose \
  --project-directory /Users/ngttu/Workspace/project_asm_prj \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
curl -fsS http://127.0.0.1:8088/edulab/actuator/health
```

Sau rollback, tiếp tục chỉ định `EDULAB_IMAGE` rollback cho các lệnh Compose;
`latest` vẫn trỏ release admin mới trừ khi chủ sở hữu chủ động đổi tag. Không dùng
`down -v`, không xoay secrets hoặc phục hồi database chỉ để rollback ứng dụng.

## Sửa upload CORS và triển khai container — 09/10/2026 (+07)

- Nguyên nhân: rule CORS R2 hiện có chỉ cho cổng 8085, không khớp origin container
  `http://127.0.0.1:8088`. Preflight trước sửa trả 403, không có allow-origin.
  Đã thêm rule riêng cho `http://127.0.0.1:8088` và `http://localhost:8088`, chỉ
  `PUT`/`Content-Type`, giữ rule 8085; không mở public bucket hoặc wildcard.
- Preflight sau sửa trả 204 và đúng allow-origin. Chrome sạch kiểm tra raw PUT
  thật trước/sau deploy từ cả hai origin, ký Content-Type/Content-Length như ticket
  ứng dụng, progress đạt 100%. Probe riêng trong `edulab/probe/` đã HEAD/GET kiểm
  tra nội dung và DELETE; không tạo file/bài viết/tài khoản thử trong cloud DB.
- XHR dọn abort listener cùng các callback khi hoàn tất/lỗi/hủy, kể cả `send()`
  lỗi đồng bộ; thông báo lỗi mạng nêu đúng origin và nơi cấu hình CORS trên R2.
  Mẫu admin dùng cổng 8088; JSON và hướng dẫn chẩn đoán được lưu trong source.
- Maven `verify -Dtest=!MysqlIntegrationTest` đạt **237 tests, 0 failures/errors/
  skipped** trên DB dùng một lần `127.0.0.1:3307/edulab_test`. Bộ Testcontainers
  3 test được loại có chủ ý, không tính là đã chạy. Lượt đầu thiếu quyền fixture DB;
  đã chuẩn bị riêng `edulab_test` trong container test, không sửa DB fixture admin.
  5 test Compose, XHR regression, syntax 26 JS và browser Cộng đồng offline 7
  viewport đều đạt. `git diff --check` đạt.
- Image Linux/amd64: `edulab-khtn8:r2-cors-20261008t175233z-6fb9f23a65f0`,
  đồng thời tag `latest`; ID
  `sha256:e1951bee06cc396a7a1aeeab777a0300b51cd2993f234b013eceb78c4422da97`.
  Source release có thay đổi chưa commit; revision là base Git, source hash riêng
  được ghi trong report. Đối chiếu 136 file resource/JSP trong WAR với source và
  WAR trong container với image đạt; HTTP `file-upload.js` khớp source.
- `edulab` healthy, health UP, UID 10001, bind `127.0.0.1:8088`. Job migrate exit 0
  **chỉ validate V20**. Không đổi `.env.remote`, runtime secrets/khóa, storage volume,
  cookie Secure, scan bypass hoặc migration approval; không thêm migration/schema.
  Fingerprint 10 bảng nội dung/lớp học chọn trước/sau khớp, không chứng minh toàn bộ
  DB bất biến vì scheduled jobs vẫn chạy. Không tạo dump database trong lượt này.
- Public smoke `/`, `/courses`, `/login` ở 1440/390px đạt không lỗi JS ứng dụng,
  resource trang hoặc overflow trong lượt kiểm tra lại. Lượt đầu có một console
  404 đơn lẻ không tái hiện; không che lỗi console hoặc sửa ngoài phạm vi.
  Không kiểm thử đăng bài ảnh end-to-end bằng tài khoản cloud. ClamAV vẫn chưa
  cấu hình nên file người dùng tiếp tục cách ly; không bật bypass để làm ảnh đăng được.
- `contentscript.js`/ObjectMultiplex không thuộc source và không xuất hiện trong
  smoke ứng dụng bằng Chrome sạch. Cần xem URL extension trong browser gốc để xác
  nhận extension cụ thể; không thay `setMaxListeners()` trong app.
- Report, snapshot/env backup riêng tư và lệnh rollback tại
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/r2-cors-20261008T175233Z/`.
  Không đưa thư mục này hoặc URL probe/secrets vào Git/image.

Rollback chỉ ứng dụng (giữ CORS đã sửa, database và volume):

```bash
EDULAB_IMAGE=edulab-khtn8:before-r2-cors-20261008t175233z docker compose \
  --project-directory /Users/ngttu/Workspace/project_asm_prj \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
curl -fsS http://127.0.0.1:8088/edulab/actuator/health
```

Sau rollback giữ `EDULAB_IMAGE` tương ứng, vì `latest` vẫn trỏ image CORS mới.

## Bỏ antivirus riêng cho ảnh Cộng đồng — 09/10/2026 (+07)

- Theo yêu cầu chủ sở hữu, **ảnh mới qua luồng Cộng đồng không gọi ClamAV**.
  Không bật `EDULAB_DEV_SCAN_BYPASS`; quét file diễn đàn/AI giữ nguyên. Chi tiết
  phạm vi và đánh đổi bảo mật ở `/Users/ngttu/Workspace/project_asm_prj/docs/social-community.md`.
- Ticket riêng `/api/v1/files/community/presign` dùng prefix server sinh
  `edulab/community/staging/` và `edulab/community/files/`. Giữ authentication,
  CSRF, quota, presigned Content-Type/Content-Length, copy final và kiểm JPEG
  ≤500 KiB/1600px/không metadata, hash và quyền sở hữu. Ảnh ghi nhận chưa quét,
  không gắn nhãn CLEAN giả; chỉ ảnh đã kiểm định từ luồng này được đăng/hiển thị
  ngay. Scheduler không quét chúng; diễn đàn/AI từ chối tái dùng file prefix này.
- Không chuyển file cũ sang CLEAN; ảnh upload từ luồng generic trước thay đổi
  cần chọn/upload lại ở Cộng đồng. R2 lifecycle nếu có cần thêm staging prefix
  mới, không thêm lifecycle xóa final images. CORS và quyền bucket giữ nguyên.
- `clean verify -Dtest=!MysqlIntegrationTest` đạt **243 tests, 0 failures/errors/
  skipped** trên DB dùng một lần. 3 test Testcontainers loại có chủ ý. Test mới
  kiểm no-scanner/publish/preview, file sai định dạng/size/metadata, file tên giả
  không bypass, quyền xem ảnh sau ẩn bài và chặn tái dùng qua diễn đàn/AI.
  Lượt verify đầu phát hiện assertion fixture sai scope biến; đã sửa và chạy
  clean verify đạt. Browser offline 7 viewport đạt luồng nén/PUT giả lập/complete/
  publish/hiển thị khi `scanStatus=PENDING_SCAN,communityReady=true`. 5 test Compose,
  XHR regression, syntax 27 JS và whitespace checks đạt.
- Image: `edulab-khtn8:community-noscan-20261008t181636z-6fb9f23a65f0`, tag `latest`;
  ID `sha256:11a0c45bdb640741e571df00183fe9328248d14a708c3a915ed562ca30acdb7e`.
  Source release gồm thay đổi chưa commit; revision là base Git và report có
  source hash riêng. 136 file JSP/resources trong WAR khớp source, WAR đang chạy
  khớp image; `social-community.js` và `file-upload.js` qua HTTP khớp source.
- Container `edulab` healthy, health UP, port `127.0.0.1:8088`. Job migrate chỉ
  validate V20, exit 0; không thêm/chạy migration/schema, không thay `.env.remote`,
  runtime guards/secret/khóa, port hoặc storage volume. Fingerprint 10 bảng
  nội dung/lớp học chọn trước/sau khớp; không chứng minh toàn DB bất biến.
- Public browser `/`, `/courses`, `/login` ở 1440/390px không lỗi JS/resource/
  overflow. POST presign mới và generic chưa đăng nhập vẫn bị từ chối. Không
  đăng nhập/tạo bài ảnh mẫu trên cloud, nên không tuyên bố image-post cloud E2E
  đã chạy; tích hợp no-scanner/publish/preview kiểm bằng R2 mock trên DB thử.
- Report riêng tư và rollback:
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/community-noscan-20261008T181636Z/`.
  Image trước được giữ. Rollback không hạ schema/khôi phục DB hoặc xóa volume.
  Sau rollback ảnh prefix Cộng đồng mới chưa CLEAN sẽ không hiển thị ở bản cũ;
  không tự sửa dữ liệu hoặc quét status để tránh che việc chưa quét.

```bash
EDULAB_IMAGE=edulab-khtn8:before-community-noscan-20261008t181636z docker compose \
  --project-directory /Users/ngttu/Workspace/project_asm_prj \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
curl -fsS http://127.0.0.1:8088/edulab/actuator/health
```

Giữ `EDULAB_IMAGE` rollback nếu dùng bản cũ; `latest` vẫn trỏ bản community-noscan.

## Sửa JPEG canvas upload Cộng đồng — 09/10/2026 (+07)

- Tái hiện lỗi: JPEG từ Chrome canvas thêm ICC color profile APP2, bị policy
  metadata-free backend từ chối và trả thông báo chung về upload hết hạn/hủy/file
  sai định dạng. Client nay loại APP metadata/comment, chỉ giữ JFIF và dữ liệu
  JPEG trước presign/PUT; không nới lỏng backend, không bật scan bypass.
- Sửa encoder cho ảnh nhỏ dưới 320 px; không phóng lớn. Composer không còn dòng
  “Tối đa 5 ảnh · Tự động nén và kiểm tra định dạng · Không quét antivirus”, nhãn
  dung lượng hoặc thông báo nén. Giữ preview/progress, chặn gửi lặp khi đang xử lý,
  giữ draft/ảnh khi lỗi và xin ticket mới khi thử lại. Backend phân biệt thông báo
  EXPIRED/CANCELLED/REJECTED, không gộp các nguyên nhân khác nhau.
- Maven `verify -Dedulab.test.browserUploads=true -Dtest=!MysqlIntegrationTest`
  đạt **246 tests, 0 failures/errors/skipped** trên MySQL dùng một lần
  `127.0.0.1:3307/edulab_test_admin`. 3 test Testcontainers loại có chủ ý.
  Browser offline 7 viewport, XHR regression và 5 test Compose đạt. Bốn JPEG thật
  từ browser (ảnh lớn, PNG/JPEG/WebP nhỏ) qua backend policy và complete → đăng
  bài → preview trên MySQL test với R2 mock đạt; transaction rollback. Metadata
  ICC/EXIF giả vẫn bị backend từ chối. Không gọi AI hoặc thay cấu hình R2 cloud.
- Image Linux/amd64 `edulab-khtn8:community-upload-20261008t183434z`, tag `latest`;
  ID `sha256:25041b0f756b5ec9b5a0f8c729b32e99d046a305fa19040c854603c849941af4`.
  Image/WAR đối chiếu source manifest; WAR đang chạy khớp image, HTTP JS khớp
  source. Container `edulab` Docker healthy, health UP, port `127.0.0.1:8088`.
- Job migrate chỉ validate V20; không áp dụng migration. Giữ nguyên `.env.remote`,
  secrets/khóa mã hóa, runtime guards false, volume/mount/port/security options.
  Fingerprint các bảng nội dung/lớp học đã chọn trước/sau khớp; không chứng minh
  toàn DB bất biến. Public browser desktop/mobile không lỗi JS/tràn ngang;
  presign generic/Cộng đồng chưa đăng nhập bị từ chối.
- **Không đăng bài/upload ảnh bằng tài khoản cloud trong lượt này.** Xác nhận
  nghiệp vụ bằng browser bytes + MySQL test/R2 mock không thay thế cloud E2E.
  Report riêng tư và rollback image:
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/community-upload-20261008T183434Z/`.

Rollback chỉ ứng dụng, không hạ schema/phục hồi DB/xóa volume:

```bash
EDULAB_IMAGE=edulab-khtn8:before-upload-20261008t183434z docker compose \
  --project-directory /Users/ngttu/Workspace/project_asm_prj \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
curl -fsS http://127.0.0.1:8088/edulab/actuator/health
```

Sau rollback giữ `EDULAB_IMAGE` cụ thể; `latest` vẫn trỏ bản đã sửa upload.

## Cập nhật sticky tiện ích + khung xem ảnh/bài viết ngày 09/10/2026 (+07)

- Container `edulab` dùng image `edulab-khtn8:community-viewer-20261008t185619z`.
  Cột tiện ích giữ vị trí khi cuộn desktop; bấm bài có ảnh mở dialog với ảnh lớn,
  thông tin bài và bình luận, giữ draft/cảm xúc/cursor khi đóng. Mobile xếp dọc.
- Maven verify: **246 tests, 0 failures/errors/skips** trên MySQL dùng một lần;
  `MysqlIntegrationTest` (3 Testcontainers tests) bị loại có chủ đích. Browser mock:
  7 viewport, sidebar 1280/1440 px, popup 320/390/768/1440 px; chuyển ảnh, focus/Esc,
  comment CRUD/phân trang/retry, cảm xúc, giữ draft/scroll và xóa bài đều PASS.
  XHR upload regression và 5 kiểm tra Compose/Docker PASS.
- Docker healthy, health API UP, WAR và các CSS/JS được phục vụ khớp source.
  Giữ nguyên schema V20, `.env.remote`, khóa/R2, mount, port và security options.
  Job migrate chỉ validate. Smoke browser live kiểm tra trang public và redirect
  Cộng đồng → Đăng nhập; **chưa kiểm thử tương tác tài khoản thật hoặc PUT R2 thật**.
- Báo cáo riêng ở `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/community-viewer-20261008T185619Z/`;
  pointer `/Users/ngttu/Workspace/project_asm_prj/.tools/community-viewer-deployment-last`.

Rollback chỉ ứng dụng, không rollback schema và không xóa volume:

```bash
EDULAB_IMAGE=edulab-khtn8:before-viewer-20261008t185619z docker compose \
  --project-directory /Users/ngttu/Workspace/project_asm_prj \
  -f /Users/ngttu/Workspace/project_asm_prj/compose.cloud.yml \
  up -d --no-deps --no-build --wait --wait-timeout 180 app
curl -fsS http://127.0.0.1:8088/edulab/actuator/health
```

Sau rollback giữ `EDULAB_IMAGE` cụ thể; `latest` trỏ bản viewer đã xác nhận healthy.