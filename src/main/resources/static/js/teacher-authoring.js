(() => {
    'use strict';
    const $ = id => document.getElementById(id), api = window.edulab.api;
    const notice = $('authorNotice'), list = $('questionList');
    let busy = false;
    let dirty = false;
    document.querySelectorAll('#teacherLessonForm, #teacherQuizForm').forEach(form => {
        form.addEventListener('input', () => { dirty = true; });
        form.addEventListener('change', () => { dirty = true; });
    });
    window.addEventListener('beforeunload', event => { if(dirty) {event.preventDefault();event.returnValue='';} });
    function notify(message, error = false) { notice.textContent = message; notice.className = error ? 'alert alert-danger' : 'alert alert-success'; notice.scrollIntoView({block:'nearest'}); }
    async function run(action) {
        if (busy) return;
        busy = true;
        const controls = [...document.querySelectorAll('#lessonEditor button, #quizEditor button, #lessonSelect, #quizSelect')];
        controls.forEach(el => el.disabled = true);
        try { await action(); } catch (error) { notify(error.message, true); }
        finally { busy = false; controls.forEach(el => el.disabled = false); renumber(); }
    }
    function classIds(name) { const ids = [...document.querySelectorAll('input[name="'+name+'"]:checked')].map(el => Number(el.value)); if (!ids.length) throw new Error('Hãy chọn ít nhất một lớp.'); return ids; }
    function setClasses(name, ids) { document.querySelectorAll('input[name="'+name+'"] ').forEach(el => el.checked = ids.includes(Number(el.value))); }
    function renumber() { if (!list) return; [...list.children].forEach((el,i) => { el.querySelector('legend').textContent = 'Câu '+(i+1); el.querySelector('[data-action="up"]').disabled = i===0; el.querySelector('[data-action="down"]').disabled = i===list.children.length-1; }); $('questionCount').textContent = '('+list.children.length+'/200)'; }
    function addQuestion(q = {content:'', options:['',''], correctIndex:0, explanation:'', points:1}) {
        if (list.children.length >= 200) throw new Error('Tối đa 200 câu hỏi.');
        const item = $('questionTemplate').content.firstElementChild.cloneNode(true);
        item.querySelectorAll('[data-field]').forEach(el => el.value = q[el.dataset.field] ?? '');
        item.querySelectorAll('[data-option]').forEach((el,i) => el.value = q.options[i] || '');
        list.append(item); renumber();
    }
    function questions() {
        if (!list.children.length) throw new Error('Cần ít nhất một câu hỏi.');
        return [...list.children].map((el,i) => {
            const value = name => el.querySelector('[data-field="'+name+'"]').value;
            const options = [...el.querySelectorAll('[data-option]')].map(o => o.value.trim());
            while (options.length && !options.at(-1)) options.pop();
            const correctIndex = Number(value('correctIndex'));
            if (options.some(o => !o) || correctIndex >= options.length) throw new Error('Câu '+(i+1)+': không được bỏ trống lựa chọn ở giữa hoặc chọn đáp án không có nội dung.');
            return {content:value('content'), explanation:value('explanation'), options, correctIndex, points:Number(value('points'))};
        });
    }
    $('addQuestion')?.addEventListener('click', () => { try {addQuestion();} catch(e) {notify(e.message,true);} });
    list?.addEventListener('click', event => {
        const button = event.target.closest('[data-action]'); if (!button || busy) return;
        const item = button.closest('[data-question]');
        if (button.dataset.action==='remove') item.remove();
        if (button.dataset.action==='up' && item.previousElementSibling) list.insertBefore(item,item.previousElementSibling);
        if (button.dataset.action==='down' && item.nextElementSibling) list.insertBefore(item.nextElementSibling,item);
        renumber();
    });
    $('importQuiz')?.addEventListener('click', () => run(async () => {
        const file = $('quizCsv').files[0]; if (!file) throw new Error('Hãy chọn file CSV.');
        if (file.size>1024*1024) throw new Error('File tối đa 1 MB.');
        if (list.querySelector('[data-field="content"]') && [...list.querySelectorAll('[data-field="content"]')].some(el => el.value.trim()) && !confirm('Thay thế các câu hỏi đang soạn bằng nội dung file?')) return;
        const form = new FormData(); form.append('file',file);
        const data = await api('/api/v1/teacher/quizzes/import',{method:'POST',body:form});
        list.replaceChildren(); data.forEach(addQuestion); notify('Đã đọc '+data.length+' câu. Kiểm tra và sửa bên dưới, sau đó nhấn Lưu quiz.');
    }));
    $('previewLesson')?.addEventListener('click', () => run(async () => {
        const result=await api('/api/v1/teacher/lessons/preview',{method:'POST',body:{html:$('lessonContent').value}});
        $('lessonPreviewBody').innerHTML=result.html;
        $('lessonPreview').classList.remove('d-none');
        $('lessonPreview').scrollIntoView({block:'nearest'});
    }));
    $('teacherLessonForm')?.addEventListener('submit', event => { event.preventDefault();run(async () => {
        const id = $('lessonSelect').value;
        const body = {topicId:Number($('lessonTopic').value),title:$('lessonTitle').value,summary:$('lessonSummary').value,durationMinutes:Number($('lessonDuration').value),objectives:$('lessonObjectives').value,contentHtml:$('lessonContent').value,status:$('lessonStatus').value,classIds:classIds('lessonClass')};
        const result = await api('/api/v1/teacher/lessons'+(id?'/'+id:''),{method:id?'PUT':'POST',body});
        dirty = false;
        window.location.assign(window.edulab.context+'/teacher/lessons/'+result.id);
    }); });
    $('teacherQuizForm')?.addEventListener('submit', event => { event.preventDefault();run(async () => {
        const id = $('quizSelect').value;
        const body = {title:$('quizTitle').value,durationMinutes:Number($('quizDuration').value),maxAttempts:Number($('quizAttempts').value),mode:$('quizMode').value,revealAfter:$('quizReveal').value?new Date($('quizReveal').value).toISOString():null,published:$('quizPublished').checked,classIds:classIds('quizClass'),questions:questions()};
        const result = await api('/api/v1/teacher/quizzes'+(id?'/'+id:''),{method:id?'PUT':'POST',body});
        dirty = false;
        window.location.assign(window.edulab.context+'/teacher/quizzes/'+result.id);
    }); });
    function resetLesson() { $('teacherLessonForm').reset();$('lessonSelect').value=''; }
    function resetQuiz() { $('teacherQuizForm').reset();$('quizSelect').value='';list.replaceChildren();addQuestion();mode(); }
    $('newLesson')?.addEventListener('click', () => { if(confirm('Bỏ nội dung đang soạn và tạo bài học mới?')) resetLesson(); });
    $('newQuiz')?.addEventListener('click', () => { if(confirm('Bỏ nội dung đang soạn và tạo quiz mới?')) resetQuiz(); });
    $('lessonSelect')?.addEventListener('change', () => run(async () => {
        const id=$('lessonSelect').value; if (!id) {resetLesson();return;}
        const d = await api('/api/v1/teacher/lessons/'+id);
        for (const [field,key] of Object.entries({lessonTitle:'title',lessonTopic:'topic_id',lessonSummary:'summary',lessonDuration:'duration_minutes',lessonObjectives:'objectives',lessonContent:'content_html',lessonStatus:'status'})) $(field).value=d[key]??'';
        setClasses('lessonClass',d.classIds);notify('Đã mở bài học.');
    }));
    $('quizSelect')?.addEventListener('change', () => run(async () => {
        const id=$('quizSelect').value; if (!id) {resetQuiz();return;}
        const d = await api('/api/v1/teacher/quizzes/'+id);
        for(const [field,key] of Object.entries({quizTitle:'title',quizDuration:'duration_minutes',quizAttempts:'max_attempts',quizMode:'mode'})) $(field).value=d[key];
        $('quizPublished').checked=Boolean(d.published);$('quizReveal').value='';
        if(d.reveal_after) { const date=new Date(d.reveal_after);$('quizReveal').value=new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16); }
        setClasses('quizClass',d.classIds);list.replaceChildren();d.questions.forEach(addQuestion);mode();notify('Đã mở quiz.');
    }));
    function mode() { if (!$('quizMode')) return; $('quizReveal').required=$('quizMode').value==='TEST' && $('quizPublished').checked; }
    $('quizMode')?.addEventListener('change',mode);$('quizPublished')?.addEventListener('change',mode);if (list) addQuestion();mode();
    for (const id of ['lessonSelect','quizSelect']) if ($(id)?.value) $(id).dispatchEvent(new Event('change'));
})();