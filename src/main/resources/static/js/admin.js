(() => {
    const notice=document.getElementById('adminNotice');
    document.querySelectorAll('.adminForm').forEach(form=>form.addEventListener('submit',async event=>{
        event.preventDefault();const button=form.querySelector('button');button.disabled=true;
        const body=Object.fromEntries(new FormData(form));
        ['topicId','durationMinutes','requiredScore','maxAttempts'].forEach(key=>{if(key in body)body[key]=Number(body[key]);});
        ['lessonId','prerequisiteId'].forEach(key=>{if(key in body)body[key]=body[key]?Number(body[key]):null;});
        if('options' in body)body.options=body.options.split('\n').map(s=>s.trim()).filter(Boolean);
        if('correctIndex' in body)body.correctIndex=Number(body.correctIndex)-1;
        if('questionIds' in body)body.questionIds=body.questionIds.split(',').map(s=>Number(s.trim()));
        if('revealAfter' in body)body.revealAfter=body.revealAfter?new Date(body.revealAfter).toISOString():null;
        try{await window.edulab.api('/api/v1/admin/'+form.dataset.endpoint,{method:'POST',body});window.location.reload();}
        catch(error){notice.textContent=error.message;notice.scrollIntoView({block:'center'});button.disabled=false;}
    }));
    document.querySelectorAll('.adminState').forEach(button=>button.addEventListener('click',async()=>{
        if(!window.confirm('Xác nhận thay đổi trạng thái?'))return;button.disabled=true;
        try{await window.edulab.api('/api/v1/admin/'+button.dataset.endpoint,{method:'POST',body:{enabled:button.dataset.enabled==='true'}});window.location.reload();}
        catch(error){notice.textContent=error.message;button.disabled=false;}
    }));
})();