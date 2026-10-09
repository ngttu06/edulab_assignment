(() => {
    'use strict';
    const form = document.getElementById('aiSettingsForm');
    if (!form) return;
    const fields = document.getElementById('aiSettingsFields');
    const notice = document.getElementById('aiSettingsNotice');
    const modelList = document.getElementById('aiModelList'), modelsNotice = document.getElementById('aiModelsNotice');
    const baseUrl = document.getElementById('aiBaseUrl'), apiKey = document.getElementById('aiApiKey');
    let modelGeneration = 0;
    function resetModels() {
        modelGeneration++;
        modelList.replaceChildren(new Option('Chưa lấy danh sách model', ''));
        modelList.disabled = true;
        modelsNotice.textContent = 'Lấy lại model khi thay Base URL hoặc API key. Model ID vẫn có thể nhập thủ công.';
    }
    function announce(text, success = false) { notice.textContent = text; notice.className = success ? 'text-success' : 'text-danger'; }
    function fill(data) {
        resetModels();
        document.getElementById('aiEnabled').checked = data.enabled;
        document.getElementById('aiBaseUrl').value = data.baseUrl;
        document.getElementById('aiModel').value = data.model;
        document.getElementById('aiDailyLimit').value = data.dailyLimit;
        document.getElementById('aiGlobalLimit').value = data.globalDailyLimit;
        document.getElementById('aiMaxTokens').value = data.maxOutputTokens;
        document.getElementById('aiSystemPrompt').value = data.systemPrompt || '';
        document.getElementById('aiSupportSystemPrompt').value = data.supportSystemPrompt || '';
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
            maxOutputTokens:Number(document.getElementById('aiMaxTokens').value),
            systemPrompt:document.getElementById('aiSystemPrompt').value,
            supportSystemPrompt:document.getElementById('aiSupportSystemPrompt').value
        };
        fields.disabled = true;
        try { fill(await window.edulab.api('/api/v1/admin/ai-settings', {method:'PUT', body})); announce('Đã lưu. Cấu hình áp dụng cho yêu cầu AI tiếp theo.', true); }
        catch (error) { announce(error.message); }
        finally { fields.disabled = false; document.getElementById('aiApiKey').value = ''; }
    });
    baseUrl.addEventListener('input', resetModels);
    apiKey.addEventListener('input', resetModels);
    document.getElementById('aiClearKey').addEventListener('change', resetModels);
    modelList.addEventListener('change', () => {
        if (modelList.value) document.getElementById('aiModel').value = modelList.value;
    });
    document.getElementById('aiFetchModels').addEventListener('click', async event => {
        if (!baseUrl.reportValidity()) return;
        if (document.getElementById('aiClearKey').checked) {
            modelsNotice.textContent = 'Bỏ chọn xóa khóa để lấy model, hoặc lưu việc xóa khóa trước.'; return;
        }
        const ticket = ++modelGeneration, button = event.currentTarget;
        button.disabled = true; button.setAttribute('aria-busy', 'true');
        modelsNotice.textContent = 'Đang lấy model từ provider…';
        modelList.disabled = true;
        try {
            const data = await window.edulab.api('/api/v1/admin/ai-settings/models', {method:'POST',
                body:{baseUrl:baseUrl.value.trim(), apiKey:apiKey.value.trim()}});
            if (ticket !== modelGeneration) return;
            modelList.replaceChildren(new Option('Chọn model hoặc nhập Model ID thủ công', ''),
                ...data.models.map(id => new Option(id, id)));
            const current = document.getElementById('aiModel').value;
            if (data.models.includes(current)) modelList.value = current;
            modelList.disabled = false;
            modelsNotice.textContent = 'Đã lấy ' + data.models.length + ' model. Chọn model rồi bấm Lưu cấu hình AI để áp dụng.';
        } catch (error) {
            if (ticket === modelGeneration) {
                modelList.replaceChildren(new Option('Không lấy được model', ''));
                modelsNotice.textContent = error.message + ' Bạn vẫn có thể nhập Model ID thủ công.';
            }
        } finally { button.disabled = false; button.removeAttribute('aria-busy'); }
    });
    document.getElementById('aiTestConnection').addEventListener('click', async () => {
        if (!window.confirm('Kiểm tra cấu hình đã lưu bằng một yêu cầu AI thật (có thể phát sinh phí)?')) return;
        fields.disabled = true; announce('Đang kiểm tra cấu hình đã lưu…', true);
        try { const data = await window.edulab.api('/api/v1/admin/ai-settings/test', {method:'POST'}); announce(data.message, true); }
        catch (error) { announce(error.message); }
        finally { fields.disabled = false; }
    });
})();