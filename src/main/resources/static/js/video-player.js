(() => {
    'use strict';
    const scripts = new Map();
    function loadScript(url) {
        if (!scripts.has(url)) scripts.set(url, new Promise((resolve, reject) => {
            const script = document.createElement('script'); script.src = url;
            const fail = () => {
                clearTimeout(timer); script.remove(); scripts.delete(url);
                reject(new Error('Không tải được trình phát video. Kiểm tra tệp đã cài, CSP và kết nối rồi thử lại.'));
            };
            const timer = setTimeout(fail, 20000);
            script.onload = () => { clearTimeout(timer); resolve(); }; script.onerror = fail;
            document.head.append(script);
        }));
        return scripts.get(url);
    }
    const hlsEvents = new Map(), hlsReady = new Map();
    let hlsCallbacksInstalled = false;
    function installHlsCallbacks() {
        if (hlsCallbacksInstalled) return;
        hlsCallbacksInstalled = true;
        const previousEvents = window.PlayerjsEvents, previousReady = window.EdulabPlayerReady;
        window.PlayerjsEvents = (event, id, data) => {
            hlsEvents.get(id)?.(event, data);
            if (typeof previousEvents === 'function') previousEvents(event, id, data);
        };
        window.EdulabPlayerReady = id => {
            hlsReady.get(id)?.();
            if (typeof previousReady === 'function') previousReady(id);
        };
    }
    let youtubeReady;
    function youtube() {
        if (window.YT?.Player) return Promise.resolve();
        if (!youtubeReady) youtubeReady = new Promise((resolve, reject) => {
            const timer = setTimeout(() => { youtubeReady = null; reject(new Error('YouTube chưa sẵn sàng. Hãy kiểm tra kết nối rồi tải lại trang.')); }, 20000);
            window.onYouTubeIframeAPIReady = () => { clearTimeout(timer); resolve(); };
            loadScript('https://www.youtube.com/iframe_api').catch(error => { clearTimeout(timer); youtubeReady = null; reject(error); });
        });
        return youtubeReady;
    }
    async function create(config, callbacks = {}) {
        let player, dead = false;
        const notify = (event, value) => { if (!dead) callbacks[event]?.(value); };
        if (config.provider === 'YOUTUBE') {
            await youtube();
            player = new window.YT.Player(config.id, {
                host:'https://www.youtube-nocookie.com', width:'100%', height:'100%', videoId:config.youtubeId,
                playerVars:{origin:location.origin, playsinline:1, autoplay:0, enablejsapi:1},
                events:{onReady:() => notify('ready'), onStateChange:event => {
                    if (event.data === 1) notify('play');
                    else if (event.data === 2) notify('pause');
                    else if (event.data === 3) notify('buffering');
                    else if (event.data === 0) notify('end');
                }, onError:event => notify('error', new Error('Không phát được YouTube (mã ' + event.data + '). Video có thể bị xóa, hạn chế nhúng hoặc thiếu Referer.'))}
            });
            return {time:() => player.getCurrentTime?.() || 0, duration:() => player.getDuration?.() || 0,
                playing:() => player.getPlayerState?.() === 1, seek:time => player.seekTo(time,true),
                toggle:() => player.getPlayerState?.() === 1 ? player.pauseVideo() : player.playVideo(),
                pause:() => player.pauseVideo(), destroy:() => { dead=true; player.destroy(); }};
        }
        if (config.provider !== 'PLAYERJS') throw new Error('Nguồn video không hợp lệ.');
        const mp4 = new URL(config.sourceUrl).pathname.toLowerCase().endsWith('.mp4');
        if (!config.playerAvailable) throw new Error('PlayerJS chưa được cài tại ' + config.playerPath + '. Admin cần cài bản chính thức rồi build và khởi động lại ứng dụng để phát HLS/MP4. YouTube không cần PlayerJS.');
        if (!/^\/js\/[A-Za-z0-9_/-]+\.js$/.test(config.playerPath) || config.playerPath.includes('..'))
            throw new Error('Đường dẫn PlayerJS phải là tệp /js/...js cùng website.');
        await loadScript(window.edulab.context + config.playerPath);
        if (typeof window.Playerjs !== 'function') throw new Error('Tệp đã cài không phải PlayerJS hợp lệ.');
        installHlsCallbacks();
        const holder = document.getElementById(config.id), listeners = [];
        let media, metadataReady = false, healthTimer, failed = false;
        const onReady = () => {
            // PlayerJS init/ready means the UI exists, not that media metadata
            // has loaded. Never enable playback/resume on an empty media timeline.
            const duration = Number(player?.api('duration'));
            if (!dead && !failed && !metadataReady && Number.isFinite(duration) && duration > 0) {
                metadataReady = true; notify('ready');
            }
        };
        const onError = data => {
            if (failed || dead) return;
            failed = true; clearInterval(healthTimer);
            notify('error',new Error((mp4 ? 'Không phát được MP4. Kiểm tra nguồn HTTPS, CORS, codec và quyền truy cập. ' : 'Không phát được HLS (.m3u8). Kiểm tra nguồn HTTPS, CORS và manifest/segment/khóa HLS. ') + String(data || '').slice(0,150)));
        };
        const listen = (target, event, handler) => {
            target.addEventListener(event, handler); listeners.push(() => target.removeEventListener(event, handler));
        };
        const observeMedia = () => {
            const next = holder.querySelector('video');
            if (!next || next === media) return;
            media = next;
            // The official default build has the JS API but not Events tracking.
            // Use standard media events as a fallback, without modifying vendor
            // code or relying on PRO listeners. Builder callbacks still work for
            // builds that don't expose an HTML video in the container.
            for (const [event, action] of [['play','play'],['pause','pause'],['waiting','buffering'],
                    ['stalled','buffering'],['seeking','seek'],['seeked','seek'],['ended','end']])
                listen(next, event, () => { if (media === next) notify(action); });
            listen(next, 'loadedmetadata', onReady); listen(next, 'durationchange', onReady);
            listen(next, 'error', () => { if (media === next) onError('Mã media ' + (next.error?.code || 0)); });
            onReady();
        };
        hlsEvents.set(config.id, (event, data) => {
            if (event === 'init' || event === 'metadata' || event === 'duration') onReady();
            else if (['play','pause','buffering','end','seek'].includes(event) && !media) notify(event);
            else if (event === 'error' || event === 'loaderror') onError(data);
        });
        hlsReady.set(config.id, () => { observeMedia(); onReady(); });
        const observer = new MutationObserver(observeMedia);
        observer.observe(holder, {childList:true, subtree:true});
        const cleanup = () => {
            clearInterval(healthTimer); observer.disconnect(); listeners.forEach(remove => remove());
            hlsEvents.delete(config.id); hlsReady.delete(config.id);
        };
        try {
            // Official bundle option: sets crossorigin before loading src, for
            // MP4 pixels and native HLS too. Never reload to bypass denied CORS.
            player = new window.Playerjs({id:config.id, file:config.sourceUrl, autoplay:0, preload:1,
                hls:mp4 ? 0 : 1,tagcors:1,start:Number(config.start)||0,ready:'EdulabPlayerReady'});
            if (typeof player.api !== 'function') throw new Error('Bản PlayerJS cần bật Javascript API.');
            observeMedia();
            healthTimer = setInterval(() => {
                // hls.js failures don't always become HTML media errors. The
                // free API exposes the error even when Events tracking is off.
                const error = mp4 ? null : player.api('hlserror');
                if (error?.fatal) onError(error.details || error.type);
                else { observeMedia(); onReady(); }
            },500);
        } catch (error) { dead = true; cleanup(); throw error; }
        const api = (name, value) => player.api(name,value);
        return {time:() => Number(api('time')) || 0, duration:() => Number(api('duration')) || 0,
            playing:() => Boolean(api('playing')), seek:time => api('seek',time),
            toggle:() => api('toggle'), pause:() => api('pause'), destroy:() => {
                if (dead) return;
                dead = true; cleanup(); api('destroy'); holder.replaceChildren();
            }};
    }
    function youtubeId(value) {
        const url = new URL(value);
        if (url.protocol !== 'https:' || url.username || url.password) throw new Error('Hãy nhập link HTTPS hợp lệ.');
        let id;
        if (url.hostname === 'youtu.be') id = url.pathname.slice(1);
        else if (['youtube.com','www.youtube.com','m.youtube.com','www.youtube-nocookie.com'].includes(url.hostname))
            id = url.pathname === '/watch' ? url.searchParams.get('v') : /^\/(embed|shorts)\//.test(url.pathname) ? url.pathname.split('/').pop() : null;
        if (!/^[A-Za-z0-9_-]{11}$/.test(id || '')) throw new Error('Link không phải một video YouTube hợp lệ.');
        return id;
    }
    window.edulabVideoPlayer = {create,youtubeId};
})();