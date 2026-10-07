/* Local test tooling; fake player clocks exercise real app/database, not real media playback. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8086/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Video tests are local-only; verify server datasource separately.');
const out=process.env.EDULAB_SCREENSHOT_DIR || '/tmp/edulab-video-screenshots';
const fixturePlayer=`window.Playerjs=function(config){let playing=false,time=Number(config.start)||0; const clock=setInterval(()=>{if(playing)time+=0.05;},50); this.api=function(name,value){if(name==='time')return time;if(name==='duration')return 600;if(name==='playing')return playing;if(name==='toggle'){playing=!playing;window.PlayerjsEvents?.(playing?'play':'pause',config.id);}if(name==='pause'){playing=false;window.PlayerjsEvents?.('pause',config.id);}if(name==='seek'){time=value;window.PlayerjsEvents?.('seek',config.id);}if(name==='destroy')clearInterval(clock);}; setTimeout(()=>window.PlayerjsEvents?.('init',config.id),0);};`;
const fixtureYoutube=`window.YT={Player:function(id,config){let playing=false,time=0;const clock=setInterval(()=>{if(playing)time+=0.05;},50);this.getDuration=()=>600;this.getCurrentTime=()=>time;this.getPlayerState=()=>playing?1:2;this.seekTo=v=>{time=v;};this.playVideo=()=>{playing=true;config.events.onStateChange({data:1});};this.pauseVideo=()=>{playing=false;config.events.onStateChange({data:2});};this.destroy=()=>clearInterval(clock);setTimeout(()=>config.events.onReady(),0);}};window.onYouTubeIframeAPIReady();`;
async function run() {
    fs.mkdirSync(out,{recursive:true});
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const adminContext=await browser.newContext({viewport:{width:1440,height:1000}}), admin=await adminContext.newPage();
    const errors=[];
    function monitor(page) { page.on('pageerror',error=>errors.push(error.message)); page.on('dialog',dialog=>dialog.dismiss()); }
    monitor(admin);
    async function login(page,name) {
        await page.goto(base+'/login'); await page.locator('#username').fill(name); await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(url=>!url.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);
    }
    async function api(page,url,method='GET',body) { return page.evaluate(({url,method,body})=>window.edulab.api(url,{method,body}),{url,method,body}); }
    let lessonId;
    try {
        await login(admin,'admin'); await admin.goto(base+'/admin');
        const topic=Number(await admin.locator('#lessonTopic option').first().getAttribute('value'));
        const stamp=Date.now();
        const created=await api(admin,'/api/v1/admin/lessons','POST',{topicId:topic,title:'Video browser fixture '+stamp,summary:'Local-only video timeline test',durationMinutes:10,objectives:'Test watched segments',contentHtml:'<p>Local test content</p>',prerequisiteId:null,requiredScore:0});
        lessonId=created.id;
        await api(admin,'/api/v1/admin/lessons/'+lessonId+'/publish','POST',{enabled:true});
        let response=await admin.goto(base+'/teacher/videos'); assert.equal(response.status(),200);
        await admin.locator('#videoLesson').selectOption(String(lessonId));
        await admin.waitForFunction(()=>document.getElementById('videoManageNotice').textContent.includes('chưa có video'));
        await admin.locator('#videoProvider').selectOption('PLAYERJS');
        await admin.locator('#videoUrl').fill('https://media.example.com/lesson.m3u8');
        await admin.locator('#videoDuration').fill('600');
        await Promise.all([admin.waitForEvent('load'),admin.locator('#videoManageForm button[type=submit]').click()]);
        assert.ok((await api(admin,'/api/v1/teacher/lessons/'+lessonId+'/video')).provider==='PLAYERJS');
        const teacher=await admin.locator('.videoTeacherForm[data-lesson="'+lessonId+'"] select option').nth(1).getAttribute('value');
        await api(admin,'/api/v1/admin/lessons/'+lessonId+'/video-teacher','PUT',{teacherId:Number(teacher)});
        const teacherContext=await browser.newContext(),teacherPage=await teacherContext.newPage(); monitor(teacherPage);
        await login(teacherPage,'giaovien'); await teacherPage.goto(base+'/teacher/videos');
        assert.equal(await teacherPage.locator('#videoLesson option[value="'+lessonId+'"]').count(),1);
        const studentContext=await browser.newContext({viewport:{width:1440,height:1000}}),student=await studentContext.newPage(); monitor(student);
        await student.route('**/js/video-test-player.js',route=>route.fulfill({contentType:'text/javascript',body:fixturePlayer}));
        await student.route('https://www.youtube.com/iframe_api',route=>route.fulfill({contentType:'text/javascript',body:fixtureYoutube}));
        await login(student,'hocsinh');
        response=await student.goto(base+'/lessons'); assert.equal(response.status(),200);
        assert.equal(await student.locator('a[href$="/lessons/'+lessonId+'/video"]').count(),1);
        await student.goto(base+'/lessons/'+lessonId); assert.equal(await student.locator('#complete').count(),0);
        await student.goto(base+'/lessons/'+lessonId+'/video'); await student.waitForFunction(()=>!document.getElementById('videoPlay').disabled);
        await student.locator('#videoPlay').click(); await student.waitForTimeout(2400);
        await student.locator('#videoSeek').evaluate(input=>{input.value='300';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));});
        await student.waitForTimeout(2400); await student.locator('#videoPlay').click();
        await student.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Đã lưu'));
        await student.waitForTimeout(600);
        const saved=await api(student,'/api/v1/lessons/'+lessonId+'/video/progress');
        console.log('Saved timeline:',JSON.stringify(saved));
        assert.equal(saved.ranges.length,2); assert.ok(saved.ranges[0].endMs<10000); assert.ok(saved.ranges[1].startMs>=300000);
        assert.ok(saved.watchedMs>2500 && saved.watchedMs<10000); assert.ok(saved.positionMs>=300000);
        assert.equal(await student.locator('#watchedSegments rect').count(),2);
        const denied=await student.evaluate(async id=>{
            const csrf=await fetch(window.edulab.context+'/api/v1/auth/csrf').then(r=>r.json());
            return (await fetch(window.edulab.context+'/api/v1/lessons/'+id+'/progress',{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({status:'COMPLETED',completionPercent:100})})).status;
        },lessonId); assert.equal(denied,409);
        await student.reload(); await student.waitForFunction(()=>!document.getElementById('videoPlay').disabled);
        assert.equal(await student.locator('#watchedSegments rect').count(),2);
        await student.locator('#videoSeek').focus();
        const beforeKey=Number(await student.locator('#videoSeek').inputValue());
        await student.keyboard.press('ArrowRight');
        assert.ok(Number(await student.locator('#videoSeek').inputValue())>beforeKey,'Keyboard seek');
        await student.waitForTimeout(400);
        for (const width of [1440,1024,768,640,390,320]) {
            await student.setViewportSize({width,height:900}); await student.waitForTimeout(120);
            assert.equal(await student.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Watch overflow '+width);
            await student.screenshot({path:path.join(out,'watch-'+width+'.png'),fullPage:true});
            await admin.setViewportSize({width,height:900});
            assert.equal(await admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Manage overflow '+width);
            await admin.screenshot({path:path.join(out,'manage-'+width+'.png'),fullPage:true});
        }
        await api(admin,'/api/v1/teacher/lessons/'+lessonId+'/video','PUT',{provider:'YOUTUBE',sourceUrl:'https://youtu.be/M7lc1UVf-VE',durationSeconds:600,completionThreshold:95});
        const youtubeResponse=await student.goto(base+'/lessons/'+lessonId+'/video'); console.log('Video CSP:',youtubeResponse.headers()['content-security-policy']);
        try { await student.waitForFunction(()=>!document.getElementById('videoPlay').disabled); }
        catch(error) { console.log('YouTube state:',await student.locator('#videoLearning').innerText(),errors); throw error; }
        assert.equal(await student.locator('#watchedSegments rect').count(),0,'New video resets active progress');
        await student.locator('#videoPlay').click(); await student.waitForTimeout(1800); await student.locator('#videoPlay').click();
        await student.waitForTimeout(500); assert.ok((await api(student,'/api/v1/lessons/'+lessonId+'/video/progress')).watchedMs>500);
        const reportRoute='**/api/v1/lessons/'+lessonId+'/video/progress';
        let lostResponse=false; const reports=[];
        await student.route(reportRoute,async route=>{
            if (route.request().method()!=='POST') return route.continue();
            reports.push(route.request().postDataJSON());
            if (!lostResponse) { lostResponse=true; await route.fetch(); return route.abort('failed'); }
            return route.continue();
        });
        await student.locator('#videoPlay').click(); await student.waitForTimeout(1600); await student.locator('#videoPlay').click();
        await student.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Chưa lưu được'));
        const afterLost=await api(student,'/api/v1/lessons/'+lessonId+'/video/progress');
        await student.locator('#videoSeek').evaluate(input=>input.dispatchEvent(new Event('change',{bubbles:true})));
        await student.waitForFunction(()=>document.getElementById('videoNotice').textContent.includes('Đã lưu'));
        const afterRetry=await api(student,'/api/v1/lessons/'+lessonId+'/video/progress');
        assert.equal(afterLost.watchedMs,afterRetry.watchedMs,'Lost response retry does not double count');
        assert.deepEqual(reports[0],reports[1],'Retry body and sequence are immutable');
        await student.unroute(reportRoute);
        const beforeReplay=afterRetry.watchedMs;
        await student.locator('#videoSeek').evaluate(input=>{input.value='0';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));});
        await student.waitForTimeout(300); await student.locator('#videoPlay').click(); await student.waitForTimeout(1000); await student.locator('#videoPlay').click();
        await student.waitForTimeout(600);
        const replay=await api(student,'/api/v1/lessons/'+lessonId+'/video/progress');
        assert.ok(replay.watchedMs<=beforeReplay+250,'Replay cannot double count');
        assert.deepEqual(errors,[]);
        console.log('PASS: real local API/database, teacher assignment, mock YouTube/PlayerJS clocks, disjoint watched timeline, keyboard seek, resume, replay, lost-response idempotent retry, revision reset, manual bypass blocked, six responsive widths. Real media not tested.');
    } finally {
        if (lessonId) {
            await api(admin,'/api/v1/teacher/lessons/'+lessonId+'/video','DELETE').catch(()=>{});
            await api(admin,'/api/v1/admin/lessons/'+lessonId+'/publish','POST',{enabled:false}).catch(()=>{});
            await api(admin,'/api/v1/admin/lessons/'+lessonId+'/video-teacher','PUT',{teacherId:null}).catch(()=>{});
        }
        await browser.close();
    }
}
run().catch(error=>{console.error(error);process.exitCode=1;});