(() => {
    const notice = document.getElementById('schoolNotice');
    const value = id => document.getElementById(id).value;
    function bind(id, eventName, action) {
        document.getElementById(id)?.addEventListener(eventName, async event => {
            event.preventDefault();
            const button = event.currentTarget.matches('button') ? event.currentTarget : event.currentTarget.querySelector('button');
            button.disabled = true;
            try { await action(); }
            catch (error) { notice.textContent = error.message; }
            finally { button.disabled = false; }
        });
    }
    bind('generateCode', 'click', async () => {
        const result = await window.edulab.api('/api/v1/family/codes', {method: 'POST'});
        document.getElementById('linkCode').textContent = result.code + ' — Hết hạn sau 15 phút, chỉ hiển thị một lần.';
    });
    bind('revokeCodes', 'click', async () => {
        await window.edulab.api('/api/v1/family/codes', {method: 'DELETE'});
        document.getElementById('linkCode').textContent = '';
        notice.textContent = 'Đã hủy các mã chưa dùng.';
    });
    bind('redeemCode', 'submit', async () => {
        await window.edulab.api('/api/v1/family/links', {method: 'POST', body: {code: value('code').trim()}});
        window.location.reload();
    });
    document.querySelectorAll('.revokeLink').forEach(button => button.addEventListener('click', async () => {
        if (!window.confirm('Hủy quyền truy cập kết quả học tập của liên kết này?')) return;
        button.disabled = true;
        try { await window.edulab.api('/api/v1/family/links/' + button.dataset.id, {method: 'DELETE'}); window.location.reload(); }
        catch (error) { notice.textContent = error.message; button.disabled = false; }
    }));
    bind('createClass', 'submit', async () => {
        await window.edulab.api('/api/v1/admin/classes', {method: 'POST', body: {yearId: Number(value('yearId')), name: value('className')}});
        window.location.reload();
    });
    bind('assignMember', 'submit', async () => {
        const role = document.getElementById('memberId').selectedOptions[0].dataset.role;
        await window.edulab.api('/api/v1/admin/classes/' + value('classId') + (role === 'TEACHER' ? '/teachers' : '/students'), {
            method: 'POST', body: {userId: Number(value('memberId'))}
        });
        notice.textContent = 'Đã lưu phân công.';
    });
})();
