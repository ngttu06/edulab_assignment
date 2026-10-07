# PlayerJS miễn phí — cài đặt riêng cho website

PlayerJS là phần mềm bên thứ ba của https://playerjs.com/. Dự án không phân phối bản
demo của PlayerJS, không có bản PRO và không dùng tính năng Timeline Markup PRO.

1. Chủ website tạo tài khoản PlayerJS và xuất bản build miễn phí của mình từ Builder.
2. Bật HLS, Javascript API và Events tracking (callback `PlayerjsEvents`). Dùng HLS
   cùng bản hls.js tự host nếu build cần tải thư viện ngoài; không bật quảng cáo/analytics.
3. Đặt file xuất nguyên bản ở `src/main/resources/static/js/vendor/playerjs.js` rồi
   build lại WAR. Có thể đổi đường dẫn cùng origin bằng `EDULAB_PLAYERJS_PATH=/js/...js`.
4. Cấu hình `EDULAB_VIDEO_ALLOWED_HOSTS` bằng domain HTTPS của manifest, segments và
   khóa HLS, phân cách dấu phẩy, không wildcard/cổng/URL. Nguồn cần CORS từ origin app.
5. Kiểm tra CSP/network trên bản build đã chọn. Chính sách trang video không cho
   `unsafe-eval`, script inline hay tải script từ domain PlayerJS bất kỳ. Nếu build
   đòi hỏi những quyền này, chọn lại cấu hình phù hợp thay vì mở toàn website.

Timeline có tua và tô các đoạn đã xem là SVG/range của EduLab trong khung player,
điều khiển qua API miễn phí. Nó không sửa mã PlayerJS hoặc truy cập DOM nội bộ.
Trình duyệt fullscreen native/PiP có thể không hiển thị timeline tùy biến này.
Tuân thủ điều khoản của PlayerJS và quyền sử dụng nội dung video.