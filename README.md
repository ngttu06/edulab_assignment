# EduLab KHTN 8

**Deployment:** container `edulab` chỉ kết nối MySQL cloud qua TLS;
bản nguồn hiện có migration đến V25 (quiz/kiểm tra, xem lại, báo cáo và nhập đề AI).
Container assessment đã triển khai ngày 14/10/2026; cloud đã backup, diễn tập restore
và nâng V24 → V25 bằng job riêng. Nhập PDF/DOCX còn cần ClamAV/model phù hợp. Xem
`/Users/ngttu/Workspace/project_asm_prj/docs/assessment.md`. Không còn script/Compose
MySQL local cho deployment hoặc fallback datasource trong profile remote.
Hướng dẫn vận hành và hạn chế scanner ảnh tại
`/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`. Các mục ghi rõ
ngày cũ ở bên dưới là lịch sử kiểm thử, không phải cấu hình triển khai hiện tại.

Website học tập EduLab, khởi đầu với Khoa học tự nhiên lớp 8 và đã có danh mục đa môn, dùng **Java 21, Spring Boot 3.5.16, Tomcat 10.1, JSP/JSTL, Bootstrap, MySQL 8 và Flyway**, đóng gói WAR. Giao diện tiếng Việt lấy cảm hứng từ bố cục Mapstudy; giữ thương hiệu EduLab, không đưa ảnh giáo viên, quảng cáo hoặc mã Next.js của Mapstudy vào ứng dụng.

Đây là **MVP học tập, chưa phải bản production-ready**. Không dùng dữ liệu cá nhân thật của học sinh để kiểm thử.

## Chạy Docker với MySQL cloud

Image chứa ứng dụng, Java 21, embedded Tomcat/JSP và tài nguyên tĩnh. Không cần
Java/Maven trên máy chạy, không khởi tạo MySQL local, không đưa secret vào image.
Giữ cấu hình/khóa mã hóa hiện có trong `.env.remote` riêng tư, xem
[hướng dẫn Docker cloud](docs/docker-cloud.md) về backup/migration, HTTPS và chuyển image.

```bash
cd /Users/ngttu/Workspace/project_asm_prj
docker compose up -d --no-build --wait
```

Mặc định bước chuẩn bị DB chỉ validate. Nếu cloud thiếu migration của bản nguồn, lệnh dừng trước
khi chạy app; sau khi backup và review, đặt `EDULAB_MIGRATE_ON_DEPLOY=true` trong
`.env.remote` để duyệt job migration riêng. Không sửa schema tự động trong app.
Compose mặc định chỉ dùng `mysql.ngttu.com:3306/edulab_assignment`, container web
tên **`edulab`**, nạp mật khẩu/JWT/khóa mã hóa hiện có từ `.env.remote`; không có
container database hoặc fallback sang DB local. Image đã có sẵn trong workspace;
đổi `--no-build` thành `--build` nếu source thay đổi hoặc máy chưa có image.
Cổng mặc định `127.0.0.1:8088`, context `/edulab`; đăng nhập cần HTTPS vì cookie Secure.
PlayerJS/HLS có bundle chính thức 22.0.7 từ plugin PlayerJS 2.25, đóng gói nguyên
bản trong image; không có player giả/crack. Xem nguồn tải/checksum/CSP giới hạn ở
`src/main/resources/static/js/vendor/PLAYERJS-INSTALL.md`.

**Sửa upload ảnh Cộng đồng — 09/10/2026 (+07):** đã loại ICC/APP2 mà Chrome
thêm vào JPEG canvas trước upload (backend vẫn kiểm metadata nghiêm ngặt), xử lý
được ảnh nhỏ dưới 320 px và ẩn thông tin nén/hướng dẫn kỹ thuật khỏi composer.
Container `edulab` dùng `edulab-khtn8:community-upload-20261008t183434z`, đồng thời
tag `latest`, health UP/healthy; không đổi schema V20, secrets hoặc volume.
246 test backend/MySQL đạt, browser offline 7 viewport và JPEG thật từ browser
qua complete/đăng bài/preview trên MySQL test với R2 mock đều đạt. Chưa chạy đăng
ảnh bằng tài khoản cloud; chi tiết release/rollback trong hướng dẫn Docker cloud.

**Cập nhật admin — 09/10/2026 (+07):** container `edulab` đã chạy image admin mới
trên schema cloud V20, health `UP`/Docker `healthy`; giữ nguyên `.env.remote` và
volume storage, không áp dụng migration. Public browser smoke đạt. Đăng nhập và
duyệt admin trên cloud **chưa xác nhận** vì mật khẩu trong file vận hành cũ không
khớp; không đổi mật khẩu cloud. Chi tiết release/rollback ở
`/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`.

**Trạng thái triển khai 07/10/2026:** cloud đã được backup/phục hồi thử và nâng
V15–V16 theo phê duyệt của chủ sở hữu; dữ liệu bảng cũ được giữ nguyên. Container
`edulab` đang healthy, job `migrate` exit 0, approval thường trực `false`.
Lỗi Docker Desktop Start do thiếu schema đã được khắc phục; lần sau có thể Start
lại stack. Đổi biến môi trường thì dùng `docker compose up` thay vì chỉ Start.

## Chức năng hiện có

- **Cổng quản trị độc lập:** `/admin` có dashboard dữ liệu thật, sidebar riêng, CRUD theo phân hệ, tìm kiếm/phân trang, quản lý giáo viên/lớp/phân công, nội dung, moderation, AI/storage và audit. Xem [phạm vi, safeguards và kiểm thử admin](/Users/ngttu/Workspace/project_asm_prj/docs/admin-portal.md). Không thay đổi schema V20 hay tự triển khai lên cloud.

- **Danh mục khóa học:** trang chủ và `/courses` dùng môn học, khối lớp, số bài, thời lượng và tiến độ từ database; tìm kiếm và lọc theo môn/khối/khóa. Chi tiết khóa học hiển thị chương trình, trạng thái và bài tiên quyết. Không dựng giá bán, đánh giá, thống kê hoặc giáo viên giả.
- **Tài khoản và phân quyền:** ADMIN, TEACHER, STUDENT, PARENT; đăng ký học sinh/phụ huynh; admin tạo tài khoản và bật/tắt tài khoản. JWT access token 15 phút trong cookie HttpOnly; refresh token ngẫu nhiên, lưu hash SHA-256, thời hạn mặc định 14 ngày và xoay vòng khi dùng. Phát hiện dùng lại refresh token sẽ thu hồi token family liên quan và vô hiệu access token hiện tại của tài khoản. Logout thu hồi mọi phiên của tài khoản, không chỉ thiết bị hiện tại.
- **Thông tin cá nhân:** `/profile` cho mọi vai trò đã đăng nhập; sửa họ tên/email, giữ nguyên tên đăng nhập/vai trò. Email không được trùng tài khoản khác. Đổi mật khẩu cần mật khẩu hiện tại và xác nhận mật khẩu mới; tối thiểu 8 ký tự, tối đa 72 byte UTF-8, không được dùng lại mật khẩu hiện tại. Mật khẩu được băm BCrypt; đổi thành công thu hồi mọi access/refresh session, xóa cookie và yêu cầu đăng nhập lại. Chưa có xác minh email, ảnh đại diện hoặc các trường cá nhân bổ sung.
- **Bảo vệ request:** CSRF cho thao tác thay đổi dữ liệu, kể cả login/register/refresh; lấy token qua `/api/v1/auth/csrf`. Token đăng nhập không trả trong JSON hoặc lưu trong browser storage. Có giới hạn đăng nhập trong bộ nhớ của từng instance.
- **Bài học và lộ trình:** bài đã xuất bản, HTML được lọc, mục tiêu học tập, tiến độ riêng và dashboard lấy dữ liệu thật. Bài tiên quyết có thể yêu cầu hoàn thành và đạt điểm quiz tối thiểu; kiểm tra quyền mở bài ở server. Admin tạo và xuất bản bài học, câu hỏi trắc nghiệm, quiz.
- **Học qua video:** thư viện có nút học video theo bài; `/lessons/{id}/video` dùng YouTube IFrame API hoặc PlayerJS miễn phí với m3u8. `/teacher/videos` quản lý một video chính mỗi bài; admin phân công giáo viên theo bài, kiểm tra quyền ở server. Timeline tùy biến trong khung player tô các khoảng đã xem rời rạc, không tô đoạn tua bỏ qua; hỗ trợ tua bằng bàn phím/chạm và fullscreen của khung. YouTube giữ controls chính thức và thêm timeline bên dưới, không can thiệp iframe.
  - [Hướng dẫn video, API và giới hạn](docs/lesson-video-learning.md). Bundle PlayerJS chính thức đã cài ở `src/main/resources/static/js/vendor/playerjs.js`; build lại WAR/image để dùng. MP4 và HLS đều dùng PlayerJS, YouTube dùng player độc lập. Domain video mặc định nhận HTTPS hợp lệ hoặc giới hạn bằng allowlist. Mặc định hoàn thành ở 95%, giáo viên chỉnh được 80–100%. Lưu các khoảng đã xem + resume theo phiên bản, không cộng trùng; đồng bộ mỗi 10 giây/pause/seek. Thay/gỡ video bắt đầu lại tiến độ bài và giữ lịch sử video cũ; bài có video không cho đánh dấu hoàn thành thủ công.
