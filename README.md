# EduLab KHTN 8

Website học Khoa học tự nhiên lớp 8: Java 21, Spring Boot 3.3, Tomcat 10.1, JSP/Bootstrap, MySQL 8 và Flyway. Đóng gói WAR.

## Chức năng đã có trong source

- Đăng ký STUDENT/PARENT; đăng nhập JWT qua cookie HttpOnly; bốn vai trò ADMIN/TEACHER/STUDENT/PARENT. Logout thu hồi access token của tài khoản trên mọi thiết bị qua `auth_version`.
- CSRF cho mọi thao tác thay đổi dữ liệu, kể cả login/register; client lấy token từ `/api/v1/auth/csrf`. JWT không trả trong JSON và không lưu trong browser storage.
- Bài học đã xuất bản, nội dung HTML đã lọc; lộ trình là danh sách theo thứ tự chủ đề; tiến độ riêng của học sinh. Dashboard dùng dữ liệu database.
- Quiz: tạo/tiếp tục lượt làm; snapshot câu hỏi/đáp án; giới hạn lượt; chấm thang 10 trên server; kết quả của chính học sinh. Không trả đáp án/explanation trước nộp. Nộp khi đã hết giờ nhận 0; nộp lại trả kết quả đã lưu.
- Giáo viên xem lớp được phân công và kết quả học sinh; admin tạo lớp trong năm học có sẵn, ghi danh và phân công giáo viên.
- Mã liên kết phụ huynh 128-bit, lưu SHA-256, dùng một lần, hạn 15 phút. Nhập mã hợp lệ cấp quyền ngay; học sinh/phụ huynh có thể thu hồi. Không cho phụ huynh xem học sinh chưa liên kết.
- Diễn đàn theo chuyên mục công khai/lớp, phân trang 20 mục; tạo thảo luận/trả lời; giáo viên/admin khóa, mở, ẩn thảo luận theo phạm vi được phép.
- Upload tối đa 3 tệp/trả lời, JPG/PNG/WebP ≤5 MB hoặc PDF ≤10 MB; đối chiếu extension/MIME/signature, SHA-256, tên lưu ngẫu nhiên ngoài webroot, hạn mức 100 MB/tài khoản. Download yêu cầu quyền và trạng thái CLEAN, luôn tải như attachment.
- AI **chỉ là stub mẫu**, mặc định tắt; bật bằng `EDULAB_AI_ENABLED=true`. Chỉ lấy context bài đã xuất bản, giới hạn 20 yêu cầu/24 giờ; lưu metadata hạn mức, không lưu câu hỏi hoặc gửi dữ liệu ra dịch vụ ngoài.

## Trạng thái kiểm chứng

Đã kiểm chứng local ngày **30/09/2026** trên macOS với JDK 21, Maven 3.9.9 và MySQL 8.4.11:

- `mvn clean verify`: **BUILD SUCCESS**, 40 test được phát hiện, **37 đạt, 3 bỏ qua**, không có failure/error. Ba test MySQL Testcontainers tự bỏ qua do máy không có Docker; chưa xác nhận bộ test này.
- Chạy WAR bằng embedded Tomcat; Flyway V1–V8 và Hibernate schema validation thành công trên database local mới.
- HTTP smoke test đạt: trang login/register, CSS/JS, đăng nhập cả bốn vai trò, dashboard, bài học, danh sách quiz, diễn đàn, gia đình, lớp học/báo cáo, logout, kiểm tra 401/403 và CSRF.
- Có test hồi quy cho JSP forward: forward nội bộ không còn redirect lặp về login, trong khi request trực tiếp vẫn yêu cầu đăng nhập.

Chưa kiểm thử giao diện bằng trình duyệt tự động hoặc toàn bộ luồng nghiệp vụ end-to-end. Không có migration nào được chạy trên database remote. Đây không phải xác nhận sẵn sàng production.

## Chạy local

Cần **JDK 21**, **Maven 3.9+**, Docker Compose (hoặc MySQL 8 có sẵn), cổng 3306 và 8080 trống.

