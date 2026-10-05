(() => {
    'use strict';
    const form=document.getElementById('storageForm'); if(!form) return;
    const fields=document.getElementById('storageFields'),notice=document.getElementById('storageNotice');
    const el=id=>document.getElementById('storage'+id),mb=1024*1024;
    function render(data) {
        for(const [id,key] of Object.entries({Endpoint:'endpoint',Bucket:'bucket',Region:'region',Prefix:'prefix'})) el(id).value=data[key];
        for(const [id,key] of Object.entries({Enabled:'enabled',AiImages:'aiImagesEnabled',AiPdf:'aiPdfEnabled'})) el(id).checked=data[key];
        el('ImageLimit').value=data.maxImageBytes/mb;el('PdfLimit').value=data.maxPdfBytes/mb;el('Quota').value=data.userQuotaBytes/mb;
        el('AccessKey').value='';el('SecretKey').value='';
        el('KeyState').textContent=(data.keyConfigured?'Đã lưu khóa mã hóa. Để trống cả hai trường để giữ nguyên. ':'Chưa có khóa S3. ')+(data.encryptionReady?'Server đã có khóa mã hóa.':'Server cần EDULAB_STORAGE_ENCRYPTION_KEY.');
    }
    function error(ex) { notice.textContent=ex.message;notice.focus(); }
    window.edulab.api('/api/v1/admin/storage-settings').then(data=>{render(data);fields.disabled=false;}).catch(error);
    form.addEventListener('submit',async event=>{
        event.preventDefault();fields.disabled=true;notice.textContent='Đang lưu...';
        try {
            render(await window.edulab.api('/api/v1/admin/storage-settings',{method:'PUT',body:{enabled:el('Enabled').checked,endpoint:el('Endpoint').value,bucket:el('Bucket').value,region:el('Region').value,prefix:el('Prefix').value,
                accessKey:el('AccessKey').value,secretKey:el('SecretKey').value,maxImageBytes:Number(el('ImageLimit').value)*mb,maxPdfBytes:Number(el('PdfLimit').value)*mb,userQuotaBytes:Number(el('Quota').value)*mb,aiImagesEnabled:el('AiImages').checked,aiPdfEnabled:el('AiPdf').checked}}));
            notice.textContent='Đã lưu cấu hình storage. Áp dụng cho lượt upload tiếp theo.';
        } catch(ex) { error(ex); } finally { fields.disabled=false; }
    });
    el('Test').addEventListener('click',async()=>{
        fields.disabled=true;notice.textContent='Đang kiểm tra upload trực tiếp...';
        try {
            const probe=await window.edulab.api('/api/v1/admin/storage-settings/test',{method:'POST'});
            await window.edulabUploads.put(probe.url,new Blob(['OK'],{type:'text/plain'}),{'Content-Type':'text/plain'},()=>{},new AbortController().signal);
            const result=await window.edulab.api('/api/v1/admin/storage-settings/test/complete',{method:'POST',body:{key:probe.key}});
            notice.textContent=result.message;
        } catch(ex) { error(ex); } finally { fields.disabled=false; }
    });
})();