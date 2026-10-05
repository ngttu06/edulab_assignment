(() => {
    'use strict';
    // This request is deliberately separate from edulab.api: no app cookies, JWT or CSRF sent to R2.
    function put(url, file, headers, progress, signal) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open('PUT', url); xhr.withCredentials = false; xhr.timeout = 180000;
            Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
            xhr.upload.onprogress = event => { if (event.lengthComputable) progress(Math.round(event.loaded * 100 / event.total)); };
            const abort = () => xhr.abort();
            signal.addEventListener('abort', abort, {once:true});
            const done = () => signal.removeEventListener('abort', abort);
            xhr.onload = () => { done(); xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error('R2 từ chối upload. URL có thể hết hạn; hãy thử lại.')); };
            xhr.onerror = () => { done(); reject(new Error('Không kết nối được R2. Kiểm tra mạng, CORS đúng origin/cổng và CSP.')); };
            xhr.ontimeout = () => { done(); reject(new Error('Upload hết thời gian chờ. Hãy thử lại.')); };
            xhr.onabort = () => { done(); reject(new Error('Đã hủy upload.')); };
            if (signal.aborted) { done(); reject(new Error('Đã hủy upload.')); return; }
            xhr.send(file);
        });
    }
    window.edulabUploads = {
        put,
        attach(inputId, statusId, cancelId) {
            const input = document.getElementById(inputId), status = document.getElementById(statusId), cancel = document.getElementById(cancelId);
            const uploads = new Map(); let controller;
            const report = text => { status.textContent = text; };
            const selected=document.createElement('ul');selected.className='list-unstyled small mt-2 text-break';input.after(selected);
            function renderSelection() {
                selected.replaceChildren();
                Array.from(input.files).forEach((file,index)=>{
                    const item=document.createElement('li'),remove=document.createElement('button');
                    item.className='d-flex flex-wrap align-items-center gap-2 mb-2';
                    const label=document.createElement('span');label.textContent=file.name+' · '+(file.size/1024/1024).toFixed(2)+' MB';
                    remove.type='button';remove.className='btn btn-sm btn-outline-secondary';remove.textContent='Bỏ chọn';remove.setAttribute('aria-label','Bỏ chọn '+file.name);
                    remove.addEventListener('click',()=>{
                        if(input.disabled) return;
                        const transfer=new DataTransfer();Array.from(input.files).filter((_,i)=>i!==index).forEach(f=>transfer.items.add(f));input.files=transfer.files;renderSelection();
                    });
                    item.append(label,remove);selected.append(item);
                });
            }
            input.addEventListener('change',renderSelection);
            cancel.addEventListener('click', () => controller?.abort());
            return {
                async upload() {
                    const files = Array.from(input.files);
                    if (files.length > 3) throw new Error('Tối đa 3 file đính kèm.');
                    for (const file of files) {
                        const limit = file.type === 'application/pdf' ? 10*1024*1024 : 5*1024*1024;
                        if (!['image/jpeg','image/png','image/webp','application/pdf'].includes(file.type) || file.size < 1 || file.size > limit) throw new Error('Chỉ nhận ảnh JPG/PNG/WebP tối đa 5 MB hoặc PDF tối đa 10 MB.');
                    }
                    controller = new AbortController(); input.disabled = true;selected.querySelectorAll('button').forEach(b=>b.disabled=true);cancel.hidden = true;
                    const ids = [];
                    try {
                        for (const file of files) {
                            if (controller.signal.aborted) throw new Error('Đã hủy upload.');
                            let entry = uploads.get(file);
                            if(entry?.fileId) { ids.push(entry.fileId);continue; }
                            if (!entry) {
                                const ticket = await window.edulab.api('/api/v1/files/presign', {method:'POST',body:{filename:file.name,contentType:file.type,size:file.size}});
                                entry = {ticket, sent:false}; uploads.set(file, entry);
                            }
                            if (!entry.sent) {
                                cancel.hidden=false;
                                try {
                                    await put(entry.ticket.url, file, entry.ticket.headers, percent => report(`${file.name}: upload trực tiếp R2 ${percent}%`), controller.signal);
                                    entry.sent = true;
                                } catch (error) {
                                    await window.edulab.api('/api/v1/files/uploads/'+entry.ticket.uploadId, {method:'DELETE'}).catch(()=>{});
                                    uploads.delete(file); throw error;
                                }
                            }
                            cancel.hidden=true;
                            if(controller.signal.aborted) throw new Error('Đã hủy upload.');
                            report(`${file.name}: đang xác nhận và kiểm tra file...`);
                            const completed = await window.edulab.api('/api/v1/files/uploads/'+entry.ticket.uploadId+'/complete', {method:'POST'});
                            if (!completed.fileId) { uploads.delete(file); throw new Error(completed.message); }
                            if (!['CLEAN','PENDING_SCAN'].includes(completed.scanStatus)) throw new Error(completed.message);
                            entry.fileId = completed.fileId; ids.push(completed.fileId); report(file.name+': '+completed.message);
                        }
                        return ids;
                    } finally { input.disabled = false;selected.querySelectorAll('button').forEach(b=>b.disabled=false);cancel.hidden = true; }
                }
            };
        }
    };
})();