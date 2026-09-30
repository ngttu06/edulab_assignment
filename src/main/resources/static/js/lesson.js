document.getElementById('aiForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, button = form.querySelector('button');
    button.disabled = true;
    document.getElementById('aiAnswer').textContent = 'Trợ lý đang chuẩn bị hướng dẫn...';
    try {
        const answer = await window.edulab.api('/api/v1/ai/questions', {method: 'POST', body: {
            lessonId: Number(form.dataset.id), question: document.getElementById('aiQuestion').value
        }});
        document.getElementById('aiAnswer').textContent = (answer.sample ? 'AI MẪU — ' : 'AI HỖ TRỢ — ') + answer.text + '\n\nTài liệu tham khảo: ' + answer.sourceLabels.join(', ') + '. Cần giáo viên kiểm tra.';
    } catch (error) { document.getElementById('aiAnswer').textContent = error.message; }
    finally { button.disabled = false; }
});

document.getElementById('complete')?.addEventListener('click', async event => {
    const button = event.currentTarget;
    const notice = document.getElementById('lessonNotice');
    button.disabled = true;
    try {
        const lesson = await window.edulab.api('/api/v1/lessons/' + button.dataset.id + '/progress', {
            method: 'POST', body: {status: 'COMPLETED', completionPercent: 100}
        });
        document.getElementById('progressValue').textContent = lesson.completionPercent + '%';
        button.textContent = 'Đã hoàn thành';
        notice.textContent = 'Đã lưu tiến độ học tập.';
    } catch (error) {
        notice.textContent = error.message;
        button.disabled = false;
    }
});
