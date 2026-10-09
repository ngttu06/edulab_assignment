# Học qua video

## Vận hành

- Thư viện: nút **Học qua video** / **Tiếp tục xem video** cho bài có video; bài chưa
  có video vẫn đọc nội dung như trước. Trang xem: `/lessons/{id}/video`.
- Menu **Video bài học**: `/teacher/videos`. Admin phân công giáo viên theo bài;
  chỉ admin hoặc giáo viên được phân công được cấu hình/thay/gỡ video. Quyền quản lý
  lớp không mặc nhiên cho phép sửa video của mọi bài.
- Một video chính mỗi bài, nguồn `YOUTUBE` hoặc `PLAYERJS` (HLS `.m3u8` / MP4 `.mp4`). Nhập link trực tiếp, xem thử để điền
  thời lượng chính xác (1–21600 giây), ngưỡng hoàn thành 80–100%, mặc định **95%**.
  Video cần VOD có thời lượng xác định; không hỗ trợ livestream/DVR. Ngưỡng 100%
  có thể khó đạt vì sai số metadata/lấy mẫu; 95% là mặc định dung sai cho học tập.
- Mỗi lần lưu tạo phiên bản mới, kể cả lưu lại cấu hình tương tự. Học sinh bắt đầu
  tiến trình bài mới, lịch sử video cũ còn nguyên. Các bài phụ thuộc có thể bị khóa lại.
  Gỡ video cũng đặt lại tiến trình bài, sau đó bài trở về hoàn thành thủ công.
- Học sinh chỉ xem bài xuất bản/đã mở khóa. Giáo viên/admin/phụ huynh xem thử không
  được ghi tiến độ. API tiến độ luôn lấy student ID từ tài khoản đăng nhập.

## Nguồn MP4 và HLS

- Trong form chọn **HLS (.m3u8) / MP4 (.mp4)**. Cả MP4 và HLS đều phát bằng
  **PlayerJS chính thức**, dùng chung adapter/timeline/fullscreen/ghi tiến độ
  và lấy khung hình AI. YouTube vẫn dùng IFrame Player độc lập. Provider lưu vẫn là `PLAYERJS`.
- Adapter đặt `tagcors:1` khi khởi tạo PlayerJS để media có `crossorigin="anonymous"`
  **trước** khi tải `src`; nguồn khác origin phải
  trả CORS phù hợp, codec MP4 phải được trình duyệt hỗ trợ. Không fallback tải lại không
  CORS khi lỗi. Có controls PlayerJS để chỉnh âm lượng/tốc độ và controls timeline EduLab.
- `EDULAB_VIDEO_ALLOWED_HOSTS` **trống hoặc `*` cho phép mọi domain HTTPS hợp lệ**,
  áp dụng cả HLS lẫn MP4. Có thể dùng danh sách domain chính xác phân cách dấu phẩy
  để giới hạn lại. Vẫn từ chối HTTP, IP/localhost/.local/.localhost, tài khoản trong URL,
  fragment, cổng ngoài 443, ký tự điều khiển nguồn và URL dài quá 2048 ký tự.
  Đây là kiểm tra cú pháp domain, không tra DNS hoặc xác minh chủ sở hữu nguồn.
- Chỉ trang xem/quản lý video mở CSP `connect-src`/`media-src` cho `https:` khi không
  giới hạn domain (bao gồm manifest/segment/khóa/redirect khác domain). Không mở script
  hoặc iframe tùy ý, không thay CSP trang khác, không proxy/fetch video bằng backend.
- Nới domain không bỏ qua CORS, quyền nhúng, codec hay yêu cầu VOD có thời lượng cố định.

### Cài PlayerJS miễn phí để phát HLS và MP4

EduLab hiện đã đóng gói player mặc định **PlayerJS 22.0.7** nguyên bản từ plugin
WordPress chính thức **2.25** tại `static/js/vendor/playerjs.js`; không cần cài
WordPress. Build lại WAR/image và khởi động lại để cập nhật ứng dụng đang chạy.
Nguồn tải, checksum và điều khoản được ghi trong
`src/main/resources/static/js/vendor/PLAYERJS-INSTALL.md`.
Bundle có HLS và JS API, không bật Events tracking; adapter dùng media events chuẩn
kết hợp API và preload metadata để hỗ trợ tiến độ/tua. Build riêng có Events tracking
vẫn dùng `PlayerjsEvents`. Bundle chính thức cần `unsafe-eval`; ngoại lệ CSP chỉ ở
ba trang video, không ở trang khác/API và không cho script inline/domain tùy ý.

