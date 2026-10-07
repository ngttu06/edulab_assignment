# Học qua video

## Vận hành

- Thư viện: nút **Học qua video** / **Tiếp tục xem video** cho bài có video; bài chưa
  có video vẫn đọc nội dung như trước. Trang xem: `/lessons/{id}/video`.
- Menu **Video bài học**: `/teacher/videos`. Admin phân công giáo viên theo bài;
  chỉ admin hoặc giáo viên được phân công được cấu hình/thay/gỡ video. Quyền quản lý
  lớp không mặc nhiên cho phép sửa video của mọi bài.
- Một video chính mỗi bài, nguồn `YOUTUBE` hoặc `PLAYERJS`. Nhập link, xem thử để điền
  thời lượng chính xác (1–21600 giây), ngưỡng hoàn thành 80–100%, mặc định **95%**.
  Video cần VOD có thời lượng xác định; không hỗ trợ livestream/DVR. Ngưỡng 100%
  có thể khó đạt vì sai số metadata/lấy mẫu; 95% là mặc định dung sai cho học tập.
- Mỗi lần lưu tạo phiên bản mới, kể cả lưu lại cấu hình tương tự. Học sinh bắt đầu
  tiến trình bài mới, lịch sử video cũ còn nguyên. Các bài phụ thuộc có thể bị khóa lại.
  Gỡ video cũng đặt lại tiến trình bài, sau đó bài trở về hoàn thành thủ công.
- Học sinh chỉ xem bài xuất bản/đã mở khóa. Giáo viên/admin/phụ huynh xem thử không
  được ghi tiến độ. API tiến độ luôn lấy student ID từ tài khoản đăng nhập.

## Cài PlayerJS miễn phí

1. Chủ website tạo bản build tại https://playerjs.com/builder theo điều khoản của
   PlayerJS. Bật HLS, Javascript API, Events tracking → `PlayerjsEvents`; không
   bật quảng cáo/analytics. Giữ controls gốc để điều chỉnh âm lượng/chất lượng/tốc độ.
2. Cài build nguyên bản vào
   `src/main/resources/static/js/vendor/playerjs.js`, build lại WAR. Không có file
   này thì trang báo chưa cài player; không dùng player giả hay fallback vào thư viện
   khác. Đường dẫn có thể đổi bằng `EDULAB_PLAYERJS_PATH=/js/...js`.
3. Cấu hình domain chính xác của manifest, segments và khóa HLS, ví dụ:

   ```sh
   export EDULAB_VIDEO_ALLOWED_HOSTS=media.example.com,cdn.example.com
   ```

   Không wildcard, IP loopback, URL hoặc cổng tùy ý. Link m3u8 cần HTTPS và đuôi
   `.m3u8`, có thể có query token URL-encoded. Giáo viên không nhập embed HTML/JS.
4. Nguồn HLS cần CORS cho origin EduLab. Không lưu token nguồn có quyền admin/bucket
   vào URL vì học sinh nhận URL phát. Không hỗ trợ server proxy, ký/xoay URL nguồn
   hay cookies của nhà cung cấp. Chọn VOD HTTPS được phép nhúng/phát từ trình duyệt.
5. Nếu build cần tải hls.js riêng, tự host bản thư viện mà build đó hỗ trợ dưới
   `/js/`, theo hướng dẫn PlayerJS. CSP không cho tải script tùy ý, `unsafe-eval`
   hoặc script inline. Chỉ trang xem/quản lý video có CSP cho frame YouTube, các
   media host được cấu hình, blob media/worker và inline style cần cho player.

Timeline SVG/range của EduLab được đặt **trong khung PlayerJS** và điều khiển player
qua API miễn phí, không sửa/decode mã PlayerJS, không dùng PRO Timeline Markup.
Xanh là đoạn đã xem, xám là chưa xem, trắng là playhead; tua bằng chuột/chạm/bàn phím.
Toàn màn hình của **khung trang** giữ timeline nếu trình duyệt hỗ trợ. Native mobile
fullscreen, nút fullscreen nội bộ player và PiP có thể không hiển thị timeline này.
Timeline YouTube đặt bên dưới iframe; không sửa seek bar do YouTube quản lý.

## Tiến độ và đồng bộ

- Mốc thời gian dùng millisecond. Server hợp nhất các khoảng chồng/lân cận chính xác
  (không lấp khoảng trống), tính tổng độ dài duy nhất và phần trăm theo thời lượng
  phiên bản. Resume position lưu riêng, không được dùng để tính phần trăm.
- Ví dụ xem 0–2 và 5–7 phút của video 10 phút: 40%, giữ nguyên khoảng 2–5 chưa xem.
- Frontend lấy mẫu 250 ms, không nối qua seek, pause, buffering, tab ẩn hoặc gap lớn;
  dừng khi chuyển nền, chỉ ghi nhận tới 2x. YouTube không cung cấp seek event trực
  tiếp nên dùng kiểm tra bước nhảy thời gian; tua rất nhỏ trong dung sai lấy mẫu
  không thể phân biệt tuyệt đối với phát bình thường.
