(() => {
    'use strict';
    const context = document.querySelector('meta[name="app-context"]')?.content || '';
    async function api(path, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
            const csrfResponse = await fetch(context + '/api/v1/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
            if (!csrfResponse.ok) throw new Error('Không thể xác thực thao tác. Hãy tải lại trang.');
            const csrf = await csrfResponse.json();
            headers.set(csrf.headerName, csrf.token);
        }
        let body = options.body;
        if (body !== undefined && !(body instanceof FormData) && typeof body !== 'string') {
            headers.set('Content-Type', 'application/json');
            body = JSON.stringify(body);
        }
        const response = await fetch(context + path, {...options, method, headers, body, credentials: 'same-origin', cache: 'no-store'});
        const data = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) {
            throw new Error(data?.message || (response.status === 401 ? 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.' : 'Không thể thực hiện thao tác.'));
        }
        return data;
    }
    window.edulab = {api, context};
    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('logoutForm')?.addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.currentTarget.querySelector('button');
            button.disabled = true;
            try {
                await api('/api/v1/auth/logout', {method: 'POST'});
                window.location.assign(context + '/login');
            } catch (error) {
                button.disabled = false;
                const notice = document.getElementById('globalNotice');
                notice.textContent = error.message;
                notice.classList.remove('d-none');
            }
        });
    });
})();
