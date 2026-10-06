(() => {
    'use strict';
    const feed = document.getElementById('communityFeed');
    if (!feed) return;
    const {api, context} = window.edulab;
    const more = document.getElementById('feedLoadMore');
    const notice = document.getElementById('feedNotice');
    const template = document.getElementById('feedCardTemplate');
    const states = new WeakMap();
    const endpoint = card => '/api/v1/forum/threads/' + card.dataset.id;
    const report = (card, message) => { card.querySelector('.feed-card-notice').textContent = message; };
    const node = (tag, className, text) => {
        const element = document.createElement(tag);
        element.className = className;
        if (text !== undefined) element.textContent = text;
        return element;
    };
    function attachments(files) {
        const wrapper = node('div', 'feed-attachments');
        for (const file of files || []) {
            const box = node('div', 'feed-attachment');
            if (file.scan_status === 'CLEAN') {
                const url = context + '/api/v1/files/' + file.id;
                if (['image/jpeg','image/png','image/webp'].includes(file.content_type)) {
                    const link = node('a', 'feed-image-link'); link.href = url;
                    const image = node('img', ''); image.src = url + '/image'; image.alt = file.original_filename;
                    image.loading = 'lazy'; image.decoding = 'async'; link.append(image); box.append(link);
                }
                const link = node('a', 'feed-file-link', file.original_filename + ' ↓'); link.href = url; box.append(link);
            } else box.append(node('span', 'feed-file-link', file.original_filename + ' — Đang cách ly / không khả dụng'));
            wrapper.append(box);
        }
        return wrapper;
    }
    function renderThread(thread) {
        const card = template.content.firstElementChild.cloneNode(true);
        card.dataset.id = thread.id;
        const text = (selector, value) => { card.querySelector(selector).textContent = value; };
        text('.feed-avatar', thread.author.substring(0,1)); text('.feed-author strong', thread.author);
        text('.feed-category', thread.category_name); text('.feed-time', thread.created_at);
        text('.feed-title', thread.title); text('.feed-body', thread.body);
        text('.feed-like-count', thread.like_count); text('.feed-comment-count', thread.comment_count);
        card.querySelectorAll('.feed-title,.feed-detail-link,.feed-comment-link').forEach(link => { link.href = context + '/forum/threads/' + thread.id; });
        card.querySelector('.feed-like').setAttribute('aria-pressed', String(thread.liked));
        card.querySelector('.feed-locked').hidden = thread.status !== 'LOCKED';
        card.querySelector('.comments-locked').hidden = thread.status !== 'LOCKED';
        card.querySelector('.feed-reply-form').hidden = thread.status === 'LOCKED';
        card.querySelector('.feed-attachments').replaceWith(attachments(thread.files));
        const comments = card.querySelector('.feed-comments'); comments.id = 'comments-' + thread.id;
        card.querySelector('.feed-comments-toggle').setAttribute('aria-controls', comments.id);
        for (const [selector, prefix] of [['textarea','reply-'],['input[type=file]','reply-files-'],['.feed-reply-form details p','reply-status-'],['.feed-reply-form details button','reply-cancel-']]) {
            card.querySelector(selector).id = prefix + thread.id;
        }
        card.querySelector('.feed-reply-form>label').htmlFor = 'reply-' + thread.id;
        card.querySelector('.feed-reply-form details label').htmlFor = 'reply-files-' + thread.id;
        return card;
    }
    function state(card) {
        if (!states.has(card)) states.set(card, {cursor:null, loaded:false, loading:false, ids:new Set()});
        return states.get(card);
    }
    function appendComments(card, items) {
        const current = state(card), list = card.querySelector('.feed-comment-list');
        for (const item of items) {
            if (current.ids.has(String(item.id))) continue;
            current.ids.add(String(item.id));
            const comment = node('article', 'feed-comment'); comment.dataset.id = item.id;
            comment.append(node('span', 'feed-avatar', item.author.substring(0,1)));
            const content = node('div', 'feed-comment-content');
            content.append(node('strong','',item.author), node('span','feed-comment-time',item.created_at), node('p','forum-body',item.body), attachments(item.files));
            comment.append(content);
            const next = [...list.children].find(el => Number(el.dataset.id) > item.id);
            list.insertBefore(comment, next || null);
        }
    }
    async function loadComments(card) {
        const current = state(card), button = card.querySelector('.comments-more');
        if (current.loading) return;
        current.loading = true; button.disabled = true; report(card, 'Đang tải bình luận...');
        try {
            const result = await api(endpoint(card) + '/posts' + (current.cursor ? '?after=' + current.cursor : ''));
            appendComments(card, result.items);
            current.cursor = result.nextCursor; current.loaded = true; button.hidden = !result.hasMore;
            report(card, result.items.length ? '' : 'Chưa có bình luận. Hãy chia sẻ cách hiểu của bạn.');
        } catch (error) { report(card, error.message); button.hidden = false; }
        finally { current.loading = false; button.disabled = false; }
    }
    feed.addEventListener('click', async event => {
        const card = event.target.closest('.feed-card'); if (!card) return;
        const button = event.target.closest('button');
        if (event.target.closest('.feed-comment-link')) {
            event.preventDefault(); card.querySelector('.feed-comments-toggle').click(); return;
        }
        if (!button) return;
        if (button.classList.contains('feed-comments-toggle')) {
            const comments = card.querySelector('.feed-comments'); comments.hidden = !comments.hidden;
            button.setAttribute('aria-expanded', String(!comments.hidden));
            if (!comments.hidden && !state(card).loaded) await loadComments(card);
            if (!comments.hidden && !state(card).uploads && !card.querySelector('.feed-reply-form').hidden) {
                const id = card.dataset.id;
                state(card).uploads = window.edulabUploads.attach('reply-files-' + id, 'reply-status-' + id, 'reply-cancel-' + id);
            }
        } else if (button.classList.contains('comments-more')) await loadComments(card);
        else if (button.classList.contains('feed-like')) {
            button.disabled = true;
            try {
                const result = await api(endpoint(card) + '/like', {method:button.getAttribute('aria-pressed') === 'true' ? 'DELETE' : 'PUT'});
                button.setAttribute('aria-pressed', String(result.liked)); card.querySelector('.feed-like-count').textContent = result.count;
                report(card, result.liked ? 'Đã thích bài viết.' : 'Đã bỏ thích bài viết.');
            } catch (error) { report(card, error.message); }
            finally { button.disabled = false; }
        } else if (button.classList.contains('feed-share')) {
            const url = new URL(context + '/forum/threads/' + card.dataset.id, location.origin).href;
            try {
                if (!navigator.clipboard) throw new Error('clipboard unavailable');
                await navigator.clipboard.writeText(url); report(card, 'Đã sao chép liên kết. Người mở vẫn cần quyền xem bài viết.');
            } catch {
                const input = node('input', 'form-control'); input.readOnly = true; input.value = url;
                input.setAttribute('aria-label','Liên kết bài viết để sao chép');
                const message = card.querySelector('.feed-card-notice'); message.replaceChildren(node('span','','Sao chép liên kết bên dưới:'),input); input.focus(); input.select();
            }
        }
    });
    feed.addEventListener('submit', async event => {
        const form = event.target.closest('.feed-reply-form'); if (!form) return;
        event.preventDefault();
        const card = form.closest('.feed-card'), current = state(card), button = form.querySelector('[type=submit]');
        button.disabled = true;
        try {
            const id = card.dataset.id;
            if (!current.uploads) current.uploads = window.edulabUploads.attach('reply-files-' + id, 'reply-status-' + id, 'reply-cancel-' + id);
            const fileIds = await current.uploads.upload();
            const result = await api(endpoint(card) + '/posts', {method:'POST', body:{body:form.querySelector('textarea').value, fileIds}});
            form.reset();
            form.querySelector('input[type=file]').dispatchEvent(new Event('change'));
            card.querySelector('.feed-comment-count').textContent = Number(card.querySelector('.feed-comment-count').textContent) + 1;
            report(card, 'Đã gửi bình luận.');
            // Read the saved, server-authorized comment; no raw user HTML is rendered.
            try { appendComments(card, (await api(endpoint(card) + '/posts?size=1' + (result.id > 1 ? '&after=' + (result.id - 1) : ''))).items); }
            catch { report(card, 'Bình luận đã lưu. Mở trang chi tiết để xem nếu bảng tin chưa cập nhật.'); }
        } catch (error) { report(card, error.message); }
        finally { button.disabled = false; }
    });
    let loading = false, automatic = true;
    async function loadFeed(manual = false) {
        if (loading || more.hidden || !feed.dataset.cursor) return;
        loading = true; more.disabled = true; feed.setAttribute('aria-busy','true'); notice.textContent = 'Đang tải thêm bài viết...';
        try {
            const params = new URLSearchParams({before:feed.dataset.cursor});
            if (feed.dataset.category) params.set('category',feed.dataset.category);
            const result = await api('/api/v1/forum/feed?' + params);
            const ids = new Set([...feed.children].map(card => card.dataset.id));
            let first;
            for (const thread of result.items) if (!ids.has(String(thread.id))) {
                const card = renderThread(thread); feed.append(card); first ||= card;
            }
            feed.dataset.cursor = result.nextCursor || ''; more.hidden = !result.hasMore;
            notice.textContent = result.hasMore ? 'Đã tải thêm bài viết.' : 'Bạn đã xem hết các bài viết hiện tại.';
            if (manual && first) { first.querySelector('.feed-title').focus(); }
        } catch (error) { notice.textContent = error.message + ' Chọn “Xem thêm” để thử lại.'; automatic = false; }
        finally { loading = false; more.disabled = false; feed.removeAttribute('aria-busy'); }
    }
    more.addEventListener('click', () => loadFeed(true));
    if ('IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
        const observer = new IntersectionObserver(entries => {
            if (automatic && entries.some(entry => entry.isIntersecting)) loadFeed();
        }, {rootMargin:'200px'});
        observer.observe(document.getElementById('feedSentinel'));
    }
})();