- **Quiz:** PRACTICE và TEST; snapshot câu hỏi/đáp án khi bắt đầu, giới hạn lượt, tiếp tục bài đang làm, tự lưu và khôi phục câu trả lời. Server chấm điểm có trọng số trên thang 10. Khi hết giờ, chỉ chấm câu trả lời đã lưu; không nhận đáp án mới từ request nộp muộn. Nộp lại trả kết quả đã lưu. PRACTICE công bố đáp án sau chấm; TEST còn phải chờ lịch công bố. Có tác vụ hoàn tất bài hết giờ.
- **Lớp học:** giáo viên xem lớp được phân công, giao quiz đã xuất bản với hạn nộp, xem kết quả và nhận xét học sinh. Học sinh và phụ huynh đã liên kết xem bài được giao và nhận xét. Bài được tính hoàn thành khi có lượt chấm nộp trong khoảng từ lúc giao đến hạn nộp; kết quả trước lúc giao không được tính. Admin quản lý lớp, ghi danh và phân công giáo viên.
- **Gia đình:** mã liên kết ngẫu nhiên 128-bit, lưu hash, dùng một lần, hết hạn sau 15 phút. Học sinh/phụ huynh có thể thu hồi liên kết; kiểm tra phạm vi truy cập báo cáo ở server.
- **Diễn đàn:** chuyên mục chung/theo lớp, thảo luận và trả lời, phân trang, khóa/mở/ẩn theo quyền; đính kèm tối đa ba tệp mỗi trả lời.
- **Upload:** JPG/PNG/WebP tối đa 5 MB, PDF tối đa 10 MB, hạn mức 100 MB/tài khoản. Kiểm tra extension/MIME/signature, hash SHA-256, tên lưu ngẫu nhiên ngoài webroot. Download yêu cầu quyền và trạng thái CLEAN, trả dạng attachment. Mặc định tệp bị cách ly; xem giới hạn scanner bên dưới.
- **Trợ giúp AI:** có adapter GateCheap gọi HTTPS `/chat/completions`, lấy ngữ cảnh từ bài đã xuất bản và kiểm tra prerequisite. Mặc định tắt. Hạn mức mặc định 20 yêu cầu/24 giờ, kể cả yêu cầu thất bại; chặn AI khi học sinh đang làm TEST còn thời gian. Có stub chỉ dành cho local. Nội dung trả lời được ghi rõ cần giáo viên kiểm tra.

## Trạng thái kiểm chứng — 30/09/2026

Kiểm tra giao diện mới trên macOS, JDK 21, Maven 3.9.9, MySQL 8.4.11 và **WAR chạy embedded Tomcat tại `127.0.0.1:8083/edulab`**. Deployment Tomcat ngoài cổng 8082 của lần xác nhận trước không bị ghi đè:

| Hạng mục | Kết quả |
|---|---|
| Build WAR + unit/security/local-MySQL tests | **65 test đạt, 0 failure, 0 error, 0 skip**; chủ động loại `MysqlIntegrationTest` dùng Docker/Testcontainers |
| Database dùng để xác nhận | Chỉ `127.0.0.1:3307/edulab_test`; Flyway xác nhận đủ **12 migration** |
| WAR giao diện mới | `/Users/ngttu/Workspace/project_asm_prj/target/edulab-khtn8.war`, context `/edulab`, connector `127.0.0.1:8083` |
| Browser smoke bằng Playwright/Chrome | Đạt: desktop/mobile, bốn vai trò, roadmap, quiz autosave/reload/submit, liên kết gia đình, diễn đàn, giao bài, nhận xét, báo cáo học sinh/phụ huynh, admin, refresh và kiểm tra 401/403 |
| Rendering và lỗi trình duyệt | Smoke nghiệp vụ không có JavaScript page error; homepage mobile 390 px không tràn ngang; ảnh nằm trong `/Users/ngttu/Workspace/project_asm_prj/.tools/screenshots/theme-flows` |
| Theme regression Playwright/Chrome | Đạt ở 1440/1024/768/390/320 px: catalog/filter/rỗng/404, drawer bàn phím, assets/font nội bộ; rendering bốn vai trò ở 1440/768/390 px; không lỗi JavaScript/CSP hoặc tràn ngang trong các trang kiểm tra |
| Auto-update Git | **13 test đạt** bằng Node.js với repo tạm/bare remote local: commit/push, watch, từ chối phân kỳ/staged changes/secret, retry push và khóa đồng thời; không chứng minh quyền truy cập GitHub |
| Log ứng dụng sau deploy/smoke | Không có application ERROR/SEVERE hoặc lỗi render JSP; còn cảnh báo cấu hình AuthenticationProvider của Spring Security |

Đã sửa lỗi `/roadmap` HTTP 500 do MySQL trả predicate dạng số nhưng JSP cần Boolean. Mapper dùng chung chuyển `unlocked`, `completed`, `overdue` thành Java Boolean; có unit test và hồi quy MySQL cho các trạng thái mở/khóa, chờ/hoàn thành/quá hạn. Test refresh-token kiểm tra riêng family bị thu hồi và giữ family khác, không phụ thuộc tổng số phiên có sẵn.

Sau browser smoke, đã chạy lại bộ unit/security/local-MySQL: **65 test đạt, 0 failure/error/skip** trên cùng DB test có dữ liệu smoke. Đã đồng bộ kỳ vọng test AI với ngữ cảnh môn/khối có sẵn và sửa regex bật bộ workflow local. Đây vẫn là bộ loại trừ Testcontainers, không phải toàn bộ test với Docker.

**Chưa kiểm chứng:** Docker/Testcontainers trong lần xác nhận này, kết nối/migration database remote, HTTPS production, AI thật với API key/model của nhà cung cấp, scanner mã độc, toàn bộ thao tác quản trị/upload qua trình duyệt, tải cao hoặc khả năng truy cập đầy đủ. Browser smoke là một tập luồng đại diện, không phải E2E toàn bộ hệ thống.

Phần trên là kết quả lịch sử ngày 30/09. Các xác nhận mới về AI/R2 và chuyển database cloud ngày 05/10 nằm ở các mục vận hành bên dưới; không suy ra đã triển khai website production.

## Thiết lập local

Các lệnh bên dưới dùng thư mục workspace tuyệt đối; đổi một lần nếu clone ở nơi khác:

```bash
export PROJECT_ROOT='/Users/ngttu/Workspace/project_asm_prj'
cd "$PROJECT_ROOT"
```

Cần JDK 21, Maven 3.9+, MySQL 8; Node.js, Playwright và Chrome chỉ cần cho browser smoke. Nếu dùng bộ công cụ có sẵn trong workspace này:

```bash
export JAVA_HOME="$PROJECT_ROOT/.tools/jdk-21.0.12.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PROJECT_ROOT/.tools/apache-maven-3.9.9/bin:$PATH"
java -version
mvn -version
```

### Database xác nhận riêng tại cổng 3307

MySQL phải được chuẩn bị trước, chỉ lắng nghe loopback, có database **edulab_test** và user **edulab_test**, mật khẩu fixture **local-test-only-password**, chỉ có quyền trên database này. Đây là tài khoản thử nghiệm công khai, không được tái sử dụng ở nơi khác. Các lệnh dưới không tự khởi động MySQL và không xóa database.

Trong terminal sạch, không nạp cấu hình remote:

```bash
export SPRING_PROFILES_ACTIVE=local
export EDULAB_DB_URL='jdbc:mysql://127.0.0.1:3307/edulab_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export EDULAB_DB_USERNAME=edulab_test
export EDULAB_DB_PASSWORD=local-test-only-password
export EDULAB_JWT_SECRET="$(openssl rand -base64 48)"
# AI mặc định tắt ở ai_settings; biến EDULAB_AI_ENABLED cũ không còn tác dụng.
export EDULAB_AI_PROVIDER=gatecheap
export EDULAB_DEV_SCAN_BYPASS=false
export EDULAB_UPLOAD_ROOT="$PROJECT_ROOT/.tools/test-uploads"
mkdir -p "$EDULAB_UPLOAD_ROOT"

# Build trước bằng lệnh kiểm thử ở phần bên dưới, rồi chạy embedded Tomcat:
java -jar "$PROJECT_ROOT/target/edulab-khtn8.war" \
  --spring.profiles.active=local --server.address=127.0.0.1 --server.port=8082
```

Mở **http://127.0.0.1:8082/edulab/**. Không chạy embedded Tomcat đồng thời với Tomcat ngoài trên cùng cổng. Nếu app đã được deploy như phiên xác nhận này, không cần khởi động thêm.

JWT secret phải là Base64 của ít nhất 32 byte ngẫu nhiên; không có fallback trong source. Không in key ra log hoặc commit vào Git. Key sinh lại khi khởi động làm access token cũ mất hiệu lực; refresh session vẫn nằm trong database, nên chỉ xoay JWT key không thay thế việc thu hồi phiên.

### Script phát triển mặc định (không phải môi trường xác nhận 3307)

Đã bỏ script và Compose MySQL local. `docker compose up` dùng duy nhất MySQL cloud. Chỉ bộ test chạy trên MySQL dùng một lần tại loopback, theo cấu hình riêng ở phần kiểm thử; không dùng nó làm datasource của container deployment.

```bash
docker compose up -d --no-build --wait
```

Container bind `127.0.0.1:8088`, context `/edulab`; xem `/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`. Không dùng profile local với DB quan trọng, kể cả qua tunnel loopback. `sslMode=DISABLED` và public-key retrieval chỉ dành cho DB loopback dùng một lần trong kiểm thử.

### Dữ liệu demo

Flyway V1–V13 tạo schema, cấu hình AI mặc định tắt, lớp 8A1, năm học 2026–2027, nội dung KHTN 8 cùng các khóa khởi động Toán, Ngữ văn, Tiếng Anh lớp 8 và Toán, Tiếng Anh lớp 10. Nội dung này là mẫu, không bao phủ toàn bộ chương trình các môn.

| Đăng nhập | Vai trò |
|---|---|
| `admin` | ADMIN |
| `giaovien` | TEACHER |
| `hocsinh` | STUDENT |
| `phuhuynh` | PARENT |

Mật khẩu chung **`ChangeMe123!`**, chỉ dùng local. Browser smoke đã xác nhận đăng nhập cả bốn tài khoản. Chạy smoke tạo dữ liệu thật trong DB thử nghiệm và tiêu thụ lượt quiz; không tự rollback.

## Biến môi trường

