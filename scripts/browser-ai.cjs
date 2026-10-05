/* Local-only chat/admin regression. Uses mocked chat responses unless EDULAB_AI_LIVE_TEST=true. */
const { chromium } = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8085/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Local-only: verify a disposable local database');
const out = process.env.EDULAB_SCREENSHOT_DIR || '.tools/screenshots/ai';
const live = process.env.EDULAB_AI_LIVE_TEST === 'true';
(async () => {
    fs.mkdirSync(out, {recursive:true});
    const browser = await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:true});
    try {
        const context = await browser.newContext({viewport:{width:1440,height:1000}});
        const page = await context.newPage();
        const errors = [], external = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('console', message => {if (/Content Security Policy|Refused to|violates.*directive/i.test(message.text())) errors.push(message.text());});
        page.on('request', request => {if (!request.url().startsWith(base) && !request.url().startsWith('data:')) external.push(request.url());});
        async function api(path, method = 'GET', body, csrf = true) {
            const headers = {Accept:'application/json'};
            if (method !== 'GET' && csrf) {
                const token = await (await context.request.get(base + '/api/v1/auth/csrf')).json();
                headers[token.headerName] = token.token;
            }
            return context.request.fetch(base + path, {method, headers, data:body});
        }
        await page.goto(base + '/');
        assert.equal((await api('/api/v1/admin/ai-settings')).status(), 401);
        assert.equal((await api('/api/v1/ai/support/chat','POST',{question:'Hi',history:[]},false)).status(),403);
        let requests = [];
        await page.route('**/api/v1/ai/support/chat', async route => {
            requests.push(route.request().postDataJSON());
            await new Promise(resolve => setTimeout(resolve, 150));
            await route.fulfill({contentType:'application/json',body:JSON.stringify({text:'Toán 8 <img src=x onerror=alert(1)> /courses/2',sample:false,
                links:[{label:'Toán 8',path:'/courses/2'},{label:'Malicious',path:'https://evil.test'}]})});
        });
        for (const width of [1440,1024,768,640,390,320]) {
            await page.setViewportSize({width,height:width<640?844:1000});
            await page.locator('#aiChatToggle').click();
            assert.equal(await page.locator('#aiChatToggle').getAttribute('aria-expanded'), 'true');
            assert.equal(await page.evaluate(()=>document.activeElement.id), 'aiChatQuestion');
            assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`No horizontal overflow ${width}`);
            const bounds=await page.locator('#aiChatDialog').boundingBox();
            assert.ok(bounds.x>=0 && bounds.y>=0 && bounds.x+bounds.width<=width+1);
            await page.screenshot({path:`${out}/chat-${width}.png`});
            await page.keyboard.press('Escape');
            await page.waitForFunction(()=>document.getElementById('aiChatToggle').getAttribute('aria-expanded')==='false');
            assert.equal(await page.locator('#aiChatToggle').getAttribute('aria-expanded'), 'false');
            assert.equal(await page.evaluate(()=>document.activeElement.id), 'aiChatToggle');
        }
        await page.locator('#aiChatToggle').click();
        await page.locator('#aiChatQuestion').fill('Tư vấn Toán 8');
        await page.keyboard.press('Enter');
        await page.locator('.ai-chat-message.assistant').nth(1).waitFor();
        assert.equal(requests.length,1);
        assert.equal(requests[0].history.length,0);
        assert.equal(await page.locator('.ai-chat-message img').count(),0,'AI HTML is displayed as text');
        assert.equal(await page.locator('.ai-chat-links a').count(),1,'External links rejected');
        await page.locator('#aiChatQuestion').fill('Tôi muốn học thêm');
        await page.locator('#aiChatSend').click();
        await page.locator('.ai-chat-message.assistant').nth(2).waitFor();
        assert.equal(requests[1].history.length,2);
        await page.locator('#aiChatReset').click();
        assert.equal(await page.locator('.ai-chat-message').count(),1);
        await page.unroute('**/api/v1/ai/support/chat');
        await page.route('**/api/v1/ai/support/chat', route=>route.fulfill({status:429,contentType:'application/json',body:'{"message":"Hết hạn mức thử nghiệm"}'}));
        await page.locator('#aiChatQuestion').fill('Câu hỏi giữ lại');
        await page.locator('#aiChatSend').click();
        await page.waitForFunction(()=>document.getElementById('aiChatStatus').textContent.includes('Hết hạn mức'));
        assert.equal(await page.locator('#aiChatQuestion').inputValue(),'Câu hỏi giữ lại');
        assert.ok(await page.locator('#aiChatSend').isEnabled());
        await page.locator('#aiChatClose').click();
        await page.goto(base + '/courses');
        await page.setViewportSize({width:390,height:844});
        await page.locator('#menuToggle').click();
        assert.ok(await page.evaluate(()=>document.querySelector('.ai-widget').inert),'Chat cannot overlap open mobile navigation');
        await page.keyboard.press('Escape');
        assert.ok(await page.locator('#aiChatToggle').isEnabled());
        for(const path of ['/login','/register']) {
            await page.goto(base+path);
            assert.equal(await page.locator('#aiChatToggle').count(),1);
            await page.locator('#aiChatToggle').click();await page.keyboard.press('Escape');
        }
        assert.equal((await api('/api/v1/auth/login','POST',{username:'admin',password:'ChangeMe123!'})).status(),200);
        await page.goto(base+'/admin');
        await page.waitForFunction(()=>!document.getElementById('aiSettingsFields').disabled);
        const configuration=await (await api('/api/v1/admin/ai-settings')).json();
        assert.equal(configuration.apiKey,undefined);
        assert.equal(await page.locator('#aiApiKey').inputValue(),'');
        assert.equal(configuration.model,'gpt-6.1-sol');
        await page.locator('#aiSettingsForm [type=submit]').click();
        await page.waitForFunction(()=>document.getElementById('aiSettingsNotice').textContent.includes('Đã lưu'));
        assert.equal(await page.locator('#aiApiKey').inputValue(),'');
        for(const width of [1440,768,390,320]) {
            await page.setViewportSize({width,height:1000});
            assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`Admin fits ${width}`);
            await page.screenshot({path:`${out}/admin-${width}.png`});
        }
        if (live) {
            await page.unroute('**/api/v1/ai/support/chat');
            const guest=await browser.newContext({viewport:{width:1440,height:1000}});
            const guestPage=await guest.newPage();
            guestPage.on('pageerror',error=>errors.push(error.message));
            await guestPage.goto(base+'/');await guestPage.locator('#aiChatToggle').click();
            await guestPage.locator('#aiChatQuestion').fill('Tôi học lớp 8, muốn củng cố Toán. Website có khóa nào phù hợp và học thế nào? Trả lời ngắn.');
            const response=guestPage.waitForResponse(r=>r.url().endsWith('/api/v1/ai/support/chat'),{timeout:65000});
            await guestPage.locator('#aiChatSend').click();
            const result=await response;
            assert.equal(result.status(),200,'Real provider returns success');
            const answer=await result.json();assert.ok(answer.text.length>10);assert.equal(answer.sample,false);
            await guestPage.locator('.ai-chat-message.assistant').nth(1).waitFor();
            await guestPage.screenshot({path:`${out}/chat-live.png`});
            console.log('Real guest chat passed:',answer.text.length,'characters; links:',answer.links.map(l=>l.path).join(', '));
            await guest.close();
        }
        // Separate student context avoids invalidating other existing user sessions.
        const student=await browser.newContext();
        let token=await (await student.request.get(base+'/api/v1/auth/csrf')).json();
        assert.equal((await student.request.post(base+'/api/v1/auth/login',{headers:{[token.headerName]:token.token},data:{username:'hocsinh',password:'ChangeMe123!'}})).status(),200);
        assert.equal((await student.request.get(base+'/api/v1/admin/ai-settings')).status(),403);
        const lessonPage=await student.newPage();
        lessonPage.on('pageerror',error=>errors.push(error.message));
        lessonPage.on('console',message=>{if (/Content Security Policy|Refused to|violates.*directive/i.test(message.text())) errors.push(message.text());});
        const lessons=await (await student.request.get(base+'/api/v1/lessons')).json();
        assert.ok(lessons.length>0);
        let lessonRequest;
        await lessonPage.route('**/api/v1/ai/questions',route=>{
            lessonRequest=route.request().postDataJSON();
            return route.fulfill({contentType:'application/json',body:JSON.stringify({provider:'gatecheap',sample:false,
                text:'Gợi ý <img src=x onerror=alert(1)>',sourceLabels:[lessons[0].title],needsTeacherReview:true})});
        });
        await lessonPage.goto(base+'/lessons/'+lessons[0].id);
        for(const width of [1440,390,320]) {
            await lessonPage.setViewportSize({width,height:width<640?844:1000});
            assert.ok(await lessonPage.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`Lesson fits ${width}`);
            await lessonPage.locator('#aiChatToggle').click();await lessonPage.keyboard.press('Escape');
        }
        await lessonPage.locator('#aiQuestion').fill('Gợi ý bước đầu');
        await lessonPage.locator('#aiForm button[type=submit]').click();
        await lessonPage.waitForFunction(()=>document.getElementById('aiAnswer').textContent.includes('AI HỖ TRỢ'));
        assert.equal(lessonRequest.lessonId,lessons[0].id);
        assert.equal(lessonRequest.question,'Gợi ý bước đầu');
        assert.equal(await lessonPage.locator('#aiAnswer img').count(),0);
        assert.ok((await lessonPage.locator('#aiAnswer').textContent()).includes(lessons[0].title));
        await lessonPage.screenshot({path:`${out}/lesson-ai-320.png`,fullPage:true});
        await lessonPage.unroute('**/api/v1/ai/questions');
        const saved={enabled:configuration.enabled,baseUrl:configuration.baseUrl,model:configuration.model,apiKey:'',clearApiKey:false,
            dailyLimit:configuration.dailyLimit,globalDailyLimit:configuration.globalDailyLimit,maxOutputTokens:configuration.maxOutputTokens};
        try {
            assert.equal((await api('/api/v1/admin/ai-settings','PUT',{...saved,enabled:false})).status(),200);
            assert.equal((await (await student.request.get(base+'/api/v1/ai/support/status')).json()).enabled,false);
            token=await (await student.request.get(base+'/api/v1/auth/csrf')).json();
            assert.equal((await student.request.post(base+'/api/v1/ai/support/chat',{headers:{[token.headerName]:token.token},data:{question:'Hi',history:[]}})).status(),503);
            await lessonPage.locator('#aiForm button[type=submit]').click();
            await lessonPage.waitForFunction(()=>document.getElementById('aiAnswer').textContent.includes('đang tắt'));
            assert.ok(await lessonPage.locator('#aiForm button[type=submit]').isEnabled());
            await lessonPage.goto(base+'/forum');
            assert.equal(await lessonPage.locator('#aiChatToggle').count(),1);
            await lessonPage.locator('#threadTitle').fill('ai_disabled_regression_'+Date.now());
            await lessonPage.locator('#threadBody').fill('Câu hỏi kiểm thử local; AI tắt vẫn giữ bài đăng.');
            await lessonPage.locator('#threadLesson').selectOption(String(lessons[0].id));
            await lessonPage.locator('#threadAi').check();
            let alerted=false;
            lessonPage.once('dialog',async dialog=>{alerted=dialog.message().includes('AI chưa sẵn sàng');await dialog.accept();});
            const created=lessonPage.waitForResponse(r=>r.url().endsWith('/api/v1/forum/threads') && r.request().method()==='POST');
            await lessonPage.locator('#threadForm button[type=submit]').click();
            const createdResponse=await created;assert.equal(createdResponse.status(),200);
            const thread=await createdResponse.json();assert.equal(thread.aiUnavailable,true);
            await lessonPage.waitForURL(base+'/forum/threads/'+thread.id);
            assert.ok(alerted,'Forum warns without losing the created thread');
            assert.ok((await lessonPage.locator('.forum-body').first().textContent()).includes('AI tắt vẫn giữ bài đăng'));
            await lessonPage.screenshot({path:`${out}/forum-ai-disabled-320.png`,fullPage:true});
            console.log('Disposable AI-disabled forum thread:',thread.id);
        } finally {assert.equal((await api('/api/v1/admin/ai-settings','PUT',saved)).status(),200,'Restore AI settings');}
        await student.close();
        assert.deepEqual(errors,[],'No JavaScript/CSP failures');assert.deepEqual(external,[],'No browser requests to AI host');
        console.log('Chat/admin regression passed: 6 viewport widths, focus/Escape, guest CSRF, safe rendering, history/reset, errors, mobile menu, auth layouts and admin access.');
        console.log('Lesson/forum regression passed: safe lesson rendering, public widget, runtime disable, disabled guest/lesson rejection and forum fallback. No paid requests unless live opt-in.');
    } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});