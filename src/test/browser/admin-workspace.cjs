/* Only writes synthetic fixtures to a disposable loopback deployment. */
const {chromium} = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8093/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base) || process.env.EDULAB_DISPOSABLE_TEST_DB !== 'true') throw Error('Requires disposable loopback test deployment');
const resources = ['teachers','students','parents','administrators','classes','academic-years','parent-links','subjects','courses','topics','lessons','question-banks','questions','quizzes','assignments','feedback','forum-categories','forum-threads','forum-posts','community-posts','community-comments','content-reports','files','audit-logs'];
async function main() {
    const browser = await chromium.launch({executablePath: process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true});
    const errors = [], stamp = Date.now(), directory = process.env.EDULAB_SCREENSHOT_DIR || '/tmp/edulab-admin-screenshots';
    fs.mkdirSync(directory, {recursive: true});
    const context = await browser.newContext({viewport:{width:1440,height:1000}}), page = await context.newPage();
    page.on('pageerror', e => errors.push(e.message));
    page.on('dialog', d => d.accept());
    async function go(route) {
        const response = await page.goto(base + route); assert.equal(response.status(), 200, route + ': ' + (await page.locator('body').innerText()).slice(0, 600));
        assert.equal(await page.locator('.admin-sidebar').count(), 1, route);
        assert.equal(await page.locator('.sidebar-note,.ai-widget,.site-footer').count(), 0, route);
    }
    async function api(route, method='GET', body) {return page.evaluate(({route,method,body})=>window.edulab.api(route,{method,body}),{route,method,body});}
    async function submit(route) {
        await Promise.all([page.waitForURL(u => route.test(u.pathname)), page.locator('#adminResourceForm button[type=submit]').click()]);
    }
    async function confirm() {await page.locator('#adminConfirm button[value=confirm]').click();}
    try {
        await page.goto(base + '/login'); await page.locator('#username').fill('admin'); await page.locator('#password').fill('ChangeMe123!');
        await Promise.all([page.waitForURL(u=>!u.pathname.endsWith('/login')),page.locator('#loginForm button').click()]);
        await go('/admin'); await page.screenshot({path:path.join(directory,'dashboard-desktop.png'),fullPage:true});
        for (const resource of resources) {
            await go('/admin/' + resource);
            assert.equal(await page.locator('.admin-nav [aria-current=page]').count(),1,resource);
            if (resource !== 'audit-logs') await go('/admin/' + resource + '/new');
        }
        for (const route of ['/admin/teaching-assignments','/admin/videos','/admin/settings/ai','/admin/settings/storage','/admin/community/reports']) await go(route);
        await go('/admin/teachers/new');
        await page.locator('[name=full_name]').fill('Giáo viên thử nghiệm ' + stamp);
        await page.locator('[name=username]').fill('admin_browser_' + stamp);
        await page.locator('[name=email]').fill('admin_browser_' + stamp + '@example.test');
        await page.locator('[name=password]').fill('TestOnly123!');await submit(/\/admin\/teachers\/\d+$/);
        const teacherId = Number(page.url().split('/').at(-1));
        await go('/admin/teachers/'+teacherId+'/edit');await page.locator('[name=full_name]').fill('Giáo viên đã cập nhật '+stamp);await submit(/\/admin\/teachers\/\d+$/);
        const duplicate = await page.evaluate(async stamp => {
            try {await window.edulab.api('/api/v1/admin/workspace/teachers',{method:'POST',body:{username:'admin_browser_'+stamp,email:'other_'+stamp+'@example.test',full_name:'Duplicate',password:'TestOnly123!',enabled:true}});return false;}
            catch {return true;}
        },stamp);assert.equal(duplicate,true);
        const xss='<img src=x onerror="window.adminXss=true">';
        for(let i=0;i<22;i++)await api('/api/v1/admin/workspace/subjects','POST',{name:'Page '+stamp+' '+i+(i===0?' '+xss:''),grade_level:8,description:'Browser pagination'});
        await go('/admin/subjects?search='+encodeURIComponent('Page '+stamp));
        assert.equal(await page.locator('.admin-table tbody tr').count(),20);
        await Promise.all([page.waitForURL(u=>u.searchParams.get('page')==='1'),page.getByRole('link',{name:'Trang sau →'}).click()]);
        assert.equal(await page.locator('.admin-table tbody tr').count(),2);assert.equal(await page.evaluate(()=>window.adminXss),undefined);
        await go('/admin/classes/new');await page.locator('[name=name]').fill('Lớp quản trị '+stamp);
        await page.locator('[name=academic_year_id] option').nth(1).waitFor({state:'attached'});
        await page.locator('[name=academic_year_id]').selectOption({index:1});await page.locator('[name=grade_level]').fill('10');await submit(/\/admin\/classes\/\d+$/);
        const classId=Number(page.url().split('/').at(-1));
        await page.locator('[name=teacherId] option[value="'+teacherId+'"]').waitFor({state:'attached'});
        await page.locator('[name=teacherId]').selectOption(String(teacherId));await page.locator('[name=homeroom]').selectOption('true');
        await page.locator('[data-relationship=teacher] button.btn-primary').click();
        await Promise.all([page.waitForEvent('load'),confirm()]);
        assert.equal(await page.locator('#classTeachers tbody tr').count(),1);
        assert.match(await page.locator('#classTeachers').innerText(),/Phụ trách chính/);
        await page.locator('[name=studentId] option').nth(1).waitFor({state:'attached'});
        await page.locator('[name=studentId]').selectOption({index:1});
        await Promise.all([page.waitForEvent('load'),page.locator('[data-relationship=student] button.btn-primary').click()]);
        assert.equal(await page.locator('[data-enrollment]').count(),1);
        assert.equal((await page.request.get(base+'/admin/classes/'+classId+'/results.csv')).status(),200);
        const lessonInput={title:'Bài học admin '+stamp,topic_id:Number((await api('/api/v1/admin/workspace/options/topics'))[0].id),summary:'Tóm tắt',duration_minutes:20,objectives:'Mục tiêu',content_html:'<h2>Nội dung an toàn</h2><script>alert(1)</script><p>Kiến thức</p>',status:'DRAFT',required_score:0,class_only:false,classIds:[]};
        const lesson=await api('/api/v1/admin/workspace/lessons','POST',lessonInput);
        await go('/admin/lessons/'+lesson.id+'/edit');assert.equal(await page.locator('[name=content_html]').inputValue(),'<h2>Nội dung an toàn</h2>\n<p>Kiến thức</p>');
        await page.locator('[name=status]').selectOption('PUBLISHED');await submit(/\/admin\/lessons\/\d+$/);
        await page.locator('[data-admin-action=delete]').click();await Promise.all([page.waitForURL(u=>u.pathname.endsWith('/admin/lessons')),confirm()]);
        await go('/admin/lessons/'+lesson.id);assert.match(await page.locator('main').innerText(),/Đã lưu trữ/);
        await page.locator('[data-admin-action=restore]').click();await Promise.all([page.waitForEvent('load'),confirm()]);
        assert.equal((await api('/api/v1/admin/workspace/lessons/'+lesson.id)).status,'DRAFT');
        const routes=['/admin','/admin/teachers','/admin/classes/'+classId,'/admin/teachers/'+teacherId+'/edit','/admin/teaching-assignments'];
        for(const width of [1440,768,390,320]) {
            await page.setViewportSize({width,height:1000});
            for(const route of routes) {
                await go(route);
                assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Overflow '+width+' '+route);
                if(route==='/admin' || route==='/admin/classes/'+classId)await page.screenshot({path:path.join(directory,width+'-'+route.replaceAll('/','-')+'.png'),fullPage:true});
            }
            if(width<992){await page.locator('#adminMenuToggle').click();assert.equal(await page.locator('#adminMenuToggle').getAttribute('aria-expanded'),'true');await page.keyboard.press('Escape');assert.equal(await page.locator('#adminMenuToggle').getAttribute('aria-expanded'),'false');}
        }
        for(const [name,route] of [['giaovien','/teacher'],['hocsinh','/dashboard'],['phuhuynh','/family']]) {
            const other=await (await browser.newContext()).newPage();await other.goto(base+'/login');await other.locator('#username').fill(name);await other.locator('#password').fill('ChangeMe123!');
            await Promise.all([other.waitForURL(u=>!u.pathname.endsWith('/login')),other.locator('#loginForm button').click()]);
            assert.equal((await other.goto(base+route)).status(),200);assert.equal(await other.locator('.admin-sidebar').count(),0);
            assert.equal((await other.request.get(base+'/admin')).status(),403);assert.equal((await other.request.get(base+'/api/v1/admin/workspace/teachers/'+teacherId)).status(),403);
            await other.close();
        }
        await page.setViewportSize({width:1440,height:1000});await go('/admin/classes/'+classId);
        const teacherPage=await (await browser.newContext()).newPage();await teacherPage.goto(base+'/login');await teacherPage.locator('#username').fill('admin_browser_'+stamp);await teacherPage.locator('#password').fill('TestOnly123!');
        await Promise.all([teacherPage.waitForURL(u=>!u.pathname.endsWith('/login')),teacherPage.locator('#loginForm button').click()]);
        assert.equal((await teacherPage.request.get(base+'/teacher/classes/'+classId)).status(),200);
        await api('/api/v1/admin/workspace/classes/'+classId+'/teachers/'+teacherId,'DELETE');
        assert.equal((await teacherPage.request.get(base+'/teacher/classes/'+classId)).status(),403);
        await api('/api/v1/admin/workspace/teachers/'+teacherId,'DELETE');
        const revoked=await teacherPage.request.get(base+'/api/v1/profile');assert.equal(revoked.status(),401);await teacherPage.close();
        assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',resources:resources.length,widths:[1440,768,390,320],classId,teacherId,screenshots:directory}));
    } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});