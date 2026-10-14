# Không gian giáo viên

Quiz/đề kiểm tra hiện có xem lại bài học sinh, báo cáo phổ điểm, nhận xét AI,
ghi nhận rời tab và nhập PDF/DOCX có xác nhận đáp án thiếu. Xem hướng dẫn và
giới hạn tại `/Users/ngttu/Workspace/project_asm_prj/docs/assessment.md`.

## Sử dụng

Đăng nhập bằng tài khoản `TEACHER` (hoặc `ADMIN`). Quản trị viên phải phân công
giáo viên vào lớp trước khi giáo viên tạo nội dung cho lớp đó.

### Bài học

Vào **Bài học của tôi** (`/edulab/teacher/lessons`), chọn **Thêm mới**: chọn chủ đề,
nhập tiêu đề, mô tả, mục tiêu, nội dung và thời lượng; chọn ít nhất một lớp.
Lưu nháp, xuất bản hoặc lưu trữ. Chọn bài học trong danh sách để chỉnh sửa.
Nội dung nhận HTML cơ bản được lọc bằng Jsoup; không hỗ trợ script/iframe.
Mỗi lần lưu tạo một phiên bản mới. Tiến độ học đã có không tự đặt lại khi sửa nội dung.

### Quiz trực tiếp

Tại mục **Quiz của tôi** (`/edulab/teacher/quizzes`), chọn **Thêm mới**:

1. Nhập tiêu đề, thời lượng 1–600 phút và số lượt làm 1–100.
2. Chọn luyện tập hoặc kiểm tra. Bài kiểm tra xuất bản cần thời điểm công bố
   đáp án sau thời điểm hiện tại cộng thời lượng làm bài.
3. Chọn các lớp được làm bài.
4. Thêm tối đa 200 câu, mỗi câu 2–4 lựa chọn, đúng một đáp án, giải thích tùy chọn.
   Có thể xóa và thay đổi thứ tự câu hỏi.
5. Điểm mỗi câu là trọng số (0.01–9999.99); kết quả được quy đổi về thang 10.
6. Chọn **Xuất bản** hoặc bỏ chọn để lưu nháp/ẩn, sau đó lưu quiz.

Chọn quiz đã tạo để sửa. Những lượt làm đã bắt đầu sử dụng snapshot cũ,
không bị thay đổi câu hỏi/đáp án/điểm khi giáo viên sửa đề. Không thể bỏ một lớp
khỏi phạm vi quiz nếu quiz đã được giao cho lớp đó; có thể ẩn quiz thay thế.

### Nhập CSV và file mẫu

- Trang riêng: `/edulab/teacher/quizzes/import`; không nhập CSV trên trang chỉnh sửa quiz.
- Nút **Tải file CSV mẫu** tải `/edulab/teacher/quizzes/template.csv`.
- File nguồn: `src/main/resources/templates/quiz-mau.csv`.
- Soạn bằng Excel/Google Sheets rồi xuất **CSV UTF-8**, không phải XLSX/DOCX/PDF.
- Giữ tên/thứ tự 8 cột:
  `question,option_a,option_b,option_c,option_d,correct_answer,explanation,points`.
- Bắt buộc câu hỏi, A, B, đáp án và điểm. C/D có thể trống từ cuối;
  không để trống C khi có D. Đáp án là A/B/C/D, không phân biệt hoa thường.
- Dùng dấu chấm thập phân. Nội dung chứa dấu phẩy/xuống dòng phải đặt trong
  ngoặc kép; dấu `"` bên trong được viết `""` theo CSV tiêu chuẩn.
- Tối đa 1 MiB/file và 200 câu. Hỗ trợ BOM UTF-8, LF/CRLF, tiếng Việt.
- **Nhập & xem trước** chỉ phân tích dữ liệu, không ghi database và không lưu file.
  Lỗi trả về số dòng bắt đầu của bản ghi; dữ liệu đang soạn được giữ khi file lỗi.
- Kiểm tra/sửa câu hỏi trên web rồi **Lưu quiz**. Không tự động thực thi công thức
  hoặc HTML trong file; nội dung câu hỏi được coi là văn bản.

### Lớp và học sinh

Vào **Quản lý lớp học**, chọn lớp:

- Tìm theo họ tên/tên đăng nhập; danh sách 25 học sinh/trang, có trạng thái thành viên.
- Thêm tài khoản học sinh đang hoạt động bằng tên đăng nhập chính xác.
- Ngừng tham gia hoặc khôi phục. Không xóa tài khoản, điểm hay tiến độ.
- Xem tiến độ bài học riêng của lớp, số học sinh hoàn thành từng bài/quiz.
- Chọn quiz đã xuất bản, đặt hạn nộp và giao cho lớp. Hạn nộp dùng để đánh giá
  hoàn thành đúng hạn, không thay thế thời lượng/số lượt làm bài.
- **Tiến độ & điểm**: tiến độ từng bài; mỗi quiz có trạng thái chưa làm/đang làm/đã chấm,
  số lượt, số lượt đã chấm, điểm cao nhất, điểm gần nhất và tối đa 200 lượt gần nhất.
  Quiz chưa làm có điểm `—`, không phải 0. Nhận xét được chia sẻ với học sinh và
  phụ huynh đã liên kết theo cơ chế nhận xét hiện có.

