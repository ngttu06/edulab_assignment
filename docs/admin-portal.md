# EduLab Admin — cổng quản trị độc lập

## Kiến trúc và phạm vi

- Giữ nguyên Spring Boot 3.5 / Java 21, JSP/JSTL, Bootstrap, JavaScript thuần và MySQL/Flyway.
- `/admin` sử dụng layout, sidebar, CSS và JavaScript riêng; không nạp giao diện học tập, AI widget hoặc footer của học sinh/giáo viên/phụ huynh.
- Danh sách phân hệ có tìm kiếm literal (escape `%`/`_`), lọc trạng thái, sắp xếp và phân trang server 20 dòng. Bộ chọn liên kết tìm tối đa 50 kết quả mỗi lần, giữ các lựa chọn hiện tại.
- Danh mục tài nguyên được khai báo cố định; không nhận tên bảng/cột/biểu thức SQL từ client. Dữ liệu ghi được lọc theo các trường được phép.
- Tất cả trang `/admin/**` và API `/api/v1/admin/**` yêu cầu ADMIN; các service mới cũng kiểm tra vai trò. Mutations dùng CSRF hiện có. Trang quản trị và API workspace dùng `private, no-store`.
- Không thay đổi migration V1–V20 và không cần schema mới. Không chạy ứng dụng/migration/fixture lên cloud trong quá trình phát triển cổng này.

## Phân hệ

| Nhóm | Chức năng |
|---|---|
| Tài khoản | Giáo viên, học sinh, phụ huynh, quản trị viên: tạo, xem, sửa, khóa/mở khóa; BCrypt; thu hồi access/refresh session khi sửa/khóa |
| Tổ chức đào tạo | Năm học; lớp học; phân công nhiều giáo viên, một phụ trách chính; thêm/rời lớp; liên kết phụ huynh–học sinh |
| Danh mục | Môn học, khóa học, chủ đề, ngân hàng câu hỏi |
| Nội dung | Bài học công khai/theo lớp, phiên bản HTML được sanitize, câu hỏi trắc nghiệm, quiz, bài đã giao, nhận xét |
| Video | Trang admin riêng, dùng nghiệp vụ version/video hiện có và alias API admin; không thay đổi trang giáo viên |
| Tệp | Upload qua quy trình presigned R2 hiện có, danh sách/chi tiết, đổi tên hiển thị, tải tệp CLEAN, chặn tải xuống bằng REJECTED |
| Cộng đồng | Danh mục hỏi đáp, câu hỏi/trả lời, bài viết/bình luận, báo cáo hỏi đáp, hàng đợi báo cáo cộng đồng |
| Hệ thống | Cấu hình AI, storage và nhật ký chỉ đọc |

Trang chi tiết giáo viên có tài khoản, lớp được phân công và bài học đã tạo. Trang lớp gồm tổng quan, giáo viên, học sinh, bài giao, kết quả và xuất CSV.

## Quy tắc bảo toàn dữ liệu

- **Tài khoản:** không đổi vai trò qua biểu mẫu sửa, không xóa vật lý; không tự khóa; luôn giữ một ADMIN hoạt động. Các lần chỉnh sửa tài khoản quản trị được tuần tự hóa trong transaction.
- **Phân công:** khóa bản ghi giáo viên và lớp; chỉ giáo viên đang hoạt động và lớp chưa lưu trữ. Primary key ngăn trùng phân công. Đổi phụ trách chính trong một transaction; người cũ vẫn là giáo viên giảng dạy. Thu hồi quyền quản lý lớp ngay ở request tiếp theo; không xóa nội dung hoặc bài làm. Nhật ký ghi ID thành viên và phụ trách chính trước đó.
- **Lớp/học sinh:** lưu trữ lớp và đặt enrollment inactive; giữ lịch sử. Quyền tác giả của giáo viên và phân công quản lý video là các quyền riêng đã có, không bị chuyển chủ sở hữu khi thu hồi lớp.
- **Bài học:** tạo phiên bản mới khi sửa; chỉ phiên bản mới nhất được xuất bản. Lưu trữ giữ tiến độ/video; khôi phục thành DRAFT. Chặn vòng lặp tiên quyết và lưu trữ bài đang được dùng làm tiên quyết.
- **Quiz:** giữ snapshot của lượt làm; sửa danh sách câu hỏi giữ trọng số cũ, câu mới mặc định 1 điểm. Không bỏ chia sẻ lớp khi còn bài giao hoạt động. Lưu trữ quiz hủy các bài giao còn hoạt động; khôi phục không tự xuất bản hay khôi phục bài giao.
- **Nhận xét:** lưu revision khi sửa, soft-delete khi ngừng sử dụng.
- **Danh mục chưa có cờ archive:** chỉ xóa vật lý khi foreign key cho phép; không cascade. Dữ liệu đã có tham chiếu trả HTTP 409.
- **Tệp:** không xóa object khỏi bucket hoặc sửa `scan_status` bằng payload. Chặn giữ bản gốc/liên kết để đối soát; không cho khôi phục thủ công để tránh bỏ qua scanner. Admin cũng chỉ tải được tệp CLEAN. Upload/thay tệp dùng luồng kiểm tra an toàn hiện có.
- **Báo cáo:** báo cáo hỏi đáp chỉ sửa trạng thái, không sửa bằng chứng. Khi xử lý báo cáo cộng đồng, sao lưu nội dung báo cáo vào metadata audit trước khi bỏ khỏi hàng đợi.

