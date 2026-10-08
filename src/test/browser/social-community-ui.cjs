/* Offline UI regression: real rendered JSP fixtures + real assets, intercepted API only.
 * First run SocialCommunityViewTest to generate target/community-ui-fixtures/*.html.
 * No request is forwarded to a server, database, object store or external origin.
 */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const fixtures=path.join(root,'target/community-ui-fixtures');
const assets=path.join(root,'src/main/resources/static');
const screenshots=path.join(root,'target/community-ui-screenshots');
const origin='https://community.test';
const illustration='<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600"><rect width="900" height="600" fill="#dce9ee"/><rect y="380" width="900" height="220" fill="#bca589"/><rect x="230" y="130" width="350" height="260" rx="12" fill="#fffaf1"/><path d="M240 345 400 190 570 345" fill="#97b7aa"/><circle cx="510" cy="190" r="25" fill="#edc66c"/><rect x="170" y="395" width="440" height="24" rx="6" fill="#e8eff7"/><rect x="190" y="420" width="400" height="24" rx="6" fill="#507893"/></svg>';
const makePost=(id,images=[])=>({id,author:'Hà Linh',body:'Chia sẻ một chút niềm vui cùng mọi người.',mine:false,
    created_at:'08/10/2026 · 14:00',like_count:2,reaction_count:2,reaction:'',reaction_counts:{LIKE:1,LOVE:1},comment_count:1,liked:false,images});
const photo=(id,scan_status='CLEAN')=>({id,scan_status});

