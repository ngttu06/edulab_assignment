(() => {
    'use strict';
    const form = document.getElementById('videoManageForm');
    if (!form) return;
    const lesson = document.getElementById('videoLesson'), notice = document.getElementById('videoManageNotice');
    const provider = document.getElementById('videoProvider'), url = document.getElementById('videoUrl');
    let preview, previewTimer, previewTimeout, generation = 0;
    const scope = document.body.classList.contains('admin-body') ? 'admin' : 'teacher';
    const endpoint = () => '/api/v1/'+scope+'/lessons/'+lesson.value+'/video';
    function stopPreview() {
        generation++; clearInterval(previewTimer); clearTimeout(previewTimeout); preview?.destroy(); preview=null;
        const holder = document.getElementById('videoPreview'); holder.replaceChildren();
        const container = document.createElement('div'); container.id='previewPlayer'; holder.append(container);
    }
    lesson.addEventListener('change', async () => {
        stopPreview(); const selected = lesson.value;
        form.reset(); lesson.value = selected; document.getElementById('removeVideo').disabled = true;
        if (!selected) return;
        try {
            const config = await window.edulab.api(endpoint());
            if (lesson.value !== selected) return;
            provider.value = config.provider || 'YOUTUBE'; url.value = config.source_url || '';
            document.getElementById('videoDuration').value = config.duration_seconds || '';
            document.getElementById('videoThreshold').value = config.completion_threshold || 95;
            document.getElementById('removeVideo').disabled = !config.provider;
            notice.textContent = config.provider ? 'Đang sửa video v'+config.revision+'. Lưu sẽ tạo phiên bản mới và bắt đầu lại tiến độ bài.' : 'Bài học chưa có video.';
        } catch(error) { notice.textContent=error.message; }
    });
    form.addEventListener('submit',async event => {
        event.preventDefault(); const button=form.querySelector('[type=submit]'); button.disabled=true;
        try {
            await window.edulab.api(endpoint(),{method:'PUT',body:{provider:provider.value,sourceUrl:url.value.trim(),
                durationSeconds:Number(document.getElementById('videoDuration').value),completionThreshold:Number(document.getElementById('videoThreshold').value)}});
            window.location.reload();
        } catch(error) { notice.textContent=error.message; } finally { button.disabled=false; }
    });
    document.getElementById('removeVideo').addEventListener('click',async event => {
        if (!confirm('Gỡ video và bắt đầu lại tiến độ bài? Lịch sử video cũ vẫn được giữ.')) return;
        event.currentTarget.disabled=true;
        try { await window.edulab.api(endpoint(),{method:'DELETE'}); location.reload(); }
        catch(error) { notice.textContent=error.message; event.currentTarget.disabled=false; }
    });
    document.querySelectorAll('.videoTeacherForm').forEach(assign => assign.addEventListener('submit',async event => {
        event.preventDefault(); const button=assign.querySelector('button'); button.disabled=true;
        try {
            await window.edulab.api('/api/v1/admin/lessons/'+assign.dataset.lesson+'/video-teacher',{method:'PUT',body:{teacherId:Number(assign.querySelector('select').value)||null}});
            notice.textContent='Đã lưu phân công giáo viên.';
        } catch(error) { notice.textContent=error.message; } finally { button.disabled=false; }
    }));
    document.getElementById('previewVideo').addEventListener('click',async () => {
        stopPreview(); const ticket=generation, text=document.getElementById('previewNotice');
        try {
            if (!lesson.value) throw new Error('Hãy chọn bài học trước.');
            const source=await window.edulab.api('/api/v1/'+scope+'/videos/validate',{method:'POST',body:{provider:provider.value,sourceUrl:url.value.trim(),durationSeconds:1,completionThreshold:95}});
            if (ticket !== generation) return;
            text.textContent='Đang tải video xem thử…';
            const nextPreview=await window.edulabVideoPlayer.create({id:'previewPlayer',provider:provider.value,
                sourceUrl:source.url,youtubeId:source.youtubeId,
                playerPath:form.dataset.playerPath,playerAvailable:form.dataset.playerAvailable==='true'},
                {error:error => {
                    if (ticket !== generation) return;
                    clearInterval(previewTimer); clearTimeout(previewTimeout); text.textContent=error.message;
                }});
            if (ticket !== generation) { nextPreview.destroy(); return; }
            preview=nextPreview;
            previewTimer=setInterval(() => {
                const duration=preview?.duration();
                if (Number.isFinite(duration) && duration>0) {
                    document.getElementById('videoDuration').value=String(Math.round(duration));
                    text.textContent='Thời lượng: '+Math.round(duration)+' giây. Đã điền thời lượng; xem thử không lưu tiến độ.';
                    clearInterval(previewTimer); clearTimeout(previewTimeout);
                }
            },500);
            previewTimeout=setTimeout(() => {
                clearInterval(previewTimer);
                if (ticket === generation) text.textContent='Không tải được thời lượng video. Kiểm tra nguồn HTTPS, CORS, manifest/segment HLS và CSP rồi thử lại.';
            },25000);
        } catch(error) { if (ticket === generation) text.textContent=error.message; }
    });
})();