document.getElementById('assignmentForm')?.addEventListener('submit',async event=>{
    event.preventDefault();const form=event.currentTarget,button=form.querySelector('button');button.disabled=true;
    try{await window.edulab.api('/api/v1/teacher/classes/'+form.dataset.id+'/assignments',{method:'POST',body:{quizId:Number(document.getElementById('assignedQuiz').value),dueAt:new Date(document.getElementById('assignmentDue').value).toISOString()}});window.location.reload();}
    catch(error){document.getElementById('assignmentNotice').textContent=error.message;button.disabled=false;}
});
document.getElementById('feedbackForm')?.addEventListener('submit',async event=>{
    event.preventDefault();const form=event.currentTarget,button=form.querySelector('button');button.disabled=true;
    try{await window.edulab.api('/api/v1/teacher/students/'+form.dataset.id+'/feedback',{method:'POST',body:{body:document.getElementById('feedbackBody').value}});window.location.reload();}
    catch(error){document.getElementById('feedbackNotice').textContent=error.message;button.disabled=false;}
});