(() => {
    const notice = document.getElementById('forumNotice');
    const value = id => document.getElementById(id).value;
    const threadUploads=document.getElementById('threadForm')?window.edulabUploads.attach('threadFiles','threadUploadStatus','threadUploadCancel'):null;
    const postUploads=document.getElementById('postForm')?window.edulabUploads.attach('files','postUploadStatus','postUploadCancel'):null;
    document.getElementById('threadForm')?.addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget, button = form.querySelector('button[type=submit]'); button.disabled = true;
        try {
            if(document.getElementById('threadAi').checked && !value('threadLesson')) throw new Error('Chọn bài học tham khảo trước khi nhờ AI.');
            const fileIds=await threadUploads.upload();
            const result = await window.edulab.api('/api/v1/forum/threads', {method: 'POST', body: {categoryId: Number(form.dataset.category), title: value('threadTitle'), body: value('threadBody'),lessonId:Number(value('threadLesson'))||null,askAi:document.getElementById('threadAi').checked,fileIds}});
            if(result.aiUnavailable) window.alert('Câu hỏi đã đăng thành công. AI chưa sẵn sàng; hãy đợi cộng đồng hoặc giáo viên trả lời.');
            window.location.assign(window.edulab.context + '/forum/threads/' + result.id);
        } catch (error) { notice.textContent = error.message; button.disabled = false; }
    });
    document.getElementById('postForm')?.addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget, button = form.querySelector('button[type=submit]'); button.disabled = true;
        try {
            const fileIds=await postUploads.upload();
            await window.edulab.api('/api/v1/forum/threads/' + form.dataset.id + '/posts', {method: 'POST', body: {body: value('postBody'), fileIds}});
            window.location.reload();
        } catch (error) { notice.textContent = error.message; button.disabled = false; }
    });
    document.getElementById('forumRetryAi')?.addEventListener('click',async event=>{
        const button=event.currentTarget;button.disabled=true;notice.textContent='AI đang đọc câu hỏi và file...';
        try { await window.edulab.api('/api/v1/forum/threads/'+button.dataset.id+'/ai',{method:'POST',body:{lessonId:Number(button.dataset.lesson)}});window.location.reload(); }
        catch(error) { notice.textContent=error.message;button.disabled=false; }
    });
    document.querySelectorAll('.moderateThread').forEach(button => button.addEventListener('click', async () => {
        if (!window.confirm('Xác nhận thay đổi trạng thái thảo luận?')) return;
        button.disabled = true;
        try {
            await window.edulab.api('/api/v1/forum/threads/' + button.dataset.id + '/moderate', {method: 'POST', body: {status: button.dataset.status}});
            window.location.assign(window.edulab.context + '/forum');
        } catch (error) { notice.textContent = error.message; button.disabled = false; }
    }));
})();
