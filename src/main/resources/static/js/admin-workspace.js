(() => {
    'use strict';
    const {api, context} = window.edulab;
    const notice = document.getElementById('adminNotice');
    function message(text, error = false) {
        notice.textContent = text; notice.classList.toggle('error', error); notice.hidden = false;
        notice.focus(); notice.scrollIntoView({block: 'nearest'});
    }
    const sidebar = document.getElementById('adminSidebar'), toggle = document.getElementById('adminMenuToggle');
    const backdrop = document.getElementById('adminBackdrop'), workspace = document.querySelector('.admin-workspace');
    const mobile = matchMedia('(max-width: 991px)');
    function menu(open, restore = true) {
        document.body.classList.toggle('admin-menu-open', open); toggle.setAttribute('aria-expanded', String(open));
        backdrop.hidden = !open; workspace.inert = open; sidebar.inert = mobile.matches && !open;
        if (open) {sidebar.setAttribute('role', 'dialog'); sidebar.setAttribute('aria-modal', 'true'); document.getElementById('adminMenuClose').focus();}
        else {sidebar.removeAttribute('role'); sidebar.removeAttribute('aria-modal'); if (restore) toggle.focus();}
    }
    sidebar.inert = mobile.matches;
    toggle.addEventListener('click', () => menu(true));
    document.getElementById('adminMenuClose').addEventListener('click', () => menu(false));
    backdrop.addEventListener('click', () => menu(false)); mobile.addEventListener('change', () => menu(false, false));
    document.addEventListener('keydown', event => {
        if (!document.body.classList.contains('admin-menu-open')) return;
        if (event.key === 'Escape') menu(false);
        if (event.key === 'Tab') {
            const controls = [...sidebar.querySelectorAll('a[href],button')].filter(e => e.getClientRects().length);
            const first = controls[0], last = controls.at(-1);
            if (event.shiftKey && document.activeElement === first) {event.preventDefault(); last.focus();}
            else if (!event.shiftKey && document.activeElement === last) {event.preventDefault(); first.focus();}
        }
    });
    async function confirmAction(text) {
        const dialog = document.getElementById('adminConfirm');
        document.getElementById('adminConfirmMessage').textContent = text; dialog.returnValue = '';
        return new Promise(resolve => {dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), {once: true}); dialog.showModal();});
    }
    async function operate(button, request, confirmText, redirect) {
        if (confirmText && !await confirmAction(confirmText)) return;
        button.disabled = true;
        try {await request(); if (redirect) location.assign(context + redirect); else location.reload();}
        catch (error) {message(error.message, true); button.disabled = false;}
    }
    document.querySelectorAll('[data-admin-action]').forEach(button => button.addEventListener('click', () => {
        const {resource, id, adminAction} = button.dataset, restore = adminAction === 'restore';
        operate(button, () => api(`/api/v1/admin/workspace/${resource}/${id}${restore ? '/restore' : ''}`, {method: restore ? 'POST' : 'DELETE'}),
            restore ? 'Khôi phục bản ghi này? Nội dung bài học/quiz sẽ cần được kiểm tra trước khi xuất bản lại.' : resource === 'files' ? 'Chặn tệp này? Mọi liên kết tải xuống sẽ ngừng hoạt động. Bản gốc được giữ để đối soát; không có chức năng bỏ chặn thủ công.' : 'Ngừng hoạt động / lưu trữ bản ghi này? Danh mục không có trạng thái sẽ bị xóa nếu không còn dữ liệu liên quan. Lịch sử học tập vẫn được giữ.',
            button.dataset.returnList ? `/admin/${resource}` : null);
    }));
    document.querySelectorAll('.admin-reference').forEach(widget => {
        const select = widget.querySelector('select'), search = widget.querySelector('input[type=search]');
        const button = widget.querySelector('[data-find-reference]'), status = widget.querySelector('[role=status]');
        let sequence = 0;
        async function find() {
            const version = ++sequence; button.disabled = true; status.textContent = 'Đang tìm…';
            try {
                const rows = await api(`/api/v1/admin/workspace/options/${widget.dataset.reference}?search=${encodeURIComponent(search.value.trim())}`);
                if (sequence !== version) return;
                const saved = [...select.selectedOptions].filter(o => o.value).map(o => ({id: o.value, label: o.textContent}));
                select.replaceChildren();
                if (!select.multiple) select.add(new Option('— Chọn —', ''));
                const seen = new Set();
                [...saved, ...rows].forEach(row => {
                    const value = String(row.id); if (seen.has(value)) return; seen.add(value);
                    const selected = saved.some(o => String(o.id) === value); select.add(new Option(row.label, value, selected, selected));
                });
                if (!saved.length && !select.multiple) select.value = '';
                status.textContent = `${rows.length} kết quả${rows.length === 50 ? ' · Nhập tên cụ thể hơn để tìm tiếp.' : ''}`;
            } catch (error) {if (sequence === version) status.textContent = error.message;}
            finally {if (sequence === version) button.disabled = false;}
        }
        button.addEventListener('click', find);
        search.addEventListener('keydown', event => {if (event.key === 'Enter') {event.preventDefault(); find();}});
        find();
    });
    document.querySelectorAll('[data-instant]').forEach(input => {
        if (!input.dataset.instant) return;
        // SQL timestamps are UTC; the form displays the browser's local timezone.
        let value = input.dataset.instant.replace(' ', 'T');
        if (!/[zZ]|[+-]\d\d:\d\d$/.test(value)) value += 'Z';
        const date = new Date(value);
        if (!Number.isNaN(date.getTime())) input.value = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    });
    const form = document.getElementById('adminResourceForm');
    form?.addEventListener('submit', async event => {
        event.preventDefault(); const button = form.querySelector('button[type=submit]'), body = {};
        for (const field of form.querySelectorAll('input[name],select[name],textarea[name]')) {
            let value = field.value;
            if (field.multiple) value = [...field.selectedOptions].map(option => Number(option.value));
            else if (field.dataset.valueType === 'boolean') value = value === 'true';
            else if (field.dataset.valueType === 'number' || field.closest('.admin-reference')) value = value === '' ? null : Number(value);
            else if (field.type === 'datetime-local') value = value ? new Date(value).toISOString() : null;
            body[field.name] = value;
        }
        button.disabled = true; form.setAttribute('aria-busy', 'true');
        try {
            const {resource, id} = form.dataset;
            const saved = await api(`/api/v1/admin/workspace/${resource}${id ? '/' + id : ''}`, {method: id ? 'PUT' : 'POST', body});
            location.assign(context + `/admin/${resource}/${saved.id}`);
        } catch (error) {message(error.message, true); button.disabled = false; form.removeAttribute('aria-busy');}
    });
    document.querySelectorAll('[data-relationship]').forEach(form => form.addEventListener('submit', event => {
        event.preventDefault(); const data = Object.fromEntries(new FormData(form)), button = form.querySelector('button[type=submit],button.btn-primary');
        const teacher = form.dataset.relationship === 'teacher';
        const body = teacher ? {classId: Number(data.classId), teacherId: Number(data.teacherId), homeroom: data.homeroom === 'true'} : {studentId: Number(data.studentId), active: true};
        const endpoint = teacher ? '/api/v1/admin/workspace/teaching-assignments' : `/api/v1/admin/workspace/classes/${form.dataset.classId}/students`;
        operate(button, () => api(endpoint, {method: 'POST', body}), teacher && body.homeroom ? 'Đặt giáo viên này làm người phụ trách chính? Người phụ trách chính cũ vẫn giữ vai trò giảng dạy.' : null);
    }));
    document.querySelectorAll('[data-remove-teacher]').forEach(button => button.addEventListener('click', () => {
        operate(button, () => api(`/api/v1/admin/workspace/classes/${button.dataset.classId}/teachers/${button.dataset.removeTeacher}`, {method: 'DELETE'}), 'Thu hồi phân công? Giáo viên sẽ không còn quyền quản lý lớp này theo phân công; lịch sử vẫn được giữ.');
    }));
    document.querySelectorAll('[data-enrollment]').forEach(button => button.addEventListener('click', () => {
        const active = button.dataset.active === 'true';
        operate(button, () => api(`/api/v1/admin/workspace/classes/${button.dataset.classId}/students`, {method: 'POST', body: {studentId: Number(button.dataset.enrollment), active}}), active ? 'Thêm học sinh trở lại lớp?' : 'Cho học sinh rời lớp? Tiến độ và bài làm trước đây không bị xóa.');
    }));
})();