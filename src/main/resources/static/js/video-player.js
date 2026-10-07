(() => {
    'use strict';
    const scripts = new Map();
    function loadScript(url) {
        if (!scripts.has(url)) scripts.set(url, new Promise((resolve, reject) => {
            const script = document.createElement('script'); script.src = url;
            script.onload = resolve; script.onerror = () => { scripts.delete(url); reject(new Error('Không tải được trình phát video.')); };
            document.head.append(script);
        }));
        return scripts.get(url);
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
        if (!config.playerAvailable) throw new Error('PlayerJS chưa được cài. Admin cần cài bản miễn phí bật HLS và API Events; không thể phát m3u8 lúc này.');
        await loadScript(window.edulab.context + config.playerPath);
        if (typeof window.Playerjs !== 'function') throw new Error('Tệp đã cài không phải PlayerJS hợp lệ.');
        // Free Builder must send API events to PlayerjsEvents. No PRO markup is used.
        window.PlayerjsEvents = (event, id, data) => {
            if (id !== config.id || dead) return;
            if (event === 'init' || event === 'metadata') notify('ready');
            else if (event === 'play' || event === 'pause' || event === 'buffering' || event === 'end' || event === 'seek') notify(event);
            else if (event === 'error' || event === 'loaderror') notify('error',new Error('Không phát được m3u8. Kiểm tra nguồn, CORS và domain manifest/segment/khóa HLS. ' + String(data || '').slice(0,150)));
        };
        window.EdulabPlayerReady = id => { if (id === config.id) notify('ready'); };
        player = new window.Playerjs({id:config.id, file:config.sourceUrl, autoplay:0, hls:1,start:Number(config.start)||0,ready:'EdulabPlayerReady'});
        const api = (name, value) => player.api(name,value);
        return {time:() => Number(api('time')) || 0, duration:() => Number(api('duration')) || 0,
            playing:() => Boolean(api('playing')), seek:time => api('seek',time),
            toggle:() => api('toggle'), pause:() => api('pause'), destroy:() => { dead=true; api('destroy'); document.getElementById(config.id)?.replaceChildren(); }};
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