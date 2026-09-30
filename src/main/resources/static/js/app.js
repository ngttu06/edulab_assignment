(() => {
    'use strict';
    const context = document.querySelector('meta[name="app-context"]')?.content || '';
    let refreshing;
    async function api(path, options = {}, retried = false) {
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
        if (response.status === 401 && !retried && !path.startsWith('/api/v1/auth/')) {
            if (!refreshing) refreshing = api('/api/v1/auth/refresh', {method:'POST'}, true).finally(() => { refreshing = null; });
            await refreshing;
            return api(path, options, true);
        }
        const data = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) {
            throw new Error(data?.message || (response.status === 401 ? 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.' : 'Không thể thực hiện thao tác.'));
        }
        return data;
    }
    window.edulab = {api, context};
    document.addEventListener('DOMContentLoaded', () => {
        const currentPath = window.location.pathname.replace(/\/$/, '') || '/';
        document.querySelectorAll('[data-nav]').forEach(link => {
            const path = new URL(link.href).pathname.replace(/\/$/, '') || '/';
            if (currentPath === path || (path !== (context || '/') && currentPath.startsWith(path + '/'))) {
                link.classList.add('is-active');
                link.setAttribute('aria-current', 'page');
            }
        });
        const menu = document.getElementById('sideNav');
        const toggle = document.getElementById('menuToggle');
        const backdrop = document.getElementById('menuBackdrop');
        const mobile = window.matchMedia('(max-width: 991px)');
        const background = [document.querySelector('.main-content'), document.querySelector('.utility-column'), document.querySelector('.site-footer'), document.querySelector('.app-header'), document.querySelector('.skip-link')].filter(Boolean);
        function setMenu(open, restoreFocus = true) {
            if (!menu) return;
            document.body.classList.toggle('menu-open', open);
            toggle.setAttribute('aria-expanded', String(open));
            toggle.setAttribute('aria-label', open ? 'Đóng menu' : 'Mở menu');
            backdrop.hidden = !open;
            background.forEach(element => { element.inert = open; });
            menu.inert = mobile.matches && !open;
            if (open) {
                menu.setAttribute('role', 'dialog');
                menu.setAttribute('aria-modal', 'true');
                // Wait for the hidden drawer's visibility to be resolved before focusing.
                requestAnimationFrame(() => {
                    if (!document.body.classList.contains('menu-open')) return;
                    // Resolve the CSS visibility transition; focusing a still-hidden element is ignored.
                    menu.getBoundingClientRect();
                    requestAnimationFrame(() => {
                        if (document.body.classList.contains('menu-open')) document.getElementById('menuClose').focus();
                    });
                });
            } else {
                menu.removeAttribute('role');
                menu.removeAttribute('aria-modal');
                if (restoreFocus) toggle.focus();
            }
        }
        if (menu) {
            menu.inert = mobile.matches;
            toggle.addEventListener('click', () => setMenu(true));
            document.getElementById('menuClose').addEventListener('click', () => setMenu(false));
            backdrop.addEventListener('click', () => setMenu(false));
            mobile.addEventListener('change', () => setMenu(false, false));
            document.addEventListener('keydown', event => {
                if (!document.body.classList.contains('menu-open')) return;
                if (event.key === 'Escape') setMenu(false);
                if (event.key === 'Tab') {
                    const items = [...menu.querySelectorAll('a[href], button')].filter(el => el.getClientRects().length);
                    const first = items[0], last = items[items.length - 1];
                    if (!menu.contains(document.activeElement)) { event.preventDefault(); (event.shiftKey ? last : first).focus(); }
                    else if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
                    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
                }
            });
        }
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
