document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const profileForm = document.getElementById('profileForm');
    const passwordForm = document.getElementById('passwordForm');
    if (!profileForm || !passwordForm) return;
    const fullName = document.getElementById('profileFullName');
    const email = document.getElementById('profileEmail');
    const current = document.getElementById('currentPassword');
    const next = document.getElementById('newPassword');
    const confirm = document.getElementById('confirmPassword');
    function notice(id, message, success = false) {
        const element = document.getElementById(id);
        element.textContent = message;
        element.className = 'alert ' + (success ? 'alert-success' : 'alert-danger');
        element.focus();
    }
    function busy(form, pending) {
        form.setAttribute('aria-busy', String(pending));
        form.querySelectorAll('button').forEach(button => { button.disabled = pending; });
        const button = form.querySelector('[type="submit"]');
        button.dataset.label ||= button.textContent;
        button.textContent = pending ? 'Đang xử lý…' : button.dataset.label;
    }
    profileForm.addEventListener('reset', () => {
        document.getElementById('profileNotice').className = 'alert d-none';
    });
    profileForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (profileForm.getAttribute('aria-busy') === 'true') return;
        if (!fullName.value.trim()) {
            notice('profileNotice', 'Vui lòng nhập họ và tên.');
            fullName.focus();
            return;
        }
        busy(profileForm, true);
        try {
            const profile = await window.edulab.api('/api/v1/profile', {
                method: 'PUT', body: {fullName: fullName.value.trim(), email: email.value.trim()}
            });
            fullName.value = fullName.defaultValue = profile.fullName;
            email.value = email.defaultValue = profile.email;
            document.getElementById('profileDisplayName').textContent = profile.fullName;
            notice('profileNotice', 'Đã cập nhật thông tin cá nhân.', true);
        } catch (error) {
            notice('profileNotice', error.message);
        } finally {
            busy(profileForm, false);
        }
    });
    document.getElementById('showPasswords').addEventListener('change', event => {
        [current, next, confirm].forEach(input => { input.type = event.target.checked ? 'text' : 'password'; });
    });
    passwordForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (passwordForm.getAttribute('aria-busy') === 'true') return;
        if ([current, next, confirm].some(input => new TextEncoder().encode(input.value).length > 72)) {
            notice('passwordNotice', 'Mật khẩu không được vượt quá 72 byte UTF-8.');
            return;
        }
        if (next.value !== confirm.value) {
            notice('passwordNotice', 'Xác nhận mật khẩu mới không khớp.');
            return;
        }
        busy(passwordForm, true);
        try {
            await window.edulab.api('/api/v1/profile/password', {method: 'POST', body: {
                currentPassword: current.value, newPassword: next.value, confirmPassword: confirm.value
            }});
            passwordForm.reset();
            window.location.assign(window.edulab.context + '/login?passwordChanged=true');
        } catch (error) {
            notice('passwordNotice', error.message);
            current.value = '';
            busy(passwordForm, false);
        }
    });
});