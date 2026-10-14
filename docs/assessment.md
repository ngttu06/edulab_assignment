# Quiz, đề kiểm tra và nhập đề AI

## Học sinh

- `/quizzes`: bắt đầu/tiếp tục luyện tập hoặc kiểm tra. Trắc nghiệm một đáp án,
  2–4 lựa chọn, tối đa 200 câu, điểm quy đổi thang 10 theo trọng số.
- `/quizzes/history`: 200 lượt gần nhất, gồm cả lượt đang làm và lịch sử đã chấm.
- `/quizzes/attempts/{id}`: tự lưu lựa chọn, nộp và xem lại. Quiz luyện tập hiển thị
  đúng/sai/bỏ trống, đáp án, giải thích và bộ lọc sau khi nộp. Kiểm tra chỉ hiển thị
  điểm cho đến `reveal_after_snapshot`; API cũng ẩn đáp án và trạng thái đúng/sai.
- Nhận xét AI tự tải ở trang kết quả, có nút thử lại. Trước công bố, nhận xét kiểm
  tra chỉ sử dụng điểm và số câu, không gửi nội dung đề/đáp án/câu sai cho provider.
  Sau công bố có nhận xét chi tiết và nút hỏi AI giải thích từng câu.
- Nhận xét/giải thích lưu theo lượt và câu; đọc lại không tiêu thụ thêm lượt AI.
  Lỗi provider không ảnh hưởng nộp bài/điểm. AI không quyết định hoặc sửa điểm.
  Kết quả AI có thể sai, cần giáo viên kiểm tra; chế độ stub phải ghi rõ AI mẫu.
  Nhãn AI mẫu và provider được lưu cùng nhận xét, không mất nhãn khi đổi provider.
- Trong một bài kiểm tra đang làm, API nhận xét kể cả cache bị khóa. Quota AI
  hiện có tiếp tục chặn các yêu cầu trợ giảng khác của học sinh.

## Giáo viên

- `/teacher/quizzes`: tạo quiz hoặc **Tạo đề kiểm tra** (`new?mode=TEST`). Cùng máy
  soạn đề/chấm bài; kiểm tra xuất bản cần lịch công bố sau thời lượng làm bài.
- Từ chi tiết đề chọn **Báo cáo, phổ điểm & bài làm học sinh**.
- `/teacher/quizzes/{id}/report`: lớp trong phạm vi giáo viên được phân công,
  học sinh chưa làm/đang làm/đã có điểm, danh sách lượt, lọc tên và trạng thái.
  Phổ điểm SVG có bảng số liệu, 10 khoảng: `[0,1)` ... `[8,9)`, `[9,10]`.
- Cách tính mặc định: lượt **đã chấm gần nhất mỗi học sinh**. Có thể chọn cao nhất
  mỗi học sinh hoặc tất cả lượt đã chấm. Không tính chưa làm là 0, không đếm trùng
  một học sinh có mặt ở nhiều lớp trong báo cáo tổng. Điểm cũ vẫn tính từ snapshot.
- CSV `/teacher/quizzes/{id}/report.csv?classId=...`: tất cả lượt trong phạm vi,
  UTF-8 BOM, escape nội dung và chống công thức bảng tính. Bảo quản dữ liệu an toàn.
- `/teacher/quiz-attempts/{id}`: đề snapshot, lựa chọn, điểm, câu sai, thời gian,
  nhật ký rời tab, nhận xét AI đã lưu. Có đường dẫn từ kết quả riêng của học sinh.
  Giáo viên chỉ xem bài nếu học sinh thuộc lớp họ phụ trách và đề thuộc phạm vi
  lớp đó (quiz_classes hoặc class_assignments); admin vẫn kiểm tra đề có phạm vi lớp.
  Không cấp quyền sửa điểm/bài làm. Bài đang làm không lộ đáp án ngay trong trang này.

## Rời tab trong kiểm tra

Trước khi bắt đầu có xác nhận. Trong bài kiểm tra, `visibilitychange` khi hidden
hoặc `pagehide` tạo UUID sự kiện. API giữ một bản ghi mỗi UUID/lượt; retry không
đếm trùng. Số lần và timestamp **server nhận** hiển thị cho giáo viên. CSRF được
prefetch cho `fetch(keepalive:true)` khi rời trang; nếu lỗi, giữ tối đa 200 sự kiện
trong sessionStorage và gửi lại khi trở về/online/nộp bài. Bài đã chấm hoặc hết hạn
không nhận sự kiện mới; sự kiện offline tới sau hạn có thể không được ghi nhận.

Không đếm `blur` đơn thuần để tránh cảnh báo giả khi click giao diện trình duyệt.
Hệ thống không chặn tuyệt đối chuyển tab, không phát hiện thiết bị thứ hai hay
mọi ứng dụng khác, không bảo đảm sự kiện khi đóng/crash/mất mạng hoặc client bị
can thiệp. Không tự trừ điểm hay kết luận gian lận. Đây là thông tin hỗ trợ giáo viên.

## Nhập PDF / Word