Nếu muốn thay bằng bản build riêng của website:

1. Chủ website tạo bản build tại https://playerjs.com/builder theo điều khoản của
   PlayerJS. Bật HLS, Javascript API, Events tracking → `PlayerjsEvents`; không
   bật quảng cáo/analytics. Giữ controls gốc để điều chỉnh âm lượng/chất lượng/tốc độ.
2. Cài build nguyên bản vào
   `src/main/resources/static/js/vendor/playerjs.js`, build lại WAR. Không có file
   này thì trang báo chưa cài player; không dùng player giả hay fallback vào thư viện
   khác. Đường dẫn có thể đổi bằng `EDULAB_PLAYERJS_PATH=/js/...js`.
3. Mặc định không cần khai báo từng domain. Có thể cấu hình rõ chế độ mở:

   ```sh
   export EDULAB_VIDEO_ALLOWED_HOSTS='*'
   ```

   Hoặc giới hạn bằng `media.example.com,cdn.example.com`, bao gồm domain của
   manifest, segment và khóa. Không dùng wildcard từng phần như `*.example.com`, IP,
   URL hoặc cổng tùy ý. Link cần HTTPS và đuôi `.m3u8` hoặc `.mp4`, có thể có query token
   URL-encoded. Giáo viên không nhập embed HTML/JS.
4. Nguồn HLS cần CORS cho origin EduLab. Không lưu token nguồn có quyền admin/bucket
   vào URL vì học sinh nhận URL phát. Không hỗ trợ server proxy, ký/xoay URL nguồn
   hay cookies của nhà cung cấp. Chọn VOD HTTPS được phép nhúng/phát từ trình duyệt.
5. Nếu build cần tải hls.js riêng, tự host bản thư viện mà build đó hỗ trợ dưới
   `/js/`, theo hướng dẫn PlayerJS. CSP không cho tải script tùy ý hoặc script
   inline; `unsafe-eval` chỉ dành cho bundle chính thức ở trang video. Chỉ trang xem/quản lý video có CSP cho frame YouTube, các
   media HTTPS theo chính sách trên, blob media/worker và inline style cần cho player.

Timeline SVG/range của EduLab được đặt **trong khung PlayerJS** và điều khiển player
qua API miễn phí, không sửa/decode mã PlayerJS, không dùng PRO Timeline Markup.
Xanh là đoạn đã xem, xám là chưa xem, trắng là playhead; tua bằng chuột/chạm/bàn phím.
Thanh điều khiển EduLab thông thường dùng icon phát/thoát, thời gian và timeline 6 px
trên một hàng; mobile hẹp chuyển timeline xuống hàng thứ hai. Vùng tua vẫn cao 30 px
và có focus bàn phím. Chú giải màu nằm trong card tiến trình, không chiếm chỗ dưới video.
Toàn màn hình của **khung trang** chỉ giữ timeline mảnh và nút thoát, ẩn chú giải,
nút phát phụ và thời gian phụ của EduLab; không đưa khung chat/tiến trình bài vào fullscreen.
Nút đổi nhãn/icon vào–thoát theo `fullscreenchange`, kể cả thoát bằng Esc.
Controls gốc của YouTube/PlayerJS vẫn thuộc nhà cung cấp, không bị can thiệp.
Native mobile
fullscreen, nút fullscreen nội bộ player và PiP có thể không hiển thị timeline này.
Timeline YouTube đặt bên dưới iframe; không sửa seek bar do YouTube quản lý.

## Chat AI theo khung hình — 09/10/2026

- Desktop có **Trợ lý AI bài học** bên phải video; màn hình hẹp chuyển chat xuống dưới.
  Chat này dùng ngữ cảnh bài học, không phải chatbot tư vấn website nổi trên trang khác.
- Nút **Ẩn trợ lý AI / Hiện trợ lý AI** ở tiêu đề, bên ngoài panel, hỗ trợ Enter/Space,
  `aria-controls` và `aria-expanded`. Khi ẩn, video chiếm hết chiều ngang desktop;
  panel bị loại khỏi thứ tự Tab. Nếu focus đang trong panel khi ẩn, trả focus về nút.
  Không tạo lại player/phiên học, không dừng playback hoặc xóa chat, draft, preview,
  tiến độ hay request đang gửi. Trạng thái chỉ giữ trong trang, không ghi localStorage.
