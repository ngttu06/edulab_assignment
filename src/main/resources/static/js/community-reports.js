document.addEventListener('click',async event=>{
    const button=event.target.closest('.report-action');if(!button)return;
    if(!confirm(button.dataset.type==='hide'?'Ẩn bài viết này?':button.dataset.type==='restore'?'Cho hiển thị lại bài viết?':'Đánh dấu toàn bộ báo cáo bài này đã xử lý?'))return;
    button.disabled=true;
    try{const path=button.dataset.type==='hide'?'/api/v1/community/posts/'+button.dataset.id+'/hide':
        '/api/v1/admin/community/posts/'+button.dataset.id+(button.dataset.type==='restore'?'/restore':'/reports');
        await window.edulab.api(path,{method:button.dataset.type==='resolve'?'DELETE':'POST'});location.reload();
    }catch(error){document.getElementById('reportStatus').textContent=error.message;button.disabled=false;}
});