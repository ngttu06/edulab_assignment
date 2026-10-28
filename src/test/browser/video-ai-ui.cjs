/* Offline: rendered JSP + real JS/CSS, intercepted API and PlayerJS/YouTube fixtures.
 * Run VideoLearningViewTest first. No requests reach app/database/AI/cloud/YouTube. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..'), assets=path.join(root,'src/main/resources/static');
const fixture=path.join(root,'target/video-ui-fixtures/watch.html');
const hlsFixture=path.join(root,'target/video-ui-fixtures/watch-hls.html');
const screenshots=path.join(root,'target/video-ui-screenshots');
const origin='https://video.test';
const fakeYoutube=`window.YT={Player:function(id,config){
let playing=false,time=0;const iframe=document.createElement('iframe');iframe.id=id;iframe.title='Offline YouTube fixture';
iframe.src='https://www.youtube-nocookie.com/embed/offline';document.getElementById(id).replaceWith(iframe);
this.getDuration=()=>600;this.getCurrentTime=()=>time;this.getPlayerState=()=>playing?1:2;
this.seekTo=v=>{time=v;};this.playVideo=()=>{playing=true;config.events.onStateChange({data:1});};
this.pauseVideo=()=>{playing=false;config.events.onStateChange({data:2});};
setInterval(()=>{if(playing)time+=0.25;},250);setTimeout(()=>config.events.onReady(),0);
}};window.onYouTubeIframeAPIReady();`;
// The source contract is HLS/PlayerJS; actual media here is a local canvas stream,
// not a distributed PlayerJS build or a test of HLS decoding/CDN configuration.
const fakePlayerjs=`window.Playerjs=function(config){
if(config.file!=='https://media.test/fixture.m3u8')throw new Error('Unexpected PlayerJS source');
const canvas=document.createElement('canvas');canvas.width=640;canvas.height=360;
const draw=()=>{const ctx=canvas.getContext('2d');ctx.fillStyle='#16a060';ctx.fillRect(0,0,640,360);ctx.fillStyle='#fff';ctx.font='24px sans-serif';ctx.fillText('VIDEO FRAME ONLY',30,65);};
draw();setInterval(draw,40);const media=document.createElement('video');media.muted=true;media.playsInline=true;
media.style.cssText='width:100%;height:100%;object-fit:contain';media.srcObject=canvas.captureStream(25);
const overlay=document.createElement('div');overlay.textContent='PLAYER CONTROLS — NOT IN FRAME';overlay.style.cssText='position:absolute;bottom:0;background:#ff00ff;color:white;width:100%;text-align:center';
document.getElementById(config.id).append(media,overlay);window.__fixtureMedia=media;window.__fixtureCanvas=canvas;
let playing=false,time=Number(config.start)||0;setInterval(()=>{if(playing)time+=0.25;},250);
this.api=(name,value)=>{if(name==='time')return time;if(name==='duration')return 600;if(name==='playing')return playing;
if(name==='toggle'){playing=!playing;if(playing)media.play();else media.pause();window.PlayerjsEvents(playing?'play':'pause',config.id);}
if(name==='pause'){playing=false;media.pause();window.PlayerjsEvents('pause',config.id);}
if(name==='seek'){time=value;window.PlayerjsEvents('seek',config.id);}};
media.play().then(()=>{media.pause();window.PlayerjsEvents('init',config.id);});};`;
async function main() {
    assert.ok(fs.existsSync(fixture) && fs.existsSync(hlsFixture),'Run VideoLearningViewTest first');fs.mkdirSync(screenshots,{recursive:true});
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],unexpected=[],calls=[];
    let crossOriginVideo;
    async function open(width,height=960,source='hls') {
        const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce',serviceWorkers:'block'});
        let failAi=false,aiDelay=0,aiGate;
        await context.addInitScript(()=>{
            window.__forbiddenCaptureCalls=[];
            for(const name of ['getDisplayMedia','setCaptureHandleConfig'])
                Object.defineProperty(navigator.mediaDevices,name,{configurable:true,value:()=>{
                    window.__forbiddenCaptureCalls.push(name);throw new Error('Browser capture is forbidden');
                }});
        });
        await context.route('**/*',async route=>{
            const request=route.request(),url=new URL(request.url()),pathname=url.pathname;
            if(url.href==='https://www.youtube.com/iframe_api') return route.fulfill({contentType:'text/javascript',body:fakeYoutube});
            if(url.href==='https://www.youtube-nocookie.com/embed/offline') return route.fulfill({contentType:'text/html',body:'<body style="background:#152b43;color:white;text-align:center"><h2>Offline YouTube iframe</h2><p>Phản ứng hóa học</p></body>'});
            if(url.href==='https://media.test/frame.webm' && crossOriginVideo) return route.fulfill({contentType:'video/webm',body:crossOriginVideo});
            if(url.origin!==origin) {unexpected.push(url.href);return route.abort();}
            if(pathname==='/edulab/lessons/1/video') return route.fulfill({contentType:'text/html',body:fs.readFileSync(source==='hls' ? hlsFixture : fixture)});
            if(pathname==='/edulab/js/vendor/playerjs.js') return route.fulfill({contentType:'text/javascript',body:fakePlayerjs});
            if(pathname==='/edulab/api/v1/auth/csrf') return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'fixture'}});
            if(pathname.includes('/api/')) {
                const body=request.postDataJSON();calls.push({path:pathname,method:request.method(),body});
                if(request.method()==='POST') assert.equal(request.headers()['x-csrf-token'],'fixture');
                const progress={videoId:2,positionMs:120000,ranges:[{startMs:0,endMs:30000}],watchedMs:30000,completionPercent:5,status:'IN_PROGRESS'};
                if(pathname.endsWith('/video/sessions')) return route.fulfill({json:{sessionId:'fixture-session',progress}});
                if(pathname.endsWith('/video/progress')) return route.fulfill({json:progress});
                if(pathname.endsWith('/video/chat')) {
                    if(aiGate) await aiGate;
                    if(aiDelay) await new Promise(resolve=>setTimeout(resolve,aiDelay));
                    return route.fulfill(failAi ? {status:503,json:{message:'AI đang bận. Hãy thử lại.'}} : {json:{text:'Chất phản ứng biến đổi thành sản phẩm. <img src=x onerror=alert(1)>',sample:false,frameAnalyzed:!!body.frame,positionMs:body.frame?.timestampMs ?? body.positionMs,sourceLabels:['Bài học'],needsTeacherReview:true}});
                }
                unexpected.push(pathname);return route.abort();
            }
            const file=path.resolve(assets,pathname.replace(/^\/edulab\//,''));
            if(file.startsWith(assets+path.sep) && fs.existsSync(file) && fs.statSync(file).isFile())
                return route.fulfill({contentType:{'.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml','.woff2':'font/woff2'}[path.extname(file)] || 'application/octet-stream',body:fs.readFileSync(file)});
            unexpected.push(pathname);return route.abort();
        });
        const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(7000);
        await page.goto(origin+'/edulab/lessons/1/video');
        await page.waitForFunction(()=>!document.getElementById('videoPlay').disabled);await page.evaluate(()=>document.fonts.ready);
        return {context,page,fail:value=>{failAi=value;},delay:value=>{aiDelay=value;},hold:()=>{
            let release;aiGate=new Promise(resolve=>{release=resolve;});
            return ()=>{aiGate=undefined;release();};
        }};
    }
    try {
        const {page,context,fail,delay,hold}=await open(1440);
        assert.equal(await page.locator('#aiChatToggle').count(),0);
        const left=await page.locator('#videoLearning').boundingBox(),right=await page.locator('#videoAi').boundingBox();
        assert.ok(right.x>=left.x+left.width,'Desktop AI is on the right');
        assert.ok((await page.locator('.video-controls').boundingBox()).height<=48,'Compact single-row controls');
        assert.equal(await page.locator('.watched-timeline svg').evaluate(el=>el.getBoundingClientRect().height),6);
        assert.equal(await page.locator('.video-controls .timeline-legend').count(),0);
        assert.equal(await page.locator('#videoAiToggle').getAttribute('aria-controls'),'videoAi');
        assert.equal(await page.locator('#videoAiToggle').getAttribute('aria-expanded'),'true');
        await page.screenshot({path:path.join(screenshots,'desktop.png'),fullPage:true});
        const restored=await page.locator('#watchedSegments').innerHTML();
        const sessionsBefore=calls.filter(call=>call.path.endsWith('/sessions')).length;
        await page.locator('#videoAiQuestion').fill('Bản nháp được giữ');
        await page.locator('#videoAiToggle').focus();await page.locator('#videoAiToggle').press('Space');
        assert.equal(await page.locator('#videoAi').isVisible(),false);
        assert.equal(await page.locator('#videoAiToggle').getAttribute('aria-expanded'),'false');
        assert.equal(await page.locator('#videoAiToggle').innerText(),'Hiện trợ lý AI');
        const expanded=await page.locator('#videoLearning').boundingBox(),layout=await page.locator('.video-learning-layout').boundingBox();
        assert.ok(expanded.width>left.width && Math.abs(expanded.width-layout.width)<1,'Hidden AI gives the video full width');
        assert.equal(await page.locator('#watchedSegments').innerHTML(),restored);
        await page.screenshot({path:path.join(screenshots,'desktop-ai-hidden.png'),fullPage:true});
        await page.locator('#videoAiToggle').press('Enter');
        assert.equal(await page.locator('#videoAi').isVisible(),true);
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'Bản nháp được giữ');
        assert.equal(await page.locator('#videoAiToggle').evaluate(el=>el===document.activeElement),true);
        // Hide while focus is inside the panel: restore it before making controls unreachable.
        await page.locator('#videoAiQuestion').focus();await page.evaluate(()=>document.getElementById('videoAiToggle').click());
        assert.equal(await page.locator('#videoAiToggle').evaluate(el=>el===document.activeElement),true);
        await page.locator('#videoAiToggle').click();
        assert.equal(calls.filter(call=>call.path.endsWith('/sessions')).length,sessionsBefore,'Toggle never recreates a learning session');
        assert.equal(await page.evaluate(()=>window.__fixtureMedia===document.querySelector('#lessonPlayer video')),true,'Toggle keeps the player instance');
        await page.locator('#videoFullscreen').click();
        await page.waitForFunction(()=>document.fullscreenElement?.id==='videoFrame');
        assert.equal(await page.locator('#videoFullscreen').getAttribute('aria-label'),'Thoát toàn màn hình');
        assert.equal(await page.locator('#videoFullscreen').getAttribute('aria-pressed'),'true');
        assert.equal(await page.locator('#videoFrame .timeline-legend').count(),0);
        assert.equal(await page.locator('#videoPlay').isVisible(),false);
        assert.equal(await page.locator('#watchedSegments').innerHTML(),restored,'Fullscreen preserves restored watched ranges');
        assert.ok((await page.locator('.video-controls').boundingBox()).height<60,'Compact fullscreen controls');
        await page.screenshot({path:path.join(screenshots,'fullscreen.png')});
        await page.locator('#videoSeek').focus();await page.locator('#videoSeek').press('ArrowRight');
        await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!document.fullscreenElement);
        assert.equal(await page.locator('#videoFullscreen').getAttribute('aria-label'),'Toàn màn hình');
        await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!!document.fullscreenElement);
        await page.keyboard.press('Escape');
        // Headless Chrome may not dispatch native Esc exit; external exit uses the same fullscreenchange event.
        if(await page.evaluate(()=>!!document.fullscreenElement)) await page.evaluate(()=>document.exitFullscreen());
        await page.waitForFunction(()=>document.getElementById('videoFullscreen').getAttribute('aria-pressed')==='false');
        assert.equal(await page.locator('.timeline-legend').isVisible(),true);
        assert.equal(await page.locator('#watchedSegments').innerHTML(),restored,'Seek/fullscreen exit does not count skipped ranges');
        await page.locator('#videoPlay').click();
        assert.equal(await page.locator('#videoPlay').getAttribute('aria-label'),'Tạm dừng video');
        await page.waitForTimeout(800);
        await page.locator('#videoAiToggle').click();
        const playingPosition=await page.evaluate(()=>document.getElementById('videoLearning').videoContext.position());
        await page.waitForTimeout(500);
        assert.ok(await page.evaluate(()=>document.getElementById('videoLearning').videoContext.position())>playingPosition,'Hiding AI does not pause playback');
        await page.locator('#videoAiToggle').click();
        await page.locator('#videoFullscreen').click();
        await page.waitForTimeout(900);
        const credited=await page.locator('#watchedSegments rect').count();
        assert.ok(credited>1,'Playback continues adding watched ranges in fullscreen');
        await page.locator('#videoFullscreen').click();await page.locator('#videoPlay').click();
        await page.waitForTimeout(350);
        const reports=calls.filter(call=>call.path.endsWith('/video/progress') && call.method==='POST');
        assert.ok(reports.some(report=>report.body.ranges.some(range=>range.startMs>=120000 && range.endMs>range.startMs)),
            'Watched playback is still reported after fullscreen (a subsequent position-only save may be empty)');
        assert.equal(await page.locator('#videoPlay').getAttribute('aria-label'),'Phát video');
        await page.locator('#videoAiCapture').click();
        await page.locator('#videoAiPreview').waitFor({state:'visible'});
        assert.match(await page.locator('#videoAiStatus').innerText(),/trực tiếp từ video/);
        const capturedTimestamp=await page.evaluate(()=>Math.round(window.__fixtureMedia.currentTime*1000));
        // Decode the player's video pixels: never the magenta control overlay or page UI.
        const crop=await page.evaluate(async()=>{
            const img=document.getElementById('videoAiPreviewImage');await img.decode();
            const canvas=document.createElement('canvas');canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;
            const ctx=canvas.getContext('2d');ctx.drawImage(img,0,0);return {width:canvas.width,height:canvas.height,pixel:Array.from(ctx.getImageData(5,5,1,1).data)};
        });
        assert.equal(crop.width,640);assert.equal(crop.height,360);
        assert.ok(crop.pixel[1]>crop.pixel[0]*2 && crop.pixel[1]>crop.pixel[2],'Image contains video, not page UI');
        const bottomPixel=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=640;c.height=360;const ctx=c.getContext('2d');ctx.drawImage(document.getElementById('videoAiPreviewImage'),0,0);return Array.from(ctx.getImageData(5,355,1,1).data);});
        assert.ok(bottomPixel[1]>bottomPixel[0]*2 && bottomPixel[1]>bottomPixel[2],'Player control overlay is not in the frame');
        assert.equal(calls.filter(call=>call.path.endsWith('/chat')).length,0,'Capture does not send AI until submit');
        const previewSrc=await page.locator('#videoAiPreviewImage').getAttribute('src'),frameTime=await page.locator('#videoAiFrameTime').innerText();
        await page.locator('#videoAiToggle').click();await page.locator('#videoAiToggle').click();
        assert.equal(await page.locator('#videoAiPreviewImage').getAttribute('src'),previewSrc);
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'Bản nháp được giữ');
        await page.screenshot({path:path.join(screenshots,'direct-player-frame.png'),fullPage:true});
        await page.locator('#videoSeek').evaluate(el=>{el.value='300';el.dispatchEvent(new Event('input',{bubbles:true}));});
        assert.equal(await page.locator('#videoAiFrameTime').innerText(),frameTime,'Seeking does not change the selected image timestamp');
        await page.locator('#videoAiQuestion').fill('Giải thích khung hình này');
        const releaseFirst=hold();await page.locator('#videoAiSend').click();
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'','Submitted question is removed from the composer immediately');
        assert.equal(await page.locator('#videoAiPreview').isVisible(),false,'Submitted frame is removed from the composer immediately');
        assert.equal(await page.locator('#videoAiPreviewImage').getAttribute('src'),null,'Composer releases the submitted image');
        assert.equal(await page.locator('#videoAiSend').isDisabled(),true,'Sending stays locked while the request is pending');
        assert.equal(await page.locator('#videoAiForm').getAttribute('aria-busy'),'true');
        assert.equal(await page.locator('#videoAiMessages .user').count(),1,'Submitted question appears only once');
        assert.equal(await page.locator('#videoAiMessages .user p').innerText(),'Giải thích khung hình này');
        assert.equal(await page.locator('#videoAiMessages .user img').getAttribute('src'),previewSrc,'Message retains the submitted image');
        assert.equal(await page.locator('#videoAiMessages .user small').innerText(),frameTime,'Message retains the captured timestamp');
        await page.screenshot({path:path.join(screenshots,'composer-reset-pending.png'),fullPage:true});
        await page.locator('#videoAiQuestion').fill('Giải thích thêm');
        await page.locator('#videoAiQuestion').press('Enter');
        assert.equal(await page.locator('#videoAiMessages .user').count(),1,'Enter while busy does not submit a second message');
        releaseFirst();await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        const first=calls.filter(call=>call.path.endsWith('/chat')).at(-1).body;
        assert.ok(first.frame.dataUrl.startsWith('data:image/jpeg;base64,'));assert.equal(first.frame.timestampMs,capturedTimestamp);assert.equal(first.positionMs,300000);assert.equal(first.videoId,2);
        assert.equal(await page.locator('#videoAiMessages .assistant img').count(),0,'AI output is text, not injected HTML');
        assert.equal(await page.locator('#videoAiPreview').isVisible(),false);
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'Giải thích thêm','AI response does not clear the next draft');
        const releaseSecond=hold();await page.locator('#videoAiQuestion').press('Enter');
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'','Text-only submission resets the composer too');
        releaseSecond();
        await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        const second=calls.filter(call=>call.path.endsWith('/chat')).at(-1).body;assert.equal(second.frame,null);assert.equal(second.history.length,2);
        assert.ok((await page.locator('#videoAiStatus').innerText()).includes('không có khung hình'));
        const conversation=await page.locator('#videoAiMessages').innerHTML();
        await page.locator('#videoAiToggle').click();await page.locator('#videoAiToggle').click();
        assert.equal(await page.locator('#videoAiMessages').innerHTML(),conversation,'Toggle preserves messages/history');
        // Manual screenshot fallback is decoded and JPEG-normalized without R2 upload.
        const png=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=20;c.height=10;c.getContext('2d').fillRect(0,0,20,10);return c.toDataURL('image/png').split(',')[1];});
        await page.locator('#videoAiImage').setInputFiles({name:'frame.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')});
        await page.locator('#videoAiPreview').waitFor({state:'visible'});
        const manualFrame=await page.locator('#videoAiPreviewImage').getAttribute('src'),manualTime=await page.locator('#videoAiFrameTime').innerText();
        fail(true);const releaseFailed=hold();await page.locator('#videoAiQuestion').fill('Giữ lại khi lỗi');await page.locator('#videoAiSend').click();
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'');assert.equal(await page.locator('#videoAiPreview').isVisible(),false);
        releaseFailed();
        await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'Giữ lại khi lỗi');assert.equal(await page.locator('#videoAiPreview').isVisible(),true);
        assert.equal(await page.locator('#videoAiPreviewImage').getAttribute('src'),manualFrame,'Retry restores the exact image');
        assert.equal(await page.locator('#videoAiFrameTime').innerText(),manualTime,'Retry restores the exact timestamp');
        assert.equal(await page.locator('#videoAiMessages .user').count(),2,'Failed submission is not duplicated in the conversation');
        assert.match(await page.locator('#videoAiStatus').innerText(),/AI đang bận/);
        fail(false);delay(500);await page.locator('#videoAiSend').click();
        await page.locator('#videoAiToggle').click();
        await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        await page.locator('#videoAiToggle').click();delay(0);
        assert.equal(await page.locator('#videoAiMessages .assistant').count(),4,'In-flight answer completes even while AI is hidden');
        const retried=calls.filter(call=>call.path.endsWith('/chat')).at(-1).body;
        assert.equal(retried.frame.dataUrl,manualFrame);assert.equal(retried.question,'Giữ lại khi lỗi');
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'');assert.equal(await page.locator('#videoAiPreview').isVisible(),false);
        // A failed request must not pair its old frame with a new question drafted while waiting.
        await page.locator('#videoAiImage').setInputFiles({name:'frame.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')});
        await page.locator('#videoAiPreview').waitFor({state:'visible'});
        fail(true);const releaseDraftFailure=hold();
        await page.locator('#videoAiQuestion').fill('Câu hỏi gặp lỗi');await page.locator('#videoAiSend').click();
        await page.locator('#videoAiQuestion').fill('Bản nháp mới không bị ghi đè');
        releaseDraftFailure();await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);fail(false);
        assert.equal(await page.locator('#videoAiQuestion').inputValue(),'Bản nháp mới không bị ghi đè');
        assert.equal(await page.locator('#videoAiPreview').isVisible(),false,'Failed frame is not attached to a different draft');
        assert.equal(await page.locator('#videoAiMessages .user').count(),3);
        await page.locator('#videoAiReset').click();assert.equal(await page.locator('#videoAiMessages > div').count(),1);
        // A pending seek must settle before the direct frame is read.
        await page.evaluate(()=>{
            Object.defineProperty(window.__fixtureMedia,'seeking',{configurable:true,value:true});
            setTimeout(()=>{delete window.__fixtureMedia.seeking;window.__fixtureMedia.dispatchEvent(new Event('seeked'));},150);
        });
        await page.locator('#videoAiCapture').click();await page.locator('#videoAiPreview').waitFor({state:'visible'});
        const preservedFrame=await page.locator('#videoAiPreviewImage').getAttribute('src');
        // Simulate the browser's origin-clean rejection; no display capture fallback.
        await page.evaluate(()=>{
            window.__originalToDataURL=HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL=function(){throw new DOMException('Tainted canvas','SecurityError');};
        });
        await page.locator('#videoAiCapture').click();await page.waitForFunction(()=>!document.getElementById('videoAiCapture').disabled);
        assert.match(await page.locator('#videoAiStatus').innerText(),/CORS/);
        assert.equal(await page.locator('#videoAiPreviewImage').getAttribute('src'),preservedFrame,'Failed capture does not replace the previous selection');
        await page.evaluate(()=>{HTMLCanvasElement.prototype.toDataURL=window.__originalToDataURL;});
        // Real browser origin-clean failure: local WebM served from another origin
        // without ACAO. Playback works, but canvas export must be rejected.
        const webm=await page.evaluate(async()=>{
            const stream=window.__fixtureCanvas.captureStream(25),recorder=new MediaRecorder(stream,{mimeType:'video/webm'}),chunks=[];
            recorder.ondataavailable=event=>{if(event.data.size)chunks.push(event.data);};
            const stopped=new Promise(resolve=>{recorder.onstop=resolve;});
            recorder.start();await new Promise(resolve=>setTimeout(resolve,250));recorder.stop();await stopped;
            stream.getTracks().forEach(track=>track.stop());
            const blob=new Blob(chunks,{type:'video/webm'});
            return new Promise(resolve=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.readAsDataURL(blob);});
        });
        crossOriginVideo=Buffer.from(webm,'base64');
        await page.evaluate(async()=>{
            window.__fixtureMedia.remove();const media=document.createElement('video');media.muted=true;media.src='https://media.test/frame.webm';
            document.getElementById('lessonPlayer').append(media);window.__crossOriginMedia=media;
            await media.play();media.pause();
        });
        await page.locator('#videoAiCapture').click();await page.waitForFunction(()=>!document.getElementById('videoAiCapture').disabled);
        assert.match(await page.locator('#videoAiStatus').innerText(),/CORS/,'Playing cross-origin media is not automatically drawable');
        assert.equal(await page.locator('#videoAiPreviewImage').getAttribute('src'),preservedFrame);
        await page.evaluate(()=>window.__crossOriginMedia.remove());
        await page.locator('#videoAiCapture').click();await page.waitForFunction(()=>!document.getElementById('videoAiCapture').disabled);
        assert.match(await page.locator('#videoAiStatus').innerText(),/chưa cung cấp phần tử video/);
        await page.evaluate(()=>{
            document.getElementById('lessonPlayer').append(window.__fixtureMedia);
            Object.defineProperty(window.__fixtureMedia,'readyState',{configurable:true,value:0});
        });
        await page.locator('#videoAiCapture').click();await page.waitForFunction(()=>!document.getElementById('videoAiCapture').disabled);
        assert.match(await page.locator('#videoAiStatus').innerText(),/chưa có khung hình sẵn sàng/);
        await page.evaluate(()=>{delete window.__fixtureMedia.readyState;});
        await page.locator('#videoAiReset').click();
        for(let i=0;i<13;i++) {
            await page.locator('#videoAiQuestion').fill('Câu hỏi '+i);await page.locator('#videoAiSend').click();
            await page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        }
        assert.equal(calls.filter(call=>call.path.endsWith('/chat')).at(-1).body.history.length,8,'History stays bounded');
        assert.ok(await page.locator('#videoAiMessages > div').count()<=25,'Rendered messages stay bounded');
        assert.deepEqual(await page.evaluate(()=>window.__forbiddenCaptureCalls),[]);
        await context.close();
        for(const width of [1920,1280,1024,768,640,390,320]) {
            const {page,context}=await open(width);
            assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'No overflow at '+width);
            if(width<=1100) {
                const video=await page.locator('#videoLearning').boundingBox(),ai=await page.locator('#videoAi').boundingBox();
                assert.ok(ai.y>=video.y+video.height,'Chat stacks below video at '+width);
            }
            assert.ok((await page.locator('.video-controls').boundingBox()).height<80,'Compact mobile/desktop controls at '+width);
            await page.screenshot({path:path.join(screenshots,`watch-${width}.png`),fullPage:true});
            await page.locator('#videoAiToggle').click();
            assert.equal(await page.locator('#videoAi').isVisible(),false);
            assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'No overflow with AI hidden at '+width);
            await page.locator('#videoAiToggle').click();assert.equal(await page.locator('#videoAi').isVisible(),true);
            if(width===390) {
                await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!!document.fullscreenElement);
                assert.ok((await page.locator('.video-controls').boundingBox()).height<60,'Mobile fullscreen remains one thin row');
                await page.locator('#videoFullscreen').click();await page.waitForFunction(()=>!document.fullscreenElement);
            }
            assert.deepEqual(await page.evaluate(()=>window.__forbiddenCaptureCalls),[]);await context.close();
        }
        const youtube=await open(1440,960,'youtube');
        await youtube.page.locator('#videoPlay').click();
        await youtube.page.locator('#videoAiCapture').click();
        assert.match(await youtube.page.locator('#videoAiStatus').innerText(),/iframe YouTube/);
        assert.equal(await youtube.page.locator('#videoAiPreview').isVisible(),false);
        assert.equal(await youtube.page.locator('#videoPlay').getAttribute('aria-label'),'Tạm dừng video','Unsupported capture does not pause playback');
        await youtube.page.locator('#videoAiQuestion').fill('Hỏi không có ảnh');await youtube.page.locator('#videoAiSend').click();
        await youtube.page.waitForFunction(()=>!document.getElementById('videoAiSend').disabled);
        assert.equal(calls.filter(call=>call.path.endsWith('/chat')).at(-1).body.frame,null);
        assert.deepEqual(await youtube.page.evaluate(()=>window.__forbiddenCaptureCalls),[]);
        await youtube.page.screenshot({path:path.join(screenshots,'youtube-frame-unsupported.png'),fullPage:true});
        await youtube.context.close();
        assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
        assert.doesNotMatch(fs.readFileSync(path.join(assets,'js/video-ai.js'),'utf8'),/getDisplayMedia|setCaptureHandleConfig|getCaptureHandle/);
        console.log('PASS: real JSP/JS/CSS; PlayerJS + YouTube adapters; 8 responsive widths; keyboard AI toggle preserves player/draft/preview/history/in-flight replies; compact controls + fullscreen/button/external exit + watched reporting; direct video JPEG pixels exclude player overlay, timestamp preserved after seek; simulated and real browser cross-origin canvas rejection (offline WebM), missing media, seek readiness/timeout, honest YouTube limitation; no browser capture API calls; immediate composer reset preserves sent images/timestamps/new drafts, restores failed submissions for retry without overwriting new drafts; CSRF/manual image/retry/reset/bounded history. Offline fixtures, not actual HLS decoding/CDN or live AI.');
    } finally { await browser.close(); }
}
main().catch(error=>{console.error(error);process.exitCode=1;});