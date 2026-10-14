/* End-to-end on a disposable loopback deployment only. AI responses in UI paths are mocked. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const base=process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8092/edulab';
if(!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base) || process.env.EDULAB_DISPOSABLE_TEST_DB!=='true') throw Error('Requires disposable local test database');
(async()=>{
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],stamp=Date.now();
    const screenshots=process.env.EDULAB_SCREENSHOT_DIR || '/tmp/edulab-assessment-screenshots';fs.mkdirSync(screenshots,{recursive:true});
    const api=(page,path,method='GET',body)=>page.evaluate(({path,method,body})=>window.edulab.api(path,{method,body}),{path,method,body});
    async function login(name) {
        const page=await (await browser.newContext({viewport:{width:1440,height:1000}})).newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
        await page.goto(base+'/login');await page.locator('#username').fill(name);await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(u=>!u.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);return page;
    }
    try {
        const teacher=await login('giaovien'),student=await login('hocsinh');
        await teacher.goto(base+'/teacher/quizzes/new?mode=TEST');assert.equal(await teacher.locator('#quizMode').inputValue(),'TEST');
        const classId=Number(await teacher.locator('[name=quizClass]').first().getAttribute('value'));
        const questions=[0,1,2].map(i=>({content:'Câu '+(i+1),explanation:'Giải thích '+(i+1),options:['Đúng','Sai'],correctIndex:0,points:1}));
        async function create(mode) {return (await api(teacher,'/api/v1/teacher/quizzes','POST',{title:'Assessment '+mode+' '+stamp,durationMinutes:15,maxAttempts:3,mode,revealAfter:mode==='TEST'?new Date(Date.now()+7200000).toISOString():null,published:true,classIds:[classId],questions})).id;}
        const quiz=await create('PRACTICE'),attempt=await api(student,'/api/v1/quizzes/'+quiz+'/attempts','POST');
        await student.route('**/api/v1/quiz-attempts/*/ai-review',r=>r.fulfill({json:{text:'Bạn cần ôn lại câu sai.',sample:false}}));
        await student.route('**/api/v1/quiz-attempts/*/questions/*/ai-explanation',r=>r.fulfill({json:{text:'Đáp án đúng vì dữ kiện trong câu hỏi.',sample:false}}));
        await student.goto(base+'/quizzes/attempts/'+attempt.id);
        await student.locator('#option-'+attempt.questions[0].options[0].id).check();await student.locator('#option-'+attempt.questions[1].options[1].id).check();
        await Promise.all([student.waitForEvent('load'),student.locator('#quizForm button[type=submit]').click()]);
        assert.equal(await student.locator('[data-outcome=CORRECT]').count(),1);assert.equal(await student.locator('[data-outcome=WRONG]').count(),1);assert.equal(await student.locator('[data-outcome=UNANSWERED]').count(),1);
        assert.equal(await student.locator('.ai-widget').count(),0);assert.equal(await student.locator('.form-check-label').first().evaluate(el=>getComputedStyle(el).opacity),'1');
        await student.locator('#questionFilter').selectOption('WRONG');assert.equal(await student.locator('.assessment-question:visible').count(),1);
        await student.locator('.askQuestionAi:visible').click();await student.getByText('Đáp án đúng vì dữ kiện trong câu hỏi.').waitFor();await student.getByText('Bạn cần ôn lại câu sai.').waitFor();
        await teacher.goto(base+'/teacher/quizzes/'+quiz+'/report');assert.equal(await teacher.locator('#scoreChart rect').count(),10);await teacher.getByRole('link',{name:'Xem bài làm'}).first().click();
        assert.equal(await teacher.locator('.askQuestionAi').count(),0);assert.equal(await teacher.locator('[data-outcome=WRONG]').count(),1);
        const csv=await teacher.request.get(base+'/teacher/quizzes/'+quiz+'/report.csv?classId='+classId);assert.equal(csv.status(),200);assert.match(csv.headers()['content-type'],/text\/csv/);assert.match(await csv.text(),/Số lần rời tab/);
        const test=await create('TEST'),testAttempt=await api(student,'/api/v1/quizzes/'+test+'/attempts','POST');
        await student.goto(base+'/quizzes/attempts/'+testAttempt.id);
        await student.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'));});
        await student.waitForFunction(()=>document.getElementById('tabWarning').textContent.includes('1 lần'));
        await student.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>false});document.dispatchEvent(new Event('visibilitychange'));});
        await Promise.all([student.waitForEvent('load'),student.locator('#quizForm button[type=submit]').click()]);
        assert.equal(await student.locator('.askQuestionAi').count(),0);assert.equal(await student.locator('[data-outcome=HIDDEN]').count(),3);
        const csrf=await (await student.request.get(base+'/api/v1/auth/csrf')).json();
        const locked=await student.request.post(base+'/api/v1/quiz-attempts/'+testAttempt.id+'/questions/'+testAttempt.questions[0].id+'/ai-explanation',{headers:{[csrf.headerName]:csrf.token}});assert.equal(locked.status(),409);
        await teacher.goto(base+'/teacher/quiz-attempts/'+testAttempt.id);assert.match(await teacher.locator('summary').first().innerText(),/1/);
        const response=await student.request.get(base+'/api/v1/teacher/quiz-attempts/'+testAttempt.id);assert.equal(response.status(),403);
        await teacher.goto(base+'/teacher/quizzes/import');
        const imported={id:'fixture',filename:'de.pdf',warnings:[],missingCount:1,questions:[{content:'Câu từ PDF',options:['A','B'],correctIndex:null,explanation:'',points:1}]};
        await teacher.route('**/api/v1/teacher/quizzes/document-import',r=>r.fulfill({json:imported}));
        await teacher.route('**/api/v1/teacher/quizzes/document-import/fixture/generate-answers',r=>r.fulfill({json:{...imported,missingCount:0,questions:[{...imported.questions[0],correctIndex:1,explanation:'B đúng',aiGenerated:true}]}}));
        await teacher.locator('#quizDocument').setInputFiles({name:'de.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4')});await teacher.locator('#importDocument').click();
        await teacher.locator('#missingAnswers:not([hidden])').waitFor();assert.equal(await teacher.locator('[data-field=correctIndex]').inputValue(),'');
        await teacher.locator('#generateAnswers').click();await teacher.locator('[data-ai-review]:not([hidden])').waitFor();assert.equal(await teacher.locator('[data-field=correctIndex]').inputValue(),'1');
        await teacher.locator('#quizTitle').fill('Imported '+stamp);await teacher.locator('[name=quizClass]').first().check();await teacher.locator('#teacherQuizForm button[type=submit]').click();
        await teacher.waitForFunction(()=>document.getElementById('authorNotice').textContent.includes('cần kiểm tra và duyệt'));
        await teacher.locator('[data-reviewed]').check();await Promise.all([teacher.waitForURL(/\/teacher\/quizzes\/\d+$/),teacher.locator('#teacherQuizForm button[type=submit]').click()]);
        for(const width of [1440,768,390,320]) {
            await teacher.setViewportSize({width,height:1000});await student.setViewportSize({width,height:1000});
            for(const path of ['/teacher/quizzes/'+quiz+'/report','/teacher/quiz-attempts/'+testAttempt.id,'/teacher/quizzes/new?mode=TEST','/teacher/quizzes/import']) {
                assert.equal((await teacher.goto(base+path)).status(),200);assert.ok(await teacher.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Overflow '+width+' '+path);
                if(width===1440 || width===320) await teacher.screenshot({path:require('node:path').join(screenshots,width+'-'+path.replaceAll('/','-').replace('?','-')+'.png'),fullPage:true});
            }
            for(const path of ['/quizzes/history','/quizzes/attempts/'+attempt.id,'/quizzes/attempts/'+testAttempt.id]) {
                assert.equal((await student.goto(base+path)).status(),200);assert.ok(await student.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Overflow '+width+' '+path);
                if(width===1440 || width===320) await student.screenshot({path:require('node:path').join(screenshots,width+'-'+path.replaceAll('/','-')+'.png'),fullPage:true});
            }
        }
        assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',quiz,test,widths:[1440,768,390,320],ai:'mocked'}));
    } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});