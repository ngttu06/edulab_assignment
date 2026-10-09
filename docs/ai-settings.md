# Cài đặt AI: system prompt và danh sách model

Admin mở `/edulab/admin/settings/ai` → **Cấu hình AI**.

## System prompt

- **Trợ giảng bài học / video**: chỉ dẫn cho giải thích bài học, file đính kèm và
  trợ lý khung hình video.
- **Hỗ trợ website**: chỉ dẫn riêng cho chat hỗ trợ công khai, tránh lẫn vai trò.
- Tối đa **8000 ký tự mỗi prompt**. Lưu áp dụng vào yêu cầu AI tiếp theo, không
  cần restart. Để trống một ô rồi lưu để khôi phục mặc định của ô đó.
- Prompt nằm trong `ai_settings`, không gửi trong API công khai. Không nhập bí mật,
  API key hoặc thông tin cá nhân vào prompt: prompt được gửi tới provider khi chat.
- Server vẫn bổ sung chỉ dẫn bảo vệ dữ liệu/đề kiểm tra và xử lý tài liệu/file như
  dữ liệu không đáng tin. Đây là chỉ dẫn cho model, không thay thế kiểm tra quyền
  truy cập ở backend; việc tuân thủ còn phụ thuộc provider/model.
- API lưu cũ không gửi trường prompt sẽ giữ nguyên prompt đã lưu. Chuỗi trống reset
  mặc định; hai prompt được chỉnh độc lập.

## Lấy và chọn model

1. Nhập **Base URL gồm phiên bản API**, ví dụ `https://provider.example/v1`.
   Host phải thuộc `EDULAB_AI_ALLOWED_HOSTS`; không nhập `/chat/completions` hoặc `/models`.
2. Bấm **Lấy model từ provider**. Backend gọi `GET {baseUrl}/models`, ví dụ
   `/v1/models`, với `Authorization: Bearer ...`.
3. Cùng Base URL đã lưu có thể dùng khóa đã mã hóa; để ô khóa mới trống. Nếu đổi
   Base URL, nhập API key mới để không gửi khóa đã lưu sang provider khác.
4. Chọn model trong **Chọn model đã lấy** hoặc nhập Model ID thủ công. Bấm
   **Lưu cấu hình AI** để áp dụng; việc lấy danh sách không lưu provider/key/model/prompt.
5. Provider không hỗ trợ API này hoặc timeout: thông báo lỗi và vẫn cho nhập model
   thủ công. Danh sách không chứng minh model có vision/PDF; cần kiểm tra khả năng
   model trước khi bật ảnh/PDF.

API nội bộ: `POST /api/v1/admin/ai-settings/models`, body `{baseUrl, apiKey}`;
response `{models: ["id", ...]}`. Chỉ admin, yêu cầu CSRF, `private, no-store`.
API key đã lưu không trả về browser. Khóa mới do admin nhập được gửi tới backend
trong request HTTPS, không đặt trên query string. Không lưu khóa khi chỉ lấy model.

Server kiểm tra HTTPS/allowlist/địa chỉ IP công khai theo chính sách AI hiện có,
không đi theo redirect, connect timeout 5 giây/read timeout 15 giây,
giới hạn phản hồi 1 MiB, tối đa 2000 model và hai yêu cầu đồng thời.
Chỉ nhận model ID hợp lệ, loại trùng và sắp xếp; không trả upstream body/metadata.

## Database và kiểm thử

V21 chỉ thêm `system_prompt`, `support_system_prompt` nullable vào `ai_settings`.
NULL dùng mặc định ứng dụng, giữ nguyên API key, provider, model và hạn mức.
Phải chạy migration trước khi khởi động image mới.

Test Java: `AiSettingsTest`, `AiSettingsMysqlTest`, `GateCheapAiProviderTest`,
`AiModelServiceTest`, `SupportAiSecurityTest`, `AiSettingsViewTest`.
Test browser: `src/test/browser/ai-settings-ui.cjs`, chạy sau `AiSettingsViewTest`.
Browser test dùng JSP/JS/CSS thật nhưng intercept API, không gọi AI/provider/cloud.