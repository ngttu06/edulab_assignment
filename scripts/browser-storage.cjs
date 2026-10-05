/* Local-only storage regression; mock network by default. LIVE performs a real private R2 sample upload. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const base=process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8085/edulab';
if(!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Only disposable loopback application allowed');
const live=process.env.EDULAB_STORAGE_LIVE_TEST==='true';
const out=process.env.EDULAB_SCREENSHOT_DIR || '/Users/ngttu/Workspace/project_asm_prj/.tools/screenshots/storage';
(async()=>{
    fs.mkdirSync(out,{recursive:true});
    const browser=await chromium.launch({headless:true,executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
    try {
        const context=await browser.newContext(),page=await context.newPage(),errors=[];
        page.on('pageerror',e=>errors.push(e.message));
        async function api(path,method='GET',data) {
            const headers={Accept:'application/json'};
            if(method!=='GET'){const t=await(await context.request.get(base+'/api/v1/auth/csrf')).json();headers[t.headerName]=t.token;}
            return context.request.fetch(base+path,{method,headers,data});
        }
        assert.equal((await api('/api/v1/admin/storage-settings')).status(),401);
        assert.equal((await context.request.post(base+'/api/v1/files/presign',{data:{filename:'a.png',contentType:'image/png',size:1}})).status(),403);
        assert.equal((await api('/api/v1/auth/login','POST',{username:'admin',password:'ChangeMe123!'})).status(),200);
        await page.goto(base+'/admin');await page.waitForFunction(()=>!document.getElementById('storageFields').disabled);
        const settings=await(await api('/api/v1/admin/storage-settings')).json();
        assert.equal(settings.secretKey,undefined);assert.equal(settings.accessKey,undefined);
        assert.equal(await page.locator('#storageSecretKey').inputValue(),'');
        assert.equal(await page.locator('#storageBucket').inputValue(),'edulab');
        for(const width of [1440,1024,768,640,390,320]) {
            await page.setViewportSize({width,height:1000});
            assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Admin fits '+width);
            await page.screenshot({path:out+'/admin-'+width+'.png'});
        }
        if(live){
            await page.locator('#storageTest').click();await page.waitForFunction(()=>!document.getElementById('storageFields').disabled,null,{timeout:60000});
            assert.match(await page.locator('#storageNotice').textContent(),/thành công/);console.log('Live browser PUT, CORS, HEAD, GET and DELETE probe passed.');
        }
        assert.equal((await api('/api/v1/auth/login','POST',{username:'hocsinh',password:'ChangeMe123!'})).status(),200);
        assert.equal((await api('/api/v1/admin/storage-settings')).status(),403);
        assert.equal((await api('/api/v1/files','POST')).status(),410);
        await page.goto(base+'/lessons');const lesson=await page.locator('a[href*="/lessons/"]').first().getAttribute('href');
        await page.goto(new URL(lesson,base).href);
        let puts=0,questions=0;const uploadUrl=settings.endpoint+'/edulab/edulab/staging/browser-mock';
        if(!live){
            await page.route('**/api/v1/files/presign',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({uploadId:'mock-upload',url:uploadUrl,headers:{'Content-Type':'image/png'},method:'PUT'})}));
            await page.route(uploadUrl,route=>{puts++;assert.equal(route.request().method(),'PUT');assert.equal(route.request().headers()['cookie'],undefined);return route.fulfill({status:200,headers:{'Access-Control-Allow-Origin':new URL(base).origin}});});
            await page.route('**/api/v1/files/uploads/mock-upload/complete',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({fileId:123,scanStatus:'CLEAN',status:'COMPLETED',message:'File sẵn sàng'})}));
            await page.route('**/api/v1/ai/questions',route=>{questions++;assert.deepEqual(route.request().postDataJSON().fileIds,[123]);return route.fulfill({contentType:'application/json',body:JSON.stringify({text:'Đã nhận bài thử',sourceLabels:['Bài học'],sample:false})});});
        } else {
            // No provider charge: verify file IDs reach the AI boundary, mock only the AI answer.
            await page.route('**/api/v1/ai/questions',async route=>{
                questions++;assert.equal(route.request().postDataJSON().fileIds.length,1);
                const id=route.request().postDataJSON().fileIds[0];const download=await api('/api/v1/files/'+id);
                assert.equal(download.status(),404,'No scanner => quarantine; never serve live sample');
                await route.fulfill({status:409,contentType:'application/json',body:'{"message":"File mẫu đang cách ly; AI chưa gọi thật"}'});
            });
        }
        await page.locator('#aiFiles').setInputFiles({name:'exercise.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jW7sAAAAASUVORK5CYII=','base64')});
        await page.locator('#aiQuestion').fill('Giải thích bài trong ảnh mẫu');
        await page.locator('#aiForm button[type=submit]').click();
        await page.waitForFunction(()=>!document.querySelector('#aiForm button[type=submit]').disabled,null,{timeout:90000});
        assert.equal(questions,1,'File uploaded and included at AI boundary');
        if(!live){
            assert.equal(puts,1);assert.match(await page.locator('#aiAnswer').textContent(),/Đã nhận/);
            await page.locator('#aiForm button[type=submit]').click();await page.waitForFunction(()=>!document.querySelector('#aiForm button[type=submit]').disabled);
            assert.equal(puts,1,'AI retry reuses already uploaded file');assert.equal(questions,2);
            await page.unroute(uploadUrl);
            await page.route(uploadUrl,route=>route.fulfill({status:403,headers:{'Access-Control-Allow-Origin':new URL(base).origin}}));
            await page.route('**/api/v1/files/uploads/mock-upload',route=>route.fulfill({contentType:'application/json',body:'{"status":"CANCELLED"}'}));
            await page.locator('#aiFiles').setInputFiles({name:'retry.png',mimeType:'image/png',buffer:Buffer.from([137,80,78,71,13,10,26,10,1])});
            await page.locator('#aiForm button[type=submit]').click();await page.waitForFunction(()=>!document.querySelector('#aiForm button[type=submit]').disabled);
            assert.match(await page.locator('#aiAnswer').textContent(),/từ chối upload/);
            assert.equal(questions,2,'Failed PUT never asks AI');
            await page.unroute(uploadUrl);
            await page.route(uploadUrl,route=>route.fulfill({status:200,headers:{'Access-Control-Allow-Origin':new URL(base).origin}}));
            await page.locator('#aiForm button[type=submit]').click();await page.waitForFunction(()=>!document.querySelector('#aiForm button[type=submit]').disabled);
            assert.equal(questions,3,'Fresh URL lets user recover from upload failure');
            console.log('Retry avoids duplicate PUT; rejected PUT cancels reservation and never calls AI; fresh retry succeeds.');
        }
        else assert.match(await page.locator('#aiAnswer').textContent(),/cách ly/);
        for(const width of [1440,390,320]) {
            await page.setViewportSize({width,height:1000});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:out+'/lesson-'+width+'.png'});
        }
        await page.goto(base+'/forum');
        for(const width of [1440,390,320]){await page.setViewportSize({width,height:1000});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
        assert.equal(await page.locator('#threadFiles').count(),1);
        assert.deepEqual(errors,[]);
        console.log('Storage regression passed: admin permissions/secrets, responsive UI, direct PUT and attachment IDs'+(live?' (live R2; AI mocked, scan bypass OFF).':' (mock network).'));
    } finally {await browser.close();}
})().catch(error=>{console.error(String(error.message).replace(/https:\/\/[^\s]+/g,'[URL redacted]'));process.exitCode=1;});