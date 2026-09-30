document.querySelectorAll('.startQuiz').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try {
        const attempt = await window.edulab.api('/api/v1/quizzes/' + button.dataset.id + '/attempts', {method: 'POST'});
        window.location.assign(window.edulab.context + '/quizzes/attempts/' + attempt.id);
    } catch (error) { document.getElementById('quizNotice').textContent = error.message; button.disabled = false; }
}));
const deadline = document.getElementById('deadline');
if (deadline) deadline.textContent = new Date(deadline.dateTime).toLocaleString('vi-VN');
const quizForm=document.getElementById('quizForm');
let saving=Promise.resolve();
function selectedAnswers(form){const answers={};new FormData(form).forEach((value,key)=>{answers[key]=Number(value);});return answers;}
if(quizForm?.dataset.status==='IN_PROGRESS') {
    quizForm.addEventListener('change',()=>{
        const answers=selectedAnswers(quizForm);
        saving=saving.catch(()=>{}).then(async()=>{
            document.getElementById('quizNotice').textContent='Đang lưu câu trả lời...';
            const result=await window.edulab.api('/api/v1/quiz-attempts/'+quizForm.dataset.id+'/answers',{method:'PUT',body:{answers}});
            if(result.status==='GRADED'){window.location.reload();return;}
            document.getElementById('quizNotice').textContent='Đã lưu câu trả lời trên server.';
        }).catch(error=>{document.getElementById('quizNotice').textContent=error.message+' Câu trả lời mới có thể chưa được lưu.';});
    });
    const timer=document.getElementById('quizTimer');
    let finalizing=false;
    const update=()=>{
        const seconds=Math.max(0,Math.ceil((new Date(deadline.dateTime).getTime()-Date.now())/1000));
        if(timer)timer.textContent=Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');
        if(seconds===0&&!finalizing){finalizing=true;saving.finally(()=>window.edulab.api('/api/v1/quiz-attempts/'+quizForm.dataset.id).then(result=>{if(result.status==='GRADED')window.location.reload();else finalizing=false;}).catch(()=>{finalizing=false;}));}
    };update();window.setInterval(update,1000);
}
document.getElementById('quizForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    if (form.dataset.status !== 'IN_PROGRESS') return;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    const answers = selectedAnswers(form);
    try {
        await saving;
        await window.edulab.api('/api/v1/quiz-attempts/' + form.dataset.id + '/submit', {method: 'POST', body: {answers}});
        window.location.reload();
    } catch (error) { document.getElementById('quizNotice').textContent = error.message; button.disabled = false; }
});
