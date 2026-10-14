(() => {
    'use strict';
    const svg=document.getElementById('scoreChart'),rows=[...document.querySelectorAll('#scoreBins tr')];
    const max=Math.max(1,...rows.map(row=>Number(row.dataset.count)));
    const node=(name,attributes,text)=>{const el=document.createElementNS('http://www.w3.org/2000/svg',name);for(const [key,value] of Object.entries(attributes)) el.setAttribute(key,value);if(text!==undefined) el.textContent=text;svg.append(el);return el;};
    node('line',{x1:40,y1:250,x2:640,y2:250});
    node('text',{x:10,y:24},'Số');node('text',{x:10,y:42},'kết quả');
    rows.forEach((row,i)=>{const value=Number(row.dataset.count),height=200*value/max,x=50+i*59;
        const bar=node('rect',{x,y:250-height,width:38,height});const title=document.createElementNS('http://www.w3.org/2000/svg','title');title.textContent=row.dataset.label+': '+value+' kết quả';bar.append(title);
        node('text',{x:x+19,y:Math.max(22,242-height),'text-anchor':'middle'},value);
        node('text',{x:x+19,y:276,'text-anchor':'middle'},row.dataset.label);
    });
    const search=document.getElementById('attemptSearch'),status=document.getElementById('attemptStatus');
    function filter() {const term=search.value.trim().toLocaleLowerCase('vi');document.querySelectorAll('#reportAttempts tr').forEach(row=>row.hidden=!row.cells[0].textContent.toLocaleLowerCase('vi').includes(term) || status.value!=='all' && row.cells[2].textContent!==(status.value==='GRADED'?'Đã chấm':'Đang làm'));}
    search.addEventListener('input',filter);status.addEventListener('change',filter);
})();