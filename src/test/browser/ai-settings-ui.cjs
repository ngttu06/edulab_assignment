/* Actual admin JSP + JS/CSS; APIs intercepted, no app/DB/provider/AI calls. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../..'),assets=path.join(root,'src/main/resources/static');
const html=fs.readFileSync(path.join(root,'target/ai-ui-fixtures/settings.html'));
const origin='https://ai.test',base='/edulab/api/v1/admin/ai-settings';
async function main() {
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],unexpected=[],calls=[];
    let config={enabled:true,baseUrl:'https://provider.test/v1',model:'old-model',keyConfigured:true,encryptionReady:true,dailyLimit:20,globalDailyLimit:200,maxOutputTokens:1000,systemPrompt:'Giải thích từng bước. <script>window.promptXss=true</script>',supportSystemPrompt:'Hỗ trợ website ngắn gọn.'};
    let failModels=false,delayModels=false;
    try {
        const context=await browser.newContext({viewport:{width:1440,height:1000},serviceWorkers:'block'});
        await context.route('**/*',async route=>{
            const request=route.request(),url=new URL(request.url()),pathname=url.pathname;
            if(url.origin!==origin) {unexpected.push(url.href);return route.abort();}
            if(pathname==='/edulab/admin/settings/ai') return route.fulfill({contentType:'text/html',body:html,headers:{'Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'"}});
            if(pathname==='/edulab/api/v1/auth/csrf') return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'fixture'}});
            if(pathname.startsWith(base)) {
                const body=request.postDataJSON();calls.push({pathname,method:request.method(),body});
                if(request.method()!=='GET') assert.equal(request.headers()['x-csrf-token'],'fixture');
                if(pathname===base && request.method()==='GET') return route.fulfill({json:config});
                if(pathname===base && request.method()==='PUT') {
                    config={...config,...body,systemPrompt:body.systemPrompt || 'DEFAULT LESSON',supportSystemPrompt:body.supportSystemPrompt || 'DEFAULT SUPPORT'};
                    delete config.apiKey;delete config.clearApiKey;return route.fulfill({json:config});
                }
                if(pathname===base+'/models') {
                    if(delayModels) await new Promise(resolve=>setTimeout(resolve,400));
                    return failModels ? route.fulfill({status:503,json:{message:'Provider không hỗ trợ /models.'}}) : route.fulfill({json:{models:['a-model','z-model']}});
                }
            }
            const file=path.resolve(assets,pathname.replace(/^\/edulab\//,''));
            if(file.startsWith(assets+path.sep) && fs.existsSync(file) && fs.statSync(file).isFile())
                return route.fulfill({body:fs.readFileSync(file),contentType:{'.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml'}[path.extname(file)] || 'application/octet-stream'});
            unexpected.push(pathname);return route.abort();
        });
        const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
        await page.goto(origin+'/edulab/admin/settings/ai');await page.waitForFunction(()=>!document.getElementById('aiSettingsFields').disabled);
        assert.equal(await page.locator('#aiSystemPrompt').inputValue(),config.systemPrompt);
        assert.equal(await page.evaluate(()=>window.promptXss),undefined);
        await page.locator('#aiFetchModels').click();await page.waitForFunction(()=>!document.getElementById('aiModelList').disabled);
        assert.equal(calls.at(-1).body.apiKey,'');assert.equal(calls.at(-1).body.baseUrl,config.baseUrl);
        assert.equal(await page.locator('#aiModel').inputValue(),'old-model','Fetch never silently changes the saved model');
        await page.locator('#aiModelList').selectOption('z-model');assert.equal(await page.locator('#aiModel').inputValue(),'z-model');
        await page.locator('#aiSystemPrompt').fill('Prompt trợ giảng mới');await page.locator('#aiSupportSystemPrompt').fill('Prompt hỗ trợ mới');
        await page.locator('#aiSettingsForm button[type=submit]').click();await page.waitForFunction(()=>document.getElementById('aiSettingsNotice').textContent.includes('Đã lưu'));
        assert.equal(config.systemPrompt,'Prompt trợ giảng mới');assert.equal(config.supportSystemPrompt,'Prompt hỗ trợ mới');
        await page.reload();await page.waitForFunction(()=>!document.getElementById('aiSettingsFields').disabled);
        assert.equal(await page.locator('#aiSystemPrompt').inputValue(),'Prompt trợ giảng mới');
        await page.locator('#aiBaseUrl').fill('https://new-provider.test/v1');await page.locator('#aiApiKey').fill('test-only-new-key');
        await page.locator('#aiFetchModels').click();await page.waitForFunction(()=>!document.getElementById('aiModelList').disabled);
        assert.equal(calls.at(-1).body.apiKey,'test-only-new-key');
        assert.equal(config.baseUrl,'https://provider.test/v1','Discovery does not persist drafts');
        failModels=true;await page.locator('#aiFetchModels').click();await page.waitForFunction(()=>document.getElementById('aiModelsNotice').textContent.includes('không hỗ trợ'));
        assert.equal(await page.locator('#aiModelList').isDisabled(),true);
        await page.locator('#aiModel').fill('manual-model');await page.locator('#aiSystemPrompt').fill('');await page.locator('#aiSupportSystemPrompt').fill('');
        await page.locator('#aiSettingsForm button[type=submit]').click();await page.waitForFunction(()=>document.getElementById('aiSystemPrompt').value==='DEFAULT LESSON');
        assert.equal(config.model,'manual-model');assert.equal(await page.locator('#aiApiKey').inputValue(),'');
        failModels=false;delayModels=true;await page.locator('#aiFetchModels').click();
        await page.locator('#aiBaseUrl').fill('https://different.test/v1');await page.waitForTimeout(600);
        assert.equal(await page.locator('#aiModelList').isDisabled(),true,'Stale results ignored after editing provider');
        for(const width of [1440,768,390,320]) {
            await page.setViewportSize({width,height:1000});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'AI settings overflow '+width);
        }
        const screenshots=path.join(root,'target/ai-ui-screenshots');fs.mkdirSync(screenshots,{recursive:true});
        await page.screenshot({path:path.join(screenshots,'settings-320.png'),fullPage:true});
        assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
        console.log('PASS: actual admin AI JSP; prompt load/save/reload/default reset; model discovery with stored/new key, select/manual fallback, draft preservation, stale-result rejection, safe text rendering, CSRF, responsive 320/390/768/1440px. No external calls.');
    } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});