- Dừng ở đoạn cần hỏi → **Lấy khung hình** → kiểm tra ảnh/mốc thời gian → nhập câu
  hỏi và **Gửi**. Player được dừng trước khi đọc ảnh, đợi seek/frame sẵn sàng tối đa 3 giây.
  Timestamp lấy từ `currentTime` của chính `<video>`, không phải thời điểm gửi câu hỏi.
  Ảnh cũ giữ mốc lúc chụp, không tự đổi mốc nếu tua tiếp. Muốn hỏi khung hình mới cần chụp lại.
- **Chỉ trích ảnh từ phần tử HTML `<video>` của PlayerJS hoặc MP4 mà trang truy cập được**, qua
  canvas → JPEG; ảnh không chứa UI của player, chat hoặc trình duyệt. Không chụp tab,
  cửa sổ hay màn hình; không xin quyền ghi màn hình/thu âm. Không có fallback chia sẻ
  trình duyệt và không cố truy cập pixel trong iframe khác origin.
- **HLS phát được chưa đảm bảo trích ảnh được.** Cần media cùng origin hoặc media khác
  origin được player tải theo chế độ CORS thích hợp và server/CDN cho phép origin EduLab
  (manifest, segment, khóa và redirect liên quan). Cần kiểm tra bản build PlayerJS thực tế,
  đặc biệt native HLS Safari; đặt `crossorigin` sau khi video đã tải không sửa canvas bị taint.
  Không tự reload nguồn để đổi CORS, proxy nguồn hoặc thay thư viện phát trong lúc capture.
  Nguồn được bảo vệ/không cho đọc ảnh có thể không hỗ trợ. Lỗi CORS/canvas, thiếu `<video>`
  hoặc video chưa tải xong được thông báo; ảnh đã chọn trước đó không bị thay bằng ảnh lỗi.
- YouTube IFrame API không cung cấp ảnh tại timestamp. Nút lấy ảnh báo rõ giới hạn iframe
  và không dừng playback của nguồn không hỗ trợ. Muốn tự trích ảnh cần chuyển cấu hình
  bài sang nguồn MP4/HLS được phép sử dụng và cho đọc ảnh; ứng dụng không tự chuyển nguồn.
- Nút ảnh vẫn cho phép chọn thủ công JPG/PNG/WebP ≤5 MiB rồi chuẩn hóa JPEG.
  Đây là thao tác do người dùng chọn file, không phải capture trình duyệt tự động.
  Người dùng phải tự chọn đúng
  khung hình/mốc; không tự nhận diện nội dung video. Không có ảnh thì chat chỉ dùng
  bài học/mốc đã khai báo và báo rõ không nhìn thấy video.
- Client thu ảnh xuống ≤1280 px. API nhận duy nhất JPEG ≤512 KiB, kích thước ≤1600 px,
  kiểm tra định dạng/kích thước trước decode rồi re-encode để loại metadata/bytes lạ.
  Đây là endpoint ảnh tạm thời, **không phải upload file tổng quát**: không lưu R2,
  không tạo `stored_files`, không thay scan status hoặc bypass kiểm tra đính kèm cũ.
  Câu hỏi/ảnh/lịch sử không lưu localStorage hoặc log. Provider AI nhận ảnh khi gửi
  và có chính sách lưu giữ riêng, cần kiểm tra trước khi dùng dữ liệu thật.
- AI cần được bật, model có vision và **Khả năng đọc ảnh của AI** trong cấu hình storage
  được bật. Storage upload không cần bật vì khung hình không lưu trên object store.
  Model mẫu không nhận ảnh. Backend kiểm bài xuất bản/quyền/mở khóa, phiên bản video,
  mốc thời gian, CSRF, hạn mức hiện có và chặn khi đang làm TEST. Không gọi AI mỗi frame.
  Lịch sử tối đa 8 tin nhắn (2000 ký tự/tin), chỉ role user/assistant; không nhận system.
  Đáp án hiển thị text thuần, nguồn bài do server xác định; AI không chứng minh được
  ảnh client thực sự thuộc video và câu trả lời vẫn cần kiểm chứng.

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
| POST | `/api/v1/lessons/{id}/video/chat` | Gửi `{videoId,question,positionMs,history:[{role,content}],frame:null\|{dataUrl,timestampMs}}`, trả text, frameAnalyzed, positionMs, sourceLabels |
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
Cần test thêm nguồn CDN thực tế, mobile Safari/native HLS và YouTube thật có quyền
nhúng trước khi đưa lên production. PlayerJS chính thức/decode HLS được kiểm riêng bên dưới.