Trong trang tạo/chỉnh sửa đề hoặc `/teacher/quizzes/import`:

1. Chọn PDF hoặc DOCX, tối đa 10 MB (PDF còn tuân theo giới hạn admin thấp hơn).
2. Quét ClamAV bằng `FileScanner`; chỉ `CLEAN` mới được đọc/gửi AI. Không cấu hình
   scanner hoặc lỗi scan: dừng, không gửi provider. Không bật bypass trong production.
3. PDF cần model hỗ trợ file PDF và `ai_pdf_enabled` trong storage settings.
   DOCX đọc văn bản/paragraph/bảng bằng API XML/ZIP chuẩn của JDK, không cần thư
   viện mới; giới hạn 15.000 ký tự, 500 entries, 2 MiB/entry, 15 MiB giải nén,
   cấm DTD/external entities/macro/path traversal. Không đọc hình/công thức Word:
   báo lỗi và yêu cầu xuất PDF. `.doc` cũ yêu cầu chuyển `.docx`/PDF.
4. AI trả JSON được kiểm tra schema, giới hạn trường, chỉ số đáp án và `complete`.
   **Tối đa 15 câu/lần PDF/DOCX** để phù hợp giới hạn đầu ra provider hiện tại.
   Đề dài chia tệp; mỗi lần thay thế bản xem trước, hoặc dùng CSV để nhập 200 câu.
   Đầu ra báo chưa đầy đủ, vượt giới hạn token, JSON bị cắt/hỏng: không thay thế đề.
5. Thiếu/mơ hồ đáp án: `correctIndex=null`, UI không tự chọn A. Hỏi giáo viên
   **Đồng ý tạo đáp án bằng AI** hoặc **Không, tôi tự nhập đáp án**.
6. Chỉ endpoint tạo đáp án (POST riêng, CSRF, phiên thuộc người nhập) mới đề xuất
   đáp án thiếu, theo batch 5 câu; không ghi đè đáp án sẵn có. AI có thể vẫn trả
   null nếu thiếu dữ kiện. Đáp án đề xuất được đánh dấu và cần giáo viên duyệt từng
   câu. Sửa nội dung sau khi duyệt làm bỏ dấu duyệt trên UI.
7. Thiếu đáp án hoặc AI chưa duyệt: không lưu/ xuất bản qua UI hoặc input API chuẩn.
   Giáo viên xem lại toàn bộ câu và đáp án trích xuất trước khi lưu. Không tự xuất bản.

Tệp gốc được quét trong bộ nhớ, không lưu storage/database và không thực thi nội
dung; chỉ dữ liệu câu đã trích xuất được lưu trong phiên nhập. Nội dung gửi đến
nhà cung cấp AI do admin cấu hình; không tải thông tin cá nhân học sinh. Phiên chỉ
cho người nhập truy cập và hết hiệu lực sau 24 giờ. Hiện chưa có tác vụ xóa vật lý
phiên nhập cũ. Các luồng upload R2 cộng đồng/bài học vẫn giữ giới hạn/định dạng cũ.
AI mẫu không được dùng để giả lập đọc tài liệu.

## Migration và kiểm thử

Migration mới: `V25__assessment_review_and_import.sql`, tạo `quiz_tab_events`,
`quiz_ai_reviews`, `quiz_import_sessions`. Không sửa migration cũ và không xóa
điểm/snapshot. Cloud phải backup/review các migration pending rồi chạy job migrate
riêng theo quy trình vận hành; không tự bật migration trong app remote. Không chỉ
áp dụng V25 nếu cloud còn thiếu V21–V24. Lượt deploy 14/10/2026 bên dưới xác nhận
cloud đã có V1–V24 và chỉ áp dụng V25 sau backup/diễn tập.

Backend: `AssessmentReviewTest`, `AssessmentSecurityTest`, `AssessmentMysqlTest`,
`QuizDocumentReaderTest`, `QuizImportServiceTest`, cộng các hồi quy quiz/teacher.
MySQL tests chỉ bật qua `EDULAB_TEST_DB_URL` loopback 3307/edulab_test. AI và scanner
trong nghiệp vụ nhập đề được mock; không chứng minh chất lượng trích xuất AI thật.

Browser: `/Users/ngttu/Workspace/project_asm_prj/src/test/browser/assessment.cjs`,
chỉ chạy với `EDULAB_DISPOSABLE_TEST_DB=true` và `EDULAB_SMOKE_URL` loopback.
Kiểm tra nộp bài, bộ lọc câu sai, UI AI, cảnh báo/số lần rời tab, quyền giáo viên,
SVG phổ điểm, luồng đáp án thiếu/duyệt và responsive 1440/768/390/320 px.
AI UI dùng network mock; visibility được mô phỏng để kiểm handler, không phải
chứng nhận chống gian lận hoặc kiểm thử mọi trình duyệt/thiết bị.

### Kết quả xác nhận 14/10/2026