```bash
java -version
mvn -version
docker compose up -d --wait mysql
export EDULAB_JWT_SECRET="$(openssl rand -base64 48)"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Mở `http://localhost:8080/edulab/login`. Local datasource mặc định khớp Compose. Script `bash run-local.sh` thực hiện kiểm tra công cụ, khởi động MySQL và ứng dụng. Không dùng script local cho database remote.

Nếu đã có MySQL 8 chạy tại `localhost:3306`, với database `edulab_assignment` và user `edulab`/`edulab_local_password` có quyền trên database đó, có thể bỏ qua Docker:

```bash
bash run-local.sh --existing-mysql
```

Chế độ này không tự tạo hoặc khởi động MySQL. Cần JDK 21 và Maven trong PATH. URL local dùng `allowPublicKeyRetrieval=true` để xác thực `caching_sha2_password` của MySQL 8 qua loopback khi không bật TLS; **không dùng cấu hình này cho remote**. Remote vẫn phải dùng `sslMode=VERIFY_IDENTITY`.

Để chạy trực tiếp WAR đã build (MySQL phải đang chạy):

```bash
export EDULAB_JWT_SECRET="$(openssl rand -base64 48)"
java -jar target/edulab-khtn8.war --spring.profiles.active=local --server.address=127.0.0.1
```

Spring Boot **không tự đọc `.env`**. `.env.example` chỉ là mẫu biến môi trường; export các giá trị trong shell hoặc thiết lập bằng IDE/service manager. Không commit secret, `.env`, thư mục storage hoặc file build.

JWT secret bắt buộc là Base64 của ít nhất 32 byte ngẫu nhiên, không có fallback trong source. Token sống 15 phút; chưa có refresh flow, nên cần đăng nhập lại khi hết hạn. Restart với secret mới làm hết hiệu lực token cũ.

### Dữ liệu mẫu

Flyway V1–V8 tạo schema và seed: 3 bài, 1 quiz có 1 câu, lớp 8A1, năm học 2026–2027.

| Tên đăng nhập | Vai trò |
|---|---|
| `admin` | ADMIN |
| `giaovien` | TEACHER |
| `hocsinh` | STUDENT |
| `phuhuynh` | PARENT |

Mật khẩu demo: `ChangeMe123!`. Đã xác nhận đăng nhập qua HTTP bằng mật khẩu này cho cả bốn tài khoản trên MySQL local. Chỉ dùng tài khoản mẫu ở local. Không dùng dữ liệu cá nhân thật để thử nghiệm.

### Upload và cách ly

Mặc định file mới là **PENDING_SCAN**, không tải được. Chưa tích hợp scanner: file sẽ tiếp tục bị cách ly cho đến khi một scanner đáng tin được tích hợp; không có API tự đánh dấu CLEAN.

Để thử với tệp tự tạo, tin cậy trên máy local:

```bash
export EDULAB_DEV_SCAN_BYPASS=true
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Bypass chỉ được phép khi profile `local` bật và không có `remote`/`prod`. Đây **không phải quét mã độc**; signature không loại trừ polyglot hoặc nội dung PDF nguy hiểm. Không bật bypass trên máy phục vụ người dùng. Thư mục `EDULAB_UPLOAD_ROOT` phải nằm ngoài Tomcat webapps, thuộc tài khoản hệ điều hành chạy app, không chia sẻ quyền ghi với người dùng khác. Bản MVP chưa có dọn file mồ côi hoặc giao diện xóa upload.

## Database remote / Tomcat

**Không trỏ ứng dụng vào database quan trọng để thử nghiệm.** Flyway chạy khi khởi động; V4/V5 seed tài khoản/lớp demo, kể cả profile remote. Chỉ áp dụng vào database mới đã được duyệt, sao lưu trước. Không sửa migration đã được áp dụng.

```bash
export EDULAB_DB_URL='jdbc:mysql://your-db-host:3306/edulab_assignment?sslMode=VERIFY_IDENTITY&serverTimezone=UTC'
export EDULAB_DB_USERNAME='your-app-user'
export EDULAB_DB_PASSWORD='your-private-password'
export EDULAB_JWT_SECRET='your-generated-base64-secret'
export EDULAB_UPLOAD_ROOT='/srv/edulab/private-files'
export SPRING_PROFILES_ACTIVE=remote
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=remote
```

Dùng chứng chỉ CA tin cậy/truststore phù hợp với máy MySQL; không tắt kiểm tra TLS. Remote cần HTTPS phía trình duyệt vì cookie Secure bắt buộc. Không bật đồng thời profile local/remote.

Ứng dụng **từ chối khởi động ngoài local** khi còn tài khoản enabled mang hash demo. Sau migration đầu tiên, DBA cần đặt hash BCrypt riêng cho admin, đổi mật khẩu các tài khoản demo còn dùng hoặc disable chúng, và tăng `auth_version`. Không tắt guard để tránh bước này. Chưa có UI quản lý mật khẩu/tạo giáo viên; cần quy trình quản trị DB có kiểm soát trước khi triển khai thật.

Build WAR và deploy thủ công sau khi kiểm thử:

```bash
mvn clean verify
# Chỉ copy sau khi đã cấu hình env/HTTPS cho Tomcat:
cp target/edulab-khtn8.war "$CATALINA_HOME/webapps/edulab.war"
```

Tomcat 10.1 chạy Java 21. Tên `edulab.war` tạo context `/edulab`; nếu đổi tên WAR, context bên Tomcat cũng đổi. Bootstrap tải từ CDN nên giao diện cần truy cập mạng; JS nghiệp vụ nằm trong ứng dụng.

## Kiểm thử

```bash
mvn clean verify
```

- Unit: điểm có trọng số, đáp án ngoại lai, bài hết giờ, max attempts/submit lại, lọc HTML, tiến độ, parent/teacher policy, định dạng upload.
- MockMvc: API chưa đăng nhập 401, thiếu CSRF 403, lấy CSRF thật từ endpoint rồi login sai trả 401; cho phép JSP forward nội bộ nhưng không mở quyền request trực tiếp.
- MySQL Testcontainers: Flyway + Hibernate validation, hash demo, quiz snapshot/chấm bài, tạo/thu hồi link. **Tự skip nếu không có Docker**; kiểm tra số skipped trong `target/surefire-reports`.
- Không có test nào cần hoặc được cấu hình kết nối database remote.

Smoke-test thủ công sau khi build thành công:
1. Đăng ký học sinh/phụ huynh, login/logout; kiểm tra lỗi credential, hết hạn token, CSRF.
2. Học sinh mở bài, hoàn thành, tải lại dashboard; giáo viên/phụ huynh không cập nhật tiến độ được.
3. Bắt đầu quiz, kiểm tra Network không lộ đáp án, nộp, xem lại, thử hết lượt/hết giờ và ID lượt của người khác.
4. Học sinh tạo mã, phụ huynh nhập; dùng lại mã bị từ chối, hủy liên kết mất quyền xem báo cáo.
5. Giáo viên chỉ xem lớp được phân công. Admin phân công/ghi danh; tài khoản khác không gọi API admin được.
6. Đăng thread/trả lời với ảnh/PDF; thử file giả, file quá lớn, file pending và URL file không có quyền.
7. Bật AI mẫu local; kiểm tra nhãn mẫu, nguồn bài học và hạn mức.
8. Kiểm tra JSP trên Tomcat thật ở màn hình điện thoại và desktop.

## Giới hạn trước khi dùng thực tế

Đây là MVP học tập, **không phải bản production-ready**. Chưa có: refresh-token rotation, khôi phục mật khẩu/email verification, rate limit login phân tán, CRUD nội dung/question bank và tài khoản giáo viên đầy đủ, khóa tiến độ theo prerequisite, bài tập giao theo lớp, scanner/queue, báo cáo vi phạm hoàn chỉnh, thông báo, AI provider thật, concurrency/load/browser E2E tests. Admin member picker hiển thị tối đa 500 tài khoản; báo cáo hiển thị 100 kết quả quiz gần nhất. Cần review bảo mật, quyền riêng tư trẻ em và vận hành trước khi phục vụ học sinh thật.
