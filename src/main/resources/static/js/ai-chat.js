(() => {
    'use strict';
    const toggle = document.getElementById('aiChatToggle');
    if (!toggle) return;
    const dialog = document.getElementById('aiChatDialog');
    const form = document.getElementById('aiChatForm');
    const input = document.getElementById('aiChatQuestion');
    const messages = document.getElementById('aiChatMessages');
    const status = document.getElementById('aiChatStatus');
    const send = document.getElementById('aiChatSend');
    const reset = document.getElementById('aiChatReset');
    const suggestions = document.getElementById('aiChatSuggestions');
    let history = [], busy = false;
    const greeting = messages.firstElementChild.cloneNode(true);
    function scroll() { messages.scrollTop = messages.scrollHeight; }
    function message(role, text, links = []) {
        const bubble = document.createElement('div');
        bubble.className = 'ai-chat-message ' + role;
        const label = document.createElement('strong');
        label.textContent = role === 'user' ? 'Bạn' : 'EduLab AI';
        const content = document.createElement('p');
        content.textContent = text;
        bubble.append(label, content);
        const actions = document.createElement('div');
        actions.className = 'ai-chat-links';
        for (const link of links) {
            if (!/^\/(?:courses(?:\/\d+)?|login)$/.test(link.path)) continue;
            const anchor = document.createElement('a');
            anchor.href = window.edulab.context + link.path;
            anchor.textContent = link.label;
            actions.append(anchor);
        }
        if (actions.childElementCount) bubble.append(actions);
        messages.append(bubble);
        // Keep the visible transcript bounded too. No browser storage of questions or credentials.
        while (messages.childElementCount > 31) messages.children[1].remove();
        scroll();
    }
    toggle.addEventListener('click', async () => {
        dialog.showModal();
        toggle.setAttribute('aria-expanded', 'true');
        input.focus();
        if (!busy) {
            try {
                const data = await window.edulab.api('/api/v1/ai/support/status');
                if (!data.enabled && dialog.open) status.textContent = 'Trợ lý AI đang tắt. Bạn vẫn có thể khám phá mục Khóa học.';
            } catch (error) { if (dialog.open && !busy) status.textContent = error.message; }
        }
    });
    document.getElementById('aiChatClose').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => { toggle.setAttribute('aria-expanded', 'false'); toggle.focus(); });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const question = input.value.trim();
        if (busy || !question || question.length > 2000) return;
        busy = true; send.disabled = true; reset.disabled = true;
        suggestions.querySelectorAll('button').forEach(button => { button.disabled = true; });
        status.textContent = 'Trợ lý đang chuẩn bị câu trả lời…';
        message('user', question); input.value = ''; suggestions.hidden = true;
        try {
            const answer = await window.edulab.api('/api/v1/ai/support/chat', {
                method: 'POST', body: {question, history: history.slice(-8)}
            });
            message('assistant', (answer.sample ? 'AI MẪU — ' : '') + answer.text, answer.links);
            history.push({role:'user', content:question}, {role:'assistant', content:answer.text.slice(0, 2000)});
            history = history.slice(-8);
            status.textContent = '';
        } catch (error) {
            status.textContent = error.message;
            if (!input.value) input.value = question;
        } finally {
            busy = false; send.disabled = false; reset.disabled = false;
            suggestions.querySelectorAll('button').forEach(button => { button.disabled = false; });
            if (dialog.open) input.focus();
        }
    });
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault(); if (!busy) form.requestSubmit();
        }
    });
    suggestions.querySelectorAll('button').forEach(button => button.addEventListener('click', () => {
        input.value = button.textContent; form.requestSubmit();
    }));
    reset.addEventListener('click', () => {
        if (busy) return;
        history = []; messages.replaceChildren(greeting.cloneNode(true));
        suggestions.hidden = false; status.textContent = ''; input.value = ''; input.focus();
    });
})();