- Java 21 / Maven: `verify -Dtest='!MysqlIntegrationTest' -Dedulab.test.browserUploads=true`
  trên MySQL 8.4 dùng một lần, loopback 3307: **300 tests, 0 failures, 0 errors,
  0 skipped**. `MysqlIntegrationTest` dùng Docker/Testcontainers được loại rõ;
  không tuyên bố đã chạy Docker suite. Fixture JPEG trình duyệt hiện có dùng cho
  hồi quy upload cũng đạt.
- DB sạch áp dụng/validate đủ 25 migration, V25 tạo các bảng mới thành công.
  Chỉ DB nằm trong thư mục `.tools/assessment-mysql-final` được dùng; không truy cập
  hoặc sửa cloud/secrets. Giữ lại DB trước đó để không xóa fixture khi thử lại.
- WAR build thành công tại
  `/Users/ngttu/Workspace/project_asm_prj/target/edulab-khtn8.war`.
- Browser assessment đạt **1440/768/390/320 px**: nộp, đúng/sai/bỏ trống, hỏi AI,
  nhận xét, CSV thật, quyền giáo viên, API giải thích khóa trước công bố, rời tab,
  sinh/duyệt đáp án thiếu, lịch sử, phổ điểm SVG; không pageerror/overflow trang.
  Hồi quy `teacher-workspace.cjs` đạt **27 trang, 4 kích thước**.
- Kiểm tra ảnh desktop/mobile; tăng độ rõ chữ đáp án khóa, giữ nhãn biểu đồ đọc
  được bằng vùng cuộn ngang trên mobile, không có widget AI nổi che trang làm bài.
  Ảnh ở `/tmp/edulab-assessment-screenshots`. Đây không phải audit accessibility đầy đủ.
- UI AI dùng network mock; backend import/review dùng mock provider/scanner,
  DOCX reader đọc tệp ZIP/XML thật trong test. Chưa xác nhận chất lượng trích xuất
  PDF/DOCX với AI thật hoặc ClamAV thật. Đây là kết quả local trước lượt deploy bên dưới.
- Các process app/MySQL test khởi tạo trong đợt này được dừng sau xác nhận.

### Triển khai container và xác nhận bổ sung — 14/10/2026

- Image `edulab-khtn8:assessment-v25-20261014`, container `edulab`, port
  `127.0.0.1:8088`, context `/edulab`. Compose image pin và tag `latest` được cập nhật;
  image trước được giữ lại. Docker **healthy**, health **UP**, restart count **0**.
- Cloud được kiểm tra chỉ đọc: history V1–V24 hợp lệ, đúng một migration pending.
  Backup SQL gzip có SHA-256, restore trên MySQL tách biệt khớp dữ liệu, Flyway
  diễn tập V24 → V25 thành công. Job cloud chỉ áp dụng V25; kiểm tra sau migration
  xác nhận schema/dữ liệu bảng cũ không đổi, ba bảng mới trống, FK/CHECK TABLE đạt.
- Approval migration chỉ truyền cho job một lần; `.env.remote` không đổi,
  `EDULAB_MIGRATE_ON_DEPLOY=false` và Spring Flyway vẫn tắt khi chạy app.
  Giữ secrets/khóa mã hóa, UID không root, cookie Secure, scan bypass false,
  port/volume storage như trước. Không repair/clean/baseline/restore cloud.
- Chạy lại Maven `verify -Dedulab.test.browserUploads=true` trên DB dùng một lần
  **sạch trước browser**: **303 tests, 0 failures, 0 errors, 0 skipped**, gồm cả
  **3 Docker/Testcontainers tests**. Fixture MySQL dùng `utf8mb4_unicode_ci` để
  phù hợp migration curriculum; không sửa migration đã áp dụng hay collation cloud.
  Bộ kiểm tra Compose: **6 tests đạt**; WAR/container build thành công.
- 147 resource/JSP và 25 file migration trong image khớp byte với source.
  Browser assessment đạt 1440/768/390/320 px; teacher regression đạt 27 trang ở
  bốn kích thước. Video AI regression offline đạt tám kích thước, bảo toàn thay đổi video.
- Browser live chỉ GET trang chủ/khóa học/login/register ở 1440/390 px: HTTP 200,
  không overflow, không pageerror/resource nội bộ lỗi. Không đăng nhập hoặc tạo
  fixture trên cloud; nghiệp vụ đăng nhập/làm bài được kiểm tra trên DB dùng một lần.
- **Giới hạn live:** ClamAV chưa cấu hình và `ai_pdf_enabled=false`; nhập PDF/DOCX
  bị chặn an toàn, không gửi file chưa CLEAN tới AI. Không đổi provider/model,
  không bật PDF hoặc scan bypass. Chưa kiểm thử trích xuất với AI/ClamAV thật.
- Backup/log/report riêng tư (không commit):
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/assessment-v25-20261014/`.
  DB browser dùng một lần được giữ trong container test đã dừng; không xóa dữ liệu
  hoặc volume deployment. Image cũ chỉ có V1–V24 nên không phải rollback đã validate
  với cloud V25; không restore DB hoặc bỏ qua guard để rollback ứng dụng.