(() => {
    'use strict';
    const notice = document.getElementById('classNotice');
    const form = document.getElementById('teacherEnrollForm');
    form?.addEventListener('submit', async event => {
        event.preventDefault(); const button=form.querySelector('button');button.disabled=true;
        try { await window.edulab.api('/api/v1/teacher/classes/'+form.dataset.class+'/students',{method:'POST',body:{username:document.getElementById('studentUsername').value.trim()}});window.location.assign(window.edulab.context+'/teacher/classes/'+form.dataset.class+'/students'); }
        catch(error) {notice.textContent=error.message;notice.className='alert alert-danger';button.disabled=false;}
    });
    document.querySelectorAll('[data-membership]').forEach(button => button.addEventListener('click',async () => {
        const active=button.dataset.active==='true';
        if(!confirm(active?'Khôi phục học sinh vào lớp?':'Ngừng tham gia lớp? Tài khoản và lịch sử học tập vẫn được giữ nguyên.')) return;
        button.disabled=true;
        try {await window.edulab.api('/api/v1/teacher/classes/'+button.dataset.class+'/students/'+button.dataset.membership,{method:'PUT',body:{active}});window.location.reload();}
        catch(error) {notice.textContent=error.message;notice.className='alert alert-danger';button.disabled=false;}
    }));
})();