Điểm tổng hợp theo quiz gồm các lượt làm của quiz, không chỉ lượt trong thời hạn
một lần giao bài. Nếu một quiz dùng ở nhiều lớp, các lớp đó cùng xem kết quả quiz;
tạo quiz riêng khi cần bài kiểm tra độc lập giữa các lớp.

## Phân quyền và tương thích

- Giáo viên chỉ quản lý thành viên/báo cáo trong lớp được phân công.
- Chỉ tác giả hoặc admin sửa nội dung; tác giả cũng phải còn phụ trách các lớp
  đã liên kết với nội dung đó. Giáo viên khác trong cùng lớp không được đọc đáp án
  qua API soạn quiz.
- Nội dung tạo từ màn hình mới mặc định chỉ dành cho lớp đã chọn. Học sinh ngoài
  lớp/đã ngừng tham gia không thấy bài và không thể bắt đầu lượt quiz mới bằng ID.
- Lượt làm của chính học sinh đã lưu vẫn giữ quyền xem/nộp theo cơ chế snapshot,
  ngay cả khi quiz bị ẩn hoặc học sinh rời lớp; dữ liệu lịch sử không bị xóa.
- Nội dung dùng chung trước đây giữ nguyên. Danh mục công khai/AI tư vấn không
  lấy bài riêng của lớp. Báo cáo học sinh cũ cũng lọc nội dung riêng theo quyền giáo viên.
- Quyền tạo tài khoản, đổi vai trò và phân công giáo viên vẫn thuộc admin.
- API ghi yêu cầu CSRF. Có audit cho lưu nội dung và đổi thành viên.

## Triển khai

V17 thêm người tạo/phạm vi bài học, phạm vi quiz và hai bảng liên kết lớp.
V18 (`V18__teacher_workspace_crud.sql`) thêm dấu xóa/lưu trữ/hủy và bảng
phiên bản nhận xét. Không sửa migration cũ. WAR mới yêu cầu schema V18.
Chạy migration theo quy trình triển khai hiện có trước khi dùng WAR mới;
không bật tự động migrate trên database remote. Sao lưu trước khi nâng cấp.

Servlet multipart được bật lazy cho CSV, giới hạn file 1 MiB/request 2 MiB.
Upload ảnh/PDF trực tiếp tới object storage vẫn dùng cơ chế hiện có.

## Kiểm thử

- `QuizCsvTest`: file mẫu, BOM, dấu phẩy, xuống dòng, dấu ngoặc kép, encoding,
  giới hạn file/câu hỏi/điểm và lỗi dòng.
- `TeacherSecurityTest`: login, role, CSRF, validation, tải mẫu và multipart import.
- `TeacherMysqlTest`: tạo/sửa/xuất bản, XSS, cách ly lớp, membership, báo cáo,
  snapshot và điểm, dữ liệu không hợp lệ, không lộ nội dung lớp khác qua báo cáo cũ.
- `src/test/browser/teacher-authoring.cjs`: chạy trên **database test dùng một lần**;
  tạo fixture và không tự xóa lịch sử. Yêu cầu `EDULAB_DISPOSABLE_TEST_DB=true`,
  `EDULAB_SMOKE_URL` loopback và Playwright/Chrome từ môi trường kiểm thử.

## Workspace V18: trang riêng và CRUD

Các đường dẫn bên dưới nằm sau context `/edulab`:

| Chức năng | Đường dẫn |
|---|---|
| Bài học / quiz | `/teacher/lessons`, `/teacher/quizzes`; `/new`, `/{id}`, `/{id}/edit` |
| CSV | `/teacher/quizzes/import` (xem trước, sửa câu hỏi và lưu quiz mới) |
| Lớp | `/teacher/classes`; `/new`, `/{id}`, `/{id}/edit` |
| Phân công giáo viên | `/teacher/classes/{id}/teachers` (admin) |
| Thành viên lớp | `/teacher/classes/{id}/students`; `/new`, `/{studentId}` |
| Bài giao | `/teacher/classes/{id}/assignments`; `/new`, `/{assignmentId}`, `/{assignmentId}/edit` |
| Tiến độ / kết quả | `/teacher/classes/{id}/progress`, `/results`; tương tự dưới `/students/{studentId}` |
| Nhận xét | `/teacher/students/{studentId}/feedback`; `/new`, `/{id}`, `/{id}/edit` |

Danh sách không chứa biểu mẫu soạn nội dung. Trang chi tiết liên kết sang trang sửa;
thao tác xóa có xác nhận và kiểm tra CSRF. Giáo viên chỉ quản lý lớp được phân công.

### Quy tắc xóa và sửa

- **Bài học:** xóa mềm và lưu trữ các phiên bản; giữ liên kết lớp và tiến độ.
- **Quiz:** xóa mềm, ngừng xuất bản, hủy các bài giao liên quan. Không thay đổi snapshot,
  câu hỏi hay điểm của lượt đã bắt đầu; học sinh vẫn xem/nộp lượt cũ qua ID lượt làm.
