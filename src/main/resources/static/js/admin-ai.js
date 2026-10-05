(() => {
    'use strict';
    const form = document.getElementById('aiSettingsForm');
    if (!form) return;
    const fields = document.getElementById('aiSettingsFields');
    const notice = document.getElementById('aiSettingsNotice');
    function announce(text, success = false) { notice.textContent = text; notice.className = success ? 'text-success' : 'text-danger'; }
    function fill(data) {
        document.getElementById('aiEnabled').checked = data.enabled;
        document.getElementById('aiBaseUrl').value = data.baseUrl;
        document.getElementById('aiModel').value = data.model;
        document.getElementById('aiDailyLimit').value = data.dailyLimit;
        document.getElementById('aiGlobalLimit').value = data.globalDailyLimit;
        document.getElementById('aiMaxTokens').value = data.maxOutputTokens;
        document.getElementById('aiApiKey').value = '';
        document.getElementById('aiClearKey').checked = false;
        document.getElementById('aiKeyState').textContent = (data.keyConfigured ? 'Đã có khóa. Để trống để giữ nguyên.' : 'Chưa lưu API key.') +
            (data.encryptionReady ? '' : ' Server cần EDULAB_AI_ENCRYPTION_KEY (Base64, 32 byte) trước khi lưu khóa.');
        fields.disabled = false;
    }
    window.edulab.api('/api/v1/admin/ai-settings').then(fill).catch(error => announce(error.message));
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const body = {
            enabled:document.getElementById('aiEnabled').checked,
            baseUrl:document.getElementById('aiBaseUrl').value.trim(),
            model:document.getElementById('aiModel').value.trim(),
            apiKey:document.getElementById('aiApiKey').value.trim(),
            clearApiKey:document.getElementById('aiClearKey').checked,
            dailyLimit:Number(document.getElementById('aiDailyLimit').value),
            globalDailyLimit:Number(document.getElementById('aiGlobalLimit').value),
            maxOutputTokens:Number(document.getElementById('aiMaxTokens').value)
        };
        fields.disabled = true;
        try { fill(await window.edulab.api('/api/v1/admin/ai-settings', {method:'PUT', body})); announce('Đã lưu. Cấu hình áp dụng cho yêu cầu AI tiếp theo.', true); }
        catch (error) { announce(error.message); }
        finally { fields.disabled = false; document.getElementById('aiApiKey').value = ''; }
    });
    document.getElementById('aiTestConnection').addEventListener('click', async () => {
        if (!window.confirm('Kiểm tra cấu hình đã lưu bằng một yêu cầu AI thật (có thể phát sinh phí)?')) return;
        fields.disabled = true; announce('Đang kiểm tra cấu hình đã lưu…', true);
        try { const data = await window.edulab.api('/api/v1/admin/ai-settings/test', {method:'POST'}); announce(data.message, true); }
        catch (error) { announce(error.message); }
        finally { fields.disabled = false; }
    });
})();