## API workspace

Prefix: `/api/v1/admin/workspace`.

- `GET /{resource}?search=&state=all|active|inactive&page=0&sort=newest|oldest|name`
- `GET /{resource}/{id}`
- `POST /{resource}` và `PUT /{resource}/{id}`: tên trường theo schema biểu mẫu. Ngày giờ gửi ISO-8601 UTC; giao diện hiển thị giờ thiết bị.
- `DELETE /{resource}/{id}`: ngừng/lưu trữ hoặc xóa danh mục không có tham chiếu.
- `POST /{resource}/{id}/restore`: khôi phục khi nghiệp vụ cho phép.
- `GET /options/{resource}?search=`: bộ chọn tìm kiếm.
- `POST /teaching-assignments`: `{classId, teacherId, homeroom}` (upsert).
- `DELETE /classes/{id}/teachers/{teacherId}`.
- `POST /classes/{id}/students`: `{studentId, active}`.

API upload, AI/storage và moderation hiện có được tái sử dụng; không tạo quy trình thứ hai cho quét tệp/khóa bí mật.

## Kiểm thử an toàn

Chỉ dùng database dùng một lần trên `127.0.0.1:3307`, tên bắt đầu bằng `edulab_test`, user `edulab_test` / mật khẩu fixture công khai `local-test-only-password`. Không dùng tunnel tới cloud và không nạp `.env.remote`.

```bash
export PROJECT_ROOT=/Users/ngttu/Workspace/project_asm_prj
export JAVA_HOME="$PROJECT_ROOT/.tools/jdk-21.0.12.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PROJECT_ROOT/.tools/apache-maven-3.9.9/bin:$PATH"
export EDULAB_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/edulab_test_admin?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
mvn -f "$PROJECT_ROOT/pom.xml" -B verify
```

Browser smoke cần WAR mới chạy với profile local, bind `127.0.0.1`, DB dùng một lần và các tài khoản demo gốc. Script cố ý giữ fixture để chụp/kiểm tra; không chạy trên dữ liệu quan trọng.

```bash
EDULAB_DISPOSABLE_TEST_DB=true \
EDULAB_SMOKE_URL=http://127.0.0.1:8093/edulab \
EDULAB_PLAYWRIGHT_PATH=/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright \
node /Users/ngttu/Workspace/project_asm_prj/src/test/browser/admin-workspace.cjs
```

Ảnh được ghi mặc định vào `/tmp/edulab-admin-screenshots`. Script kiểm tra tất cả danh sách/biểu mẫu phân hệ, CRUD đại diện, phân trang, XSS literal, thu hồi phân công/session, role denial và layout ở 1440/768/390/320px.

## Giới hạn cần biết

### Kết quả xác nhận 08/10/2026

