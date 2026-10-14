(() => {
'use strict';
document.querySelectorAll('.startQuiz').forEach(button => button.addEventListener('click', async () => {
    if(button.dataset.mode==='TEST' && !confirm('Bài kiểm tra sẽ ghi nhận việc chuyển tab/rời trang và gửi số lần đến giáo viên. Đáp án chỉ mở theo lịch công bố; AI bị khóa khi đang làm bài. Bạn đã sẵn sàng?')) return;
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
let tracking = quizForm?.dataset.status==='IN_PROGRESS' && quizForm.dataset.teacher!=='true';
let flushTabs = async () => {};
if(tracking) {
    quizForm.addEventListener('change',()=>{
        const answers=selectedAnswers(quizForm);
        saving=saving.catch(()=>{}).then(async()=>{
            document.getElementById('quizNotice').textContent='Đang lưu câu trả lời...';
            const result=await window.edulab.api('/api/v1/quiz-attempts/'+quizForm.dataset.id+'/answers',{method:'PUT',body:{answers}});
            if(result.status==='GRADED'){tracking=false;window.location.reload();return;}
            document.getElementById('quizNotice').textContent='Đã lưu câu trả lời trên server.';
        }).catch(error=>{document.getElementById('quizNotice').textContent=error.message+' Câu trả lời mới có thể chưa được lưu.';});
    });
    const timer=document.getElementById('quizTimer');
    let finalizing=false;
    const update=()=>{
        const seconds=Math.max(0,Math.ceil((new Date(deadline.dateTime).getTime()-Date.now())/1000));
        if(timer)timer.textContent=Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');
        if(seconds===0&&!finalizing){tracking=false;finalizing=true;saving.finally(()=>window.edulab.api('/api/v1/quiz-attempts/'+quizForm.dataset.id).then(result=>{if(result.status==='GRADED')window.location.reload();else finalizing=false;}).catch(()=>{finalizing=false;}));}
    };update();window.setInterval(update,1000);
}
document.getElementById('quizForm')?.addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    if (form.dataset.status !== 'IN_PROGRESS' || form.dataset.teacher==='true') return;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    const answers = selectedAnswers(form);
    try {
        await saving;
        await flushTabs();
        await window.edulab.api('/api/v1/quiz-attempts/' + form.dataset.id + '/submit', {method: 'POST', body: {answers}});
        tracking=false;
        window.location.reload();
    } catch (error) { document.getElementById('quizNotice').textContent = error.message; button.disabled = false; }
});
document.getElementById('questionFilter')?.addEventListener('change',event => {
    let shown=0;
    document.querySelectorAll('.assessment-question').forEach(el => {el.hidden=event.target.value!=='all' && el.dataset.outcome!==event.target.value;if(!el.hidden) shown++;});
    document.getElementById('questionFilterEmpty').hidden=shown!==0;
});
async function showAi(button,target,path) {
    if(button.disabled) return;
    button.disabled=true;target.textContent='Đang hỏi AI…';
    try {const result=await window.edulab.api(path,{method:'POST'});target.textContent=(result.sample?'[AI mẫu] ':'')+result.text;}
    catch(error) {target.textContent=error.message+' Bài làm và điểm vẫn được lưu. Bạn có thể hỏi giáo viên hoặc thử lại sau.';}
    finally {button.disabled=false;}
}
const summaryButton=document.getElementById('retryAiReview');
if(summaryButton) {
    const load=()=>showAi(summaryButton,document.getElementById('aiReviewText'),'/api/v1/quiz-attempts/'+quizForm.dataset.id+'/ai-review');
    summaryButton.addEventListener('click',load);load();
}
document.querySelectorAll('.askQuestionAi').forEach(button=>button.addEventListener('click',()=>showAi(button,document.getElementById('ai-question-'+button.dataset.question),'/api/v1/quiz-attempts/'+quizForm.dataset.id+'/questions/'+button.dataset.question+'/ai-explanation')));
if(tracking && quizForm.dataset.mode==='TEST') {
    const path='/api/v1/quiz-attempts/'+quizForm.dataset.id+'/tab-events',key='edulab-tabs-'+quizForm.dataset.id,warning=document.getElementById('tabWarning');
    let pending=[];try {pending=JSON.parse(sessionStorage.getItem(key)||'[]').filter(id=>typeof id==='string').slice(0,200);} catch {}
    let count=0,hidden=false,flushing=null,csrf;
    const persist=()=>{try {sessionStorage.setItem(key,JSON.stringify(pending));} catch {}};
    const inform=()=>{warning.textContent='Đã ghi nhận '+count+' lần'+(pending.length?' · '+pending.length+' sự kiện đang chờ gửi.':' trên server.');};
    const prepareCsrf=async()=>{try {const response=await fetch(window.edulab.context+'/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});if(response.ok) csrf=await response.json();} catch {}};
    flushTabs=()=>{
        if(flushing) return flushing;
        if(!pending.length) return Promise.resolve();
        flushing=(async()=>{
            try {for(const id of [...pending]) {const result=await window.edulab.api(path,{method:'POST',body:{eventId:id},keepalive:true});count=Math.max(count,result.count);pending=pending.filter(value=>value!==id);persist();inform();}}
            catch {inform();}
        })().finally(()=>{flushing=null;});
        return flushing;
    };
    function leave() {
        if(!tracking || hidden || Date.now()>=new Date(deadline.dateTime).getTime()) return;
        hidden=true;
        if(pending.length>=200) {warning.textContent='Có quá nhiều sự kiện chưa gửi. Kiểm tra kết nối mạng.';return;}
        const id=crypto.randomUUID();pending.push(id);persist();inform();
        // A prefetched CSRF token allows a single keepalive request when the page is being hidden.
        if(csrf) fetch(window.edulab.context+path,{method:'POST',credentials:'same-origin',cache:'no-store',keepalive:true,headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({eventId:id})}).then(async response=>{
            if(!response.ok) return;const result=await response.json();count=Math.max(count,result.count);pending=pending.filter(value=>value!==id);persist();inform();
        }).catch(()=>{});
        else flushTabs();
    }
    document.addEventListener('visibilitychange',()=>{if(document.hidden) leave();else {hidden=false;inform();flushTabs();prepareCsrf();}});
    window.addEventListener('pagehide',leave);
    window.addEventListener('online',()=>flushTabs());
    window.edulab.api(path).then(result=>{count=result.count;inform();flushTabs();}).catch(()=>{warning.textContent='Chưa tải được số lần rời tab. Kiểm tra kết nối.';});
    prepareCsrf();
}
})();
