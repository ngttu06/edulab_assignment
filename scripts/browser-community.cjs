/* Local-only community regression. Creates test discussions, then hides them as admin. */
const {chromium} = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8086/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Community tests are local-only');
const out = process.env.EDULAB_SCREENSHOT_DIR || '.tools/screenshots/community';

(async () => {
    fs.mkdirSync(out,{recursive:true});
    const browser = await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const context = await browser.newContext({viewport:{width:1440,height:1000}, reducedMotion:'reduce', permissions:['clipboard-read','clipboard-write']});
    const page = await context.newPage();
    const created = [], errors = [], badAssets = [];
    const marker = 'Community browser test ' + Date.now();
    const imageFixture = process.env.EDULAB_IMAGE_FIXTURE_PATH ? JSON.parse(fs.readFileSync(process.env.EDULAB_IMAGE_FIXTURE_PATH,'utf8')) : null;
    page.on('pageerror',error => errors.push(error.message));
    page.on('console',message => { if (/Content Security Policy|Refused to|violates.*directive/i.test(message.text())) errors.push(message.text()); });
    page.on('response',response => { if (/\/(css|js)\//.test(response.url()) && response.status() >= 400) badAssets.push(response.url()); });
    const api = (path,options) => page.evaluate(async ({path,options}) => window.edulab.api(path,options),{path,options});
    async function login(name) {
        await page.goto(base+'/login');
        await page.locator('#username').fill(name);await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(url=>!url.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);
    }
    async function navigate(path) {
        const response=await page.goto(base+path,{waitUntil:'networkidle'});
        assert.equal(response.status(),200,path);
        assert.equal(await page.locator('h1').count(),1);
        assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No overflow: '+path);
    }
    try {
        await page.goto(base+'/forum');assert.ok(page.url().endsWith('/login'));
        assert.equal((await context.request.get(base+'/api/v1/forum/feed')).status(),401);
        await login('hocsinh');await navigate('/forum');
        const category=Number(await page.locator('#threadCategory option').first().getAttribute('value'));
        for (let i=0;i<14;i++) {
            const result=await api('/api/v1/forum/threads',{method:'POST',body:{categoryId:category,title:marker+' '+i,body:i===13?'<script>window.communityXss=true</script>\nA safe text-only question.':'How can I understand this lesson?',fileIds:imageFixture && [1,13].includes(i)?[imageFixture.id]:[],askAi:false}});
            created.push(result.id);
        }
        const id=created.at(-1);
        for(let i=0;i<12;i++) await api('/api/v1/forum/threads/'+id+'/posts',{method:'POST',body:{body:'Reply '+i,fileIds:[]}});
        await navigate('/forum');
        const card=page.locator(`.feed-card[data-id="${id}"]`);
        assert.equal(await page.locator('#communityFeed>.feed-card').count(),10,'JSP first page');
        const noJs=await browser.newContext({javaScriptEnabled:false,storageState:await context.storageState()});
        const noJsPage=await noJs.newPage();await noJsPage.goto(base+'/forum');
        const firstIds=await noJsPage.locator('#communityFeed>.feed-card').evaluateAll(cards=>cards.map(card=>card.dataset.id));
        await noJsPage.locator('.feed-pagination noscript a').click();
        const secondIds=await noJsPage.locator('#communityFeed>.feed-card').evaluateAll(cards=>cards.map(card=>card.dataset.id));
        assert.ok(secondIds.length>0 && secondIds.every(id=>!firstIds.includes(id)),'No-JavaScript cursor link avoids duplicates');
        await noJs.close();
        assert.equal(await page.evaluate(()=>window.communityXss),undefined);
        assert.ok((await card.locator('.feed-body').textContent()).includes('<script>'));
        if (imageFixture) {
            assert.ok(await card.locator('img').evaluate(image=>image.complete && image.naturalWidth>0),'Authorized inline image decodes');
            const response=await context.request.get(base+'/api/v1/files/'+imageFixture.id+'/image');
            assert.equal(response.status(),200);assert.equal(response.headers()['content-type'],'image/png');
            assert.ok(response.headers()['content-disposition'].startsWith('inline;'));
            assert.ok((await context.request.get(base+'/api/v1/files/'+imageFixture.id)).headers()['content-disposition'].startsWith('attachment;'));
            const guest=await browser.newContext();assert.equal((await guest.request.get(base+'/api/v1/files/'+imageFixture.id+'/image')).status(),401);await guest.close();
        }
        await card.locator('.feed-like').click();await page.waitForFunction(id=>document.querySelector(`.feed-card[data-id="${id}"] .feed-like`).getAttribute('aria-pressed')==='true',id);
        assert.equal(await card.locator('.feed-like-count').textContent(),'1');
        await navigate('/forum');assert.equal(await card.locator('.feed-like').getAttribute('aria-pressed'),'true');
        await card.locator('.feed-like').click();await page.waitForFunction(id=>document.querySelector(`.feed-card[data-id="${id}"] .feed-like`).getAttribute('aria-pressed')==='false',id);
        await card.locator('.feed-share').click();
        await page.waitForFunction(()=>navigator.clipboard.readText().then(text=>text.includes('/forum/threads/')));
        assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),base+'/forum/threads/'+id);
        await card.locator('.feed-comments-toggle').click();
        await page.waitForFunction(id=>document.querySelectorAll(`.feed-card[data-id="${id}"] .feed-comment`).length===10,id);
        await card.locator('.comments-more').click();
        await page.waitForFunction(id=>document.querySelectorAll(`.feed-card[data-id="${id}"] .feed-comment`).length===12,id);
        await card.locator('textarea').fill('Inline reply <b>remains text</b>');
        await card.locator('.feed-reply-form [type=submit]').click();
        await page.waitForFunction(id=>document.querySelectorAll(`.feed-card[data-id="${id}"] .feed-comment`).length===13,id);
        assert.equal(await card.locator('.feed-comment-count').textContent(),'13');
        assert.equal(await card.locator('.feed-comment b').count(),0);
        // Exercise dynamically cloned cards and their unique comment controls.
        await page.locator('#feedLoadMore').click();
        await page.waitForFunction(()=>document.querySelectorAll('#communityFeed>.feed-card').length>10);
        const ids=await page.locator('#communityFeed>.feed-card').evaluateAll(cards=>cards.map(card=>card.dataset.id));
        assert.equal(new Set(ids).size,ids.length);
        const dynamic=page.locator('#communityFeed>.feed-card').nth(10);
        if (imageFixture) assert.ok(await page.locator(`.feed-card[data-id="${created[1]}"] img`).evaluate(image=>image.complete && image.naturalWidth>0),'Dynamically loaded image decodes');
        await dynamic.locator('.feed-comments-toggle').click();assert.ok(await dynamic.locator('.feed-comments').isVisible());
        assert.equal(await dynamic.locator('.feed-comments-toggle').getAttribute('aria-controls'),await dynamic.locator('.feed-comments').getAttribute('id'));
        for(const width of [1440,1024,768,640,390,320]) {
            await page.setViewportSize({width,height:1000});await navigate('/');
            assert.ok(await page.locator('.home-statistics').isVisible());
            await page.screenshot({path:`${out}/home-${width}.png`,fullPage:true});
            await navigate('/forum');await card.locator('.feed-comments-toggle').click();
            await page.waitForFunction(id=>document.querySelectorAll(`.feed-card[data-id="${id}"] .feed-comment`).length===10,id);
            assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
            await page.evaluate(()=>scrollTo(0,0));
            await page.screenshot({path:`${out}/feed-${width}.png`,fullPage:true});
        }
        // Keyboard activation reaches an action with a visible focus treatment.
        await card.locator('.feed-like').focus();
        await page.keyboard.press('Tab');await page.keyboard.press('Shift+Tab');
        assert.ok(await card.locator('.feed-like').evaluate(el=>el.matches(':focus-visible')));
        await page.keyboard.press('Enter');
        await page.waitForFunction(id=>document.querySelector(`.feed-card[data-id="${id}"] .feed-like`).getAttribute('aria-pressed')==='true',id);
        await page.setViewportSize({width:1440,height:1000});
        // Test automatic cursor loading separately; reduced-motion mode above keeps manual paging deterministic.
        await page.emulateMedia({reducedMotion:'no-preference'});await navigate('/forum');
        await page.locator('#feedSentinel').scrollIntoViewIfNeeded();
        await page.waitForFunction(()=>document.querySelectorAll('#communityFeed>.feed-card').length>10);
        await api('/api/v1/auth/logout',{method:'POST'});await login('admin');
        await api('/api/v1/forum/threads/'+id+'/moderate',{method:'POST',body:{status:'LOCKED'}});
        await navigate('/forum');assert.ok(await card.locator('.feed-locked').isVisible());
        await card.locator('.feed-comments-toggle').click();assert.ok(await card.locator('.feed-reply-form').isHidden());
        await page.evaluate(()=>scrollTo(0,0));
        await page.screenshot({path:`${out}/feed-locked-1440.png`,fullPage:false});
        await navigate('/forum/threads/'+id);assert.ok(await page.locator('.moderateThread').count());
        // Composer keeps direct-upload and explicit AI consent boundaries; all R2 traffic here is mocked.
        await navigate('/forum');await page.locator('.composer-options summary').click();
        let puts=0, consent=false, submitted;
        const uploadUrl='https://c9da795e23235545fa54e8c7ee61d3cf.r2.cloudflarestorage.com/edulab/staging/community-browser-test';
        await page.route('**/api/v1/files/presign',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({uploadId:'community-mock',url:uploadUrl,headers:{'Content-Type':'image/png'},method:'PUT'})}));
        await page.route(uploadUrl,route=>{puts++;assert.equal(route.request().headers().cookie,undefined);assert.equal(route.request().headers().authorization,undefined);return route.fulfill({status:200});});
        await page.route('**/api/v1/files/uploads/community-mock/complete',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({fileId:123,scanStatus:'PENDING_SCAN',message:'Đang chờ quét'})}));
        await page.route('**/api/v1/forum/threads',route=>{
            submitted=route.request().postDataJSON();consent=submitted.askAi;
            return route.fulfill({contentType:'application/json',body:JSON.stringify({id,aiUnavailable:true})});
        });
        await page.locator('#threadFiles').setInputFiles({name:'question.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jX1sAAAAASUVORK5CYII=','base64')});
        assert.equal(await page.locator('#threadAi').isChecked(),false,'AI consent is off by default');
        await page.locator('#threadTitle').fill('Upload regression');await page.locator('#threadBody').fill('Image question');
        await page.locator('#threadLesson').selectOption(await page.locator('#threadLesson option').nth(1).getAttribute('value'));
        await page.locator('#threadAi').check();
        page.once('dialog',dialog=>dialog.accept());
        await Promise.all([page.waitForURL(base+'/forum/threads/'+id),page.locator('#threadForm [type=submit]').click()]);
        assert.equal(puts,1);assert.equal(consent,true);assert.deepEqual(submitted.fileIds,[123]);
        assert.equal(submitted.categoryId,Number(category));
        assert.deepEqual(errors,[]);assert.deepEqual(badAssets,[]);
        console.log('PASS: six viewports; SSR/cursor/auto loading; persistent likes; inline paged replies; safe text; clipboard; keyboard; locked posts; detail moderation; mocked direct upload and explicit AI consent.');
    } finally {
        if (created.length) {
            await api('/api/v1/auth/logout',{method:'POST'}).catch(()=>{});
            await login('admin');
            for (const id of created) await api('/api/v1/forum/threads/'+id+'/moderate',{method:'POST',body:{status:'HIDDEN'}});
        }
        await browser.close();
    }
})().catch(error=>{console.error(error);process.exit(1);});