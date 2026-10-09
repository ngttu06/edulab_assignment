<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<section class="card mb-4" aria-labelledby="aiSettingsTitle"><div class="card-body">
    <h2 id="aiSettingsTitle" class="h5">Cấu hình trợ lý AI</h2>
    <p class="text-muted small">Áp dụng ngay cho chat hỗ trợ website và trợ giảng bài học. API tương thích OpenAI Chat Completions; host HTTPS phải được server cho phép. Khóa được mã hóa, không hiển thị lại.</p>
    <p id="aiSettingsNotice" role="status" aria-live="polite"></p>
    <form id="aiSettingsForm">
        <fieldset id="aiSettingsFields" disabled>
            <div class="form-check mb-3"><input id="aiEnabled" name="enabled" type="checkbox" class="form-check-input"><label for="aiEnabled" class="form-check-label">Bật trợ lý AI</label></div>
            <div class="row g-3">
                <div class="col-md-6"><label for="aiBaseUrl" class="form-label">Host / Base URL</label><input id="aiBaseUrl" name="baseUrl" type="url" class="form-control" required maxlength="500" placeholder="https://gatecheap.vn/v1"><p class="small text-muted mt-1">Không nhập phần /chat/completions. Khi đổi host, cần nhập khóa mới hoặc xóa khóa cũ.</p></div>
                <div class="col-md-6"><label for="aiModel" class="form-label">Model ID</label><input id="aiModel" name="model" class="form-control" required maxlength="120" pattern="[A-Za-z0-9._:/\-]{1,120}" placeholder="gpt-6.1-sol" aria-describedby="aiModelsNotice"><div class="d-flex flex-wrap gap-2 mt-2"><button id="aiFetchModels" type="button" class="btn btn-outline-primary">Lấy model từ provider</button></div><label for="aiModelList" class="form-label mt-2">Chọn model đã lấy</label><select id="aiModelList" class="form-select" disabled><option value="">Chưa lấy danh sách model</option></select><p id="aiModelsNotice" class="small text-muted mt-1" role="status" aria-live="polite">Gọi Base URL + /models bằng API key mới hoặc khóa đã lưu. Không lưu cấu hình khi lấy model; vẫn có thể nhập Model ID thủ công.</p></div>
                <div class="col-md-8"><label for="aiApiKey" class="form-label">API key mới</label><input id="aiApiKey" name="apiKey" type="password" class="form-control" maxlength="512" autocomplete="new-password" aria-describedby="aiKeyState"><p id="aiKeyState" class="small text-muted mt-1"></p></div>
                <div class="col-md-4 d-flex align-items-center"><div class="form-check"><input id="aiClearKey" type="checkbox" class="form-check-input"><label for="aiClearKey" class="form-check-label">Xóa khóa đã lưu</label></div></div>
                <div class="col-md-4"><label for="aiDailyLimit" class="form-label">Lượt/người hoặc IP/ngày (UTC)</label><input id="aiDailyLimit" type="number" class="form-control" min="1" max="100" required></div>
                <div class="col-md-4"><label for="aiGlobalLimit" class="form-label">Tổng lượt chat/ngày (UTC)</label><input id="aiGlobalLimit" type="number" class="form-control" min="1" max="10000" required></div>
                <div class="col-md-4"><label for="aiMaxTokens" class="form-label">Số token phản hồi tối đa</label><input id="aiMaxTokens" type="number" class="form-control" min="100" max="4000" required></div>
                <div class="col-12"><label for="aiSystemPrompt" class="form-label">System prompt — trợ giảng bài học / video</label><textarea id="aiSystemPrompt" name="systemPrompt" class="form-control" rows="8" maxlength="8000" aria-describedby="aiPromptHelp"></textarea></div>
                <div class="col-12"><label for="aiSupportSystemPrompt" class="form-label">System prompt — hỗ trợ website</label><textarea id="aiSupportSystemPrompt" name="supportSystemPrompt" class="form-control" rows="8" maxlength="8000" aria-describedby="aiPromptHelp"></textarea><p id="aiPromptHelp" class="small text-muted mt-1">Tối đa 8000 ký tự mỗi prompt. Để trống và lưu để khôi phục mặc định. Áp dụng ở yêu cầu tiếp theo; các quy tắc bảo vệ dữ liệu, nguồn tham khảo và đề kiểm tra của ứng dụng vẫn được bổ sung. Không nhập API key hoặc thông tin riêng tư vào prompt.</p></div>
            </div>
            <p class="small text-muted mt-3">Chat công khai: tối đa 5 lượt/phút/IP và 4 yêu cầu đồng thời. Lượt thất bại vẫn tính hạn mức. Kiểm tra kết nối gửi một yêu cầu thật và có thể phát sinh phí.</p>
            <div class="d-flex flex-wrap gap-2"><button type="submit" class="btn btn-primary">Lưu cấu hình AI</button><button id="aiTestConnection" type="button" class="btn btn-outline-primary">Kiểm tra kết nối đã lưu</button></div>
        </fieldset>
    </form>
</div></section>