Offline regression mới: `VideoFramePolicyTest`, `VideoAiServiceTest`, `VideoAiSecurityTest`,
`VideoLearningViewTest`, `PlayerJsDistributionTest`. Test view render JSP thật thành `watch.html`, `watch-hls.html`, `watch-mp4.html`, `manage.html` và `manage-installed.html`
trong `target/video-ui-fixtures`, không bootstrap Spring, không datasource.
Lệnh verify chỉ chạy các test không dùng database (giữ nguyên môi trường DB hiện tại):

```sh
env -u EDULAB_TEST_DB_URL \
  JAVA_HOME=/Users/ngttu/Workspace/project_asm_prj/.tools/jdk-21.0.12.1+1/Contents/Home \
  /Users/ngttu/Workspace/project_asm_prj/.tools/apache-maven-3.9.9/bin/mvn \
  -f /Users/ngttu/Workspace/project_asm_prj/pom.xml -B \
  '-Dtest=!MysqlIntegrationTest,!*Mysql*,!LocalMysqlWorkflowTest' verify
```

Sau đó chạy browser với Playwright hiện có:

```sh
EDULAB_PLAYWRIGHT_PATH=/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright \
  node /Users/ngttu/Workspace/project_asm_prj/src/test/browser/video-ai-ui.cjs
EDULAB_PLAYWRIGHT_PATH=/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright \
  node /Users/ngttu/Workspace/project_asm_prj/src/test/browser/video-mp4-ui.cjs
EDULAB_PLAYWRIGHT_PATH=/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright \
  node /Users/ngttu/Workspace/project_asm_prj/src/test/browser/video-hls-ui.cjs
```

Test dùng assets thật và API/player fixtures: desktop/mobile 8 độ rộng, AI ẩn/hiện bằng
bàn phím, giữ player/draft/preview/history/in-flight answer và watch tracking; controls
compact, fullscreen/button/external exit (headless có thể mô phỏng exit tương đương Esc).
Nhánh PlayerJS dùng `<video>` với canvas stream offline (không giả ảnh JPEG đầu ra), kiểm
pixel không chứa overlay player, timestamp không đổi sau seek, payload/history/CSRF,
retry/reset, chọn ảnh thủ công, thiếu media và frame chưa sẵn sàng/timeout. Nhánh YouTube
dùng iframe khác origin offline để kiểm giới hạn, không đọc xuyên iframe.
Kiểm CORS gồm lỗi SecurityError mô phỏng và WebM tạo offline, phục vụ qua route khác
origin không có ACAO: video phát được nhưng browser thực sự từ chối xuất canvas.
API chia sẻ trình duyệt bị chặn và đếm lần gọi trong test: yêu cầu bằng 0; mã capture cũng
được kiểm không chứa API này. Không cần flag tự chấp thuận capture của Chrome.
Đây không phải kiểm thử decode HLS hay PlayerJS build thật, CORS CDN thật, Safari/mobile
native HLS hoặc câu trả lời model vision thật; các phần này vẫn cần nguồn thực tế phù hợp.

Riêng `video-hls-ui.cjs` dùng **bundle PlayerJS 22.0.7 nguyên bản thật**, CSP trang
video và ba segment MPEG-TS/H.264 VOD thật từ nguồn demo công khai hls.js/Mux. Lần
đầu tải segment qua HTTPS về `target/hls-test-media`, kiểm SHA-256; các lần sau dùng
cache. Media mẫu không được đóng gói trong WAR. Browser chỉ chạy route offline;
API tiến độ/AI được intercept, không gọi database/server/cloud. Test kiểm metadata
preload, play/pause/seek, khoảng đã xem không lấp gap, JPEG trực tiếp, fullscreen,
preview lặp, bản thiếu asset và lỗi manifest HTTP 403 qua API HLS thật. Không thay
player/time bằng mock. CORS CDN thực tế vẫn cần kiểm tra trên nguồn sẽ triển khai.

`video-mp4-ui.cjs` tạo MP4/H.264 offline bằng MediaRecorder của Chrome rồi kiểm decode
và phát/tua/pause/khôi phục mốc thật qua native video (không giả clock/player); ghi tiến độ,
fullscreen, JPEG/timestamp AI, toggle giữ state, preview quản lý điền thời lượng và cleanup,
responsive và lỗi CORS không phù hợp. Yêu cầu Chrome hỗ trợ ghi MP4/H.264. Không gọi nguồn, app,
database hoặc model thật; endpoint được intercept. Unit/MVC tests kiểm URL mọi domain
với cấu hình trống/`*`, allowlist cụ thể, MP4, HTTPS/URL an toàn, CSRF/quyền và CSP theo route.

