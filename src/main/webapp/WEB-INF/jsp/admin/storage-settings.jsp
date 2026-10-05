<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<section class="card mb-4" aria-labelledby="storageSettingsTitle"><div class="card-body">
    <span class="eyebrow">FILE BÀI TẬP · BUCKET PRIVATE</span>
    <h2 id="storageSettingsTitle" class="h5">Lưu trữ S3 / Cloudflare R2</h2>
    <p class="text-muted small">Trình duyệt upload trực tiếp bằng presigned PUT URL (5 phút). Server chỉ cấp URL, xác nhận và đọc file để kiểm tra/quét/AI. File local cũ vẫn đọc được.</p>
    <p id="storageNotice" role="status" aria-live="polite" tabindex="-1"></p>
    <form id="storageForm"><fieldset id="storageFields" disabled>
        <div class="form-check mb-3"><input id="storageEnabled" type="checkbox" class="form-check-input"><label for="storageEnabled" class="form-check-label">Bật upload trực tiếp R2</label></div>
        <div class="row g-3">
            <div class="col-md-12"><label for="storageEndpoint" class="form-label">S3 endpoint HTTPS</label><input id="storageEndpoint" type="url" class="form-control" maxlength="500" required><p class="small text-muted mt-1">Host phải thuộc allowlist server. Đổi bucket/endpoint/region/prefix cần nhập lại cả hai khóa; file cũ giữ cấu hình riêng.</p></div>
            <div class="col-md-4"><label for="storageBucket" class="form-label">Bucket</label><input id="storageBucket" class="form-control" required maxlength="63"></div>
            <div class="col-md-4"><label for="storageRegion" class="form-label">Region</label><input id="storageRegion" class="form-control" required maxlength="63"></div>
            <div class="col-md-4"><label for="storagePrefix" class="form-label">Prefix</label><input id="storagePrefix" class="form-control" required maxlength="80" pattern="[A-Za-z0-9_-]+"></div>
            <div class="col-md-6"><label for="storageAccessKey" class="form-label">Access Key ID mới</label><input id="storageAccessKey" type="password" class="form-control" maxlength="256" autocomplete="new-password"></div>
            <div class="col-md-6"><label for="storageSecretKey" class="form-label">Secret Access Key mới</label><input id="storageSecretKey" type="password" class="form-control" maxlength="512" autocomplete="new-password"></div>
            <div class="col-12"><p id="storageKeyState" class="small text-muted mb-0"></p></div>
            <div class="col-md-4"><label for="storageImageLimit" class="form-label">Ảnh tối đa (MB)</label><input id="storageImageLimit" type="number" min="1" max="5" step="1" required class="form-control"></div>
            <div class="col-md-4"><label for="storagePdfLimit" class="form-label">PDF tối đa (MB)</label><input id="storagePdfLimit" type="number" min="1" max="10" step="1" required class="form-control"></div>
            <div class="col-md-4"><label for="storageQuota" class="form-label">Storage mỗi tài khoản (MB)</label><input id="storageQuota" type="number" min="1" max="1024" step="1" required class="form-control"></div>
        </div>
        <p class="small text-muted mt-3">File cần quét ClamAV trước khi dùng. Chưa có scanner hoặc scanner lỗi: tiếp tục cách ly. Bypass chỉ dành cho profile local với file mẫu tin cậy.</p>
        <div class="form-check"><input id="storageAiImages" type="checkbox" class="form-check-input"><label for="storageAiImages" class="form-check-label">Model AI đã được xác nhận đọc ảnh (OpenAI image_url)</label></div>
        <div class="form-check mb-3"><input id="storageAiPdf" type="checkbox" class="form-check-input"><label for="storageAiPdf" class="form-check-label">Model AI đã được xác nhận đọc PDF (OpenAI file_data)</label></div>
        <p class="small text-muted">Hai tùy chọn này không tự kiểm chứng model. Chỉ bật sau khi thử file mẫu; file gửi AI được đọc từ R2, kiểm tra hash và truyền dưới dạng dữ liệu, không công khai URL.</p>
        <div class="d-flex flex-wrap gap-2"><button class="btn btn-primary" type="submit">Lưu cấu hình storage</button><button id="storageTest" type="button" class="btn btn-outline-primary">Kiểm tra PUT / CORS / đọc / xóa</button></div>
    </fieldset></form>
    <details class="mt-3"><summary>CORS cho local hiện tại</summary><p class="small mt-2">Thiết lập trong Cloudflare → R2 → edulab → Settings → CORS. Không mở public bucket; origin không có /edulab. Đổi cổng thì đổi origin.</p><pre class="text-break small">[
  {
    "AllowedOrigins": ["http://127.0.0.1:8085", "http://localhost:8085"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["Content-Type"],
    "MaxAgeSeconds": 3600
  }
]</pre></details>
</div></section>