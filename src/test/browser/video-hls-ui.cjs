/* Actual official PlayerJS bundle + MPEG-TS/H.264 decode, rendered JSP and real
 * application JS/CSS. Only public demo media is downloaded if not cached in target.
 * All browser requests are intercepted; no application/database/AI/cloud calls.
 * Run VideoLearningViewTest before this script. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const https=require('node:https');
const {createHash}=require('node:crypto');
const root=path.resolve(__dirname,'../../..'),assets=path.join(root,'src/main/resources/static');
const fixtures=path.join(root,'target/video-ui-fixtures'),mediaDir=path.join(root,'target/hls-test-media');
const screenshots=path.join(root,'target/video-ui-screenshots');
const origin='https://video.test',source='https://media.test/fixture.m3u8';
const csp="default-src 'self'; connect-src 'self' https:; script-src 'self' 'unsafe-eval' https://cdn.jsdelivr.net https://www.youtube.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; img-src 'self' data: https://i.ytimg.com; media-src 'self' blob: https:; worker-src 'self' blob:; frame-src https://www.youtube.com https://www.youtube-nocookie.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
const sha=buffer=>createHash('sha256').update(buffer).digest('hex');
function download(url) {
    return new Promise((resolve,reject)=>{
        const request=https.get(url,response=>{
            if(response.statusCode!==200){response.resume();reject(new Error('Demo media HTTP '+response.statusCode));return;}
            const chunks=[];response.on('data',chunk=>chunks.push(chunk));response.on('end',()=>resolve(Buffer.concat(chunks)));response.on('error',reject);
        });
        request.setTimeout(30000,()=>request.destroy(new Error('Demo media download timeout')));request.on('error',reject);
    });
}
async function main() {
    for(const name of ['watch-hls.html','manage-installed.html']) assert.ok(fs.existsSync(path.join(fixtures,name)),'Run VideoLearningViewTest first: '+name);
    assert.equal(sha(fs.readFileSync(path.join(assets,'js/vendor/playerjs.js'))),'44c7d16598f92f5e6b81960ebcd81111c7f9d581a209d1b06577d7a6aa85ef4f','Unchanged official distribution');
    fs.mkdirSync(mediaDir,{recursive:true});fs.mkdirSync(screenshots,{recursive:true});
    // The first three 10-second HLS fragments from the public hls.js/Mux sample.
    // They are test-only cached artifacts, never packaged in the application.
    const hashes=['b82fcf4dbcec2d8fab7d94bdd48b070aa6e74d7240b1965a0b28c128d6858477','cd2a98d055eef5ec3aca73bd136a40340539138da73144d589d9de5a3a52149a','128e690f89419ecbea473a490c42cac94a2293ecf1f57d60492ceafce3d5cfdb'];
    for(let i=0;i<hashes.length;i++) {
        const file=path.join(mediaDir,'segment-'+i+'.ts');
        if(!fs.existsSync(file)) fs.writeFileSync(file,await download('https://test-streams.mux.dev/x36xhzz/url_2/url_'+(526+i)+'/193039199_mp4_h264_aac_ld_7.ts'));
        assert.equal(sha(fs.readFileSync(file)),hashes[i],'Demo MPEG-TS checksum '+i);
    }
    const manifest='#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-TARGETDURATION:10\n'+hashes.map((_,i)=>'#EXTINF:10.000,\nsegment-'+i+'.ts\n').join('')+'#EXT-X-ENDLIST\n';
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],unexpected=[],calls=[],requests=[];
    async function open(manage=false,available=true,blockedSource=false) {
        const context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block',reducedMotion:'reduce'});
        let progress={videoId:2,positionMs:1000,ranges:[{startMs:0,endMs:500}],watchedMs:500,completionPercent:1,status:'IN_PROGRESS'};
        await context.route('**/*',async route=>{
            const request=route.request(),url=new URL(request.url()),pathname=url.pathname;
            requests.push(url.href);
            if(url.hostname==='media.test') {
                if(request.method()==='OPTIONS') return route.fulfill({status:204,headers:{'access-control-allow-origin':'*','access-control-allow-methods':'GET,OPTIONS'}});
                if(blockedSource) return route.fulfill({status:403,headers:{'access-control-allow-origin':'*'},body:'Test source denied'});
                const name=pathname.slice(1),body=name==='fixture.m3u8' ? manifest : /^segment-[0-2]\.ts$/.test(name) ? fs.readFileSync(path.join(mediaDir,name)) : undefined;
                assert.ok(body,'Unexpected HLS media '+name);
                return route.fulfill({headers:{'access-control-allow-origin':'*'},contentType:name.endsWith('.ts') ? 'video/mp2t' : 'application/vnd.apple.mpegurl',body});
            }
            if(url.origin!==origin){unexpected.push(url.href);return route.abort();}
            if(pathname==='/edulab/lessons/1/video' || pathname==='/edulab/teacher/videos') {
                let html=fs.readFileSync(path.join(fixtures,manage ? 'manage-installed.html' : 'watch-hls.html'),'utf8').replace('data-duration="600"','data-duration="30"').replace('max="600"','max="30"');
                if(!available) html=html.replace('data-player-available="true"','data-player-available="false"');
                return route.fulfill({contentType:'text/html',headers:{'Content-Security-Policy':csp},body:html});
            }
            if(pathname==='/edulab/api/v1/auth/csrf') return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'fixture'}});
            if(pathname==='/edulab/api/v1/ai/support/status') return route.fulfill({json:{enabled:false}});
            if(pathname.startsWith('/edulab/api/')) {
                const body=request.postDataJSON();calls.push({path:pathname,method:request.method(),body});
                if(request.method()==='POST') assert.equal(request.headers()['x-csrf-token'],'fixture');
                if(pathname.endsWith('/video/sessions')) return route.fulfill({json:{sessionId:'fixture-session',progress}});
                if(pathname.endsWith('/video/progress')) {
                    if(request.method()==='POST') {
                        progress={...progress,positionMs:body.positionMs,ranges:[...progress.ranges,...body.ranges]};
                        progress.watchedMs=progress.ranges.reduce((sum,r)=>sum+r.endMs-r.startMs,0);
                    }
                    return route.fulfill({json:progress});
                }
                if(pathname.endsWith('/video/chat')) return route.fulfill({json:{text:'Offline HLS frame test',frameAnalyzed:!!body.frame,positionMs:body.frame?.timestampMs || body.positionMs}});
                if(pathname==='/edulab/api/v1/teacher/lessons/1/video') return route.fulfill({json:{}});
                if(pathname==='/edulab/api/v1/teacher/videos/validate') return route.fulfill({json:{url:body.sourceUrl,youtubeId:null}});
                unexpected.push(pathname);return route.abort();
            }
            const file=path.resolve(assets,pathname.replace(/^\/edulab\//,''));
            if(file.startsWith(assets+path.sep) && fs.existsSync(file) && fs.statSync(file).isFile())
                return route.fulfill({contentType:{'.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.ttf':'font/ttf'}[path.extname(file)] || 'application/octet-stream',body:fs.readFileSync(file)});
            unexpected.push(pathname);return route.abort();
        });
        const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(10000);
        await page.goto(origin+'/edulab/'+(manage ? 'teacher/videos' : 'lessons/1/video'));
        return {context,page};
    }
    try {
        const {context,page}=await open();
        await page.waitForFunction(()=>!document.getElementById('videoPlay').disabled);
        assert.equal(await page.evaluate(()=>typeof window.Playerjs),'function');
        assert.equal(await page.locator('#lessonPlayer video').count(),1,'Actual official PlayerJS media element');
        await page.waitForFunction(()=>document.querySelector('#lessonPlayer video').readyState>=2);
        assert.ok(await page.locator('#lessonPlayer video').evaluate(v=>v.videoWidth>0 && Math.abs(v.duration-30)<0.1),'Actual MPEG-TS decode and metadata');
        assert.match(await page.locator('#videoTime').innerText(),/00:30/);
        await page.locator('#videoPlay').click();
        await page.waitForFunction(()=>document.querySelector('#lessonPlayer video').currentTime>2.2);
        await page.locator('#videoPlay').click();
        await page.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Đã lưu'));
        assert.ok(calls.some(call=>call.method==='POST' && call.path.endsWith('/progress') && call.body.ranges.some(r=>r.endMs-r.startMs>500)),'Pause persists real watched ranges without Builder Events tracking');
        await page.locator('#videoSeek').evaluate(input=>{input.value='15';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));});
        await page.waitForFunction(()=>Math.abs(document.querySelector('#lessonPlayer video').currentTime-15)<0.3);
        await page.locator('#videoPlay').click();await page.waitForFunction(()=>document.querySelector('#lessonPlayer video').currentTime>16.5);await page.locator('#videoPlay').click();
        await page.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Đã lưu'));
        const ranges=calls.filter(call=>call.method==='POST' && call.path.endsWith('/progress')).flatMap(call=>call.body.ranges);
        assert.ok(ranges.some(r=>r.startMs>=14000),'Disjoint watched segment after seek');
        assert.ok(ranges.every(r=>!(r.startMs<5000 && r.endMs>14000)),'Skipped gap is not marked as watched');
        await page.locator('#videoAiCapture').click();await page.waitForFunction(()=>!document.getElementById('videoAiPreview').hidden);
        assert.ok((await page.locator('#videoAiPreviewImage').getAttribute('src')).startsWith('data:image/jpeg;base64,'),'Pixel capture from actual decoded HLS video');
        await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!!document.fullscreenElement);
        await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!document.fullscreenElement);
        for(const width of [1440,768,390,320]) {
            await page.setViewportSize({width,height:960});
            assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'HLS overflow '+width);
        }
        await page.screenshot({path:path.join(screenshots,'hls-official-320.png'),fullPage:true});await context.close();
        const manage=await open(true);
        assert.ok(!(await manage.page.locator('body').innerText()).includes('PlayerJS chưa được cài'));
        await manage.page.locator('#videoLesson').selectOption('1');await manage.page.waitForFunction(()=>document.getElementById('videoManageNotice').textContent.includes('chưa có video'));
        await manage.page.locator('#videoProvider').selectOption('PLAYERJS');await manage.page.locator('#videoUrl').fill(source);
        for(let i=0;i<2;i++) {
            await manage.page.locator('#previewVideo').click();
            await manage.page.waitForFunction(()=>document.getElementById('previewNotice').textContent.includes('Thời lượng: 30'));
            assert.equal(await manage.page.locator('#previewPlayer video').count(),1,'Repeated HLS preview cleans up old player');
        }
        await manage.page.screenshot({path:path.join(screenshots,'hls-official-preview.png'),fullPage:true});await manage.context.close();
        const missing=await open(false,false);await missing.page.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('PlayerJS chưa được cài'));
        assert.equal(await missing.page.locator('#videoPlay').isDisabled(),true);assert.equal(await missing.page.locator('#lessonPlayer video').count(),0);await missing.context.close();
        const blocked=await open(false,true,true);
        try {await blocked.page.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('CORS'),null,{timeout:30000});}
        catch(error) {
            console.error('Blocked HLS state:',await blocked.page.locator('#videoNotice').innerText(),await blocked.page.locator('#lessonPlayer video').evaluate(v=>({duration:String(v.duration),ready:v.readyState,error:v.error?.code})));throw error;
        }
        assert.equal(await blocked.page.locator('#videoPlay').isDisabled(),true,'Denied manifest never enables controls');await blocked.context.close();
        assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
        assert.ok(requests.some(url=>url.endsWith('/js/vendor/playerjs.js')));
        assert.ok(requests.some(url=>url===source));assert.ok(requests.some(url=>url.endsWith('/segment-0.ts')));
        console.log('PASS: unchanged official PlayerJS 22.0.7; actual HLS MPEG-TS/H.264 decode under video CSP; metadata preload, restore/play/pause/seek/watch reports, JPEG frame capture, fullscreen, repeated preview cleanup, 320/390/768/1440px, missing-player and fatal manifest error states. No app/database/AI/cloud requests.');
    } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});