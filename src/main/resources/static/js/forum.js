(() => {
    const notice = document.getElementById('forumNotice');
    const value = id => document.getElementById(id).value;
    document.getElementById('threadForm')?.addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget, button = form.querySelector('button'); button.disabled = true;
        try {
            const result = await window.edulab.api('/api/v1/forum/threads', {method: 'POST', body: {categoryId: Number(form.dataset.category), title: value('threadTitle'), body: value('threadBody')}});
            window.location.assign(window.edulab.context + '/forum/threads/' + result.id);
        } catch (error) { notice.textContent = error.message; button.disabled = false; }
    });
    const uploaded = new Map();
    document.getElementById('postForm')?.addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget, button = form.querySelector('button'); button.disabled = true;
        try {
            const files = Array.from(document.getElementById('files').files);
            if (files.length > 3) throw new Error('Tối đa 3 tệp cho mỗi trả lời.');
            const fileIds = [];
            for (const file of files) {
                const key = file;
                if (!uploaded.has(key)) {
                    const body = new FormData(); body.append('file', file);
                    const result = await window.edulab.api('/api/v1/files', {method: 'POST', body});
                    uploaded.set(key, result.id);
                    notice.textContent = result.message;
                }
                fileIds.push(uploaded.get(key));
            }
            await window.edulab.api('/api/v1/forum/threads/' + form.dataset.id + '/posts', {method: 'POST', body: {body: value('postBody'), fileIds}});
            window.location.reload();
        } catch (error) { notice.textContent = error.message; button.disabled = false; }
    });
    document.querySelectorAll('.moderateThread').forEach(button => button.addEventListener('click', async () => {
        if (!window.confirm('Xác nhận thay đổi trạng thái thảo luận?')) return;
        button.disabled = true;
        try {
            await window.edulab.api('/api/v1/forum/threads/' + button.dataset.id + '/moderate', {method: 'POST', body: {status: button.dataset.status}});
            window.location.assign(window.edulab.context + '/forum');
        } catch (error) { notice.textContent = error.message; button.disabled = false; }
    }));
})();
