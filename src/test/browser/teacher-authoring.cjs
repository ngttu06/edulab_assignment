/* Run only against a disposable local test database. Creates fixtures in that database. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8091/edulab';
if(!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base) || process.env.EDULAB_DISPOSABLE_TEST_DB!=='true') throw new Error('Requires local URL and EDULAB_DISPOSABLE_TEST_DB=true; confirm datasource first.');
async function main() {
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],stamp=Date.now();
    const out=process.env.EDULAB_SCREENSHOT_DIR || '/tmp/edulab-teacher-screenshots';fs.mkdirSync(out,{recursive:true});
    async function pageFor(name) {
        const ctx=await browser.newContext({viewport:{width:1440,height:1000}}),page=await ctx.newPage();
        page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
        page.on('console',m=>{if(m.type()==='error' && /Content Security Policy/.test(m.text())) errors.push(m.text());});
        await page.goto(base+'/login');await page.locator('#username').fill(name);await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(u=>!u.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);return page;
    }
    async function api(page,url,method='GET',body) {return page.evaluate(({url,method,body})=>window.edulab.api(url,{method,body}),{url,method,body});}
    async function rendered(page,url) {const response=await page.goto(base+url);assert.equal(response.status(),200,url);assert.ok((await page.locator('body').innerText()).length>100);}
    try {
        const teacher=await pageFor('giaovien');await rendered(teacher,'/teacher/lessons');
        const classId=Number(await teacher.locator('[name=lessonClass]').first().getAttribute('value'));
        await teacher.locator('#lessonTitle').fill('Browser bài học '+stamp);
        await teacher.locator('#lessonContent').fill('<h2>Kiến thức</h2><script>alert(1)</script><p>Nội dung lớp học.</p>');
        await teacher.locator('[name=lessonClass]').first().check();await teacher.locator('#lessonStatus').selectOption('PUBLISHED');
        await teacher.locator('#teacherLessonForm button[type=submit]').click();
        await teacher.waitForFunction(()=>document.getElementById('authorNotice').textContent==='Đã lưu bài học.');
        const lessonId=Number(await teacher.locator('#lessonSelect').inputValue());assert.ok(lessonId>0);
        const template=await teacher.request.get(base+'/teacher/quizzes/template.csv');assert.equal(template.status(),200);
        await teacher.locator('#quizCsv').setInputFiles({name:'quiz-mau.csv',mimeType:'text/csv',buffer:await template.body()});
        await teacher.locator('#importQuiz').click();await teacher.waitForFunction(()=>document.querySelectorAll('[data-question]').length===3);
        await teacher.locator('#quizTitle').fill('Browser quiz CSV '+stamp);await teacher.locator('[name=quizClass]').first().check();await teacher.locator('#quizPublished').check();
        await teacher.locator('#teacherQuizForm button[type=submit]').click();await teacher.waitForFunction(()=>document.getElementById('quizSelect').value!=='');
        const quizId=Number(await teacher.locator('#quizSelect').inputValue());
        const quiz=await api(teacher,'/api/v1/teacher/quizzes/'+quizId);assert.equal(quiz.questions.length,3);assert.equal(quiz.questions[1].correctIndex,1);
        await teacher.reload();await teacher.locator('#quizSelect').selectOption(String(quizId));await teacher.waitForFunction(()=>document.querySelectorAll('[data-question]').length===3);
        assert.equal(await teacher.locator('[data-field=correctIndex]').nth(1).inputValue(),'1');
        await teacher.locator('[data-field=content]').first().fill('Câu sửa trên web');await teacher.locator('[data-action=down]').first().click();
        await teacher.locator('#teacherQuizForm button[type=submit]').click();await teacher.waitForFunction(()=>document.getElementById('authorNotice').textContent.startsWith('Đã lưu quiz'));
        const edited=await api(teacher,'/api/v1/teacher/quizzes/'+quizId);assert.equal(edited.questions[1].content,'Câu sửa trên web');
        // Invalid import must leave existing editor content untouched.
        await teacher.locator('#quizCsv').setInputFiles({name:'bad.csv',mimeType:'text/csv',buffer:Buffer.from('wrong\n')});
        await teacher.locator('#importQuiz').click();await teacher.waitForFunction(()=>document.getElementById('authorNotice').textContent.startsWith('Dòng 1:'));
        assert.equal(await teacher.locator('[data-question]').count(),3);
        // Create a second quiz entirely in the editor.
        await teacher.locator('#newQuiz').click();await teacher.locator('#quizTitle').fill('Browser quiz trực tiếp '+stamp);
        await teacher.locator('[name=quizClass]').first().check();await teacher.locator('[data-field=content]').fill('1 + 1 = ?');
        await teacher.locator('[data-option]').nth(0).fill('2');await teacher.locator('[data-option]').nth(1).fill('3');
        await teacher.locator('#teacherQuizForm button[type=submit]').click();await teacher.waitForFunction(()=>document.getElementById('quizSelect').value!=='');
        const manualId=Number(await teacher.locator('#quizSelect').inputValue());assert.notEqual(manualId,quizId);
        const student=await pageFor('hocsinh');await rendered(student,'/lessons/'+lessonId);
        assert.ok(!(await api(student,'/api/v1/lessons/'+lessonId)).contentHtml.includes('<script>'));
        await api(student,'/api/v1/lessons/'+lessonId+'/progress','POST',{status:'COMPLETED',completionPercent:100});
        const available=await api(student,'/api/v1/quizzes');assert.ok(available.some(q=>q.id===quizId));assert.ok(!available.some(q=>q.id===manualId));
        await rendered(teacher,'/teacher/classes/'+classId);
        await teacher.locator('#assignedQuiz').selectOption(String(quizId));
        const due=new Date(Date.now()+3600000);await teacher.locator('#assignmentDue').fill(new Date(due.getTime()-due.getTimezoneOffset()*60000).toISOString().slice(0,16));
        await Promise.all([teacher.waitForEvent('load'),teacher.locator('#assignmentForm button').click()]);
        const attempt=await api(student,'/api/v1/quizzes/'+quizId+'/attempts','POST');const answers={};
        attempt.questions.forEach((q,i)=>answers[q.id]=q.options[edited.questions[i].correctIndex].id);
        const graded=await api(student,'/api/v1/quiz-attempts/'+attempt.id+'/submit','POST',{answers});assert.equal(Number(graded.score),10);
        const reportLink=teacher.locator('a[href*="/students/"]').first();const reportPath=new URL(await reportLink.getAttribute('href'),base).pathname.replace('/edulab','');
        await rendered(teacher,reportPath);assert.ok((await teacher.locator('body').innerText()).includes('10.00'));
        for(const width of [1440,768,390,320]) {
            await teacher.setViewportSize({width,height:1000});
            for(const route of ['/teacher/lessons','/teacher/classes/'+classId,reportPath]) {
                await rendered(teacher,route);
                assert.ok(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'overflow '+width+' '+route);
                if(route==='/teacher/classes/'+classId) {
                    assert.ok(await teacher.locator('#studentSearch + button').evaluate(el=>el.getBoundingClientRect().height<60),'search button wrapping');
                    assert.ok(await teacher.locator('tbody tr').first().evaluate(el=>el.getBoundingClientRect().height<160),'roster row too tall');
                }
                await teacher.screenshot({path:path.join(out,width+'-'+route.replaceAll('/','-')+'.png'),fullPage:true});
            }
        }
        await rendered(teacher,'/teacher/classes/'+classId);
        const remove=teacher.locator('[data-membership][data-active=false]').first();const studentId=Number(await remove.getAttribute('data-membership'));
        await Promise.all([teacher.waitForEvent('load'),remove.click()]);
        assert.ok(!(await api(student,'/api/v1/quizzes')).some(q=>q.id===quizId));
        await teacher.locator('#studentUsername').fill('hocsinh');await Promise.all([teacher.waitForEvent('load'),teacher.locator('#teacherEnrollForm button').click()]);
        assert.ok((await api(student,'/api/v1/quizzes')).some(q=>q.id===quizId));
        const report=await api(teacher,'/api/v1/teacher/classes/'+classId+'/students/'+studentId+'/report');
        assert.equal(Number(report.quizzes.find(q=>q.id===quizId).best_score),10);
        const denied=await student.request.get(base+'/api/v1/teacher/quizzes/'+quizId);assert.equal(denied.status(),403);
        assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',lessonId,quizId,manualId,classId,studentId,screenshots:out}));
    } finally {await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});