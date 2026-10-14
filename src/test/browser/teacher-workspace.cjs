/* Writes fixtures only on a disposable loopback test deployment. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8092/edulab';
if(!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base) || process.env.EDULAB_DISPOSABLE_TEST_DB!=='true') throw Error('Requires disposable local test database');
async function main() {
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],stamp=Date.now();
    const screenshots=process.env.EDULAB_SCREENSHOT_DIR || '/tmp/edulab-v18-screenshots';fs.mkdirSync(screenshots,{recursive:true});
    async function login(name) {
        const page=await (await browser.newContext({viewport:{width:1440,height:1000}})).newPage();
        page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
        await page.goto(base+'/login');await page.locator('#username').fill(name);await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(u=>!u.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);return page;
    }
    async function go(page,path) {assert.equal((await page.goto(base+path)).status(),200,path);}
    async function api(page,path,method='GET',body) {return page.evaluate(({path,method,body})=>window.edulab.api(path,{method,body}),{path,method,body});}
    async function save(page,selector,path) {await Promise.all([page.waitForURL(u=>u.pathname===('/edulab'+path)),page.locator(selector).click()]);}
    const localDate=hours=>{const d=new Date(Date.now()+hours*3600000);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
    try {
        const t=await login('giaovien'),s=await login('hocsinh'),a=await login('admin');
        await go(t,'/teacher/lessons');assert.equal(await t.locator('#teacherLessonForm,#teacherQuizForm').count(),0);
        await go(t,'/teacher/lessons/new');assert.equal(await t.locator('#teacherQuizForm').count(),0);
        const classId=Number(await t.locator('[name=lessonClass]').first().getAttribute('value')),cl='/teacher/classes/'+classId;
        await t.locator('#lessonTitle').fill('Workspace lesson '+stamp);await t.locator('#lessonContent').fill('<h2>Nội dung</h2><script>alert(1)</script><p>Kiến thức.</p>');
        await t.locator('#previewLesson').click();await t.locator('#lessonPreview:not(.d-none)').waitFor();
        assert.ok((await t.locator('#lessonPreviewBody').innerHTML()).includes('<h2>Nội dung</h2>'));
        assert.equal(await t.locator('#lessonPreviewBody script').count(),0);
        await t.locator('[name=lessonClass]').first().check();await t.locator('#lessonStatus').selectOption('PUBLISHED');
        await Promise.all([t.waitForURL(/\/teacher\/lessons\/\d+$/),t.locator('#teacherLessonForm button[type=submit]').click()]);
        const lessonId=Number(t.url().split('/').at(-1));
        await go(t,'/teacher/lessons/'+lessonId+'/edit');await t.waitForFunction(()=>document.getElementById('lessonTitle').value.length>0);
        await t.locator('#lessonTitle').fill('Workspace lesson edited '+stamp);await save(t,'#teacherLessonForm button[type=submit]','/teacher/lessons/'+lessonId);
        await api(s,'/api/v1/lessons/'+lessonId+'/progress','POST',{status:'COMPLETED',completionPercent:100});
        assert.ok(!(await api(s,'/api/v1/lessons/'+lessonId)).contentHtml.includes('<script>'));
        await go(t,'/teacher/quizzes/import');const sample=await t.request.get(base+'/teacher/quizzes/template.csv');
        await t.locator('#quizCsv').setInputFiles({name:'quiz.csv',mimeType:'text/csv',buffer:await sample.body()});await t.locator('#importQuiz').click();
        await t.waitForFunction(()=>document.querySelectorAll('[data-question]').length===3);
        await t.locator('#quizCsv').setInputFiles({name:'bad.csv',mimeType:'text/csv',buffer:Buffer.from('bad\n')});await t.locator('#importQuiz').click();
        await t.waitForFunction(()=>document.getElementById('authorNotice').className.includes('danger'));assert.equal(await t.locator('[data-question]').count(),3);
        await t.locator('#quizTitle').fill('Workspace quiz '+stamp);await t.locator('#quizAttempts').fill('3');await t.locator('[name=quizClass]').first().check();await t.locator('#quizPublished').check();
        await Promise.all([t.waitForURL(/\/teacher\/quizzes\/\d+$/),t.locator('#teacherQuizForm button[type=submit]').click()]);
        const quizId=Number(t.url().split('/').at(-1));
        await go(t,'/teacher/quizzes/'+quizId+'/edit');await t.waitForFunction(()=>document.querySelectorAll('[data-question]').length===3);
        await t.locator('[data-field=content]').first().fill('Câu chỉnh sửa');await save(t,'#teacherQuizForm button[type=submit]','/teacher/quizzes/'+quizId);
        const quiz=await api(t,'/api/v1/teacher/quizzes/'+quizId);
        const copy=await api(t,'/api/v1/teacher/quizzes/'+quizId+'/duplicate','POST');
        assert.equal((await api(t,'/api/v1/teacher/quizzes/'+copy.id)).published,false);
        await go(t,'/teacher/quizzes/new');
        await t.locator('#quizTitle').fill('Quiz trực tiếp '+stamp);await t.locator('[name=quizClass]').first().check();
        await t.locator('[data-field=content]').fill('1 + 1 = ?');await t.locator('[data-option]').nth(0).fill('2');await t.locator('[data-option]').nth(1).fill('3');
        await t.locator('[data-field=correctIndex]').selectOption('0');
        await Promise.all([t.waitForURL(/\/teacher\/quizzes\/\d+$/),t.locator('#teacherQuizForm button[type=submit]').click()]);
        const manualId=Number(t.url().split('/').at(-1));assert.notEqual(manualId,quizId);
        assert.ok(!(await api(s,'/api/v1/quizzes')).some(q=>q.id===manualId));
        await go(t,cl+'/assignments/new');await t.locator('#assignedQuiz').selectOption(String(quizId));await t.locator('#assignmentDue').fill(localDate(2));
        await save(t,'[data-workspace-form] button',cl+'/assignments');
        const assignmentPath=(await t.locator('a[href*="/assignments/"]').filter({hasText:'Workspace quiz'}).first().getAttribute('href')).replace('/edulab','');
        await go(t,assignmentPath+'/edit');assert.ok(await t.locator('#assignmentDue').inputValue());await t.locator('#assignmentDue').fill(localDate(3));
        await save(t,'[data-workspace-form] button',cl+'/assignments');
        const attempt=await api(s,'/api/v1/quizzes/'+quizId+'/attempts','POST'),answers={};
        attempt.questions.forEach((q,i)=>answers[q.id]=q.options[quiz.questions[i].correctIndex].id);
        assert.equal(Number((await api(s,'/api/v1/quiz-attempts/'+attempt.id+'/submit','POST',{answers})).score),10);
        await go(t,cl+'/students');const studentId=Number(await t.locator('[data-membership]').first().getAttribute('data-membership'));
        const member=cl+'/students/'+studentId,feedback='/teacher/students/'+studentId+'/feedback';
        await go(t,cl+'/results');const csv=await t.request.get(base+cl+'/results.csv');assert.equal(csv.status(),200);
        assert.match(csv.headers()['content-type'],/text\/csv/);assert.ok((await csv.body()).toString('utf8').startsWith('\uFEFFHọc sinh'));
        await t.locator('#teacherResultFilter').selectOption('graded');assert.ok(await t.locator('#teacherResultRows tr:visible').count()>0);
        await go(t,'/teacher/assignments');await t.locator('#teacherAssignmentSearch').fill('không có quiz này');
        assert.equal(await t.locator('#teacherAssignmentRows tr:visible').count(),0);
        await go(t,'/teacher/lessons');await t.locator('#teacherContentSearch').fill('Workspace lesson edited '+stamp);
        assert.ok(await t.locator('#teacherContentItems a:visible').count()>0);
        await go(t,feedback+'/new');await t.locator('#feedbackBody').fill('Nhận xét '+stamp);await save(t,'[data-workspace-form] button',feedback);
        const feedbackPath=(await t.locator('.list-group a').first().getAttribute('href')).replace('/edulab','');
        await go(t,feedbackPath+'/edit');await t.locator('#feedbackBody').fill('Nhận xét đã sửa');await save(t,'[data-workspace-form] button',feedback);
        const routes=['/teacher','/teacher/assignments','/teacher/lessons','/teacher/lessons/new','/teacher/lessons/'+lessonId,'/teacher/lessons/'+lessonId+'/edit','/teacher/quizzes','/teacher/quizzes/new','/teacher/quizzes/import','/teacher/quizzes/'+quizId,'/teacher/quizzes/'+quizId+'/edit',cl,cl+'/students',cl+'/students/new',member,cl+'/progress',cl+'/results',member+'/progress',member+'/results',cl+'/assignments',cl+'/assignments/new',assignmentPath,assignmentPath+'/edit',feedback,feedback+'/new',feedbackPath,feedbackPath+'/edit'];
        for(const width of [1440,768,390,320]) {
            await t.setViewportSize({width,height:1000});
            for(const route of routes) {
                await go(t,route);assert.ok(await t.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Overflow '+width+' '+route);
                assert.equal(await t.locator('nav[aria-label="Không gian giáo viên"] a').first().innerText(),'Tổng quan');
                if(route.startsWith(cl)) assert.ok((await t.locator('nav[aria-label="Điều hướng lớp"]').innerText()).includes('Tiến độ'));
                if(['/teacher/lessons/new',cl+'/students',member+'/results'].includes(route)) await t.screenshot({path:path.join(screenshots,width+'-'+route.replaceAll('/','-')+'.png'),fullPage:true});
            }
        }
        await go(t,member);await Promise.all([t.waitForEvent('load'),t.locator('[data-membership]').click()]);
        await go(t,member+'/results');assert.match(await t.locator('body').innerText(),/10\.00/);
        await go(t,cl+'/students/new');await t.locator('#studentUsername').fill('hocsinh');await save(t,'#teacherEnrollForm button',cl+'/students');
        await go(t,assignmentPath);await save(t,'[data-delete]',cl+'/assignments');
        await go(t,feedbackPath);await save(t,'[data-delete]',feedback);assert.equal(await t.locator('.list-group a').filter({hasText:'Nhận xét đã sửa'}).count(),0);
        const pending=await api(s,'/api/v1/quizzes/'+quizId+'/attempts','POST');
        await go(t,'/teacher/quizzes/'+quizId);await save(t,'[data-delete]','/teacher/quizzes');
        await api(s,'/api/v1/quiz-attempts/'+pending.id+'/submit','POST',{answers:{}});
        assert.ok(!(await api(s,'/api/v1/quizzes')).some(q=>q.id===quizId));
        await go(t,'/teacher/lessons/'+lessonId);await save(t,'[data-delete]','/teacher/lessons');
        const report=await api(t,'/api/v1/teacher/classes/'+classId+'/students/'+studentId+'/report');
        assert.equal(Number(report.quizzes.find(q=>q.id===quizId).best_score),10);assert.equal(Number(report.lessons.find(l=>l.id===lessonId).completion_percent),100);
        await go(a,'/teacher/classes/new');await a.locator('#className').fill('Workspace '+stamp);await save(a,'[data-workspace-form] button','/teacher/classes');
        const newClass=(await a.locator('.list-group a').filter({hasText:'Workspace '+stamp}).getAttribute('href')).replace('/edulab','');
        await go(a,newClass+'/edit');await a.locator('#className').fill('Workspace updated '+stamp);await save(a,'[data-workspace-form] button','/teacher/classes');
        await go(a,newClass+'/teachers');await a.locator('#teacherId').selectOption({index:0});
        await Promise.all([a.waitForEvent('load'),a.locator('[data-workspace-form] button').click()]);
        assert.equal(await a.locator('[data-delete]').count(),1);
        await Promise.all([a.waitForEvent('load'),a.locator('[data-delete]').click()]);
        for(const width of [390,320]) {await a.setViewportSize({width,height:1000});for(const route of [newClass,newClass+'/edit',newClass+'/teachers','/teacher/classes/new']) {await go(a,route);assert.ok(await a.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}}
        await go(a,newClass);await save(a,'[data-delete]','/teacher/classes');await go(a,newClass);assert.equal(await a.locator('[data-delete]').count(),0);
        assert.equal((await t.request.get(base+'/teacher/classes/new')).status(),403);
        assert.equal((await s.request.get(base+'/api/v1/teacher/quizzes/'+quizId)).status(),403);
        assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',pages:routes.length,widths:[1440,768,390,320],lessonId,quizId,classId,studentId}));
    } finally {await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});