- **Lớp:** chỉ admin tạo/sửa/lưu trữ. Lớp lưu trữ vẫn đọc được lịch sử, không nhận
  thành viên, phân công, nội dung hoặc bài giao mới. Không xóa tài khoản học sinh.
- **Thành viên:** ngừng tham gia/khôi phục; giáo viên vẫn xem tiến độ và kết quả
  trong lớp của thành viên đã ngừng tham gia. Không cấp quyền quản trị tài khoản.
- **Bài giao:** sửa hạn nộp, không đổi quiz hoặc thời điểm giao. Hủy không xóa
  quiz/lượt làm và không chặn truy cập quiz còn xuất bản. Muốn đổi đề phải giao mới.
- **Nhận xét:** tác giả hoặc admin sửa/xóa; bản trước khi sửa được giữ trong
  `teacher_feedback_revisions`. Nhận xét xóa không còn hiển thị cho học sinh/phụ huynh.
- **Điểm/tiến độ:** chỉ đọc; không có API chỉnh sửa thủ công trong workspace.
- Nội dung xóa không thể xuất bản lại qua màn hình quản trị cũ. V18 chưa có
  khôi phục nội dung/lớp đã xóa; khôi phục thành viên lớp vẫn được hỗ trợ.

### Kiểm thử V18

`TeacherMysqlTest` kiểm tra CRUD, tác giả/lớp, lưu trữ lớp, hủy bài giao,
phiên bản nhận xét và bảo toàn tiến độ/snapshot. `TeacherSecurityTest` kiểm tra
CSRF cho xóa và các view danh sách/form riêng.

`src/test/browser/teacher-workspace.cjs` là workflow cho V18: tạo/sửa bài học,
CSV, sửa quiz, giao/sửa/hủy bài giao, nhận xét, thành viên, lớp/admin, xóa mềm và
kiểm tra trang tại 1440/768/390/320 px và encoding tiếng Việt của navigation.
`teacher-authoring.cjs` chuyển tiếp sang suite này để giữ lệnh chạy cũ tương thích.
Chỉ chạy trên database test dùng một lần, đặt `EDULAB_DISPOSABLE_TEST_DB=true`,
`EDULAB_SMOKE_URL` loopback, `EDULAB_PLAYWRIGHT_PATH` và `EDULAB_CHROME_PATH`
nếu cần. Chạy bộ Java test trước browser để fixture browser không làm lệch số đếm.

## Tổng quan giảng dạy và theo dõi lớp

- `/teacher`: tổng quan giáo viên, số lớp đang phụ trách, số thành viên đang học theo
  từng lớp, nội dung nháp và tối đa sáu bài sắp đến hạn. `/dashboard` của giáo viên
  dẫn tới đây; admin tiếp tục dùng `/admin` và có thể mở `/teacher` trực tiếp.
- `/teacher/classes/{id}`: sĩ số đang học, số bài học đã xuất bản và năm bài giao gần
  nhất. `/teacher/assignments`: tối đa 200 bài giao gần nhất ở các lớp được phân công;
  lọc tại trình duyệt theo tên quiz/lớp và trạng thái. Bài đã hủy vẫn đọc được.
- Chi tiết bài giao liệt kê học sinh kể cả người ngừng tham gia; dấu hoàn thành chỉ
  tính lượt **đã chấm** nộp từ lúc giao đến hạn. Một quiz được giao nhiều lần có thể
  có kết quả tổng hợp khác tình trạng của từng lần giao.
- `/teacher/classes/{id}/results`: bảng điểm tất cả thành viên (bao gồm đã ngừng),
  lọc tại trình duyệt theo học sinh/quiz và đã chấm/chưa có điểm. Xuất toàn bộ dữ liệu
  của lớp qua `/teacher/classes/{id}/results.csv`: CSV UTF-8 BOM, điểm chưa có để trống,
  văn bản escape dấu nháy và tiền tố chống công thức khi mở bằng bảng tính. Đường dẫn
  xuất vẫn kiểm tra phân công lớp ở backend; dữ liệu học sinh chỉ nên lưu ở nơi an toàn.
- Danh sách bài học/quiz lọc tại trình duyệt theo tiêu đề/trạng thái. Nút **Nhân bản
  nháp** tạo bài/quiz mới với câu hỏi và phạm vi lớp được giữ, quiz mới chưa xuất bản;
  không sao chép lượt làm, điểm hay tiến độ. Chỉ tác giả/admin còn được phân công vào
  các lớp liên kết mới có thể nhân bản. Bài học có nút xem trước HTML đã lọc tại server;
  thay đổi chưa lưu sẽ được cảnh báo khi rời trang.
- Các mốc thời gian hiển thị UTC ở trang xem, trường nhập hạn nộp sử dụng giờ thiết bị.
  Các bộ lọc danh sách chạy trên dữ liệu đã trả về (không phân trang tại server); bài
  giao tổng hợp giới hạn 200 dòng, trang kết quả toàn lớp hiện chưa phân trang.