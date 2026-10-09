/* Offline official PlayerJS MP4: real JSP/JS/CSS and browser H.264 decode, intercepted APIs.
 * Run VideoLearningViewTest first. No app, database, cloud or AI requests. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..'),assets=path.join(root,'src/main/resources/static');
const fixtures=path.join(root,'target/video-ui-fixtures'),screenshots=path.join(root,'target/video-ui-screenshots');
const origin='https://video.test',source='https://unconfigured.example.org/fixture.MP4?token=test%2B123';
const csp="default-src 'self'; connect-src 'self' https:; script-src 'self' 'unsafe-eval' https://cdn.jsdelivr.net https://www.youtube.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; img-src 'self' data: https://i.ytimg.com; media-src 'self' blob: https:; worker-src 'self' blob:; frame-src https://www.youtube.com https://www.youtube-nocookie.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
async function main() {
    for(const name of ['watch-mp4.html','manage-installed.html']) assert.ok(fs.existsSync(path.join(fixtures,name)),'Run VideoLearningViewTest first');
    fs.mkdirSync(screenshots,{recursive:true});
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],unexpected=[],calls=[];
    try {
        // Produce an actual MP4 locally; do not mock video APIs or player time.
        const recorderPage=await browser.newPage();
        const recording=await recorderPage.evaluate(async()=>{
            const mime='video/mp4;codecs=avc1.42E01E';
            if(!MediaRecorder.isTypeSupported(mime)) throw new Error('Chrome must support recording MP4/H.264 for this regression');
            const canvas=document.createElement('canvas');canvas.width=320;canvas.height=180;
            const draw=()=>{const ctx=canvas.getContext('2d');ctx.fillStyle='#16a060';ctx.fillRect(0,0,320,180);ctx.fillStyle='#fff';ctx.font='20px sans-serif';ctx.fillText('MP4 VIDEO FRAME',20,50);};
            draw();const timer=setInterval(draw,40),stream=canvas.captureStream(25),recorder=new MediaRecorder(stream,{mimeType:mime}),chunks=[];
            recorder.ondataavailable=event=>{if(event.data.size)chunks.push(event.data);};
            const stopped=new Promise(resolve=>{recorder.onstop=resolve;});
            recorder.start();await new Promise(resolve=>setTimeout(resolve,6000));recorder.stop();await stopped;
            clearInterval(timer);stream.getTracks().forEach(track=>track.stop());
            const blob=new Blob(chunks,{type:'video/mp4'});
            const base64=await new Promise(resolve=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.readAsDataURL(blob);});
            const probe=document.createElement('video');probe.src=URL.createObjectURL(blob);
            await new Promise((resolve,reject)=>{probe.onloadedmetadata=resolve;probe.onerror=reject;});
            const duration=probe.duration;URL.revokeObjectURL(probe.src);
            return {base64,duration};
        });
        const mp4=Buffer.from(recording.base64,'base64');assert.equal(mp4.toString('ascii',4,8),'ftyp','Fixture is an MP4 container');
        assert.ok(Number.isFinite(recording.duration) && recording.duration>3.5,'MP4 recording has enough media to seek');
        const configuredDuration=Math.round(recording.duration);
        await recorderPage.close();
        async function open(routeName='watch',cors=true) {
            const context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block',reducedMotion:'reduce'});
            let progress={videoId:2,positionMs:1000,ranges:[{startMs:0,endMs:500}],watchedMs:500,completionPercent:8,status:'IN_PROGRESS'};
            await context.addInitScript(()=>{
                window.__forbiddenCaptureCalls=[];
                for(const name of ['getDisplayMedia','setCaptureHandleConfig'])
                    Object.defineProperty(navigator.mediaDevices,name,{configurable:true,value:()=>{window.__forbiddenCaptureCalls.push(name);throw new Error('Browser capture is forbidden');}});
            });
            await context.route('**/*',async route=>{
                const request=route.request(),url=new URL(request.url()),pathname=url.pathname;
                if(url.href===source) {
                    assert.equal(request.headers()['origin'],origin,'PlayerJS MP4 requests use CORS from the beginning');
                    // Playwright supplies ACAO automatically when omitted. Use an
                    // explicit non-matching origin to exercise real CORS rejection.
                    const headers={'Accept-Ranges':'bytes','Access-Control-Allow-Origin':cors ? origin : 'https://denied.example.org'};
                    const range=/^bytes=(\d+)-(\d*)$/.exec(request.headers()['range'] || '');
                    if(range) {
                        const start=Number(range[1]),end=range[2] ? Math.min(Number(range[2]),mp4.length-1) : mp4.length-1;
                        return route.fulfill({status:206,contentType:'video/mp4',headers:{...headers,'Content-Range':`bytes ${start}-${end}/${mp4.length}`},body:mp4.subarray(start,end+1)});
                    }
                    return route.fulfill({contentType:'video/mp4',headers,body:mp4});
                }
                if(url.origin!==origin) {unexpected.push(url.href);return route.abort();}
                if(pathname==='/edulab/lessons/1/video' || pathname==='/edulab/teacher/videos')
                    return route.fulfill({contentType:'text/html',headers:{'Content-Security-Policy':csp},body:fs.readFileSync(path.join(fixtures,routeName==='watch' ? 'watch-mp4.html' : 'manage-installed.html'),'utf8').replace('data-duration="6"',`data-duration="${configuredDuration}"`)});
                if(pathname==='/edulab/api/v1/auth/csrf') return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'fixture'}});
                if(pathname.includes('/api/')) {
                    const body=request.postDataJSON();calls.push({path:pathname,method:request.method(),body});
                    if(request.method()!=='GET') assert.equal(request.headers()['x-csrf-token'],'fixture');
                    if(pathname.endsWith('/video/sessions')) return route.fulfill({json:{sessionId:'fixture-session',progress}});
                    if(pathname.endsWith('/video/progress')) {
                        if(body) {progress={...progress,positionMs:body.positionMs,ranges:[...progress.ranges,...body.ranges]};}
                        return route.fulfill({json:progress});
                    }
                    if(pathname==='/edulab/api/v1/teacher/lessons/1/video' && request.method()==='GET') return route.fulfill({json:{}});
                    if(pathname==='/edulab/api/v1/teacher/videos/validate') return route.fulfill({json:{url:body.sourceUrl,youtubeId:null}});
                    if(pathname.endsWith('/video/chat')) return route.fulfill({json:{text:'Khung hình MP4.',sample:false,frameAnalyzed:!!body.frame,positionMs:body.frame?.timestampMs || 0,sourceLabels:['Bài học'],needsTeacherReview:true}});
                    unexpected.push(pathname);return route.abort();
                }
                const file=path.resolve(assets,pathname.replace(/^\/edulab\//,''));
                if(file.startsWith(assets+path.sep) && fs.existsSync(file) && fs.statSync(file).isFile())
                    return route.fulfill({contentType:{'.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml','.woff2':'font/woff2'}[path.extname(file)] || 'application/octet-stream',body:fs.readFileSync(file)});
                unexpected.push(pathname);return route.abort();
            });
            const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(10000);
            await page.goto(origin+'/edulab/'+(routeName==='watch' ? 'lessons/1/video' : 'teacher/videos'));
            return {page,context};
        }
        const {page,context}=await open();
        await page.waitForFunction(()=>!document.getElementById('videoPlay').disabled);
        try { await page.waitForFunction(()=>{const media=document.querySelector('#lessonPlayer video');return !media.seeking && Math.abs(media.currentTime-1)<0.1;}); }
        catch(error) {
            console.error('MP4 restore state:',await page.locator('#lessonPlayer video').evaluate(media=>({duration:media.duration,time:media.currentTime,seeking:media.seeking,ready:media.readyState,error:media.error?.code,seekable:Array.from({length:media.seekable.length},(_,i)=>[media.seekable.start(i),media.seekable.end(i)])})),await page.locator('#videoNotice').innerText());throw error;
        }
        const metadata=await page.locator('#lessonPlayer video').evaluate(media=>({duration:media.duration,time:media.currentTime,cors:media.crossOrigin,controls:media.controls,src:media.src}));
        assert.ok(Number.isFinite(metadata.duration) && Math.abs(metadata.duration-configuredDuration)<0.6,JSON.stringify(metadata));
        assert.ok(Math.abs(metadata.time-1)<0.1,'Watch position restored');
        assert.equal(metadata.cors,'anonymous');assert.equal(metadata.src,source);
        assert.equal(await page.evaluate(()=>typeof window.Playerjs),'function','MP4 uses actual official PlayerJS');
        await page.locator('#videoPlay').click();await page.waitForTimeout(900);await page.locator('#videoPlay').click();
        await page.waitForFunction(()=>document.querySelector('#lessonPlayer video').paused);
        assert.ok(await page.locator('#lessonPlayer video').evaluate(media=>media.currentTime>1.5),'PlayerJS MP4 playback advances');
        await page.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Đã lưu'));
        assert.ok(calls.some(call=>call.path.endsWith('/video/progress') && call.body?.ranges.some(range=>range.endMs>range.startMs)),'Real MP4 playback sends watched ranges');
        await page.locator('#videoSeek').evaluate(el=>{el.value='3';el.dispatchEvent(new Event('input',{bubbles:true}));});
        await page.waitForFunction(()=>{const media=document.querySelector('#lessonPlayer video');return !media.seeking && Math.abs(media.currentTime-3)<0.1;});
        await page.locator('#videoAiCapture').click();await page.locator('#videoAiPreview').waitFor({state:'visible'});
        const frame=await page.locator('#videoAiPreviewImage').getAttribute('src');assert.ok(frame.startsWith('data:image/jpeg;base64,'));
        const pixel=await page.evaluate(async()=>{const image=document.getElementById('videoAiPreviewImage');await image.decode();const c=document.createElement('canvas');c.width=image.naturalWidth;c.height=image.naturalHeight;const ctx=c.getContext('2d');ctx.drawImage(image,0,0);return Array.from(ctx.getImageData(5,5,1,1).data);});
        assert.ok(pixel[1]>pixel[0]*2 && pixel[1]>pixel[2],'Real decoded MP4 pixels captured');
        assert.match(await page.locator('#videoAiFrameTime').innerText(),/00:03/);
        await page.locator('#videoAiQuestion').fill('Bản nháp MP4');
        await page.locator('#videoAiToggle').click();await page.locator('#videoAiToggle').click();
        assert.equal(await page.locator('#videoAiPreviewImage').getAttribute('src'),frame);
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'Bản nháp MP4');
        assert.equal(await page.locator('#lessonPlayer video').count(),1);
        await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!!document.fullscreenElement);
        assert.equal(await page.locator('#videoFullscreen').getAttribute('aria-pressed'),'true');
        await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!document.fullscreenElement);
        await page.locator('#videoAiSend').click();await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        const chat=calls.filter(call=>call.path.endsWith('/chat')).at(-1).body;assert.equal(chat.frame.timestampMs,3000);
        for(const width of [1440,390,320]) {
            await page.setViewportSize({width,height:960});
            assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'MP4 overflow '+width);
        }
        await page.screenshot({path:path.join(screenshots,'mp4-playerjs-320.png'),fullPage:true});
        assert.deepEqual(await page.evaluate(()=>window.__forbiddenCaptureCalls),[]);await context.close();
        const manage=await open('manage');
        assert.match(await manage.page.locator('#videoSourcePolicy').innerText(),/tất cả domain HTTPS/);
        await manage.page.locator('#videoLesson').selectOption('1');
        await manage.page.waitForFunction(()=>document.getElementById('videoManageNotice').textContent.includes('chưa có video'));
        await manage.page.locator('#videoProvider').selectOption('PLAYERJS');await manage.page.locator('#videoUrl').fill(source);
        await manage.page.locator('#previewVideo').click();
        await manage.page.waitForFunction(duration=>Number(document.getElementById('videoDuration').value)===duration,configuredDuration);
        assert.equal(await manage.page.locator('#previewPlayer video').count(),1,'Admin/teacher preview uses PlayerJS MP4 too');
        await manage.page.locator('#previewVideo').click();
        await manage.page.waitForFunction(()=>document.querySelectorAll('#previewPlayer video').length===1 && document.querySelector('#previewPlayer video').readyState>=1);
        assert.equal(await manage.page.evaluate(()=>typeof window.Playerjs),'function');
        assert.deepEqual(await manage.page.evaluate(()=>window.__forbiddenCaptureCalls),[]);await manage.context.close();
        const blocked=await open('watch',false);
        try { await blocked.page.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Không phát được MP4')); }
        catch(error) {
            console.error('Blocked MP4 state:',await blocked.page.locator('#lessonPlayer video').evaluate(media=>({duration:media.duration,time:media.currentTime,ready:media.readyState,error:media.error?.code,cors:media.crossOrigin})),await blocked.page.locator('#videoNotice').innerText(),errors,unexpected);throw error;
        }
        assert.match(await blocked.page.locator('#videoNotice').innerText(),/CORS/);
        assert.equal(await blocked.page.locator('#videoPlay').isDisabled(),true,'MP4 with denied CORS fails explicitly, no non-CORS reload');
        assert.deepEqual(await blocked.page.evaluate(()=>window.__forbiddenCaptureCalls),[]);await blocked.context.close();
        assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
        console.log('PASS: unchanged official PlayerJS + actual offline MP4/H.264 decode; CORS before src, restore/play/pause/seek/watch reporting/fullscreen/AI JPEG timestamp/state toggle; teacher preview duration and repeated cleanup; responsive 320/390/1440px; actual CORS playback rejection; zero screen capture APIs or app/cloud/AI calls.');
    } finally { await browser.close(); }
}
main().catch(error=>{console.error(error);process.exitCode=1;});