Spring Boot **không tự đọc `.env`**. `$PROJECT_ROOT/.env.example` và `$PROJECT_ROOT/.env.remote.example` là mẫu; cấu hình shell/IDE/service manager bằng giá trị phù hợp. Riêng `$PROJECT_ROOT/run-remote.sh` chủ động source `$PROJECT_ROOT/.env.remote`: đây là shell code, chỉ được dùng tệp đáng tin, quyền `600`, không commit.

| Biến | Ý nghĩa / mặc định |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` cho loopback; `remote` cho triển khai được duyệt. Không trộn local với remote/prod |
| `EDULAB_DB_URL`, `EDULAB_DB_USERNAME`, `EDULAB_DB_PASSWORD` | Cấu hình datasource; luôn đặt rõ khi triển khai, không dựa vào host mặc định remote |
| `EDULAB_JWT_SECRET` | Base64 ngẫu nhiên, ít nhất 32 byte decoded, bắt buộc |
| `EDULAB_SERVER_PORT` | Cổng embedded Tomcat, mặc định 8080; không đổi connector của Tomcat ngoài |
| `SERVER_ADDRESS` | Spring Boot bind address; đặt `127.0.0.1` khi chạy embedded local |
| `EDULAB_COOKIE_SECURE` | Mặc định true; profile local dùng false. Remote bắt buộc HTTPS và secure cookies |
| `EDULAB_COOKIE_SAME_SITE` | `Lax` mặc định; chỉ chấp nhận `Lax` hoặc `Strict` |
| `EDULAB_UPLOAD_ROOT` | Mặc định `./storage` theo working directory; nên đặt đường dẫn tuyệt đối ngoài webroot |
| `EDULAB_DEV_SCAN_BYPASS` | false; chỉ có thể bật trong local-only với tệp thử tin cậy |
| `EDULAB_AI_PROVIDER` | `gatecheap` mặc định; `stub` chỉ được phép local-only |
| `EDULAB_AI_ENCRYPTION_KEY` | Base64 của 32 byte ngẫu nhiên, dùng AES-256-GCM mã hóa khóa AI và HMAC định danh quota. Sinh **một lần**, giữ ổn định và backup riêng khỏi DB |
| `EDULAB_AI_ALLOWED_HOSTS` | Danh sách host chính xác, phân cách dấu phẩy; mặc định `gatecheap.vn`. Chỉ operator sửa allowlist, không có trong admin API |

### Upload và AI: giới hạn vận hành

Tệp upload trực tiếp R2 được kiểm tra metadata, dung lượng thực tế và chữ ký định dạng, rồi quét qua ClamAV INSTREAM nếu operator đã cấu hình. Chưa có ClamAV hoặc scanner lỗi: **PENDING_SCAN**, không tải xuống/gửi AI. Tác vụ thử quét lại mỗi 30 giây; không có API cho client tự đánh dấu CLEAN. Signature và antivirus không chứng minh file hoàn toàn an toàn. Không bật scan bypass để phục vụ người dùng thật. File local cũ vẫn được đọc từ thư mục ngoài webroot; chưa có UI xóa file đã xác nhận hoặc tự dọn file đã xác nhận nhưng không gắn vào câu hỏi.

**Ngoại lệ Cộng đồng từ 09/10/2026:** theo yêu cầu chủ sở hữu, ảnh upload qua `/api/v1/files/community/presign` không gọi ClamAV và đăng ngay sau kiểm tra JPEG tối ưu (≤500 KiB, ≤1600 px, không metadata), hash và quyền sở hữu. Prefix storage riêng chặn tái sử dụng trong diễn đàn/AI; file không bị đánh dấu giả là CLEAN. Không bật bypass toàn ứng dụng hoặc tự chuyển file cũ sang CLEAN. Đây là bỏ kiểm tra antivirus riêng cho ảnh Cộng đồng, không phải bảo đảm ảnh không có mã độc. Chi tiết: `/Users/ngttu/Workspace/project_asm_prj/docs/social-community.md`.

### Upload trực tiếp S3 / Cloudflare R2 — 05/10/2026

- `/admin` → **Lưu trữ S3 / Cloudflare R2**: bật/tắt upload, endpoint HTTPS, bucket, region, prefix, thay cả hai khóa, giới hạn ảnh/PDF/tài khoản và khả năng ảnh/PDF của AI. Mặc định endpoint R2 của tài khoản đã cung cấp, bucket `edulab`, region `auto`, prefix `edulab`; migration không chứa credentials. `EDULAB_STORAGE_ALLOWED_HOSTS` do operator quản lý, không phải admin tùy ý thêm host. CSP `connect-src` dùng đúng các host trong allowlist.
- Sinh một lần khóa 32 byte Base64 (`openssl rand -base64 32`) và đặt `EDULAB_STORAGE_ENCRYPTION_KEY` riêng, ổn định qua restart, sao lưu riêng DB. Access/Secret Key lưu AES-256-GCM với domain storage riêng; API GET chỉ trả `keyConfigured`, không trả khóa/ciphertext. Audit không lưu payload. Khi đổi vị trí storage, tạo profile mới và giữ profile cũ cho file cũ; xoay khóa cùng endpoint/bucket cập nhật các profile cùng bucket. Nếu chuyển sang bucket khác, operator vẫn phải duy trì quyền khóa cũ để đọc file cũ.
- `POST /api/v1/files/presign` nhận `{filename,contentType,size}`; yêu cầu đăng nhập và CSRF. Trả `{uploadId,url,method:"PUT",headers,expiresInSeconds:300}`. Giữ quota theo dung lượng khai báo, khóa hàng user để chống cạnh tranh, tối đa 6 phiên đang chờ và 30 URL/người/giờ. Mặc định ảnh 5 MiB, PDF 10 MiB, 100 MiB/tài khoản; admin được giảm/tăng quota trong giới hạn backend. Ký **Content-Type và Content-Length chính xác**; JS chỉ đặt Content-Type, trình duyệt tự đặt Content-Length cho Blob. Không dùng presigned POST policy (R2 không hỗ trợ HTML form POST).
- Browser dùng XMLHttpRequest `PUT` raw File **thẳng R2**, có tiến độ/hủy/chọn-bỏ file; không cookie/JWT/CSRF của app, không FormData qua backend. `POST /api/v1/files` cũ trả 410; multipart của Spring bị tắt. Không fallback upload qua server. Presigned URL là bearer token có thể tái sử dụng trước khi hết hạn; không lưu URL ở DB/localStorage/log.
- `POST /api/v1/files/uploads/{uploadId}/complete` xác nhận phiên chính chủ trong 10 phút, `HEAD` staging, copy **nội bộ R2** sang final key chưa từng cấp PUT cho client, rồi kiểm tra **bản final** (size/MIME/signature/SHA-256/quét). Vì copy-source conditional không được dựa vào để bảo vệ, chỉ bản final đã kiểm chứng được chấp nhận. Complete idempotent, không ghi file/tính quota lần nữa. Truy cập file dùng fileId, không lấy key từ client. `GET .../{uploadId}/status` xem trạng thái, `DELETE .../{uploadId}` hủy phiên đang chờ và giải phóng reservation; hủy không thu hồi ngay URL PUT đã cấp. Tắt storage chỉ chặn URL mới; các URL cũ còn hiệu lực đến hết hạn.
- Tác vụ dọn staging/phiên kết thúc sau 1 ngày, tối đa 20 phiên/phút; giữ đủ metadata cho rate limit/retry. Khi app không chạy, cleanup không chạy: nên thêm lifecycle chỉ cho prefix `edulab/staging/` (và `edulab/probe/` cho probe bỏ dở), không áp dụng vào `edulab/files/`. URL PUT tái sử dụng vẫn có thể ghi staging trước expiry, nhưng không sửa final key. Object ghi quá hạn mức do client gian lận bị R2 từ chối khi lệch Content-Length đã ký; final còn kiểm tra dung lượng/hash.
- `EDULAB_CLAMAV_HOST` và `EDULAB_CLAMAV_PORT` (3310) là daemon INSTREAM trên mạng riêng/tin cậy, không phải URL do client chọn; không có TLS trong protocol này. Bỏ host thì fail closed, không tự cài ClamAV hoặc bật bypass. Local-only `EDULAB_DEV_SCAN_BYPASS=true` chỉ dành cho tài liệu mẫu tin cậy; deployment guard từ chối bypass ngoài local-only.
- File đính kèm tại hỏi AI bài học, tạo câu hỏi và trả lời diễn đàn. Quyền file: chính uploader hoặc thành viên được phép xem thread đã công bố, kể cả attachment ở phần câu hỏi gốc. Thread và attachments ghi atomically; AI lỗi vẫn giữ bài. Tác giả có nút gọi AI lại sau quét. Chat công khai hỗ trợ website không nhận file bài tập. Nội dung/tên file được escape; AI vẫn hiển thị text, không render HTML.
- AI chỉ nhận file chính chủ, CLEAN, hash khớp, tối đa 3 file và tổng 15 MiB; ảnh dùng OpenAI `image_url` data URL, PDF dùng `file_data` data URL (không chỉ đưa tên/URL). **Đường truyền upload không qua app**; sau upload app vẫn đọc file từ R2 để scan, download và truyền dữ liệu đã kiểm tra tới AI. Model capability phải bật riêng sau kiểm thử; không suy ra từ HTTP 200 hoặc nhãn OpenAI-compatible. Không có PDF OCR/trích xuất fallback và không có thư viện parser PDF mới. Nếu PDF không được model đọc thì upload/lưu/diễn đàn vẫn dùng được, giải bài PDF bị từ chối rõ ràng.
- Giới hạn còn lại: không có global upload budget/rate limit phân tán cho toàn hệ thống, không có UI quản lý/xóa file đã hoàn tất, không bảo đảm phát hiện mọi malware/polyglot/prompt injection; R2 client được giới hạn các thao tác cần thiết và timeout, không retry/redirect. Cần egress firewall/DNS đáng tin ở production. Bật AI nghĩa là file học sinh được gửi bên thứ ba; cần chính sách quyền riêng tư phù hợp trẻ em.

**CORS local** (bucket vẫn private; origin không có `/edulab`; container dùng cổng 8088, app thử cũ dùng 8085):

```json
[
  {
    "AllowedOrigins": ["http://127.0.0.1:8088", "http://localhost:8088", "http://127.0.0.1:8085", "http://localhost:8085"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["Content-Type"],
    "MaxAgeSeconds": 3600
  }
]
```

Cấu hình tại Cloudflare → R2 → `edulab` → Settings → CORS. Đổi cổng/origin thì cập nhật rule; CORS không thay thế authorization và không chặn client ngoài browser. Không cần public bucket hoặc Cloudflare control-plane API Token trong app. Nút kiểm tra storage gửi Blob `OK` trực tiếp từ browser, server HEAD/GET/DELETE đúng object probe riêng; probe lỗi/bỏ dở cần lifecycle dọn riêng.

Mẫu riêng cho container ở `/Users/ngttu/Workspace/project_asm_prj/docker/r2-cors.json`; khi áp dụng hãy giữ các rule/origin đang dùng và thêm đúng origin HTTPS nếu chạy qua proxy. `http://localhost:8088` khác `http://127.0.0.1:8088`. CORS nằm trên **bucket R2**, không phải Spring hay Docker: build/restart container không tự cập nhật policy. Hướng dẫn kiểm tra preflight và phân biệt cảnh báo extension ở `/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`.

**Đã xác nhận live:** presigned PUT browser + CORS, HEAD/GET/CopyObject/DeleteObject trên bucket `edulab`; thay Content-Length ký bị R2 trả 403. End-to-end ảnh tự tạo `7 + 5` upload browser → final copy → fixture INSTREAM chỉ chấp nhận hash mẫu → AI model `gpt-6.1-sol` trả `12`; fixture scanner không phải antivirus và đã gỡ sau kiểm thử, scan bypass luôn tắt. Khả năng ảnh bật cho local đã kiểm chứng. PDF mẫu trả HTTP 200 nhưng không xác nhận đọc đúng phép tính, nên **PDF capability vẫn tắt**. Máy local chưa có ClamAV thật: file người dùng tiếp tục cách ly; không tuyên bố quét malware production đã đạt. Khóa dùng cho local lưu mã hóa DB; khóa mã hóa trong `/Users/ngttu/Workspace/project_asm_prj/.tools/ai-preview.env` bị Git ignore. Lần triển khai R2 ban đầu không đổi DB remote; lần chuyển cloud theo yêu cầu riêng ngày 05/10 đã sao chép ciphertext và bổ sung đúng khóa mã hóa vào `.env.remote` riêng tư, xem mục chuyển database cloud.

Kiểm thử cuối: **139 tests, 0 failures/errors/skips** với `clean verify` Java 21 và MySQL demo loopback 3307, loại rõ `MysqlIntegrationTest` (không tuyên bố Docker/Testcontainers đã chạy). Các test mới kiểm tra SigV4/cipher/endpoint, INSTREAM giả lập, CSRF/phân quyền/admin không lộ khóa, migration V14, quota reservation, idempotency, hash, content giả/quá hạn/INFECTED và capability ảnh/PDF. Browser storage đạt sáu kích thước admin (1440/1024/768/640/390/320), lesson/forum 1440/390/320, direct PUT thật và mock retry không upload trùng; chat/auth/profile được kiểm tra lại riêng. Test provider mock không thay thế kết quả live đã nêu.

Browser regression (mock mặc định, không gọi AI thật):

```bash
EDULAB_PLAYWRIGHT_PATH="$PROJECT_ROOT/.tools/browser/node_modules/playwright" \
EDULAB_SMOKE_URL='http://127.0.0.1:8085/edulab' \
node "$PROJECT_ROOT/scripts/browser-storage.cjs"
# Tùy chọn EDULAB_STORAGE_LIVE_TEST=true upload file mẫu R2 thật; AI vẫn mock,
# cần DB local dùng thử, scanner tắt; script tạo một file mẫu cách ly, không tự xóa DB/R2.
```

Migration **V14** cần review/apply trước deploy vào database mới, không bật Flyway startup trên production. Database cloud cụ thể ở mục chuyển database bên dưới đã có history V1–V14 được xác minh; không chạy lại migration hoặc import trên đó. Không có secret trong migration hoặc mẫu env. Các khóa đã gửi trong hội thoại cần rotate; nhập cặp mới ở admin trước khi phục vụ dữ liệu thật.

Khi bật GateCheap, **câu hỏi, tối đa 8 tin nhắn gần nhất và ngữ cảnh công khai liên quan được gửi đến bên thứ ba**; trợ giảng bài học còn gửi nội dung bài đã xuất bản. App không gửi token đăng nhập, API key trong prompt hay chủ động thêm hồ sơ học sinh; người dùng vẫn có thể tự nhập dữ liệu cá nhân. Cần xem xét chính sách cho trẻ em và điều khoản lưu dữ liệu của provider trước khi bật. Bảng quota chỉ lưu metadata; không lưu prompt/câu trả lời. Câu trả lời AI đăng vào diễn đàn vẫn lưu như nội dung diễn đàn. Provider có timeout, không tự retry hoặc theo HTTP redirect để tránh phí lặp/rò khóa. Tests dùng mock HTTP không chứng minh AI thật hoạt động; xác nhận live được ghi riêng.

### Chat AI và cấu hình quản trị

- Nút **Hỏi AI** ở góc dưới phải trong cả layout học tập và đăng nhập/đăng ký. Native dialog hỗ trợ focus, Tab/Escape, đóng/mở, câu hỏi gợi ý, lịch sử giới hạn trong bộ nhớ trang, trạng thái đang chờ/lỗi và cuộc trò chuyện mới. Không lưu chat vào localStorage/sessionStorage hoặc DB; tải lại trang sẽ bắt đầu lại.
- Khách chưa đăng nhập dùng được `GET /api/v1/ai/support/status` và `POST /api/v1/ai/support/chat`; POST vẫn yêu cầu CSRF. Các endpoint AI bài học còn lại vẫn yêu cầu đăng nhập.
- Trợ lý hỗ trợ chức năng website, tư vấn khóa có trong DB và tạo nút tới danh mục/chi tiết khóa. Chỉ lấy các cột công khai, không lấy progress/hồ sơ/nội dung nháp. Website **chưa có giá, đơn hàng hay thanh toán**; trợ lý không tự mua hoặc cấp quyền. Không dựa vào AI để đưa ra quyết định thanh toán.
- `/admin/settings/ai` → **Cấu hình AI**: bật/tắt, Base URL, model, API key mới/xóa khóa, hạn mức/người và tổng/ngày, output tokens, nút kiểm tra kết nối đã lưu (một request thật có thể phát sinh phí). Chỉnh system prompt riêng cho trợ giảng bài học/video và hỗ trợ website; để trống để reset mặc định. Nút **Lấy model từ provider** gọi `GET /v1/models` qua backend với khóa đã lưu/khóa mới; vẫn nhập model thủ công được. Chọn model và bấm Lưu để áp dụng cho yêu cầu tiếp theo. Xem [hướng dẫn cấu hình AI](docs/ai-settings.md).
- Flyway **V13** thêm `ai_settings` (mặc định tắt, host `https://gatecheap.vn/v1`, model `gpt-6.1-sol`) và `support_ai_requests`. Không sửa các migration cũ. Remote/prod cần migrate có duyệt trước khi deploy, không tự chạy startup migration.
- API key chỉ lưu AES-256-GCM; GET admin chỉ trả `keyConfigured`, không trả key/ciphertext. Audit chỉ ghi người/thao tác, không ghi payload. Đổi host khi còn khóa cần khóa mới hoặc xóa khóa cũ. Host phải HTTPS, không credentials/query/fragment, nằm trong allowlist và không phân giải về mạng nội bộ. Không dùng allowlist cho host không đáng tin; triển khai thật cần thêm egress firewall/DNS an toàn.
- Sinh khóa mã hóa riêng bằng `openssl rand -base64 32`, export `EDULAB_AI_ENCRYPTION_KEY` ở service manager/shell rồi khởi động. Không sinh lại mỗi restart; mất/đổi khóa sẽ không đọc được API key cũ, cần nhập lại từ admin. **Không commit khóa**. Các biến legacy `EDULAB_AI_ENABLED/BASE_URL/API_KEY/MODEL/MAX_OUTPUT_TOKENS/DAILY_REQUEST_LIMIT` không còn điều khiển runtime; phải cấu hình qua admin.
- Chat công khai mặc định 20 lượt/người **hoặc IP**/ngày, tổng 200/ngày (00:00 UTC), 5 lượt/phút/IP, tối đa 4 request đang chờ. Lượt lỗi cũng tính. Database lock giữ budget reservation giữa các instance; IP/guest/user được HMAC và đổi định danh theo ngày. Cookie khách là HttpOnly/SameSite Strict. Metadata cũ hơn 2 ngày được dọn hàng giờ. Trợ giảng bài học vẫn dùng quota riêng trong 24 giờ; chặn AI khi tài khoản đang làm TEST. Client khách ẩn danh không thể được liên kết chắc chắn với bài TEST của một tài khoản khác.
- Mọi connection của pool đặt MySQL session `time_zone='+00:00'`; ngày HMAC và mốc quota cùng lấy từ database, không phụ thuộc múi giờ JVM. Giới hạn phút/IP cũng làm mới khi định danh ngày đổi lúc 00:00 UTC; giới hạn request đang chờ vẫn dùng metadata toàn cục. Không đổi session time zone ngoài cấu hình pool.
- Dùng `request.getRemoteAddr()`, không tin `X-Forwarded-For` tùy ý. Khi chạy sau proxy, operator cần cấu hình trusted proxy ở container; nếu không, các khách chung IP proxy sẽ chia hạn mức. Global budget ở đây dành cho chat hỗ trợ và test kết nối, không gộp quota trợ giảng bài học.

Browser regression local (mặc định mock, không gọi provider):

```bash
EDULAB_SMOKE_URL='http://127.0.0.1:8085/edulab' \
EDULAB_PLAYWRIGHT_PATH="$PROJECT_ROOT/.tools/browser/node_modules/playwright" \
EDULAB_SCREENSHOT_DIR="$PROJECT_ROOT/.tools/screenshots/ai" \
node "$PROJECT_ROOT/scripts/browser-ai.cjs"
```

Chỉ chạy trên DB demo loopback đã xác nhận. Script đăng nhập admin/học sinh demo, lưu lại cấu hình hiện tại và ghi audit; không xóa audit. Phần lesson/forum mock câu trả lời bài học, tạm tắt AI để kiểm tra API 503 và đăng thảo luận vẫn thành công, rồi khôi phục cấu hình trong `finally`. Thảo luận thử được giữ lại và script in ID để dọn riêng. Không chạy đồng thời script này với quản trị viên đang sửa cấu hình hoặc test dùng cùng tài khoản demo. Thêm `EDULAB_AI_LIVE_TEST=true` để gửi **một câu hỏi khách thật** bằng cấu hình đã lưu; có thể phát sinh phí và tiêu thụ quota. Không log hoặc screenshot API key.

### Xác nhận chat AI cuối — 05/10/2026

- JDK **21.0.12.1**, Maven **3.9.9**: `clean verify '-Dtest=!MysqlIntegrationTest'` đạt **121 test, 0 failure/error/skip** trên MySQL **8.4.11** tại `127.0.0.1:3307/edulab_test`; đủ **13 migration**, không dùng database remote. Test mới kiểm tra UTC session, reset quota/HMAC qua 00:00 UTC, cleanup và scheduler, cấu hình runtime của trợ giảng, fallback diễn đàn và startup guard remote/local. Testcontainers bị loại có chủ ý; không tuyên bố đã xác nhận Docker.
- WAR mới chạy riêng trên loopback **8085**, homepage HTTP 200. Đã đối chiếu byte nội dung `application.yml`, V13, JS chat/admin và JSP chat trong WAR với source; không chứa `.tools` hay tệp secret. README là tài liệu source đi kèm, không được Maven nhúng vào WAR. Không thay deployment/connector khác.
- Browser chat/admin đạt **1440/1024/768/640/390/320 px**: focus/Escape, mobile menu, lịch sử/reset, CSRF khách, phân quyền admin, quota error, AI HTML hiển thị như văn bản và bỏ link ngoài. Lesson đạt 1440/390/320 px và forum fallback được kiểm tra ở 320 px; bật/tắt runtime chặn khách/trợ giảng, nhưng không làm mất bài diễn đàn. Auth/home và profile đều đạt lại sáu kích thước, gồm CSRF, lưu/hoàn tác, đổi mật khẩu và thu hồi mọi phiên. Ba script đều exit 0, không lỗi JavaScript/CSP; log ứng dụng không có mức ERROR/SEVERE trong lần chạy này.
- Bằng chứng provider thật ngày **02/10/2026**: test kết nối admin thành công; chat khách thật trả lời 347 ký tự và liên kết `/courses/2` tới khóa Toán 8 đang tồn tại. Ảnh `.tools/screenshots/ai/chat-live.png` và log cũ được giữ; ngày 05/10 không gọi lại provider trả phí.
- Log mới: `.tools/ai-final-verify.log`, `.tools/browser-ai-final-{ai,auth,profile}.log`; marker `.exit` xác nhận kết quả, ảnh trong `.tools/screenshots/ai-final-{ai,auth,profile}/`. Tất cả được Git ignore. Kiểm tra cú pháp JS/shell và `git diff --check` đạt. Tệp secret local quyền `600`, được ignore; kiểm tra source không phát hiện giá trị credential local hoặc các dạng token/private key phổ biến, **không thay thế secret audit đầy đủ**. Credential từng được gửi trong hội thoại cần được **rotate** trước triển khai.
- Sau browser regression đã dọn đúng hai tài khoản `profile_smoke_student_muuomo5l` / `profile_smoke_parent_muuomo5l`, bốn refresh session của chúng và thảo luận thử ID `3` (kiểm tra khớp title/body/author, không có reply/AI answer/report). Không xóa audit, tài khoản demo, dữ liệu khác hoặc credential cấu hình AI đã lưu.

Giới hạn vận hành vẫn còn: chưa xác nhận database/HTTPS production, policy lưu dữ liệu của provider, tải cao hoặc accessibility audit đầy đủ. Allowlist/public-DNS/no-redirect không thay thế egress firewall và DNS tin cậy. Metadata chat dọn sau hai ngày; quota/trạng thái trợ giảng và bài AI đăng diễn đàn dùng cơ chế lưu trữ sẵn có, không có retention chung mới. Đây vẫn là MVP, không phải cam kết production-ready.

## Build và kiểm thử

### Unit/security và workflow trên MySQL local đã chuẩn bị

Chạy trước browser smoke trên DB thử có dữ liệu demo và còn lượt quiz:

```bash
export EDULAB_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/edulab_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
# Tests không gọi provider thật; trạng thái AI runtime nằm trong database.
export EDULAB_AI_PROVIDER=gatecheap
export EDULAB_DEV_SCAN_BYPASS=false
mvn -f "$PROJECT_ROOT/pom.xml" -B '-Dtest=!MysqlIntegrationTest' clean verify
```

Lệnh này tạo `$PROJECT_ROOT/target/edulab-khtn8.war`. Bộ `LocalMysqlWorkflowTest` chỉ bật khi `EDULAB_TEST_DB_URL` khớp loopback cổng 3307/database test; dùng user/password fixture đã nêu. Nó chạy migration local, kiểm tra admin content, autosave/hết giờ, prerequisite, Boolean predicates, giao bài/nhận xét/thu hồi liên kết và refresh rotation/reuse. Các test workflow dùng transaction rollback; database vẫn cần schema/seed và không được dùng cho dữ liệu quan trọng. Không có biến URL test thì bộ này bị **skip**, không phải đã đạt.

Unit/security tests còn kiểm tra chấm điểm, kiểm tra đáp án, lọc HTML, upload/storage, phân quyền, JWT/CSRF, migration guard, rate limiter, quota và adapter AI mock. Xem `$PROJECT_ROOT/target/surefire-reports` để biết số test thực sự chạy.

### Docker/Testcontainers (xác nhận riêng)

```bash
mvn -f "$PROJECT_ROOT/pom.xml" -B clean verify
```

`MysqlIntegrationTest` tạo MySQL container riêng khi Docker hoạt động, tự skip nếu không có Docker. Lần xác nhận 65 test ở trên **loại rõ bộ này**, không tuyên bố đã chạy nó. Không có test nào cần database remote. Không nạp `.env.remote` khi build/test. Không dùng `-DskipTests` để coi build là đã kiểm chứng.

### Browser smoke

Chỉ dùng server local có dữ liệu demo, AI tắt và scan bypass tắt. `EDULAB_SMOKE_URL` được giới hạn dạng `http://127.0.0.1:<port>/edulab`, nhưng **không thể chứng minh DB phía server là local**: người vận hành phải kiểm tra datasource trước khi chạy.

Playwright là công cụ test, không phải dependency production. Nếu chưa có, cài riêng vào thư mục tooling bị Git ignore:

```bash
npm install --prefix "$PROJECT_ROOT/.tools/browser" --no-save --package-lock=false playwright
```

Lệnh đã dùng với Chrome cài trên macOS:

```bash
EDULAB_SMOKE_URL='http://127.0.0.1:8082/edulab' \
EDULAB_PLAYWRIGHT_PATH="$PROJECT_ROOT/.tools/browser/node_modules/playwright" \
EDULAB_CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
EDULAB_SCREENSHOT_DIR="$PROJECT_ROOT/.tools/screenshots" \
node "$PROJECT_ROOT/scripts/browser-smoke.cjs"
```

Đổi `EDULAB_CHROME_PATH` thành executable Chrome/Chromium tuyệt đối trên hệ điều hành khác. Script không tự cài browser. Nó thử quiz PRACTICE còn lượt, bỏ qua quiz đã hết lượt; khi tất cả đều hết lượt thì báo lỗi. Dùng DB demo thử nghiệm mới hoặc khôi phục snapshot test có chủ đích, **không nới giới hạn nghiệp vụ hoặc xóa dữ liệu thật** để chạy lại. Script tạo thread, liên kết, bài giao và nhận xét, không dọn những dữ liệu này. Không chạy song song với test đang thao tác cùng tài khoản demo.

Ảnh chụp: homepage desktop/mobile, bài học, gia đình, diễn đàn, báo cáo học sinh/phụ huynh và admin trong `$PROJECT_ROOT/.tools/screenshots`. Các luồng có ảnh/report cá nhân thật không được công khai hoặc commit.

Kiểm tra riêng header/footer dùng chung trên đăng nhập, đăng ký và trang chủ (6 kích thước, validation, CSRF, đăng nhập/đăng xuất, focus menu và thông báo lỗi):

```bash
EDULAB_SMOKE_URL='http://127.0.0.1:8083/edulab' \
EDULAB_PLAYWRIGHT_PATH="$PROJECT_ROOT/.tools/browser/node_modules/playwright" \
EDULAB_CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
EDULAB_SCREENSHOT_DIR="$PROJECT_ROOT/.tools/screenshots/auth" \
node "$PROJECT_ROOT/scripts/browser-auth.cjs"
```

Mặc định script dùng `hocsinh` để thử lỗi đăng ký trùng tên và đăng nhập thật, không tạo tài khoản mới. Đăng xuất thu hồi phiên của tài khoản demo; không chạy đồng thời với test dùng cùng tài khoản. Để kiểm tra đăng ký thành công, có thể đặt `EDULAB_AUTH_REGISTER_USERNAME=auth_layout_test_<suffix>` với tên duy nhất. Chế độ này tạo tài khoản PARENT thử nghiệm trên DB local và không tự xóa; cần dọn tài khoản cùng refresh session của nó sau khi chạy.

Script kiểm tra POST thiếu CSRF bị từ chối với 403, cookie xác thực được xóa sau đăng xuất và `/api/v1/auth/me` trở lại 401. Khi đăng xuất ngay trên `/login`, nó chờ response 204 và form đăng xuất biến mất trong DOM mới, không chỉ chờ URL vốn đã là `/login`.

Giới hạn có sẵn: `pattern="[A-Za-z0-9._-]{3,80}"` của tên đăng nhập được giữ nguyên. Chromium hiện dùng regex `v` cho HTML pattern và bỏ qua regex này do dấu `-` chưa escape; vì vậy smoke kiểm tra native validation qua required/email/password, không tuyên bố native username validation đạt. Backend vẫn kiểm tra ký tự và độ dài tên đăng nhập qua `RegisterRequest`.

Xác nhận riêng layout xác thực ngày **30/09/2026**: `clean verify -Dtest=!MysqlIntegrationTest` đạt **73 test, 0 failure/error/skip**, gồm workflow MySQL local; không chạy Docker/Testcontainers. Cả chế độ mặc định và đăng ký PARENT mới đều đạt browser regression ở **1440/1024/768/640/390/320 px**, không tràn ngang, không trùng document/header/footer/ID, không lỗi JavaScript/CSP hoặc request ra ngoài. Đã kiểm tra đăng ký → đăng nhập → đăng xuất, lỗi đăng nhập/đăng ký/đăng xuất, CSRF, xóa cookie và API 401 sau đăng xuất. Tài khoản `auth_layout_test_20260930_1128` và refresh session thử nghiệm đã được dọn khỏi `127.0.0.1:3307/edulab_test`. WAR preview riêng và ảnh ở `.tools/screenshots/auth` được Git ignore, không đưa vào commit. Không xác nhận pixel-perfect với ảnh tham chiếu gốc vì ảnh đó không có trong phiên này.

### Kiểm tra profile — 02/10/2026

Trang `/profile` dùng layout/header/footer chung, responsive và có hai form độc lập. Các API chỉ thao tác tài khoản lấy từ principal đăng nhập, không nhận ID/vai trò/tên đăng nhập để cập nhật:

| API | Chức năng |
|---|---|
| `GET /api/v1/profile` | Họ tên, email, tên đăng nhập, vai trò và ID của chính tài khoản |
| `PUT /api/v1/profile` | Cập nhật `fullName` và `email`, yêu cầu CSRF |
| `POST /api/v1/profile/password` | Nhận `currentPassword`, `newPassword`, `confirmPassword`, yêu cầu CSRF; thành công trả 204 và kết thúc mọi phiên |

Sai mật khẩu hiện tại trả 400 (không dùng 401 để tránh tự refresh/retry), email trùng trả 409. Đổi mật khẩu bị giới hạn 10 request hợp lệ về cấu trúc/phút/tài khoản bằng limiter trong bộ nhớ, độc lập giới hạn đăng nhập theo IP; vẫn cần limiter dùng chung nếu triển khai nhiều instance. Cập nhật mật khẩu và thu hồi phiên chạy cùng transaction; khóa tài khoản và kiểm tra `auth_version` để từ chối request profile dùng principal đã bị thu hồi. Không đổi cấu trúc database.

Browser regression tạo hai tài khoản STUDENT/PARENT với prefix `profile_smoke_` và hậu tố duy nhất; chỉ chạy khi đã xác nhận datasource server là database thử **local**. Script kiểm tra 6 kích thước, cập nhật/hoàn tác/tải lại, escape tên chứa HTML, email trùng, xác nhận mật khẩu/UTF-8, CSRF, đăng nhập bằng mật khẩu mới và thu hồi phiên trình duyệt khác. Tài khoản thử không tự xóa; script in chính xác hai tên cần dọn cùng refresh session, không được xóa tài khoản hoặc dữ liệu thật.

```bash
EDULAB_SMOKE_URL='http://127.0.0.1:8084/edulab' \
EDULAB_PLAYWRIGHT_PATH="$PROJECT_ROOT/.tools/browser/node_modules/playwright" \
EDULAB_CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
EDULAB_SCREENSHOT_DIR="$PROJECT_ROOT/.tools/screenshots/profile" \
node "$PROJECT_ROOT/scripts/browser-profile.cjs"
```

Xác nhận ngày **02/10/2026**: `clean verify -Dtest=!MysqlIntegrationTest` đạt **92 test, 0 failure/error/skip**, gồm workflow MySQL local; không chạy Docker/Testcontainers. WAR mới chạy riêng tại `127.0.0.1:8084/edulab` với database `127.0.0.1:3307/edulab_test`, AI và scan bypass tắt; không ghi đè deployment cổng 8082. Browser profile và hồi quy auth/home đều đạt ở **1440/1024/768/640/390/320 px**, không tràn ngang hoặc lỗi JavaScript/CSP. Đã xác nhận thông tin lưu sau tải lại, tên chứa HTML được escape, cookie bị xóa, mật khẩu cũ bị từ chối, mật khẩu mới đăng nhập được, access/refresh token ở trình duyệt khác bị thu hồi. Ảnh thử nghiệm trong `.tools/screenshots/profile-final` và `.tools/screenshots/profile-auth` được Git ignore.

Kiểm tra cú pháp/whitespace bổ sung:

```bash
node --check "$PROJECT_ROOT/scripts/browser-smoke.cjs"
node --check "$PROJECT_ROOT/scripts/browser-auth.cjs"
node --check "$PROJECT_ROOT/scripts/browser-profile.cjs"
find "$PROJECT_ROOT/src/main/resources/static/js" -name '*.js' -exec node --check {} \;
bash -n "$PROJECT_ROOT/run-remote.sh"
git -C "$PROJECT_ROOT" diff --check
```

## Auto-update Git (commit + push)

Remote dự kiến: `https://github.com/ngttu06/edulab_assignment.git`, nhánh `main`. Script dùng **Node.js 18+ và Git**, không thêm dependency, không build/deploy ứng dụng và không thay database. Chạy từ bất kỳ thư mục nào; script luôn thao tác repository chứa nó.

```bash
# Chỉ cần thiết lập origin nếu bản clone chưa có remote này:
git -C "$PROJECT_ROOT" remote -v
# git -C "$PROJECT_ROOT" remote add origin https://github.com/ngttu06/edulab_assignment.git

# Xem những file sẽ được chọn, không stage/commit/kết nối mạng:
node "$PROJECT_ROOT/scripts/git-auto-update.cjs" --dry-run

# Đồng bộ một lần; có thể đặt commit message riêng:
node "$PROJECT_ROOT/scripts/git-auto-update.cjs" --once --message "chore: update EduLab"

# Tự commit + push ngay lần đầu, sau đó kiểm tra lại mỗi 60 giây; Ctrl+C để dừng:
node "$PROJECT_ROOT/scripts/git-auto-update.cjs" --watch --interval 60

# Kiểm thử script bằng repository tạm và bare remote local, không dùng GitHub:
node --test "$PROJECT_ROOT/scripts/git-auto-update.test.cjs"
```

**Xác thực trước:** cấu hình quyền ghi repo bằng credential helper của Git hoặc GitHub CLI trên máy. HTTPS GitHub cần token/credential helper, không dùng mật khẩu tài khoản. Không gửi token vào chat, không ghi token trong URL, script, `.env.example` hay commit. Có thể chạy `git -C "$PROJECT_ROOT" ls-remote origin` trong terminal riêng để kiểm tra truy cập. Nếu repo không tồn tại hoặc tài khoản không có quyền, cần xử lý trên GitHub trước. Script không hỏi mật khẩu tương tác và dừng khi xác thực thất bại.

**Phạm vi và an toàn:**

- Chỉ chọn `src/`, `scripts/`, `README.md`, `pom.xml`, `docker-compose.yml`, `.gitignore`, hai mẫu `.env.example`/`.env.remote.example` và hai script chạy local/remote. File ngoài danh sách phải review/commit thủ công. Tôn trọng `.gitignore`; bỏ qua secret local, `.tools/`, ảnh kiểm thử, `target/`, upload, `.agents/` và tài liệu Mapstudy tải về.
- Chặn một số dạng private key/token phổ biến và đường dẫn nhạy cảm; **không phải secret scanner đầy đủ**. Người dùng vẫn phải review mã nguồn và lịch sử trước khi chia sẻ repo. Các commit local đã có sẽ được push cùng nhánh, không được script quét lại toàn bộ lịch sử.
- Chỉ chạy trên `main`, yêu cầu index chưa có file được stage thủ công và không đang merge/rebase. Kiểm tra `origin/main` trước khi commit; nếu remote đi trước/phân kỳ thì dừng, **không tự pull/merge/rebase/reset/force-push**. Fetch/push URL phải giống nhau, không mirror hoặc push tag tự động.
- Watcher có khóa riêng trong Git common directory, không chạy hai bản cùng lúc. **Dừng watcher trước khi thao tác Git thủ công.** Khóa riêng không ngăn IDE/lệnh Git khác; nếu bị kill cứng, kiểm tra PID trong `edulab-auto-update.lock` trước khi tự xóa khóa cũ.
- Lỗi kiểm tra trước push sẽ dừng watcher. Nếu đã commit nhưng push bị từ chối thì giữ commit local; sửa nguyên nhân rồi chạy lại sẽ push commit đó, không tạo commit rỗng. Nếu commit hook/check thất bại, kiểm tra `git status` và xử lý index thủ công trước khi chạy lại.
- Watcher commit các file đã lưu kể cả đang viết dở; **không tự chạy Maven/browser tests và không đảm bảo commit xanh**. Đây là sao lưu mã nguồn opt-in, không phải pipeline CI. Không tự cài service khởi động cùng máy; cửa sổ terminal phải còn chạy.

## Deploy trên Tomcat ngoài

Dùng **Tomcat 10.1 + JDK 21**, không dùng Tomcat 9 (`javax.*`). Với Tomcat ngoài, connector quyết định địa chỉ/cổng, tên WAR quyết định context; `EDULAB_SERVER_PORT` và `server.servlet.context-path` của embedded server không thay thế cấu hình đó.

### Local đã xác nhận

Thiết lập biến môi trường local/database 3307 như phần trên trong terminal sạch. Kiểm tra không có cấu hình `setenv.sh` hoặc service manager ghi đè thành remote. Với bộ Tomcat hiện tại:

```bash
export CATALINA_HOME="$PROJECT_ROOT/.tools/tomcat/apache-tomcat-10.1.60"
export CATALINA_BASE="$CATALINA_HOME"
export CATALINA_PID="$PROJECT_ROOT/.tools/tomcat-test.pid"
export CATALINA_OUT='/tmp/edulab-tomcat-final.log'
```

Connector HTTP trong `$CATALINA_BASE/conf/server.xml` phải dùng loopback cho local, ví dụ:

```xml
<Connector address="127.0.0.1" port="8082" protocol="HTTP/1.1"
           connectionTimeout="20000" redirectPort="8443" />
```

Quy trình thay deployment:

1. Build/test thành công. Dừng đúng Tomcat này bằng `$CATALINA_HOME/bin/shutdown.sh` nếu đang chạy; đợi process thoát và cổng 8082 không còn listener. Không kill process lạ chỉ vì trùng cổng.
2. Sao lưu WAR cũ nếu cần rollback. Upload phải ở ngoài thư mục deployment. Chỉ sau khi server đã dừng, xóa bản bung cũ **`$CATALINA_BASE/webapps/edulab`** và JSP cache **`$CATALINA_BASE/work/Catalina/localhost/edulab`**, không xóa toàn bộ webapps/work hoặc database.
3. Copy WAR đã xác nhận, đặt tên `edulab.war` để có `/edulab`, rồi start với môi trường local rõ ràng:

```bash
cp "$PROJECT_ROOT/target/edulab-khtn8.war" "$CATALINA_BASE/webapps/edulab.war"
shasum -a 256 "$PROJECT_ROOT/target/edulab-khtn8.war" "$CATALINA_BASE/webapps/edulab.war"
"$CATALINA_HOME/bin/startup.sh"
curl --fail --silent --show-error --output /dev/null 'http://127.0.0.1:8082/edulab/'
```

Đợi startup hoàn tất trước HTTP/browser smoke; đối chiếu checksum và log mới, không dựa vào log của deployment cũ. Khi dùng service manager, cấu hình env tại service; export trong terminal không tự truyền vào service đã chạy. Không bật shell tracing hoặc log biến môi trường chứa secret.

### Remote / production: không tự động migrate

Host mặc định trong profile/mẫu remote không phải quyền cho phép chạy migration. Database cloud bên dưới đã được chuyển theo yêu cầu riêng của chủ sở hữu; không tự áp dụng lại import/migration trên database đó.

- Automatic Flyway chỉ được phép với **profile local-only và datasource loopback**. Base/remote mặc định tắt Flyway; guard từ chối bật startup migration ngoài local, kể cả khi bị override. Hibernate dùng `ddl-auto=validate`, không tự tạo schema.
- Với database mới, DBA cần review và chạy riêng 16 migration trong `$PROJECT_ROOT/src/main/resources/db/migration` bằng quy trình Flyway được duyệt: backup, diễn tập trên staging, kiểm tra checksum/history và kế hoạch rollback. Không sửa migration đã áp dụng, bật `baseline-on-migrate`, hoặc chuyển profile local để lách guard. Có seed demo trong migration, cần xử lý trước khi phục vụ người dùng.
- Ngoài local, app từ chối startup nếu còn tài khoản enabled mang hash demo. DBA phải đặt hash BCrypt riêng cho admin, đổi mật khẩu/disable các tài khoản demo, tăng `auth_version` và thu hồi refresh sessions liên quan. Đây không thay thế audit mật khẩu và quyền tài khoản đầy đủ.
- Cấu hình rõ URL/user/password được duyệt; URL bắt buộc `sslMode=VERIFY_IDENTITY`, CA/truststore đúng. Không dùng public-key retrieval hoặc bỏ kiểm tra TLS cho remote. Dùng tài khoản ứng dụng quyền tối thiểu, tách tài khoản migration có DDL.
- Bắt buộc HTTPS, secure cookies, reverse proxy/connector được harden, upload riêng ngoài webroot. Không dùng profile local hoặc scan bypass ở server phục vụ người dùng. Gỡ/khóa ứng dụng manager/examples mặc định của Tomcat khi triển khai thật.
- Cấp JWT key mạnh, riêng từng môi trường, lưu trong secret manager/service configuration; sao lưu và giám sát DB/storage. Không commit `.env`, API key, log, upload, screenshot người dùng hoặc build/tool state.
- Chỉ bật AI trong admin sau khi xác nhận provider/model/key, quota/chi phí và quyền riêng tư; nếu chưa sẵn sàng giữ mục Bật AI tắt.

`$PROJECT_ROOT/run-remote.sh` chạy WAR bằng embedded Tomcat với profile remote, không build hoặc migrate giúp bạn. Nó đọc `.env.remote` nếu có, yêu cầu DB password/JWT secret; khóa AI được nhập ở admin, khóa mã hóa đặt ở môi trường server. Chỉ dùng sau khi hoàn tất các bước duyệt triển khai; kiểm tra WAR không cũ. Với Tomcat ngoài, cấu hình cùng các biến qua service manager và deploy thủ công, không chạy script này song song.

### Chuyển database cloud đã xác nhận — 05/10/2026

- Đích được chủ sở hữu yêu cầu: `mysql.ngttu.com:3306/edulab_assignment`, user MySQL `ngttu`. Đích trống trước import; không ghi đè hay hợp nhất ID với dữ liệu đã có. Nguồn là `127.0.0.1:3307/edulab_test`, được sao lưu khi preview tạm dừng rồi phục hồi thử trên database staging local.
- Snapshot cloud-ready có **40 bảng, 266 dòng**, schema và SHA-256 toàn bộ dữ liệu từng bảng khớp sau import, kiểm tra khóa ngoại không có orphan và `CHECK TABLE` đạt. Flyway **chỉ validate** history/checksum V1–V14, không migrate/repair/baseline. Một lần mất kết nối ở bước đối chiếu được khắc phục bằng tiếp tục kiểm tra read-only, không import lại.
- TLS xác minh CA/hostname thành công ở cả MySQL CLI (`VERIFY_IDENTITY` + CA bundle) và Java Connector/J (`sslMode=VERIFY_IDENTITY`); không bật public-key retrieval hoặc hạ chế độ TLS trên cloud. JDBC xác nhận cipher `TLS_AES_256_GCM_SHA384`.
- Theo lựa chọn của chủ sở hữu, tài khoản web `ngttu` giữ nguyên; admin có mật khẩu mới ngẫu nhiên, BCrypt cost 12. **9 tài khoản demo/smoke-test bị khóa**, tăng `auth_version`, thu hồi toàn bộ refresh session cũ. Giữ dữ liệu học tập/diễn đàn và các quan hệ hiện có, kể cả nội dung kiểm thử; đây không phải database production đã làm sạch nội dung.
- Mật khẩu admin mới chỉ lưu tại `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-admin-credentials.json` quyền **0600**. Mật khẩu MySQL khác mật khẩu đăng nhập web. Không hiển thị mật khẩu trong README/log hoặc commit file credentials.
- `/Users/ngttu/Workspace/project_asm_prj/.env.remote` quyền **0600**, bị Git ignore, đã đặt datasource cloud/profile remote, secure cookies, scan bypass false và đúng khóa mã hóa AI/R2. Đã kiểm chứng giải mã ciphertext trên cloud. Sao lưu riêng khóa mã hóa; mất chúng sẽ không đọc được khóa provider/storage trong DB.
- HTTPS smoke **chỉ trên loopback**, WAR/profile remote kết nối DB cloud: đăng nhập admin mới, Secure/HttpOnly cookies, CSRF, chặn tài khoản demo đã khóa, metadata AI/R2 không lộ khóa, tám trang chính, admin 390 px không tràn ngang; file `PENDING_SCAN` vẫn trả 404. Không gọi AI thật hoặc upload file mới trong lượt smoke cloud. Phiên kiểm tra được dừng sau khi hoàn tất; không triển khai website lên máy chủ công khai.
- Backup/dump/manifest/report riêng tư tại `/Users/ngttu/Workspace/project_asm_prj/.tools/cloud-db-backups/20261005T050845Z/`; report ghi trạng thái snapshot trước smoke, login/logout sau đó tạo/thu hồi phiên mới. Giữ bản local, không sửa dữ liệu nguồn để chuyển cloud. Preview cổng 8085 vẫn dùng DB local; **không có đồng bộ hai chiều hoặc tự merge thay đổi sau snapshot**.
- Trước phục vụ thật: rotate mật khẩu MySQL và khóa đã gửi trong hội thoại; cập nhật secret runtime tương ứng; tách user ứng dụng quyền tối thiểu khỏi tài khoản có DDL; cấu hình HTTPS công khai, exact production CORS/allowlist, lifecycle staging/probe và ClamAV thật. R2 vẫn bật, khả năng ảnh bật, PDF tắt; không có ClamAV thì file người dùng vẫn cách ly. Chuyển DB không thay thế cấu hình hay kiểm thử deployment production.

## Cấu trúc và giới hạn còn lại

- `$PROJECT_ROOT/src/main/java/vn/edu/khtn8`: identity/security, content, assessment, school, community/files, administration, AI và deployment guards.
- `$PROJECT_ROOT/src/main/resources/db/migration`: schema/seed V1–V16; `$PROJECT_ROOT/src/main/resources/static`: CSS/JS nghiệp vụ.
- `$PROJECT_ROOT/src/main/webapp/WEB-INF/jsp`: JSP views; `$PROJECT_ROOT/src/test/java`: unit, security và workflow tests.
- `$PROJECT_ROOT/scripts/browser-smoke.cjs`: smoke test trình duyệt local-only; `$PROJECT_ROOT/.tools`: công cụ/ảnh/upload local bị Git ignore.

Còn thiếu scanner/queue, khôi phục mật khẩu/email verification, đổi mật khẩu self-service, rate limit phân tán, UI chỉnh sửa/xóa/versioning nội dung đầy đủ, quy trình kiểm duyệt/báo cáo vi phạm hoàn chỉnh, thông báo, quản trị lưu trữ/retention, audit vận hành và kiểm thử tải/concurrency. Không có chứng nhận bảo mật hay độ đúng sư phạm của AI. Báo cáo quiz và bài giao giới hạn 100 dòng, nhận xét 50 dòng; member picker tối đa 500 tài khoản. Việc giao bài không tự cấp thêm lượt làm quiz.

Một số timestamp trong báo cáo/bài giao/nhận xét hiện được JSP in trực tiếp từ JDBC theo timezone của JVM, trong khi nhãn có thể ghi `UTC`. Trên server không chạy UTC, nhãn hiển thị này có thể gây hiểu nhầm. Cần chuẩn hóa định dạng timezone trước khi dùng hạn nộp với người dùng thật; smoke hiện xác nhận luồng và trạng thái, không xác nhận chuyển đổi timezone trên giao diện.

Bootstrap 5.3.3 và Quicksand được phục vụ nội bộ trong `/Users/ngttu/Workspace/project_asm_prj/src/main/resources/static/css` (kèm giấy phép MIT/OFL); giao diện không cần CDN và không nới CSP. Icon SVG, banner và bìa khóa học là tài sản EduLab, không sao chép ảnh giáo viên hay quảng cáo tham chiếu. Chưa có coverage mọi trình duyệt, thao tác admin/upload hoặc kiểm tra accessibility đầy đủ. Cần review bảo mật, quyền riêng tư trẻ em, nội dung môn học và vận hành trước khi đưa vào sử dụng thật.

## Giao diện và hồi quy responsive

- Header 56 px, sidebar trái, nội dung trung tâm, cột tiện ích trên desktop, footer nhiều cột; màu `#155e94`/`#2470aa`, Quicksand 14 px.
- Các trang nghiệp vụ dùng khung rộng để đọc bài, làm quiz và thao tác bảng; điện thoại dùng menu drawer có Escape, focus trap và trả focus về nút mở.
- Các component dùng chung nằm tại `/Users/ngttu/Workspace/project_asm_prj/src/main/webapp/WEB-INF/tags`; dữ liệu và hook JavaScript nghiệp vụ vẫn giữ nguyên.
- Chạy theme regression riêng (không tạo nội dung, nhưng đăng nhập/đăng xuất sẽ thay đổi phiên của tài khoản demo); **không chạy song song với smoke nghiệp vụ vì logout thu hồi mọi phiên của tài khoản**:

```bash
EDULAB_SMOKE_URL='http://127.0.0.1:8083/edulab' \
EDULAB_PLAYWRIGHT_PATH='/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright' \
EDULAB_SCREENSHOT_DIR='/Users/ngttu/Workspace/project_asm_prj/.tools/screenshots/theme' \
node /Users/ngttu/Workspace/project_asm_prj/scripts/browser-theme.cjs
```

Script kiểm tra viewport 1440/1024/768/640/390/320 px, drawer keyboard, lọc/tìm kiếm, trạng thái rỗng/404, font/assets nội bộ, CSP, overflow và rendering bốn vai trò. Script smoke nghiệp vụ vẫn ở `/Users/ngttu/Workspace/project_asm_prj/scripts/browser-smoke.cjs`.

## Trang chủ và bảng tin Cộng đồng

- Trang chủ giữ branding EduLab, font Quicksand và tài sản tự thiết kế; thêm thống kê aggregate thực (không suy ra từ catalog giới hạn 60 khóa) và giáo viên liên kết với khóa học. Không có teacher ID thì hiển thị trạng thái chờ, không tạo giáo viên/ảnh giả. Danh mục hiện tại công khai; chưa có mô hình visibility riêng cho khóa học.
- `/forum` vẫn yêu cầu đăng nhập. Bảng tin gộp các chuyên mục active mà người dùng có quyền xem, hoặc lọc `?category=...`; bài `PUBLISHED`/`LOCKED` được sắp ID giảm dần. JSP render 10 bài đầu; JavaScript tải theo cursor `before`, có nút “Xem thêm” và link phân trang khi tắt JavaScript. Người dùng bật reduced motion chỉ tải thủ công.
- API đọc: `GET /api/v1/forum/feed?category=...&before=...&size=10` và `GET /api/v1/forum/threads/{id}/posts?after=...&size=10`; `size` 1–20, cursor ID dương. Kết quả `{items,nextCursor,hasMore}`. Bình luận tăng dần theo ID, chỉ đọc `PUBLISHED`. Author, count và attachment được lấy theo batch; không truy vấn riêng từng bài trong feed.
- Thích một loại: `PUT /api/v1/forum/threads/{id}/like`, bỏ thích: `DELETE` cùng URL. Cả hai idempotent, cần JWT/CSRF, trả `{liked,count}`; khóa chính `(thread_id,user_id)` và row lock chống yêu cầu trùng/đồng thời. Bài khóa vẫn xem/thích được nhưng không trả lời; bài ẩn hoặc chuyên mục không còn quyền trả 404.
- Giữ tạo bài, bình luận kèm tối đa 3 tệp, upload trực tiếp R2, AI opt-in/fallback và kiểm duyệt ở trang chi tiết. Sao chép liên kết không cấp quyền xem cho người nhận. Không có realtime, friend graph, chat hoặc reaction nhiều loại.
- `GET /api/v1/files/{id}/image` chỉ inline JPEG/PNG/WebP `CLEAN`, tối đa 5 MB; dùng quyền download hiện có, kiểm tra MIME/signature, độ dài và SHA-256. Không phục vụ SVG/PDF inline, không lộ URL object store. Download cũ vẫn `attachment`/octet-stream. Feed/comment/image trả private/no-store. Uploader giữ quyền tệp của mình như trước; người khác mất quyền khi bài/chuyên mục bị ẩn hoặc thu hồi.
- **V15** thêm likes và index phân trang. Chỉ đã migrate trên DB thử loopback; **không áp dụng cho database cloud hoặc deployment đang chạy**. Remote cần DBA review/apply V15 trước khi chạy API mới; base/remote vẫn tắt startup Flyway.

Kiểm thử riêng (chỉ chạy khi đã xác minh server dùng DB local dùng thử; chạy các browser suite **tuần tự**, logout demo thu hồi mọi phiên). Limiter đăng nhập hiện giới hạn 10 lượt/phút/IP, kể cả đăng nhập thành công; nghỉ ít nhất 60 giây giữa các suite nhiều lượt login hoặc khởi động lại preview dùng thử. Không tắt limiter của ứng dụng để chạy kiểm thử:

```bash
EDULAB_SMOKE_URL='http://127.0.0.1:8086/edulab' \
EDULAB_PLAYWRIGHT_PATH='/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright' \
node /Users/ngttu/Workspace/project_asm_prj/scripts/browser-community.cjs
```

Script tạo bài/bình luận thử, kiểm tra cursor tự động/thủ công, thích lưu lại, clipboard, keyboard, text escaping, khóa trả lời, upload/AI consent qua network mock; cuối cùng ẩn các bài thử bằng admin. Nó không upload R2 hoặc gọi AI thật. Có thể truyền `EDULAB_IMAGE_FIXTURE_PATH` trỏ JSON `{id,key}` của ảnh CLEAN fixture **được chuẩn bị riêng ở local**, không bật scan bypass hoặc tự thay đổi scan status cho file người dùng.

Java/MySQL: `CommunityMysqlTest`, `CommunitySecurityTest`, `ImagePreviewTest`, `DirectUploadMysqlTest` kiểm tra lớp khác/inactive category, bài ẩn/khóa, cursor, count, like trùng/đồng thời, CSRF/login, preview local/R2 và SHA-256. Toàn bộ `verify -Dtest=!MysqlIntegrationTest` đạt 155 test trên MySQL local; bộ Docker/Testcontainers được loại rõ vì môi trường không có Docker. Chưa kiểm thử live R2/AI, đa trình duyệt, screen reader hoặc chứng nhận accessibility đầy đủ.

Hồi quy trình duyệt Chrome/Playwright đã đạt riêng với `browser-community`, `browser-theme`, `browser-profile`, `browser-storage` (network mock), `browser-ai` (provider mock, runtime-disable thật trên DB local rồi phục hồi). Sáu độ rộng 1440/1024/768/640/390/320 px; ảnh kiểm tra nằm tại `/Users/ngttu/Workspace/project_asm_prj/.tools/screenshots/community` và các thư mục `community-*`. Màu chữ phụ trên bình luận đã điều chỉnh đạt tương phản tối thiểu 4.5:1 với các nền tương ứng; trên màn hình dưới 400 px, ẩn minh họa banner để tránh che nội dung. Browser profile tạo tài khoản thử riêng và giữ lại trong DB test; browser feed ẩn bài thử, không xóa dữ liệu người dùng. Smoke nghiệp vụ đầy đủ `browser-smoke.cjs` chưa chạy lại trong đợt này; các workflow quiz/lớp/gia đình được xác nhận bằng Java/MySQL và rendering bốn vai trò.