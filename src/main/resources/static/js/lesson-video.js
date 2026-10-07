(() => {
    'use strict';
    const root = document.getElementById('videoLearning');
    if (!root) return;
    const student = root.dataset.student === 'true', durationMs = Number(root.dataset.duration) * 1000;
    const notice = document.getElementById('videoNotice'), loading = document.getElementById('playerLoading');
    const seek = document.getElementById('videoSeek'), play = document.getElementById('videoPlay');
    const endpoint = '/api/v1/lessons/' + root.dataset.lesson + '/video';
    let adapter, session, sequence = 1, confirmed = [], pending = [], lastSample, ready = false, halted = false;
    let position = 0, inFlight, retryReport, activityTimer, saveTimer, saveRequested = false;
    function merge(ranges) {
        const result = [];
        for (const range of [...ranges].sort((a,b) => a.startMs-b.startMs)) {
            const last = result.at(-1);
            if (last && range.startMs <= last.endMs) last.endMs = Math.max(last.endMs,range.endMs);
            else result.push({...range});
        }
        return result;
    }
    function time(ms) {
        const seconds = Math.floor(ms/1000), minutes = Math.floor(seconds/60);
        return String(minutes).padStart(2,'0') + ':' + String(seconds%60).padStart(2,'0');
    }
    function draw() {
        const ranges = merge([...confirmed,...(retryReport?.ranges || []),...pending]), watched = ranges.reduce((sum,r) => sum+r.endMs-r.startMs,0);
        const percent = Math.min(100,Math.floor(watched*100/durationMs));
        const group = document.getElementById('watchedSegments');
        group.replaceChildren(...ranges.map(range => {
            const rect = document.createElementNS('http://www.w3.org/2000/svg','rect');
            rect.setAttribute('x',String(range.startMs/durationMs*1000));
            rect.setAttribute('width',String((range.endMs-range.startMs)/durationMs*1000));
            rect.setAttribute('height','12'); rect.setAttribute('class','watched-segment'); return rect;
        }));
        const x = Math.min(1000,position/durationMs*1000), line = document.getElementById('videoPlayhead');
        line.setAttribute('x1',String(x)); line.setAttribute('x2',String(x));
        seek.value = String(position/1000); seek.setAttribute('aria-valuetext',time(position));
        document.getElementById('videoTime').textContent = time(position) + ' / ' + time(durationMs);
        document.getElementById('videoPercent').textContent = percent + '%';
        document.getElementById('videoProgress').value = percent;
        document.getElementById('watchedTime').textContent = 'Đã xem ' + time(watched) + ' / ' + time(durationMs) + ' · ' + ranges.length + ' đoạn.';
    }
    function fail(error) {
        notice.textContent = error.message; halted = true; lastSample = null;
        adapter?.pause(); play.disabled = true; seek.disabled = true;
        loading.hidden = true;
    }
    function sample(terminal = false) {
        if (!adapter || !ready || halted) return;
        const actualDuration = adapter.duration()*1000;
        if (actualDuration > 0 && Math.abs(actualDuration-durationMs) > Math.max(2000,durationMs/100)) {
            fail(new Error('Thời lượng thực tế không khớp cấu hình. Giáo viên cần cập nhật đúng thời lượng video.')); return;
        }
        const current = Math.round(Math.max(0,Math.min(durationMs,adapter.time()*1000))), now = performance.now();
        const playing = adapter.playing() && !document.hidden;
        if (student && (playing || terminal) && lastSample?.playing) {
            const delta = current-lastSample.time, elapsed = now-lastSample.at;
            // Never connect separate portions across a seek, buffering gap or background tab.
            if (delta > 0 && elapsed < 1500 && delta <= elapsed*2.05+150) {
                const previous = pending.at(-1);
                if (previous && previous.endMs === lastSample.time) previous.endMs = current;
                else pending.push({startMs:lastSample.time,endMs:current});
            }
        }
        lastSample = {time:current,at:now,playing}; position = current; draw();
    }
    function save(options = {}) {
        if (!student || !session || halted) return Promise.resolve();
        if (inFlight) { saveRequested = true; return inFlight; }
        if (!retryReport) {
            const count = pending.length;
            // Snapshot the tail as well: new playback must not mutate an in-flight report.
            const ranges = merge(pending.splice(0,count));
            if (ranges.length > 64) { pending.unshift(...ranges); fail(new Error('Có quá nhiều đoạn chưa lưu. Hãy tải lại trang.')); return Promise.resolve(); }
            retryReport = {body:{sessionId:session,sequence,positionMs:position,durationMs,ranges},ranges};
        }
        const report = retryReport;
        // Include outgoing ranges while waiting so timeline never flashes back to old progress.
        notice.textContent='Đang lưu tiến độ…';
        inFlight = window.edulab.api(endpoint+'/progress',{method:'POST',body:report.body,...options}).then(progress => {
            confirmed = progress.ranges; sequence++; retryReport = null;
            notice.textContent = progress.status === 'COMPLETED' ? 'Đã lưu. Em đã hoàn thành video; có thể học bài tiếp theo.' : 'Đã lưu tiến độ. Em có thể tiếp tục từ vị trí này lần sau.';
        }).catch(error => {
            notice.textContent = 'Chưa lưu được: ' + error.message + ' Các đoạn chưa lưu được giữ trong trang để thử lại; đừng đóng trang.';
        }).finally(() => {
            inFlight = null; draw();
            const requested = saveRequested; saveRequested = false;
            if (!retryReport && (requested || pending.length)) queueMicrotask(() => save());
        });
        return inFlight;
    }
    async function init() {
        try {
            const progress = student ? await window.edulab.api(endpoint+'/sessions',{method:'POST'}) : {progress:await window.edulab.api(endpoint+'/progress')};
            session = progress.sessionId; confirmed = progress.progress.ranges; position = progress.progress.positionMs; draw();
            let initialized = false;
            const onReady = () => setTimeout(() => {
                if (!adapter || initialized) return;
                initialized = true; ready = true; loading.hidden = true;
                play.disabled = false; seek.disabled = false;
                document.getElementById('videoFullscreen').disabled = !document.getElementById('videoFrame').requestFullscreen;
                if (position && position < durationMs-1000) adapter.seek(position/1000);
                else if (position >= durationMs-1000) { position=0; adapter.seek(0); draw(); }
                notice.textContent = student ? 'Đã khôi phục các đoạn đã xem. Bấm Phát để tiếp tục học.' : 'Xem thử: không lưu tiến độ.';
                activityTimer = setInterval(sample,250); saveTimer = setInterval(() => save(),10000);
            },0);
            adapter = await window.edulabVideoPlayer.create({id:'lessonPlayer',provider:root.dataset.provider,
                sourceUrl:root.dataset.source,youtubeId:root.dataset.youtube,start:position/1000,
                playerPath:root.dataset.playerPath,playerAvailable:root.dataset.playerAvailable==='true'}, {
                ready:onReady, play:() => { lastSample=null; sample(); }, seek:() => { lastSample=null; },
                buffering:() => { lastSample=null; }, pause:() => { sample(true); lastSample=null; save(); },
                end:() => { sample(true); lastSample=null; save(); }, error:fail
            });
            // Also supports free builds without Events tracking: readiness and playback can be polled through API.
            const poll = setInterval(() => { if (halted || initialized) clearInterval(poll); else if (adapter.duration()>0) { clearInterval(poll); onReady(); } },500);
            setTimeout(() => { clearInterval(poll); if (!initialized && !halted) fail(new Error('Trình phát chưa tải được metadata. Kiểm tra bản PlayerJS/HLS, link và CORS rồi tải lại trang.')); },25000);
        } catch (error) { fail(error); }
    }
    play.addEventListener('click',() => {
        if (!adapter) return;
        const wasPlaying = adapter.playing(); sample(); lastSample=null; adapter.toggle(); sample();
        if (wasPlaying) save();
    });
    seek.addEventListener('input',() => {
        lastSample = null; position = Math.round(Number(seek.value)*1000); adapter?.seek(position/1000); draw();
    });
    seek.addEventListener('change',() => save());
    document.getElementById('videoFullscreen').addEventListener('click',async () => {
        try { await document.getElementById('videoFrame').requestFullscreen(); }
        catch { notice.textContent = 'Trình duyệt không hỗ trợ toàn màn hình tùy biến. Các đoạn đã xem vẫn được lưu.'; }
    });
    document.addEventListener('visibilitychange',() => { if (document.hidden) { sample(true); lastSample=null; adapter?.pause(); save({keepalive:true}); } });
    window.addEventListener('pagehide',() => { save({keepalive:true}); clearInterval(activityTimer); clearInterval(saveTimer); });
    window.addEventListener('beforeunload',event => {
        if (student && (pending.length || retryReport || inFlight)) { event.preventDefault(); event.returnValue=''; }
    });
    init();
})();