document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('loginForm') || document.getElementById('registerForm');
    if (!form) return;
    if (form.id === 'loginForm' && new URLSearchParams(window.location.search).get('passwordChanged') === 'true') {
        const notice = document.getElementById('loginNotice');
        notice.textContent = 'Đổi mật khẩu thành công. Mọi phiên đăng nhập đã kết thúc. Hãy đăng nhập bằng mật khẩu mới.';
        notice.classList.remove('d-none');
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const registration = form.id === 'registerForm';
        const notice = document.getElementById(registration ? 'notice' : 'error');
        const button = form.querySelector('button');
        button.disabled = true;
        notice.className = 'alert d-none small';
        const value = id => document.getElementById(id).value;
        const body = {username: value('username'), password: value('password')};
        if (registration) Object.assign(body, {fullName: value('fullName'), role: value('role'), email: value('email')});
        try {
            await window.edulab.api('/api/v1/auth/' + (registration ? 'register' : 'login'), {method: 'POST', body});
            window.location.assign(window.edulab.context + (registration ? '/login?registered=true' : '/dashboard'));
        } catch (error) {
            notice.textContent = error.message;
            notice.className = 'alert alert-danger small';
            button.disabled = false;
        }
    });
});
