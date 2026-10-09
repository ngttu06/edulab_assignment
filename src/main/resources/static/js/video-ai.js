(() => {
    'use strict';
    const root = document.getElementById('videoLearning'), form = document.getElementById('videoAiForm');
    if (!root || !form) return;
    const input = document.getElementById('videoAiQuestion'), messages = document.getElementById('videoAiMessages');
    const status = document.getElementById('videoAiStatus'), capture = document.getElementById('videoAiCapture');
    const send = document.getElementById('videoAiSend'), reset = document.getElementById('videoAiReset');
    const upload = document.getElementById('videoAiImage'), preview = document.getElementById('videoAiPreview');
    const previewImage = document.getElementById('videoAiPreviewImage'), remove = document.getElementById('videoAiRemoveFrame');
    const suggestions = document.getElementById('videoAiSuggestions'), screen = root.querySelector('.video-screen');
    const panel = document.getElementById('videoAi'), toggle = document.getElementById('videoAiToggle');
    const layout = root.closest('.video-learning-layout');
    const greeting = messages.firstElementChild.cloneNode(true);
    let history = [], selectedFrame, busy = false, capturing = false;
    toggle.addEventListener('click', () => {
        const hidden = !panel.hidden;
        if (hidden && panel.contains(document.activeElement)) toggle.focus();
        panel.hidden = hidden;
        layout.classList.toggle('is-ai-hidden', hidden);
        toggle.setAttribute('aria-expanded', String(!hidden));
        document.getElementById('videoAiToggleLabel').textContent = hidden ? 'Hiện trợ lý AI' : 'Ẩn trợ lý AI';
        if (!hidden) messages.scrollTop = messages.scrollHeight;
        // Keep the same DOM, chat state and player instance; hiding never pauses playback.
    });
    const time = ms => {
        const seconds = Math.floor(ms/1000);
        return String(Math.floor(seconds/60)).padStart(2,'0') + ':' + String(seconds%60).padStart(2,'0');
    };
    const position = () => root.videoContext?.position() || 0;
    function controls() {
        send.disabled = reset.disabled = remove.disabled = upload.disabled = busy || capturing;
        capture.disabled = busy || capturing || !root.videoContext;
        suggestions.querySelectorAll('button').forEach(button => { button.disabled = busy || capturing; });
        form.setAttribute('aria-busy', String(busy || capturing));
    }
    root.addEventListener('edulab:video-ready', controls);
    function selectFrame(frame) {
        selectedFrame = frame;
        preview.hidden = !frame;
        if (frame) {
            previewImage.src = frame.dataUrl;
            document.getElementById('videoAiFrameTime').textContent = 'Khung hình · ' + time(frame.timestampMs);
        } else previewImage.removeAttribute('src');
    }
    function jpeg(source, sx, sy, sw, sh) {
        const canvas = document.createElement('canvas');
        const scale = Math.min(1, 1280/sw, 1280/sh);
        canvas.width = Math.max(1, Math.round(sw*scale));
        canvas.height = Math.max(1, Math.round(sh*scale));
        canvas.getContext('2d').drawImage(source, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
        for (const quality of [0.85, 0.7, 0.5, 0.3]) {
            const data = canvas.toDataURL('image/jpeg', quality);
            if (data.startsWith('data:image/jpeg;base64,') && data.length <= 690000) return data;
        }
        throw new Error('Khung hình quá lớn. Hãy chọn một ảnh nhỏ hơn rồi thử lại.');
    }
    function drawable(media) {
        return !media.seeking && media.readyState >= 2 && media.videoWidth > 0 && media.videoHeight > 0;
    }
    function waitForFrame(media) {
        if (drawable(media)) return Promise.resolve();
        return new Promise((resolve, reject) => {
            const events = ['loadeddata', 'seeked', 'canplay', 'error'];
            const finish = error => {
                clearTimeout(timeout);
                events.forEach(event => media.removeEventListener(event, check));
                if (error) reject(error); else resolve();
            };
            const check = () => {
                if (media.error) finish(new Error('Không đọc được video. Hãy kiểm tra nguồn MP4/HLS hoặc chọn ảnh thủ công.'));
                else if (drawable(media)) finish();
            };
            const timeout = setTimeout(() => finish(new Error('Video chưa có khung hình sẵn sàng. Hãy đợi video tải hoặc tua xong rồi thử lại.')), 3000);
            events.forEach(event => media.addEventListener(event, check));
            check();
        });
    }
    capture.addEventListener('click', async () => {
        if (busy || capturing || !root.videoContext) return;
        capturing = true; controls(); status.textContent = 'Đang chụp khung hình…';
        try {
            if (root.dataset.provider === 'YOUTUBE')
                throw new Error('Không thể lấy ảnh trực tiếp từ iframe YouTube. Hãy chọn ảnh thủ công hoặc dùng nguồn MP4/HLS cho phép đọc khung hình.');
            // Only the player's HTML video pixels, never the surrounding page or an iframe.
            const media = screen.querySelector('video');
            if (!media) throw new Error('Player chưa cung cấp phần tử video để lấy ảnh trực tiếp. Hãy kiểm tra bản PlayerJS hoặc chọn ảnh thủ công.');
            root.videoContext.pause();
            await waitForFrame(media);
            const dataUrl = jpeg(media, 0, 0, media.videoWidth, media.videoHeight);
            const timestampMs = Math.round(Math.max(0, Math.min(Number(root.dataset.duration), media.currentTime)) * 1000);
            selectFrame({dataUrl, timestampMs});
            status.textContent = 'Đã lấy khung hình trực tiếp từ video. Kiểm tra ảnh và mốc thời gian rồi gửi câu hỏi.';
        } catch (error) {
            status.textContent = error.name === 'SecurityError'
                ? 'Nguồn video không cho phép đọc ảnh qua canvas (CORS hoặc nội dung được bảo vệ). Hãy kiểm tra cấu hình nguồn/player hoặc chọn ảnh thủ công.'
                : error.message;
        } finally { capturing = false; controls(); }
    });
    upload.addEventListener('change', async () => {
        const file = upload.files[0];
        if (!file || busy || capturing) return;
        capturing = true; controls();
        try {
            if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 5*1024*1024)
                throw new Error('Chọn ảnh JPG, PNG hoặc WebP tối đa 5 MB.');
            root.videoContext?.pause();
            const timestampMs = position(), image = await createImageBitmap(file);
            try { selectFrame({dataUrl:jpeg(image, 0, 0, image.width, image.height), timestampMs}); }
            finally { image.close(); }
            status.textContent = 'Ảnh đã sẵn sàng. Hãy kiểm tra ảnh và mốc video trước khi gửi.';
        } catch (error) { status.textContent = error.message; }
        finally { upload.value = ''; capturing = false; controls(); }
    });
    remove.addEventListener('click', () => { selectFrame(undefined); status.textContent = ''; });
    function message(role, text, frame) {
        const bubble = document.createElement('div'), label = document.createElement('strong'), content = document.createElement('p');
        bubble.className = 'video-ai-message ' + role;
        label.textContent = role === 'user' ? 'Bạn' : 'EduLab AI'; content.textContent = text;
        bubble.append(label, content);
        if (frame) {
            const image = document.createElement('img'), caption = document.createElement('small');
            image.src = frame.dataUrl; image.alt = 'Khung hình đã chia sẻ tại ' + time(frame.timestampMs);
            caption.textContent = 'Khung hình · ' + time(frame.timestampMs); bubble.append(image, caption);
        }
        messages.append(bubble);
        while (messages.childElementCount > 25) messages.children[1].remove();
        messages.scrollTop = messages.scrollHeight;
        return bubble;
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const question = input.value.trim();
        if (busy || capturing || !question || question.length > 2000) return;
        busy = true; controls(); suggestions.hidden = true;
        const frame = selectedFrame, currentPosition = position();
        const bubble = message('user', question, frame);
        status.textContent = frame ? 'AI đang đọc khung hình và chuẩn bị câu trả lời…' : 'AI đang trả lời theo nội dung bài học (chưa có khung hình)…';
        try {
            const answer = await window.edulab.api('/api/v1/lessons/' + root.dataset.lesson + '/video/chat', {
                method:'POST', body:{videoId:Number(root.dataset.video), question, positionMs:currentPosition, history:history.slice(-8), frame:frame || null}
            });
            message('assistant', (answer.sample ? 'AI MẪU — ' : '') + answer.text);
            history.push({role:'user', content:(question + (frame ? '\n[Khung hình tại ' + time(frame.timestampMs) + ']' : '')).slice(0,2000)},
                {role:'assistant', content:answer.text.slice(0,2000)});
            history = history.slice(-8);
            if (input.value.trim() === question) input.value = '';
            selectFrame(undefined);
            status.textContent = answer.frameAnalyzed ? 'Đã gửi khung hình tại ' + time(answer.positionMs) + ' cho AI. Cần kiểm chứng câu trả lời.' : 'Trả lời theo bài học; không có khung hình được gửi.';
        } catch (error) {
            bubble.remove(); status.textContent = error.message; // Keep question/frame for an explicit retry, never auto-retry a billed call.
        } finally { busy = false; controls(); }
    });
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); if (!busy && !capturing) form.requestSubmit(); }
    });
    suggestions.querySelectorAll('button').forEach(button => button.addEventListener('click', () => { input.value = button.textContent; input.focus(); }));
    reset.addEventListener('click', () => {
        if (busy || capturing) return;
        history = []; selectFrame(undefined); messages.replaceChildren(greeting.cloneNode(true));
        suggestions.hidden = false; status.textContent = ''; input.value = ''; input.focus();
    });
    controls();
})();