async function main() {
    for(const variant of ['feed','empty','detail','admin']) assert.ok(fs.existsSync(path.join(fixtures,variant+'.html')),'Run SocialCommunityViewTest first');
    fs.mkdirSync(screenshots,{recursive:true});
    const browser=await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[],unexpected=[],calls=[];
    async function open(width,variant='feed',javaScriptEnabled=true,height=900) {
        const context=await browser.newContext({viewport:{width,height},javaScriptEnabled,reducedMotion:'reduce',serviceWorkers:'block'});
        const posts=new Map(),comments=new Map();
        await context.route('**/*',async route=>{
            const request=route.request(),url=new URL(request.url()),method=request.method(),pathname=url.pathname;
            const json=data=>route.fulfill({json:data});
            if(url.origin!==origin) {unexpected.push(request.url());return route.abort();}
            if(pathname==='/edulab/community') return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(fixtures,variant+'.html'))});
            if(pathname==='/edulab/api/v1/auth/csrf') return json({headerName:'X-CSRF-TOKEN',token:'fixture-token'});
            if(/^\/edulab\/api\/v1\/files\/\d+\/image$/.test(pathname)) return route.fulfill({contentType:'image/svg+xml',body:illustration});
            if(pathname.startsWith('/edulab/api/')) {
                calls.push({method,path:pathname,body:request.postDataJSON()});
                if(method!=='GET') assert.equal(request.headers()['x-csrf-token'],'fixture-token','Real API helper must send CSRF');
                if(pathname==='/edulab/api/v1/community/posts') {
                    if(method==='GET') return json({items:[makePost(198,[photo(502),photo(503),photo(504)]),makePost(197,[photo(505),photo(506),photo(507),photo(508),photo(509)]),makePost(196,[photo(510,'PENDING_SCAN')])],hasMore:false,nextCursor:null});
                    if(method==='POST') {posts.set(300,{...makePost(300),...request.postDataJSON(),mine:true,author:'Minh Anh',like_count:0,comment_count:0});return json({id:300});}
                }
                const match=pathname.match(/^\/edulab\/api\/v1\/community\/posts\/(\d+)(.*)$/);
                if(match) {
                    const id=Number(match[1]),suffix=match[2];
                    if(!suffix && method==='GET' && posts.has(id)) return json(posts.get(id));
                    if(!suffix && ['PUT','DELETE'].includes(method)) return route.fulfill({status:204});
                    if(suffix==='/reaction') {
                        const reaction=method==='PUT'?request.postDataJSON().reaction:null;
                        const counts={LIKE:2,LOVE:1};if(reaction)counts[reaction]=(counts[reaction] || 0)+1;
                        return json({reaction,count:reaction?4:3,counts});
                    }
                    if(suffix==='/comments') {
                        if(!comments.has(id)) comments.set(id,[{id:700,author:'Bảo An',body:'Một góc thật dễ thương!',created_at:'14:35',mine:false}]);
                        if(method==='GET') return json({items:url.searchParams.has('after')?comments.get(id).slice(1):comments.get(id).slice(0,1),hasMore:!url.searchParams.has('after'),nextCursor:url.searchParams.has('after')?null:700});
                        if(method==='POST') {const comment={id:701,author:'Minh Anh',body:request.postDataJSON().body,created_at:'2026-10-08T14:40:00Z',mine:true};comments.get(id).push(comment);return json(comment);}
                    }
                    if(/^\/comments\/\d+$/.test(suffix) && ['PUT','DELETE'].includes(method)) return route.fulfill({status:204});
                    if(suffix==='/reports' || suffix==='/hide') return route.fulfill({status:204});
                }
                unexpected.push(method+' '+pathname);return route.fulfill({status:500,json:{message:'Unexpected mock API request'}});
            }
            const relative=pathname.replace(/^\/edulab\//,''),file=path.resolve(assets,relative);
            if(file.startsWith(assets+path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
                const mime={'.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml','.woff2':'font/woff2'}[path.extname(file)] || 'application/octet-stream';
                return route.fulfill({contentType:mime,body:fs.readFileSync(file)});
            }
            unexpected.push(method+' '+pathname);return route.abort();
        });
        const page=await context.newPage();
        page.on('pageerror',error=>errors.push(error.message));
        page.setDefaultTimeout(5000);
        await page.goto(origin+'/edulab/community');
        await page.evaluate(()=>document.fonts.ready);
        return {context,page};
    }
    async function noOverflow(page,label) {
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,label+' has no horizontal overflow');
    }
    async function menuKeyboard(card) {
        const summary=card.locator('.social-post-menu summary');
        await summary.focus();await summary.press('Enter');
        await card.locator('.social-menu-items button').first().waitFor({state:'visible'});
        await summary.press('Tab');
        assert.equal(await card.locator('.social-menu-items button').first().evaluate(node=>node===document.activeElement),true);
        await card.locator('.social-menu-items button').first().press('Escape');
        assert.equal(await card.locator('details').evaluate(node=>node.open),false);
        assert.equal(await summary.evaluate(node=>node===document.activeElement),true);
    }
    try {
        const {context,page}=await open(1440);
        assert.equal(await page.locator('.social-hero').count(),0);
        assert.equal(await page.locator('h1').innerText(),'Bảng tin');
        assert.equal(await page.locator('#socialBody').getAttribute('placeholder'),'Bạn đang nghĩ gì?');
        assert.ok(await page.locator('#socialComposer').evaluate(node=>node.getBoundingClientRect().top>=document.querySelector('.discovery-tabs').getBoundingClientRect().bottom));
        assert.equal(await page.locator('.discovery-tabs [aria-current=page]').getAttribute('href'),'/edulab/community');
        assert.equal(await page.locator('#utilitySubject option').count(),3);
        assert.equal(await page.locator('.utility-column').evaluate(node=>node.getBoundingClientRect().left>document.querySelector('.main-content').getBoundingClientRect().right),true);
        await noOverflow(page,'Desktop');
        const initial=page.locator('.social-post[data-id="200"]'),other=page.locator('.social-post[data-id="199"]');
        assert.equal(await other.locator('.social-post-text script').count(),0);
        assert.equal(await page.evaluate(()=>window.untrustedPost),undefined);
        await menuKeyboard(initial);
        await other.locator('summary').click();
        assert.equal(await other.locator('[data-action=report]').isVisible(),true);
        assert.equal(await other.locator('[data-action=edit]').count(),0);
        await page.locator('h1').click();assert.equal(await other.locator('details').evaluate(node=>node.open),false);
        await initial.locator('[data-action=like]').click();
        await page.waitForFunction(()=>document.querySelector('[data-id="200"] [data-action=like]').getAttribute('aria-pressed')==='true');
        assert.equal(await initial.locator('.social-like-count').innerText(),'4');
        await initial.locator('[data-action=comments]').click();
        await initial.locator('.social-comment').waitFor();
        assert.equal(await initial.locator('[data-action=comments]').getAttribute('aria-expanded'),'true');
        assert.equal(await initial.locator('.social-comment-form textarea').evaluate(node=>node===document.activeElement),true);
        await initial.locator('.social-comment-form textarea').fill('<img src=x onerror=alert(1)> Chào bạn!');
        await initial.locator('.social-comment-form button').click();
        await initial.getByText('Đã gửi bình luận.',{exact:true}).waitFor();
        assert.equal(await initial.locator('.social-comment-count').innerText(),'2');
        assert.equal(await initial.locator('.social-comment img').count(),0);
        assert.equal(await initial.locator('.social-comment').count(),2,'New comment appears without resetting the first page');
        await initial.locator('.social-comment-more').click();
        await initial.locator('.social-comment-more').waitFor({state:'hidden'});
        assert.equal(await initial.locator('.social-comment').count(),2,'Later pages do not duplicate the newly submitted comment');
        const own=initial.locator('.social-comment[data-id="701"]');
        await own.locator('[data-action=edit-comment]').click();
        await own.getByLabel('Sửa bình luận').fill('Đã sửa <b>an toàn</b>');await own.getByRole('button',{name:'Lưu',exact:true}).click();
        await initial.getByText('Đã sửa bình luận.',{exact:true}).waitFor();
        assert.equal(await own.locator('.social-comment-body').innerText(),'Đã sửa <b>an toàn</b>');
        assert.equal(await own.locator('b').count(),0);
        await own.locator('[data-action=edit-comment]').click();await own.getByLabel('Sửa bình luận').press('Escape');
        assert.equal(await own.locator('.social-comment-edit').count(),0);
        page.once('dialog',dialog=>dialog.accept());await own.getByLabel('Xóa bình luận',{exact:true}).click();await own.waitFor({state:'detached'});
        assert.equal(await initial.locator('.social-comment-count').innerText(),'1');
        await initial.locator('[data-action=comments]').click();
        assert.equal(await initial.locator('[data-action=comments]').getAttribute('aria-expanded'),'false');
        // Exercise the safe clipboard fallback without requesting browser permissions.
        await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{value:{writeText:async()=>{throw new Error('denied');}}}));
        await initial.locator('[data-action=share]').click();
        assert.equal(await initial.getByLabel('Liên kết bài viết').inputValue(),origin+'/edulab/community/posts/200');
        await initial.locator('summary').click();
        page.once('dialog',dialog=>dialog.accept('Nội dung đã sửa <b>vẫn là văn bản</b>'));
        await initial.locator('[data-action=edit]').click();
        await initial.getByText('Đã sửa bài viết.',{exact:true}).waitFor();
        assert.equal(await initial.locator('.social-post-text b').count(),0);
        await page.locator('#socialPublish').click();
        await page.getByText('Nhập nội dung hoặc chọn ít nhất một ảnh.',{exact:true}).waitFor();
        await page.locator('#socialBody').fill('Bài viết thử chỉ tồn tại trong API giả lập.');
        await page.locator('#socialPublish').click();await page.getByText('Đã đăng bài viết!',{exact:true}).waitFor();
        const added=page.locator('.social-post[data-id="300"]');await added.waitFor();await menuKeyboard(added);
        assert.equal(await added.locator('.social-post-head .social-avatar').innerText(),'M');
        assert.equal(await added.locator('[data-action=like] use').getAttribute('href'),'/edulab/css/icons.svg#like');
        assert.equal(await added.locator('[data-action=comments]').getAttribute('aria-controls'),'socialComments300');
        await page.locator('#socialMore').click();await page.locator('.social-post[data-id="198"]').waitFor();
        assert.equal(await page.locator('.social-gallery[data-count="3"] img').count(),3);
        assert.equal(await page.locator('.social-gallery[data-count="5"] img').count(),5);
        assert.equal(await page.locator('.social-post[data-id="196"] img').count(),0,'Unscanned images are never loaded');
        assert.equal(await page.locator('#socialMore').isVisible(),false);
        await menuKeyboard(page.locator('.social-post[data-id="198"]'));
        await noOverflow(page,'Dynamic feed');
        await added.locator('summary').click();
        page.once('dialog',dialog=>dialog.accept());
        await added.locator('[data-action=delete]').click();
        await added.waitFor({state:'detached'});
        await other.locator('summary').click();
        page.once('dialog',dialog=>dialog.accept('Kiểm tra báo cáo bằng API giả lập'));
        await other.locator('[data-action=report]').click();
        await other.getByText('Đã gửi báo cáo. Cảm ơn bạn!',{exact:true}).waitFor();
        // Local canvas compression/preview is also tested without uploading anything.
        const image=await page.evaluate(()=>{const canvas=document.createElement('canvas');canvas.width=1800;canvas.height=1200;
            const ctx=canvas.getContext('2d');ctx.fillStyle='#97b7aa';ctx.fillRect(0,0,1800,1200);return canvas.toDataURL('image/png').split(',')[1];});
        await page.locator('#socialFiles').setInputFiles({name:'fixture.png',mimeType:'image/png',buffer:Buffer.from(image,'base64')});
        await page.locator('#socialPreview img').waitFor();
        assert.ok(Number((await page.locator('#socialPreview small').innerText()).replace(' KB',''))<=500);
        assert.ok(await page.locator('#socialPreview img').evaluate(node=>node.naturalWidth<=1600 && node.src.startsWith('blob:')));
        await page.locator('#socialPreview button').click();assert.equal(await page.locator('#socialPreview img').count(),0);
        await context.close();

        // Failed mutations must retain selection/drafts, and failed reads stay retryable.
        const failure=await open(390),failurePage=failure.page,failureCard=failurePage.locator('.social-post').first();
        const reject=route=>route.fulfill({status:503,json:{message:'Lỗi mạng giả lập'}});
        await failurePage.route('**/posts/200/reaction',reject);
        await failureCard.locator('[data-action=like]').click();await failureCard.getByText('Lỗi mạng giả lập',{exact:true}).waitFor();
        assert.equal(await failureCard.locator('[data-action=like]').getAttribute('aria-pressed'),'false');
        assert.equal(await failureCard.locator('.social-like-count').innerText(),'3');
        await failurePage.unroute('**/posts/200/reaction',reject);
        await failurePage.route('**/posts/200/comments?*',reject);
        await failureCard.locator('[data-action=comment-count]').click();await failureCard.getByRole('button',{name:'Thử tải lại bình luận'}).waitFor();
        await failurePage.unroute('**/posts/200/comments?*',reject);
        await failureCard.getByRole('button',{name:'Thử tải lại bình luận'}).click();await failureCard.locator('.social-comment').waitFor();
        const draft=failureCard.locator('.social-comment-form textarea');
        await draft.fill('   ');await draft.press('Enter');await failureCard.getByText('Nhập nội dung bình luận trước khi gửi.',{exact:true}).waitFor();
        await draft.fill('Giữ nội dung khi lỗi');await draft.press('Shift+Enter');await draft.press('End');await draft.press('x');
        assert.match(await draft.inputValue(),/\n/);
        await failurePage.route('**/posts/200/comments',reject);
        await draft.press('Enter');await failureCard.getByText('Lỗi mạng giả lập',{exact:true}).waitFor();
        assert.match(await draft.inputValue(),/Giữ nội dung khi lỗi/);
        assert.equal(await failureCard.locator('.social-comment-count').innerText(),'1');
        await failurePage.unroute('**/posts/200/comments',reject);
        await draft.press('Enter');await failureCard.getByText('Đã gửi bình luận.',{exact:true}).waitFor();assert.equal(await draft.inputValue(),'');
        const failedOwn=failureCard.locator('.social-comment[data-id="701"]');
        await failurePage.route('**/posts/200/comments/701',reject);
        await failedOwn.locator('[data-action=edit-comment]').click();await failedOwn.getByLabel('Sửa bình luận').fill('Bản sửa chưa lưu');
        await failedOwn.getByRole('button',{name:'Lưu',exact:true}).click();await failureCard.getByText('Lỗi mạng giả lập',{exact:true}).waitFor();
        assert.equal(await failedOwn.getByLabel('Sửa bình luận').inputValue(),'Bản sửa chưa lưu');
        await failedOwn.getByRole('button',{name:'Hủy',exact:true}).click();
        failurePage.once('dialog',dialog=>dialog.accept());await failedOwn.getByLabel('Xóa bình luận',{exact:true}).click();
        await failurePage.waitForFunction(()=>!document.querySelector('[data-id="701"] [data-action=delete-comment]').disabled);
        assert.equal(await failedOwn.count(),1);assert.equal(await failureCard.locator('.social-comment-count').innerText(),'2');
        await failure.context.close();

        for(const width of [1440,1280,1024,768,639,390,320]) {
            const screen=await open(width);
            await noOverflow(screen.page,'Width '+width);
            assert.ok((await screen.page.locator('#socialComposer').boundingBox()).height<230,'Compact composer');
            for(const button of await screen.page.locator('.social-actions button:visible').all()) assert.ok((await button.boundingBox()).height>=44,'Touch target');
            const card=screen.page.locator('.social-post').first(),toggle=card.getByRole('button',{name:'Chọn cảm xúc',exact:true});
            await toggle.focus();await toggle.press('Enter');
            const picker=card.getByRole('group',{name:'Chọn cảm xúc cho bài viết'});await picker.waitFor({state:'visible'});
            assert.equal(await picker.getByRole('button').count(),7);
            await picker.getByRole('button').first().press('ArrowRight');
            assert.equal(await picker.getByRole('button',{name:'Yêu thích',exact:true}).evaluate(node=>node===document.activeElement),true);
            await picker.getByRole('button',{name:'Yêu thích',exact:true}).press('Escape');
            assert.equal(await toggle.evaluate(node=>node===document.activeElement),true);
            for(const [type,label] of Object.entries({LIKE:'Thích',LOVE:'Yêu thích',CARE:'Thương thương',HAHA:'Haha',WOW:'Wow',SAD:'Buồn',ANGRY:'Phẫn nộ'})) {
                await toggle.focus();await toggle.press('Enter');await picker.getByRole('button',{name:label,exact:true}).click();
                await screen.page.waitForFunction(({id,type})=>document.querySelector('[data-id="'+id+'"] [data-action=like]').dataset.reaction===type,{id:'200',type});
                assert.equal(await card.locator('.social-like-count').innerText(),'4','Switching does not inflate count');
                await noOverflow(screen.page,'Selected '+label+' at '+width);
            }
            await card.locator('[data-action=like]').click();await screen.page.waitForFunction(()=>document.querySelector('[data-id="200"] [data-action=like]').dataset.reaction==='');
            assert.equal(await card.locator('.social-like-count').innerText(),'3');
            await card.locator('[data-action=comments]').click();await card.locator('.social-comment').waitFor();
            await toggle.focus();await toggle.press('Enter');await noOverflow(screen.page,'Picker and comments at '+width);
            if([1440,390,320].includes(width)) await screen.page.screenshot({path:path.join(screenshots,'reactions-comments-'+width+'.png'),fullPage:false});
            await screen.page.locator('h1').click();
            await menuKeyboard(screen.page.locator('.social-post').first());
            await screen.page.locator('#socialMore').click();
            await screen.page.locator('.social-gallery[data-count="5"] img').last().scrollIntoViewIfNeeded();
            await noOverflow(screen.page,'Gallery width '+width);
            const gallery=await screen.page.locator('.social-gallery[data-count="3"]').boundingBox();
            assert.ok(gallery.height<gallery.width*1.2,'Three-photo grid stays bounded');
            await screen.page.evaluate(()=>window.scrollTo(0,0));
            if([1440,768,390,320].includes(width)) await screen.page.screenshot({path:path.join(screenshots,'feed-'+width+'.png'),fullPage:false});
            await screen.context.close();
        }
        for(const variant of ['empty','detail','admin']) {
            const screen=await open(390,variant);
            await noOverflow(screen.page,variant);
            assert.equal(await screen.page.locator('#socialForm').count(),variant==='detail'?0:1);
            assert.equal(await screen.page.locator('#socialEmpty').count(),variant==='empty'?1:0);
            assert.equal(await screen.page.locator('[data-action=hide]').count(),variant==='admin'?2:0);
            await screen.context.close();
        }
        const noScript=await open(390,'feed',false);
        assert.equal(await noScript.page.locator('.social-post').count(),2);
        await noScript.page.locator('.social-post').first().locator('summary').click();
        assert.equal(await noScript.page.locator('[data-action=edit]').isVisible(),true,'Native disclosure works without JS');
        assert.equal(await noScript.page.locator('noscript a').isVisible(),true);
        await noScript.context.close();
        assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
        assert.ok(calls.some(call=>call.method==='POST' && call.path.endsWith('/posts')));
        assert.equal(calls.some(call=>call.path.includes('/files/')),false,'No real or simulated upload');
        console.log('PASS: rendered JSP, dynamic feed, composer, menus/keyboard, seven reactions, comment pagination/edit/delete, escaping, sharing, local compression, 7 viewport widths, detail/empty/admin, no JS. All API requests mocked.');
        console.log('Screenshots: '+screenshots);
    } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});