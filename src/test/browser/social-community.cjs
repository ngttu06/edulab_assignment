/* Loopback-only smoke test; never writes to a remote or production database. */
const {chromium}=require(process.env.EDULAB_PLAYWRIGHT_PATH || '/Users/ngttu/Workspace/project_asm_prj/.tools/browser/node_modules/playwright');
const assert=require('node:assert/strict');
const base=process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8092/edulab';
if(!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base) || process.env.EDULAB_DISPOSABLE_TEST_DB!=='true') throw Error('Disposable loopback test DB required');
(async()=>{
    const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const errors=[];
    async function login(width,name) {
        const page=await (await browser.newContext({viewport:{width,height:920}})).newPage();
        page.on('pageerror',err=>errors.push(err.message));
        await page.goto(base+'/login');await page.locator('#username').fill(name);await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(url=>!url.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);return page;
    }
    try{
        const page=await login(1440,'hocsinh');
        assert.equal((await page.goto(base+'/community')).status(),200);
        assert.equal(await page.locator('h1').count(),1);
        assert.equal(await page.locator('.social-hero').count(),0);
        assert.equal(await page.locator('#socialBody').getAttribute('placeholder'),'Bạn đang nghĩ gì?');
        assert.equal(await page.locator('#socialForm').count(),1);
        assert.equal(await page.locator('#socialFeed').count(),1);
        assert.equal(await page.locator('.discovery-tabs a[aria-current="page"]').getAttribute('href'),'/edulab/community');
        assert.equal(await page.locator('.discovery-tabs a').first().getAttribute('href'),'/edulab/');
        assert.equal(await page.locator('.utility-column[aria-label="Tiện ích học tập"]').count(),1);
        assert.equal(await page.locator('#utilityGrade option').count()>1,true);
        assert.equal(await page.locator('#utilitySubject option').count()>1,true);
        assert.equal(await page.locator('.utility-column .study-promo').isVisible(),true);
        assert.equal(await page.locator('.utility-column .utility-panel').count(),2);
        assert.equal(await page.locator('.utility-column').evaluate(node=>node.getBoundingClientRect().left>document.querySelector('.main-content').getBoundingClientRect().right-2),true);
        assert.equal(await page.locator('a[href="/edulab/forum"]').count()>0,true);
        assert.equal(await page.locator('body').evaluate(node=>node.scrollWidth<=innerWidth),true);
        await page.locator('#socialBody').fill('Giao lưu vui vẻ '+Date.now());
        await page.locator('#socialPublish').click();await page.getByText('Đã đăng bài viết!',{exact:true}).waitFor();
        const card=page.locator('#socialFeed .social-post').first();assert.match(await card.locator('.social-post-text').innerText(),/Giao lưu vui vẻ/);
        await card.locator('.social-post-menu summary').click();
        assert.equal(await card.locator('[data-action=edit]').isVisible(),true);
        await card.locator('.social-post-menu summary').press('Escape');
        assert.equal(await card.locator('[data-action=edit]').isVisible(),false);
        const previous=await card.locator('[data-action=like]').getAttribute('aria-pressed');
        await card.locator('[data-action=like]').click();
        await page.waitForFunction(({id,previous})=>document.querySelector(`#socialFeed .social-post[data-id="${id}"] [data-action=like]`)?.getAttribute('aria-pressed')!==previous,
            {id:await card.getAttribute('data-id'),previous},{timeout:4000});
        await card.getByRole('button',{name:'Chọn cảm xúc',exact:true}).focus();
        await card.getByRole('button',{name:'Chọn cảm xúc',exact:true}).press('Enter');
        await card.getByRole('group',{name:'Chọn cảm xúc cho bài viết'}).getByRole('button',{name:'Yêu thích',exact:true}).click();
        await page.waitForFunction(()=>document.querySelector('#socialFeed .social-post [data-action=like]')?.dataset.reaction==='LOVE');
        await page.reload();await card.locator('[data-action=like][data-reaction=LOVE]').waitFor();
        await card.locator('[data-action=comments]').click();await card.locator('.social-comment-form textarea').fill('Chào mọi người!');
        await card.locator('.social-comment-form button').click();await page.waitForFunction(()=>Number(document.querySelector('#socialFeed .social-post .social-comment-count')?.textContent)>0);
        const comment=card.locator('.social-comment').last();await comment.locator('[data-action=edit-comment]').click();
        await comment.getByLabel('Sửa bình luận').fill('Bình luận đã sửa trên DB thử nghiệm');
        await comment.getByRole('button',{name:'Lưu',exact:true}).click();await card.getByText('Đã sửa bình luận.',{exact:true}).waitFor();
        await page.reload();await card.locator('[data-action=comments]').click();
        await card.locator('.social-comment-body').filter({hasText:'Bình luận đã sửa trên DB thử nghiệm'}).waitFor();
        page.once('dialog',dialog=>dialog.accept());await card.getByLabel('Xóa bình luận',{exact:true}).click();
        await card.getByText('Đã xóa bình luận.',{exact:true}).waitFor();
        assert.equal(await card.locator('.social-comment-count').innerText(),'0');
        // Image selection/compression is local-only: no upload credentials or scanner bypass required.
        const image=await page.evaluate(async()=>{
            const canvas=document.createElement('canvas');canvas.width=1400;canvas.height=1100;
            const ctx=canvas.getContext('2d');ctx.fillStyle='#f89c73';ctx.fillRect(0,0,1400,1100);
            for(let i=0;i<250;i++){ctx.fillStyle=`hsl(${i} 70% 45%)`;ctx.fillRect((i*137)%1400,(i*71)%1100,35,35);}
            return [...new Uint8Array(await (await new Promise(resolve=>canvas.toBlob(resolve,'image/png'))).arrayBuffer())];
        });
        await page.locator('#socialFiles').setInputFiles({name:'image.png',mimeType:'image/png',buffer:Buffer.from(image)});
        await page.locator('#socialPreview img').waitFor({timeout:10000});
        const text=await page.locator('#socialUploadStatus').textContent();assert.match(text,/KB → .*KB/);
        assert.ok(Number((await page.locator('#socialPreview small').textContent()).replace(' KB',''))<=500);
        const saved=await page.locator('#socialPreview img').evaluate(img=>({width:img.naturalWidth,height:img.naturalHeight,src:img.src}));
        assert.ok(saved.src.startsWith('blob:'));assert.ok(Math.max(saved.width,saved.height)<=1600);
        await page.locator('#socialPreview button').click();assert.equal(await page.locator('#socialPreview img').count(),0);
        const mobile=await login(390,'phuhuynh');assert.equal((await mobile.goto(base+'/community')).status(),200);
        assert.equal(await mobile.locator('.utility-column').count(),1);
        assert.equal(await mobile.locator('.discovery-tabs a[aria-current="page"]').count(),1);
        assert.equal(await mobile.locator('body').evaluate(node=>node.scrollWidth<=innerWidth),true);
        assert.ok(await mobile.getByText('Giao lưu vui vẻ',{exact:false}).count()>0);
        const admin=await login(1440,'admin');await admin.goto(base+'/admin/community/reports');
        assert.equal(await admin.locator('h1').textContent(),'Báo cáo bài viết cộng đồng');
        console.log('social community browser smoke passed: desktop, mobile, post, persisted LOVE after reload, comment create/edit/reload/delete, compression');
    }finally{await browser.close();}
    assert.deepEqual(errors,[]);
})().catch(error=>{console.error(error);process.exitCode=1;});