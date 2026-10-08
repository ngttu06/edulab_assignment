(() => {
    'use strict';
    const search=document.getElementById('teacherContentSearch'), filter=document.getElementById('teacherContentFilter');
    function bind(search,filter,selector,empty) {
        if (!search || !filter) return;
        const items=[...document.querySelectorAll(selector)];
        function update() {
            const term=search.value.trim().toLocaleLowerCase('vi');let count=0;
            for(const item of items) { item.hidden=!( (item.dataset.title+' '+(item.dataset.class || '')).toLocaleLowerCase('vi').includes(term) && (filter.value==='all' || item.dataset.status===filter.value));if(!item.hidden) count++; }
            empty.classList.toggle('d-none',count>0 || !items.length);
        }
        search.addEventListener('input',update);filter.addEventListener('change',update);
    }
    bind(search,filter,'#teacherContentItems [data-title]',document.getElementById('teacherContentEmpty'));
    bind(document.getElementById('teacherAssignmentSearch'),document.getElementById('teacherAssignmentFilter'),'#teacherAssignmentRows tr',document.getElementById('teacherAssignmentEmpty'));
    bind(document.getElementById('teacherResultSearch'),document.getElementById('teacherResultFilter'),'#teacherResultRows tr',document.getElementById('teacherResultEmpty'));
})();