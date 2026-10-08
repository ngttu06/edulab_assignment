# Không gian giáo viên

## Sử dụng

Đăng nhập bằng tài khoản `TEACHER` (hoặc `ADMIN`). Quản trị viên phải phân công
giáo viên vào lớp trước khi giáo viên tạo nội dung cho lớp đó.

### Bài học

Vào **Soạn bài học & quiz** (`/edulab/teacher/lessons`): chọn chủ đề,
nhập tiêu đề, mô tả, mục tiêu, nội dung và thời lượng; chọn ít nhất một lớp.
Lưu nháp, xuất bản hoặc lưu trữ. Chọn bài học trong danh sách để chỉnh sửa.
Nội dung nhận HTML cơ bản được lọc bằng Jsoup; không hỗ trợ script/iframe.
Mỗi lần lưu tạo một phiên bản mới. Tiến độ học đã có không tự đặt lại khi sửa nội dung.

### Quiz trực tiếp

Tại mục **Quiz của tôi** (`/edulab/teacher/quizzes#quizEditor`):

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

Migration mới `V17__teacher_authoring.sql` thêm người tạo/phạm vi bài học,
phạm vi quiz và hai bảng liên kết lớp. Không sửa migration cũ.
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