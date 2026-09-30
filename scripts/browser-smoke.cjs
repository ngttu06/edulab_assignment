/* Test tooling only. Run with Playwright installed outside production dependencies. */
const { chromium } = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8080/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Smoke tests are local-only');
const out = process.env.EDULAB_SCREENSHOT_DIR || '.tools/screenshots';

(async () => {
    fs.mkdirSync(out,{recursive:true});
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[];
    const context=await browser.newContext({viewport:{width:1440,height:1000}});
    const page=await context.newPage();
    page.on('pageerror',e=>errors.push(e.message));
    async function navigate(path) {
        const response=await page.goto(base+path,{waitUntil:'networkidle'});
        assert.equal(response.status(),200,`Page ${path}`);
        assert.equal(await page.locator('h1').count()>0,true,`Heading ${path}`);
    }
    async function api(path,method='GET',body) {
        return page.evaluate(async ({path,method,body})=>window.edulab.api(path,{method,body}),{path,method,body});
    }
    async function login(name) {
        await navigate('/login');
        await page.locator('#username').fill(name);await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(url=>!url.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);
    }
    async function logout(){await api('/api/v1/auth/logout','POST');}
    await navigate('/');await page.screenshot({path:out+'/home-desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});await navigate('/');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,'Mobile has no horizontal overflow');
    await page.screenshot({path:out+'/home-mobile.png',fullPage:true});
    await page.setViewportSize({width:1440,height:1000});
    await navigate('/register');
    const anon=await context.request.get(base+'/api/v1/auth/me');assert.equal(anon.status(),401);
    await login('hocsinh');
    for(const path of ['/dashboard','/lessons','/roadmap','/quizzes','/forum','/family'])await navigate(path);
    await page.screenshot({path:out+'/family.png',fullPage:true});
    const lessons=await api('/api/v1/lessons');assert.ok(lessons.length>=3);
    await navigate('/lessons/'+lessons[0].id);
    await page.locator('#aiQuestion').fill('Hãy giải thích một ví dụ.');await page.locator('#aiForm button').click();
    await page.waitForFunction(()=>document.getElementById('aiAnswer').textContent.includes('đang tắt'));
    await page.screenshot({path:out+'/lesson.png',fullPage:true});
    const quizzes=await api('/api/v1/quizzes');
    let quiz,attempt;
    for(const candidate of quizzes.filter(q=>q.mode==='PRACTICE')) {
        try {attempt=await api('/api/v1/quizzes/'+candidate.id+'/attempts','POST');quiz=candidate;break;}
        catch(error) {if(!error.message.includes('hết lượt'))throw error;}
    }
    assert.ok(quiz,'A practice quiz with attempts remaining is required; use a fresh local test database if exhausted');
    await navigate('/quizzes/attempts/'+attempt.id);
    if(attempt.status==='IN_PROGRESS') {
        await page.locator('#quizForm input[type="radio"]').first().check();
        await page.waitForFunction(()=>document.getElementById('quizNotice').textContent.includes('Đã lưu'));
        const saved=await api('/api/v1/quiz-attempts/'+attempt.id);assert.ok(saved.questions.some(q=>q.selectedOption));
        assert.ok(saved.questions.every(q=>q.explanation===null&&q.options.every(o=>o.correct===null)));
        await page.reload({waitUntil:'networkidle'});assert.ok(await page.locator('input[type="radio"]:checked').count()>0);
        await page.locator('#quizForm button[type="submit"]').click();
        await page.waitForFunction(()=>document.getElementById('quizForm').dataset.status==='GRADED');
    }
    const code=await api('/api/v1/family/codes','POST');
    const category=await page.evaluate(async()=>{const response=await fetch(window.edulab.context+'/forum');const html=await response.text();const doc=new DOMParser().parseFromString(html,'text/html');return Number(doc.getElementById('threadForm').dataset.category);});
    const thread=await api('/api/v1/forum/threads','POST',{categoryId:category,title:'Câu hỏi kiểm thử trình duyệt',body:'Làm thế nào để ôn tập?',askAi:false,lessonId:null});
    await navigate('/forum/threads/'+thread.id);await page.screenshot({path:out+'/forum.png',fullPage:true});
    assert.equal((await context.request.get(base+'/admin')).status(),403);
    const student=(await api('/api/v1/auth/me')).id;
    await api('/api/v1/auth/refresh','POST');await logout();
    await login('phuhuynh');await api('/api/v1/family/links','POST',{code:code.code});
    await navigate('/school/students/'+student);
    const forbidden=await context.request.get(base+'/api/v1/school/students/999999');assert.equal(forbidden.status(),403);
    await logout();await login('giaovien');await navigate('/teacher/classes');
    const classLink=await page.locator('main a[href*="/teacher/classes/"]').first().getAttribute('href');
    const classPage=await page.goto(new URL(classLink,base).href,{waitUntil:'networkidle'});
    assert.equal(classPage.status(),200,'Teacher class assignment page');
    assert.equal(await page.locator('#assignmentForm').count(),1);
    const due=new Date(Date.now()+3600_000).toISOString();
    const classId=Number(await page.locator('#assignmentForm').getAttribute('data-id'));
    await api('/api/v1/teacher/classes/'+classId+'/assignments','POST',{quizId:quiz.id,dueAt:due});
    await page.reload({waitUntil:'networkidle'});assert.ok(await page.locator('main').innerText().then(text=>text.includes(quiz.title)));
    await navigate('/school/students/'+student);await page.locator('#feedbackBody').fill('Nhận xét kiểm thử: tiếp tục luyện tập.');
    await Promise.all([page.waitForEvent('load'),page.locator('#feedbackForm button').click()]);
    await page.waitForLoadState('networkidle');
    assert.ok((await page.locator('main').innerText()).includes('Nhận xét kiểm thử: tiếp tục luyện tập.'));
    await logout();await login('hocsinh');await navigate('/roadmap');await navigate('/school/students/'+student);
    assert.ok((await page.locator('main').innerText()).includes('Cần hoàn thành'),'Student sees newly assigned quiz');
    await page.screenshot({path:out+'/assignments-student.png',fullPage:true});
    await logout();await login('phuhuynh');await navigate('/school/students/'+student);
    assert.ok((await page.locator('main').innerText()).includes(quiz.title),'Parent sees assigned quiz');
    await page.screenshot({path:out+'/assignments-parent.png',fullPage:true});
    await logout();await login('admin');await navigate('/admin');await page.screenshot({path:out+'/admin.png',fullPage:true});
    assert.equal(await page.locator('.adminForm').count(),4);
    await navigate('/teacher/classes');await logout();
    assert.deepEqual(errors,[],'No browser JavaScript errors');
    await browser.close();console.log('PASS: desktop/mobile, four roles, quiz autosave/submission, family, class assignment, forum, admin, refresh and access controls.');
})().catch(error=>{console.error(error.message);process.exit(1);});