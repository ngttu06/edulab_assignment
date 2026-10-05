/* Local-only profile regression. Creates two uniquely named disposable test accounts. */
const { chromium } = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8084/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Profile tests are local-only; verify the server uses a disposable local database');
const out = process.env.EDULAB_SCREENSHOT_DIR || '.tools/screenshots/profile';
const suffix = Date.now().toString(36);
const student = `profile_smoke_student_${suffix}`;
const parent = `profile_smoke_parent_${suffix}`;
const password = 'ProfileOldPassword123!';
const nextPassword = 'ProfileNewPassword123!';

(async () => {
    fs.mkdirSync(out, {recursive:true});
    const browser = await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:true});
    try {
        const context = await browser.newContext({viewport:{width:1440,height:1000}});
        const other = await browser.newContext();
        const page = await context.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('console', message => { if (/Content Security Policy|Refused to|violates.*directive/i.test(message.text())) errors.push(message.text()); });
        async function api(ctx, path, method = 'GET', body, csrf = true) {
            const headers = {Accept:'application/json'};
            if (method !== 'GET' && csrf) {
                const token = await (await ctx.request.get(base + '/api/v1/auth/csrf')).json();
                headers[token.headerName] = token.token;
            }
            return ctx.request.fetch(base + path, {method, headers, data:body});
        }
        assert.equal((await page.goto(base + '/profile')).status(), 200);
        assert.equal(page.url(), base + '/login');
        assert.equal((await api(context, '/api/v1/profile')).status(), 401);
        for (const [username, role] of [[student, 'STUDENT'], [parent, 'PARENT']]) {
            assert.equal((await api(context, '/api/v1/auth/register', 'POST', {
                username, role, fullName:'Tài khoản kiểm thử profile', email:username + '@example.test', password
            })).status(), 201);
        }
        await page.locator('#username').fill(student);
        await page.locator('#password').fill(password);
        await Promise.all([page.waitForURL(base + '/dashboard'), page.locator('#loginForm button').click()]);
        await page.locator('.header-account a[href$="/profile"]').click();
        await page.waitForURL(base + '/profile');
        assert.equal(await page.title(), 'Thông tin cá nhân · EduLab');
        assert.equal(await page.locator('#profileUsername').inputValue(), student);
        assert.equal(await page.locator('#profileUsername').getAttribute('readonly'), '');
        assert.equal(await page.locator('.side-links a[href$="/profile"]').getAttribute('aria-current'), 'page');
        assert.equal((await api(context, '/api/v1/profile', 'PUT', {}, false)).status(), 403);
        assert.equal((await api(context, '/api/v1/profile/password', 'POST', {}, false)).status(), 403);
        for (const width of [1440,1024,768,640,390,320]) {
            await page.setViewportSize({width,height:width < 640 ? 844 : 1000});
            await page.evaluate(() => document.fonts.ready);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `No horizontal overflow at ${width}`);
            for (const selector of ['header.app-header', 'footer.site-footer', 'main#mainContent', 'h1', '#profileForm', '#passwordForm']) {
                assert.equal(await page.locator(selector).count(), 1, selector);
            }
            assert.equal(await page.locator('input[id]').evaluateAll(inputs => new Set(inputs.map(input => input.id)).size === inputs.length), true);
            await page.screenshot({path:`${out}/profile-${width}.png`,fullPage:true});
        }
        await page.setViewportSize({width:1440,height:1000});
        const name = 'Nguyễn "An" <b>kiểm thử</b>';
        await page.locator('#profileFullName').fill(name);
        await page.locator('#profileEmail').fill(`UPDATED_${suffix}@EXAMPLE.TEST`);
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/api/v1/profile') && r.request().method() === 'PUT' && r.status() === 200), page.locator('#profileForm [type=submit]').click()]);
        await page.locator('#profileNotice.alert-success').waitFor();
        assert.equal(await page.locator('#profileDisplayName').textContent(), name);
        await page.locator('#profileFullName').fill('Thay đổi chưa lưu');
        await page.locator('#profileForm [type=reset]').click();
        assert.equal(await page.locator('#profileFullName').inputValue(), name);
        await page.reload({waitUntil:'networkidle'});
        assert.equal(await page.locator('#profileFullName').inputValue(), name);
        assert.equal(await page.locator('#profileDisplayName b').count(), 0, 'User name is escaped');
        assert.equal(await page.locator('#profileEmail').inputValue(), `updated_${suffix}@example.test`);
        await page.locator('#profileEmail').fill(parent + '@example.test');
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/api/v1/profile') && r.status() === 409), page.locator('#profileForm [type=submit]').click()]);
        await page.locator('#profileNotice.alert-danger').waitFor();
        assert.match(await page.locator('#profileNotice').textContent(), /Email đã được sử dụng/);
        await page.locator('#profileForm [type=reset]').click();
        const change = (currentPassword, newPassword, confirmPassword = newPassword) => api(context, '/api/v1/profile/password', 'POST', {currentPassword,newPassword,confirmPassword});
        assert.equal((await change(password, nextPassword, 'MismatchPassword123!')).status(), 400);
        assert.equal((await change(password, 'ế'.repeat(25))).status(), 400);
        assert.equal((await change(password, password)).status(), 400);
        await page.locator('#currentPassword').fill('WrongPassword123!');
        await page.locator('#newPassword').fill(nextPassword);
        await page.locator('#confirmPassword').fill(nextPassword);
        await page.locator('#showPasswords').check();
        assert.equal(await page.locator('#newPassword').getAttribute('type'), 'text');
        await page.locator('#showPasswords').uncheck();
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/profile/password') && r.status() === 400), page.locator('#passwordForm [type=submit]').click()]);
        await page.locator('#passwordNotice').waitFor({state:'visible'});
        assert.match(await page.locator('#passwordNotice').textContent(), /Mật khẩu hiện tại không đúng/);
        assert.equal(await page.locator('#currentPassword').inputValue(), '');
        assert.ok(await page.locator('#passwordForm [type=submit]').isEnabled());
        const rejected = [];
        const listener = request => { if (request.url().endsWith('/profile/password')) rejected.push(request.url()); };
        page.on('request', listener);
        await page.locator('#currentPassword').fill(password);
        await page.locator('#confirmPassword').fill('MismatchPassword123!');
        await page.locator('#passwordForm [type=submit]').click();
        assert.match(await page.locator('#passwordNotice').textContent(), /không khớp/);
        await page.locator('#newPassword').fill('ế'.repeat(25));
        await page.locator('#confirmPassword').fill('ế'.repeat(25));
        await page.locator('#passwordForm [type=submit]').click();
        assert.match(await page.locator('#passwordNotice').textContent(), /72 byte UTF-8/);
        assert.deepEqual(rejected, [], 'Client validation prevents invalid submissions');
        page.off('request', listener);
        assert.equal((await api(other, '/api/v1/auth/login', 'POST', {username:student,password})).status(), 200);
        assert.equal((await api(other, '/api/v1/profile')).status(), 200);
        await page.locator('#newPassword').fill(nextPassword);
        await page.locator('#confirmPassword').fill(nextPassword);
        await Promise.all([page.waitForURL(base + '/login?passwordChanged=true'), page.locator('#passwordForm [type=submit]').click()]);
        await page.locator('#loginNotice').waitFor({state:'visible'});
        assert.match(await page.locator('#loginNotice').textContent(), /Đổi mật khẩu thành công/);
        assert.ok(!(await context.cookies()).some(c => ['EDULAB_ACCESS','EDULAB_REFRESH'].includes(c.name)), 'Current authentication cookies are cleared');
        assert.equal((await api(other, '/api/v1/profile')).status(), 401, 'Other access token is revoked');
        assert.equal((await api(other, '/api/v1/auth/refresh', 'POST')).status(), 401, 'Other refresh token is revoked');
        await page.locator('#username').fill(student);
        await page.locator('#password').fill(password);
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/auth/login') && r.status() === 401), page.locator('#loginForm button').click()]);
        await page.locator('#error').waitFor({state:'visible'});
        await page.locator('#password').fill(nextPassword);
        await Promise.all([page.waitForURL(base + '/dashboard'), page.locator('#loginForm button').click()]);
        await page.goto(base + '/profile', {waitUntil:'networkidle'});
        assert.equal(await page.locator('#profileFullName').inputValue(), name);
        await page.screenshot({path:`${out}/profile-updated.png`,fullPage:true});
        await Promise.all([page.waitForURL(base + '/login'), page.locator('#logoutForm button').click()]);
        await page.locator('#username').fill(parent);
        await page.locator('#password').fill(password);
        await Promise.all([page.waitForURL(base + '/family'), page.locator('#loginForm button').click()]);
        await page.goto(base + '/profile', {waitUntil:'networkidle'});
        assert.match(await page.locator('.profile-summary p').textContent(), /Phụ huynh/);
        assert.deepEqual(errors, []);
        console.log('Profile browser regression passed: responsive layout, escaped data, update/reset, validation, CSRF, password change and all-session revocation.');
        console.log(`Disposable accounts retained in the local test database: ${student}, ${parent}`);
        await other.close(); await context.close();
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });