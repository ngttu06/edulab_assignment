(() => {
    'use strict';
    const {api, context} = window.edulab;
    const notice = document.getElementById('workspaceNotice');
    function fail(error) { notice.textContent = error.message; notice.className = 'alert alert-danger'; }
    function finish(path) { if (path) window.location.assign(context + path); else window.location.reload(); }
    document.querySelectorAll('[data-delete]').forEach(button => button.addEventListener('click', async () => {
        if (!window.confirm(button.dataset.confirm)) return;
        button.disabled = true;
        try { await api(button.dataset.delete, {method:'DELETE'}); finish(button.dataset.return); }
        catch (error) { fail(error); button.disabled = false; }
    }));
    document.querySelectorAll('[data-duplicate]').forEach(button => button.addEventListener('click', async () => {
        button.disabled = true;
        try { const result = await api(button.dataset.duplicate, {method:'POST'}); finish(button.dataset.duplicateReturn + result.id + '/edit'); }
        catch (error) { fail(error); button.disabled = false; }
    }));
    document.querySelectorAll('[data-utc]').forEach(input => {
        if (!input.dataset.utc) return;
        const raw = input.dataset.utc.replace(' ', 'T');
        const date = new Date(/Z$|[+-]\d\d:\d\d$/.test(raw) ? raw : raw + 'Z');
        if (!Number.isNaN(date.getTime())) input.value = new Date(date.getTime() - date.getTimezoneOffset()*60000).toISOString().slice(0,16);
    });
    document.querySelectorAll('[data-workspace-form]').forEach(form => form.addEventListener('submit', async event => {
        event.preventDefault(); const button = form.querySelector('button'); button.disabled = true;
        try {
            const values = Object.fromEntries(new FormData(form));
            if (values.yearId) values.yearId = Number(values.yearId);
            if (values.userId) values.userId = Number(values.userId);
            if (values.quizId) values.quizId = Number(values.quizId);
            if (values.dueAt) values.dueAt = new Date(values.dueAt).toISOString();
            await api(form.dataset.api, {method:form.dataset.method, body:values});
            finish(form.dataset.return);
        } catch (error) { fail(error); button.disabled = false; }
    }));
})();