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
const uploadFixtures=path.join(root,'target/community-upload-fixtures');
const origin='https://community.test';
const illustration='<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600"><rect width="900" height="600" fill="#dce9ee"/><rect y="380" width="900" height="220" fill="#bca589"/><rect x="230" y="130" width="350" height="260" rx="12" fill="#fffaf1"/><path d="M240 345 400 190 570 345" fill="#97b7aa"/><circle cx="510" cy="190" r="25" fill="#edc66c"/><rect x="170" y="395" width="440" height="24" rx="6" fill="#e8eff7"/><rect x="190" y="420" width="400" height="24" rx="6" fill="#507893"/></svg>';
const makePost=(id,images=[])=>({id,author:'Hà Linh',body:'Chia sẻ một chút niềm vui cùng mọi người.',mine:false,
    created_at:'08/10/2026 · 14:00',like_count:2,reaction_count:2,reaction:'',reaction_counts:{LIKE:1,LOVE:1},comment_count:1,liked:false,images});
const photo=(id,scan_status='CLEAN',image_ready=scan_status==='CLEAN')=>({id,scan_status,image_ready});

async function main() {
    for(const variant of ['feed','empty','detail','admin']) assert.ok(fs.existsSync(path.join(fixtures,variant+'.html')),'Run SocialCommunityViewTest first');
    fs.mkdirSync(screenshots,{recursive:true});
    fs.mkdirSync(uploadFixtures,{recursive:true});
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
                    if(method==='GET') return json({items:[makePost(198,[photo(502,'PENDING_SCAN',true),photo(503),photo(504)]),makePost(197,[photo(505),photo(506),photo(507),photo(508),photo(509)]),makePost(196,[photo(510,'PENDING_SCAN')])],hasMore:false,nextCursor:null});
                    if(method==='POST') {const data=request.postDataJSON();posts.set(300,{...makePost(300,data.imageIds.map(id=>photo(id,'PENDING_SCAN',true))),...data,mine:true,author:'Minh Anh',like_count:0,comment_count:0});return json({id:300});}
                }
                if(pathname==='/edulab/api/v1/files/community/presign' && method==='POST') return json({uploadId:'mock-community-ticket',url:origin+'/edulab/mock-r2',method:'PUT',headers:{'Content-Type':'image/jpeg'}});
                if(pathname==='/edulab/api/v1/files/uploads/mock-community-ticket/complete' && method==='POST') return json({fileId:600,status:'COMPLETED',scanStatus:'PENDING_SCAN',communityReady:true});
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
        assert.equal(await page.locator('.social-post[data-id="196"] img').count(),0,'Unapproved unscanned images are never loaded');
        assert.equal(await page.locator('.social-post[data-id="198"] img').count(),3,'Validated community JPEGs render without antivirus');
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
        await page.waitForFunction(()=>!document.getElementById('socialPublish').disabled);
        assert.equal(await page.locator('#socialPreview small').count(),0,'No compression size labels');
        assert.equal(await page.locator('#socialFileHint').count(),0,'No upload implementation hint');
        assert.equal(await page.locator('#socialUploadStatus').innerText(),'','No compression success message');
        assert.ok(await page.locator('#socialPreview img').evaluate(node=>node.naturalWidth<=1600 && node.src.startsWith('blob:')));
        const uploaded=[];
        await page.route('**/edulab/mock-r2',route=>{
            assert.equal(route.request().method(),'PUT');assert.equal(route.request().headers()['content-type'],'image/jpeg');
            const bytes=route.request().postDataBuffer();assert.ok(bytes.length<=512000);uploaded.push(bytes);
            let pos=2;assert.equal(bytes.readUInt16BE(0),0xffd8);
            while(pos+3<bytes.length && bytes[pos]===0xff) {
                const marker=bytes[pos+1],length=bytes.readUInt16BE(pos+2);
                if(marker===0xda) break;
                assert.ok(marker===0xe0 || marker<0xe0 || marker>0xef,'No EXIF/ICC/APP metadata');
                assert.notEqual(marker,0xfe,'No JPEG comment metadata');
                pos+=length+2;
            }
            assert.equal(bytes[pos+1],0xda,'JPEG scan data preserved');
            fs.writeFileSync(path.join(uploadFixtures,'browser-'+uploaded.length+'.jpg'),bytes);
            return route.fulfill({status:200});
        });
        await page.locator('#socialPublish').click();await page.getByText('Đã đăng bài viết!',{exact:true}).waitFor();
        await page.locator('.social-post[data-id="300"] img').waitFor();
        assert.equal(await page.locator('#socialPreview img').count(),0,'Published image selection is cleared');
        // Small PNG/JPEG/WebP inputs used to skip the encoder entirely (<320px).
        for(const [format,mimeType] of [['png','image/png'],['jpg','image/jpeg'],['webp','image/webp']]) {
            const small=await page.evaluate(mime=>{const canvas=document.createElement('canvas');canvas.width=32;canvas.height=24;
                canvas.getContext('2d').fillRect(0,0,32,24);return canvas.toDataURL(mime).split(',')[1];},mimeType);
            await page.locator('#socialFiles').setInputFiles({name:'small.'+format,mimeType,buffer:Buffer.from(small,'base64')});
            await page.locator('#socialPreview img').waitFor();await page.waitForFunction(()=>!document.getElementById('socialPublish').disabled);
            assert.equal(await page.locator('#socialUploadStatus').innerText(),'');
            assert.equal(await page.locator('#socialPreview img').evaluate(img=>img.naturalWidth===32 && img.naturalHeight===24),true);
            await page.locator('#socialPublish').click();await page.getByText('Đã đăng bài viết!',{exact:true}).waitFor();
        }
        assert.equal(uploaded.length,4);
        // Invalid images stay rejected and do not make upload API calls.
        const presignsBefore=calls.filter(call=>call.path.endsWith('/community/presign')).length;
        await page.locator('#socialFiles').setInputFiles({name:'broken.png',mimeType:'image/png',buffer:Buffer.from('not an image')});
        await page.getByText('Không đọc được ảnh. Hãy chọn JPG, PNG hoặc WebP hợp lệ.',{exact:true}).waitFor();
        assert.equal(await page.locator('#socialPreview img').count(),0);
        assert.equal(calls.filter(call=>call.path.endsWith('/community/presign')).length,presignsBefore);
        // A failed completion must preserve the draft and allow a fresh upload ticket.
        await page.locator('#socialBody').fill('Giữ bài viết khi upload lỗi');
        await page.locator('#socialFiles').setInputFiles({name:'retry.png',mimeType:'image/png',buffer:Buffer.from(image,'base64')});
        await page.locator('#socialPreview img').waitFor();await page.waitForFunction(()=>!document.getElementById('socialPublish').disabled);
        const rejectComplete=route=>route.fulfill({json:{status:'REJECTED',fileId:null,communityReady:false,message:'File không hợp lệ. Chọn file và thử lại.'}});
        await page.route('**/uploads/mock-community-ticket/complete',rejectComplete);
        await page.route('**/uploads/mock-community-ticket',route=>{assert.equal(route.request().method(),'DELETE');return route.fulfill({json:{status:'REJECTED'}});});
        await page.locator('#socialPublish').click();await page.getByText('File không hợp lệ. Chọn file và thử lại.',{exact:true}).waitFor();
        assert.equal(await page.locator('#socialBody').inputValue(),'Giữ bài viết khi upload lỗi');
        assert.equal(await page.locator('#socialPreview img').count(),1);
        await page.unroute('**/uploads/mock-community-ticket/complete',rejectComplete);
        const retryPresigns=calls.filter(call=>call.path.endsWith('/community/presign')).length;
        await page.locator('#socialPublish').click();await page.getByText('Đã đăng bài viết!',{exact:true}).waitFor();
        assert.equal(calls.filter(call=>call.path.endsWith('/community/presign')).length,retryPresigns+1);
        assert.equal(await page.locator('#socialBody').inputValue(),'');
        await context.close();

        // Desktop utilities stay anchored, including when the panel needs its own scroll.
        for(const [width,height] of [[1440,900],[1280,640]]) {
            const screen=await open(width,'feed',true,height),rail=screen.page.locator('.utility-column');
            await screen.page.locator('#socialMore').click();await screen.page.locator('[data-id="197"]').waitFor();
            const before=await rail.boundingBox();
            assert.ok(before.height<=height-56-39,'Utilities fit viewport height');
            await screen.page.evaluate(()=>window.scrollTo(0,600));
            assert.ok(await screen.page.evaluate(()=>window.scrollY)>=500);
            const after=await rail.boundingBox();assert.ok(Math.abs(before.y-after.y)<2,'Utilities stay in place while feed scrolls');
            await rail.evaluate(node=>node.scrollTop=node.scrollHeight);
            assert.ok(await rail.evaluate(node=>node.scrollTop)>0,'Entire utilities panel remains reachable');
            assert.equal(await screen.page.locator('.utility-link').last().isVisible(),true);
            await noOverflow(screen.page,'Sticky utilities '+width);await screen.context.close();
        }
        // Photo viewer reuses the live post: comments/reactions/drafts survive closing.
        for(const [width,height] of [[1440,900],[768,900],[390,844],[320,560]]) {
            const screen=await open(width,'feed',true,height),p=screen.page;
            await p.locator('#socialMore').click();await p.locator('[data-id="198"]').waitFor();
            const card=p.locator('.social-post[data-id="198"]'),trigger=card.locator('.social-gallery a').nth(1);
            await trigger.scrollIntoViewIfNeeded();const scrollBefore=await p.evaluate(()=>scrollY);
            await trigger.click();
            const dialog=p.getByRole('dialog',{name:'Bài viết của Hà Linh'});
            await dialog.waitFor();await dialog.locator('.social-comment').waitFor();
            await p.waitForFunction(()=>document.getElementById('socialViewerImage').naturalWidth>0);
            const imageBox=await dialog.locator('#socialViewerImage').boundingBox(),stageBox=await dialog.locator('.social-viewer-stage').boundingBox();
            assert.ok(imageBox.height<=stageBox.height+1 && imageBox.width<=stageBox.width+1,'Large image stays contained, not cropped');
            await p.screenshot({path:path.join(screenshots,'photo-viewer-open-'+width+'.png'),fullPage:false});
            assert.equal(await p.locator('#socialFeed .social-post[data-id="198"]').count(),0,'Post is moved, not cloned');
            assert.equal(await p.locator('.social-post[data-id="198"]').count(),1);
            assert.equal(await dialog.locator('#socialViewerCounter').innerText(),'Ảnh 2 / 3');
            assert.match(await dialog.locator('#socialViewerImage').getAttribute('src'),/\/503\/image$/);
            assert.equal(await dialog.getByLabel('Đóng bài viết',{exact:true}).evaluate(node=>node===document.activeElement),true);
            assert.equal(await dialog.getByLabel('Nội dung bài viết và bình luận').getAttribute('tabindex'),'0');
            assert.equal(await dialog.locator('.social-comments').isVisible(),true);
            assert.equal(await dialog.locator('#socialViewerPermalink').getAttribute('href'),'/edulab/community/posts/198');
            assert.equal(await p.evaluate(()=>{
                const ids=[...document.querySelectorAll('[id]')].map(node=>node.id);return ids.length===new Set(ids).size;
            }),true,'No duplicate DOM IDs');
            await dialog.getByLabel('Ảnh tiếp theo',{exact:true}).click();assert.equal(await dialog.locator('#socialViewerCounter').innerText(),'Ảnh 3 / 3');
            await dialog.getByLabel('Ảnh tiếp theo',{exact:true}).press('ArrowRight');assert.equal(await dialog.locator('#socialViewerCounter').innerText(),'Ảnh 1 / 3');
            await dialog.getByLabel('Xem ảnh 2',{exact:true}).click();assert.equal(await dialog.locator('#socialViewerCounter').innerText(),'Ảnh 2 / 3');
            const textarea=dialog.locator('.social-comment-form textarea');
            await textarea.fill('Bình luận trong khung ảnh');await textarea.press('Enter');
            await dialog.getByText('Đã gửi bình luận.',{exact:true}).waitFor();
            assert.equal(await dialog.locator('.social-comment-count').innerText(),'2');
            await textarea.fill('Bản nháp giữ lại khi đóng');await textarea.press('ArrowLeft');
            assert.equal(await dialog.locator('#socialViewerCounter').innerText(),'Ảnh 2 / 3','Editing arrows do not change photo');
            await dialog.locator('[data-action=like]').click();await dialog.getByText('Đã bày tỏ cảm xúc Thích.',{exact:true}).waitFor();
            await noOverflow(p,'Viewer '+width);
            assert.equal(await dialog.evaluate(node=>node.scrollWidth<=node.clientWidth+1),true);
            const box=await dialog.boundingBox();assert.ok(box.x>=0 && box.y>=0 && box.width<=width && box.height<=height);
            // Native modal traps keyboard focus and locks document scroll.
            await dialog.getByLabel('Đóng bài viết',{exact:true}).focus();await p.keyboard.press('Shift+Tab');
            assert.equal(await p.evaluate(()=>document.getElementById('socialPostViewer').contains(document.activeElement)),true);
            assert.equal(await p.evaluate(()=>getComputedStyle(document.documentElement).overflow),'hidden');
            await p.screenshot({path:path.join(screenshots,'photo-viewer-'+width+'.png'),fullPage:false});
            await dialog.getByLabel('Đóng bài viết',{exact:true}).focus();await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});
            await p.locator('#socialFeed .social-post[data-id="198"]').waitFor();
            assert.equal(await p.locator('#socialFeed .social-post[data-id="198"]').count(),1);
            assert.equal(await trigger.evaluate(node=>node===document.activeElement),true);
            assert.ok(Math.abs((await p.evaluate(()=>scrollY))-scrollBefore)<3,'Feed scroll restored');
            assert.equal(await card.locator('.social-comment-count').innerText(),'2');
            assert.equal(await card.locator('[data-action=like]').getAttribute('aria-pressed'),'true');
            assert.equal(await card.locator('.social-comment-form textarea').inputValue(),'Bản nháp giữ lại khi đóng');
            await card.locator('.social-post-time').click();await dialog.waitFor();
            assert.equal(await dialog.locator('.social-comment').count(),2,'Loaded comment pages retained');
            const own=dialog.locator('.social-comment[data-id="701"]');
            await own.locator('[data-action=edit-comment]').click();await own.getByLabel('Sửa bình luận').fill('Sửa trong khung bài viết');
            await own.getByLabel('Sửa bình luận').press('Escape');assert.equal(await dialog.isVisible(),true,'Escape in editor cancels edit only');
            await own.locator('[data-action=edit-comment]').click();await own.getByLabel('Sửa bình luận').fill('Sửa trong khung bài viết');
            await own.getByRole('button',{name:'Lưu',exact:true}).click();await dialog.getByText('Đã sửa bình luận.',{exact:true}).waitFor();
            await dialog.locator('[data-action=choose-reaction]').click();
            await dialog.getByRole('group',{name:'Chọn cảm xúc cho bài viết'}).getByLabel('Yêu thích',{exact:true}).press('Escape');
            assert.equal(await dialog.isVisible(),true,'Escape in reaction picker closes picker only');
            p.once('dialog',d=>d.accept());await own.getByLabel('Xóa bình luận',{exact:true}).click();
            await dialog.getByText('Đã xóa bình luận.',{exact:true}).waitFor();assert.equal(await own.count(),0);
            await dialog.locator('.social-comment-more').click();await dialog.locator('.social-comment-more').waitFor({state:'hidden'});
            await dialog.getByLabel('Đóng bài viết',{exact:true}).click();await dialog.waitFor({state:'hidden'});
            await p.locator('#socialFeed .social-post[data-id="198"]').waitFor();
            await screen.context.close();
        }
        // Single photo, failed image/comment loads, retries, and no-JS links stay usable.
        const edge=await open(1440),edgePage=edge.page;
        const imageFailure=route=>route.fulfill({status:404,body:''});
        const commentFailure=route=>route.fulfill({status:503,json:{message:'Không tải được bình luận thử'}});
        await edgePage.route('**/files/501/image?viewer-failure',imageFailure);await edgePage.route('**/posts/199/comments?*',commentFailure);
        const single=edgePage.locator('[data-id="199"] .social-gallery a');await single.evaluate(node=>node.href+='?viewer-failure');await single.focus();await single.press('Enter');
        const singleDialog=edgePage.getByRole('dialog',{name:'Bài viết của Bảo An'});
        await singleDialog.waitFor();await singleDialog.getByText('Không tải được ảnh. Ảnh có thể đã bị gỡ hoặc bạn không còn quyền xem.',{exact:true}).waitFor();
        assert.equal(await singleDialog.getByLabel('Ảnh trước',{exact:true}).isVisible(),false);
        assert.equal(await singleDialog.getByLabel('Ảnh tiếp theo',{exact:true}).isVisible(),false);
        assert.equal(await singleDialog.locator('.social-post-text script').count(),0,'Post body stays escaped in viewer');
        await singleDialog.getByRole('button',{name:'Thử tải lại bình luận'}).waitFor();
        await edgePage.unroute('**/posts/199/comments?*',commentFailure);
        await singleDialog.getByRole('button',{name:'Thử tải lại bình luận'}).click();await singleDialog.locator('.social-comment').waitFor();
        await edgePage.unroute('**/files/501/image?viewer-failure',imageFailure);await singleDialog.getByLabel('Xem ảnh 1',{exact:true}).click();
        await edgePage.waitForFunction(()=>document.getElementById('socialViewerImage').naturalWidth>0 && !document.getElementById('socialViewerImageStatus').textContent);
        // Clicking outside the dialog dismisses it without losing the feed card.
        await edgePage.mouse.click(2,2);await singleDialog.waitFor({state:'hidden'});await single.waitFor();
        // Removing a post from inside its viewer never puts a deleted card back.
        await edgePage.locator('#socialMore').click();await edgePage.locator('[data-id="198"]').waitFor();
        await edgePage.locator('[data-id="198"] .social-gallery a').first().click();
        const deleteDialog=edgePage.getByRole('dialog',{name:'Bài viết của Hà Linh'});
        await deleteDialog.waitFor();await deleteDialog.locator('.social-menu-items').evaluate(node=>{
            const button=document.createElement('button');button.type='button';button.dataset.action='delete';button.textContent='Xóa bài viết';node.append(button);
        });
        await deleteDialog.locator('summary').click();edgePage.once('dialog',d=>d.accept());await deleteDialog.getByRole('button',{name:'Xóa bài viết',exact:true}).click();
        await deleteDialog.waitFor({state:'hidden'});await edgePage.waitForFunction(()=>!document.querySelector('.social-viewer-placeholder'));
        assert.equal(await edgePage.locator('.social-post[data-id="198"]').count(),0);assert.equal(await edgePage.locator('.social-viewer-placeholder').count(),0);
        await edge.context.close();

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
        assert.match(await noScript.page.locator('.social-gallery a').getAttribute('href'),/\/files\/501\/image$/);
        assert.equal(await noScript.page.locator('#socialPostViewer').isVisible(),false);
        assert.equal(await noScript.page.locator('.social-post').count(),2);
        await noScript.page.locator('.social-post').first().locator('summary').click();
        assert.equal(await noScript.page.locator('[data-action=edit]').isVisible(),true,'Native disclosure works without JS');
        assert.equal(await noScript.page.locator('noscript a').isVisible(),true);
        await noScript.context.close();
        assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
        assert.ok(calls.some(call=>call.method==='POST' && call.path.endsWith('/posts')));
        assert.ok(calls.some(call=>call.path.endsWith('/files/community/presign')),'Community-specific upload ticket used');
        assert.equal(calls.some(call=>call.path.endsWith('/files/presign')),false,'Generic antivirus upload route not used');
        console.log('PASS: rendered JSP, sticky utilities at 1280/1440px, responsive photo viewer at 1440/768/390/320px, photo navigation/keyboard/focus/scroll restore, live comments/reactions/drafts, errors/retries/removal, dynamic feed, upload regression, 7 viewport widths, detail/empty/admin, no JS. All API requests mocked.');
        console.log('Screenshots: '+screenshots);
    } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});