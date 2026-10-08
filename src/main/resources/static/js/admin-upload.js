(() => {
    'use strict';
    const form = document.getElementById('adminUploadForm');
    if (!form) return;
    const uploads = window.edulabUploads.attach('adminUploadFiles', 'adminUploadStatus', 'adminUploadCancel');
    form.addEventListener('submit', async event => {
        event.preventDefault();const button = form.querySelector('[type=submit]');button.disabled = true;
        try {
            const ids = await uploads.upload();
            location.assign(window.edulab.context + '/admin/files' + (ids.length === 1 ? '/' + ids[0] : ''));
        } catch (error) {document.getElementById('adminUploadStatus').textContent = error.message;button.disabled = false;}
    });
})();