- Lưu mỗi 10 giây và khi pause/seek/ẩn trang. Khi lỗi mạng, giữ bản request bất biến
  trong trang, gửi lại cùng sequence để server xử lý idempotent; không cộng trùng.
  Cảnh báo đừng đóng trang khi chưa lưu; đóng trình duyệt đột ngột vẫn có thể mất
  các giây chưa đồng bộ. Không lưu JWT hoặc URL video vào localStorage.
- Một phiên báo cáo mỗi học sinh/video; mở tab mới vô hiệu phiên cũ. Phiên sống tối
  đa hai giờ kể từ báo cáo hợp lệ, được dọn hàng giờ. Server khóa dữ liệu khi ghi,
  giới hạn payload/khoảng, sequence, thời lượng và lượng thời gian theo wall clock
  (burst tối đa 90 giây media), không tin completionPercent do client gửi.
- Đạt ngưỡng thì ghi `lesson_progress=COMPLETED`; dashboard/lộ trình dùng dữ liệu
  hiện có. Bài video chặn nút và API hoàn thành thủ công. Thay/gỡ video và báo cáo
  tiến trình dùng cùng khóa bài để tránh request cũ ghi đè phiên bản mới.
- Đây không phải DRM hoặc chứng minh con người đã nhìn màn hình: client tùy biến
  vẫn có thể khai báo dữ liệu giả trong giới hạn thời gian. Cơ chế phù hợp tiến độ
  học tập, không dùng như chứng cứ điểm danh/thi có mức bảo đảm cao.

## API

| Method | Route | Công dụng |
|---|---|---|
| GET | `/api/v1/lessons/{id}/video/progress` | Đọc tiến độ của tài khoản hiện tại |
| POST | `/api/v1/lessons/{id}/video/sessions` | Mở phiên học sinh, trả `sessionId` và tiến độ |
| POST | `/api/v1/lessons/{id}/video/progress` | Gửi `{sessionId,sequence,positionMs,durationMs,ranges:[{startMs,endMs}]}` |
| GET/PUT/DELETE | `/api/v1/teacher/lessons/{id}/video` | Đọc/lưu/gỡ cấu hình theo phân công |
| POST | `/api/v1/teacher/videos/validate` | Kiểm tra link trước khi xem thử |
| PUT | `/api/v1/admin/lessons/{id}/video-teacher` | Phân công `{teacherId}` hoặc `null` |

Mọi thao tác ghi yêu cầu authentication/CSRF; API/video page có `private, no-store`.
Không có endpoint nhận progress cho user ID do client chọn. Migration
`V16__lesson_video_learning.sql` chỉ chạy tự động với profile local và DB loopback;
không chạy tự động trên cloud.

## Kiểm thử

Unit/security: `WatchedRangesTest`, `VideoPolicyTest`, `LessonVideoSecurityTest`,
`LessonServiceTest`. `LessonVideoMysqlTest` chỉ bật khi `EDULAB_TEST_DB_URL` trỏ tới
DB `edulab_test...` tại `127.0.0.1:3307`; dùng transaction rollback.

`src/test/browser/lesson-video.cjs` chạy trên app local/test DB với Playwright đang
có trong môi trường. Bài test dùng **clock giả** của PlayerJS/YouTube, API và DB thật;
xác minh seek, các đoạn rời rạc, reload, revision, quyền, lỗi/retry và responsive.
Cần test thêm bản PlayerJS thực tế với VOD m3u8, CORS, mobile Safari/Chrome và
YouTube thật có quyền nhúng trước khi đưa lên production.

### Kết quả kiểm tra local ngày 07/10/2026

- Maven `verify` với `-Dtest=!MysqlIntegrationTest`: **169 test, 0 failure/error/skip**,
  bao gồm MySQL loopback và migration V16. `MysqlIntegrationTest` cần Testcontainers
  Docker được loại khỏi lần chạy này; không kết nối database cloud.
- Browser test trên Chrome desktop headless: seek rời rạc, resume/reload, replay,
  retry sau server commit nhưng mất response, bàn phím, phân công, chặn manual
  completion và thay phiên bản đều đạt. UI không tràn ngang ở 1440/1024/768/640/390/320px.
  Hai provider dùng clock giả trong test tự động, không phải phát media thật.
- YouTube API thật/iframe thật đã tải được metadata thời lượng tại trang xem thử.
  Không khẳng định đã kiểm chứng phát trọn video. PlayerJS/HLS thật chưa kiểm chứng
  vì chưa có build miễn phí của chủ website hoặc nguồn m3u8 thật được cung cấp.
- WAR sản phẩm không chứa player giả của bài test hoặc bản PlayerJS demo bên thứ ba.