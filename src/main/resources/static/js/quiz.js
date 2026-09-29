document.querySelectorAll('.startQuiz').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try {
        const attempt = await window.edulab.api('/api/v1/quizzes/' + button.dataset.id + '/attempts', {method: 'POST'});
        window.location.assign(window.edulab.context + '/quizzes/attempts/' + attempt.id);
    } catch (error) { document.getElementById('quizNotice').textContent = error.message; button.disabled = false; }
}));
const deadline = document.getElementById('deadline');
if (deadline) deadline.textContent = new Date(deadline.dateTime).toLocaleString('vi-VN');
document.getElementById('quizForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    if (form.dataset.status !== 'IN_PROGRESS') return;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    const answers = {};
    new FormData(form).forEach((value, key) => { answers[key] = Number(value); });
    try {
        await window.edulab.api('/api/v1/quiz-attempts/' + form.dataset.id + '/submit', {method: 'POST', body: {answers}});
        window.location.reload();
    } catch (error) { document.getElementById('quizNotice').textContent = error.message; button.disabled = false; }
});