- `mvn clean verify`: **240 tests, 0 failures, 0 errors, 0 skipped**, bao gồm MySQL local dùng một lần và MySQL Testcontainers; đóng gói WAR thành công.
- Browser smoke: **PASS**, 24 phân hệ; desktop 1440px, tablet 768px, mobile 390px và 320px. Không có JavaScript page error hoặc tràn chiều ngang toàn trang ở các màn hình đại diện.
- Xác nhận CRUD giáo viên/lớp/bài học, phân công chủ nhiệm, enrollment, phân trang, chống trùng, XSS literal, thu hồi quyền lớp và access token sau khóa tài khoản, CSV và hồi quy trang teacher/student/parent.
- Đã sửa lỗi header cache ghi đè cấu hình storage và lỗi vị trí phần tử visually-hidden gây tràn ở 320px.
- Log lần xác nhận: `/tmp/edulab-admin-final-verify.log`, `/tmp/edulab-admin-browser-final.log`. Ảnh: `/tmp/edulab-admin-screenshots`.
- Preview dùng một lần: `http://127.0.0.1:8093/edulab/admin`; MySQL container `edulab-admin-test-db` bind `127.0.0.1:3307`. Không thay container triển khai `edulab` hoặc cấu hình cloud. Preview cần khởi động lại sau khi build WAR mới; không ghi đè WAR đang được JVM dùng.

### Các giới hạn còn lại

- Đây vẫn là MVP. Chưa kiểm thử tải lớn, screen reader đầy đủ hoặc toàn bộ tổ hợp dữ liệu nghiệp vụ.
- Bảng liên quan trong trang chi tiết có giới hạn hiển thị: 200 học sinh/kết quả, 100 bài giao/tiến độ, 50 bài học tác giả; chưa có phân trang riêng cho từng bảng này. Hàng đợi báo cáo cộng đồng/video vẫn dùng giới hạn nghiệp vụ hiện có.
- Form trắc nghiệm hỗ trợ 2–6 đáp án, một đáp án đúng. Chưa có editor rich-text/WYSIWYG hoặc giao diện chỉnh trọng số từng câu.
- Chưa kiểm chứng dịch vụ AI/R2/ClamAV thực trong lần kiểm thử cổng admin; không gọi dịch vụ tính phí hay thay cấu hình cloud. Tệp chỉ khả dụng khi scanner xác nhận CLEAN.
- Kiểm thử phát triển ở trên chỉ dùng DB dùng một lần; không tự cập nhật container cloud khi sửa mã nguồn. Lượt triển khai đã được phê duyệt riêng được ghi nhận bên dưới.

## Triển khai được phê duyệt — 09/10/2026 (+07)

- Container `edulab` đã chạy image `edulab-khtn8:admin-20261008t170809z-12e2d93359c6`; tag `latest` trỏ cùng image. Docker `healthy`, `/edulab/actuator/health` trả `UP`.
- MySQL vẫn là `mysql.ngttu.com/edulab_assignment`, TLS `VERIFY_IDENTITY`; 20 migration đã validate, schema V20. Không chạy migration, repair, clean, reset hay import dữ liệu test.
- Giữ nguyên biến môi trường, mật khẩu/JWT/khóa mã hóa, `.env.remote` và volume storage. Đối chiếu trước–sau khớp 10 bảng lớp học/nội dung đã chọn; đây không phải backup hoặc chứng minh toàn bộ database bất biến. Scheduled jobs vẫn hoạt động bình thường.
- Public browser smoke trên cloud đạt ở 1440/390px, không có lỗi JavaScript hoặc tràn ngang. 240 tests và browser admin 24 phân hệ/4 viewport ở phần trước thuộc **DB dùng một lần**, không phải CRUD cloud.
- **Giới hạn smoke test cloud:** đăng nhập HTTPS bằng thông tin lưu cũ trả 401. Kiểm tra chỉ đọc cho thấy tài khoản vẫn tồn tại, enabled và ADMIN, nhưng mật khẩu lưu không khớp BCrypt trong cloud. Không đổi/reset mật khẩu; đăng nhập, cookie authenticated, dashboard, danh sách/chi tiết và form admin trên cloud chưa xác nhận. Không thử CRUD/upload/gọi AI hoặc logout toàn bộ phiên.
- Proxy HTTPS loopback dùng cho smoke test đã tắt; không tạo domain hoặc HTTPS công khai. Đăng nhập thực tế cần HTTPS và mật khẩu hiện tại của chủ sở hữu.
- Report và cấu hình rollback riêng tư: `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/admin-20261008T170809Z/`. Hướng dẫn rollback ứng dụng, giữ nguyên schema/volume: `/Users/ngttu/Workspace/project_asm_prj/docs/docker-cloud.md`.