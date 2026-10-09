# PlayerJS miễn phí — bản chính thức đã cài

PlayerJS là phần mềm bên thứ ba của https://playerjs.com/. EduLab đóng gói nguyên
bản player mặc định do nhà phát hành phân phối trong plugin WordPress chính thức;
không cài WordPress/PHP, không dùng bản PRO, crack hoặc Timeline Markup PRO.

## Nguồn và tính toàn vẹn

- Nhà phát hành: PlayerJS, https://wordpress.org/plugins/playerjs/.
- Package chính thức, cố định phiên bản: https://downloads.wordpress.org/plugin/playerjs.2.25.zip.
- Tệp trong package: `playerjs/playerjs_default.js`, PlayerJS **22.0.7**, ngày build
  **06.06.2026**. Cài không sửa byte vào `static/js/vendor/playerjs.js`.
- SHA-256 package: `3796568c8739d4029de09e9369c69d56158d9e1c331610626b3356919d2b670c`.
- SHA-256 player: `44c7d16598f92f5e6b81960ebcd81111c7f9d581a209d1b06577d7a6aa85ef4f`.
- Plugin khai báo GPL-2.0-or-later; player thuộc PlayerJS, theo
  https://playerjs.com/docs/en=terms. Không khẳng định toàn bộ player là mã nguồn
  GPL chỉ dựa vào giấy phép plugin. Điều khoản cho phép đóng gói player trong sản
  phẩm website/app để phát nội dung, không phân phối như sản phẩm player độc lập,
  không giải mã/chỉnh sửa mã vendor. Giữ nguyên attribution và file phân phối.
- Bundle có HLS/hls.js và Javascript API; **không bật Events tracking mặc định**.
  EduLab dùng API + media events chuẩn của `<video>` để theo dõi metadata, phát,
  pause, buffering, seek và kết thúc. Callback `PlayerjsEvents` vẫn được hỗ trợ với
  build riêng bật Events tracking. Không cần sửa vendor hoặc tính năng PRO.

## Vận hành và cập nhật

Build lại WAR/image rồi khởi động lại ứng dụng đang chạy để đóng gói file mới.
`VideoPolicy.playerAvailable()` sẽ phát hiện asset và cảnh báo thiếu PlayerJS biến
mất; copy file vào source không thay được asset trong một WAR đã chạy.

Bundle chính thức dùng `eval()` để nạp mã đã nén. CSP chỉ cho phép `unsafe-eval`
trên `/lessons/{id}/video`, `/teacher/videos`, `/admin/videos`; vẫn không cho script
inline hoặc script bất kỳ từ domain PlayerJS. Các trang/API khác giữ CSP nghiêm.
HLS dùng thư viện đã có trong bundle, không tải thêm script ngoài. Nếu không chấp
nhận ngoại lệ CSP này, cần thay bản player và kiểm thử lại, không tự giải mã bundle.

Muốn tùy biến giao diện hoặc cập nhật lên bản Builder riêng:

1. Chủ website tạo tài khoản PlayerJS và xuất bản build miễn phí của mình từ Builder.
2. Bật HLS, Javascript API và Events tracking (callback `PlayerjsEvents`). Dùng HLS
   cùng bản hls.js tự host nếu build cần tải thư viện ngoài; không bật quảng cáo/analytics.
3. Đặt file xuất nguyên bản ở `src/main/resources/static/js/vendor/playerjs.js` rồi
   build lại WAR. Có thể đổi đường dẫn cùng origin bằng `EDULAB_PLAYERJS_PATH=/js/...js`.
4. `EDULAB_VIDEO_ALLOWED_HOSTS` trống hoặc `*` cho phép mọi domain HTTPS hợp lệ
   cho HLS và MP4. Có thể giới hạn lại bằng danh sách domain chính xác của manifest,
   segments và khóa HLS, phân cách dấu phẩy (không wildcard từng phần/cổng/URL).
   Nguồn cần CORS từ origin app. MP4 và HLS đều dùng PlayerJS; YouTube vẫn độc lập.
5. Kiểm tra CSP/network trên bản build đã chọn. Không mở quyền script toàn website;
   nếu thay build, cập nhật checksum/version và test `PlayerJsDistributionTest`.

## Lấy khung hình trực tiếp cho AI

EduLab chỉ đọc pixel từ HTML `<video>` của PlayerJS hoặc MP4 trong trang, rồi
chuyển canvas thành JPEG; không chụp tab/màn hình và không lấy UI của player.
PlayerJS trong iframe khác origin hoặc video được bảo vệ có thể không đọc ảnh được.
Nguồn HLS **phát được không đồng nghĩa đọc canvas được**: media phải origin-clean,
cùng origin hoặc được tải theo chế độ CORS hợp lệ và CDN cho phép origin EduLab.
Kiểm tra manifest, segment/khóa/redirect cùng cấu hình bản build (đặc biệt native HLS
Safari). Nếu cần thuộc tính `crossorigin`, phải được thiết lập trước khi tải media;
EduLab không reload nguồn hay tự đổi cấu hình CORS khi người học bấm lấy ảnh.
Adapter khởi tạo PlayerJS với `tagcors:1` để đặt `crossorigin="anonymous"` trước khi tải nguồn MP4/HLS; thiếu CORS sẽ báo lỗi
phát, không reload nguồn bằng chế độ không CORS hoặc fallback chụp màn hình.
Kiểm thử lại nguồn HLS/bản build của chủ website, đặc biệt Safari native HLS,
trước khi cam kết khả năng trích ảnh. Browser regression dùng bản PlayerJS thật
và MPEG-TS VOD mẫu để kiểm tra phát/seek/tiến độ/ảnh, không thay bằng clock giả.

Timeline có tua và tô các đoạn đã xem là SVG/range của EduLab trong khung player,
điều khiển qua API miễn phí. Nó không sửa mã PlayerJS hoặc truy cập DOM nội bộ.
Trình duyệt fullscreen native/PiP có thể không hiển thị timeline tùy biến này.
Tuân thủ điều khoản của PlayerJS và quyền sử dụng nội dung video.