Lượt mở domain và thêm MP4: Maven verify/WAR build đạt với **201 test không-database**;
browser regression HLS/YouTube 8 độ rộng và browser native MP4/H.264 đạt. Đã build
image và redeploy riêng service `app` ngày 09/10/2026 theo yêu cầu chủ sở hữu; xem dưới.

Lần tinh chỉnh player-only: đã chạy 198 test không-database + WAR build và browser
regression ở 320/390/640/768/1024/1280/1440/1920 px. Ảnh kiểm UI nằm trong
`target/video-ui-screenshots`; không gọi app/cloud/AI thật. Nguồn lesson 1, PlayerJS build
và CORS HLS thực tế chưa xác minh; không suy ra từ thành công của các fixture offline.

### Redeploy container ngày 09/10/2026 — player-only, mọi domain HTTPS và MP4

- Image đang chạy: `edulab-khtn8:video-mp4-20261009T012318Z`; `latest` trỏ cùng image.
  Container `edulab`, cổng `127.0.0.1:8088`, context `/edulab`.
- Chỉ `app` được recreate bằng `up -d --no-build --no-deps --wait --wait-timeout 180 app`.
  Không chạy lại `migrate`; Flyway vẫn tắt. Đối chiếu migration trong WAR mới với image
  trước đó: không thay đổi. Secrets/runtime env, volume `edulab_app-storage`, port và
  command giữ nguyên; không tạo phiên học, gọi AI hoặc ghi dữ liệu test vào cloud.
- Build image đạt; assets/JSP trong image khớp source, policy trong class của image
  được kiểm trực tiếp với cấu hình trống/`*`, HLS/MP4 và URL không an toàn.
  Sau redeploy: container healthy, restart count 0, health HTTP `UP`; JS/CSS phục vụ
  khớp source mới; trang login 200 và CSP ngoài trang video không bị mở rộng.
- `EDULAB_VIDEO_ALLOWED_HOSTS` runtime đang trống nên mọi domain HTTPS hợp lệ được
  chấp nhận. HLS vẫn cần build PlayerJS của chủ website; CORS/media thực tế lesson 1
  và model vision chưa xác minh bằng phiên học thật.
- Record local (không chứa giá trị env/secrets):
  `/Users/ngttu/Workspace/project_asm_prj/.tools/deployments/video-mp4-20261009T012318Z/deployment.json`.
- Rollback chỉ ứng dụng, giữ nguyên DB/volume:

```sh
EDULAB_IMAGE=edulab-khtn8:before-video-mp4-20261009T012318Z \
  docker compose -f /Users/ngttu/Workspace/project_asm_prj/docker-compose.yml \
  up -d --no-build --no-deps --wait --wait-timeout 180 app
```

Nếu rollback, giữ `EDULAB_IMAGE` cho lần chạy sau; `latest` hiện trỏ bản MP4 mới.

### Lịch sử triển khai trước lần tinh chỉnh player-only

- Image đã build và chạy: `edulab-khtn8:video-ai-20261009`; cổng `127.0.0.1:8088`.
- Kiểm tra đã ghi nhận cho image cũ: 198 test không dùng database đạt; `mvn verify`/WAR
  build đạt; browser offline 8 kích thước đạt; health API `UP` ở lần triển khai đó.
- Image này có luồng capture tab cũ; **chưa chứa thay đổi player-only, nút ẩn/hiện AI
  và controls thu gọn** trong workspace. Lượt tinh chỉnh này không redeploy container.
- Không chạy migration, không thay secrets/storage/schema hoặc viết dữ liệu test vào cloud.
  Các thay đổi workspace có sẵn không bị revert. Container sử dụng config/volume có sẵn.
- Rollback **chỉ ứng dụng**, không rollback database hoặc xóa volume:

```sh
EDULAB_IMAGE=edulab-khtn8:before-video-ai-20261009 \
  docker compose -f /Users/ngttu/Workspace/project_asm_prj/docker-compose.yml \
  up -d --no-build --no-deps --wait app
```

Tại lần triển khai cũ, `latest` trỏ image video-AI; trạng thái hiện tại ở mục redeploy trên.
Nguồn video lesson 1 và câu trả lời vision thật cần xác nhận với phiên đăng nhập thật;
không sử dụng tài khoản demo hoặc tạo phiên học test trên cloud để xác nhận.

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