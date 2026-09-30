/* Local authentication/layout regression. Optional registration creates a test account. */
const { chromium } = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8083/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Authentication tests are local-only');
const out = process.env.EDULAB_SCREENSHOT_DIR || '.tools/screenshots/auth';
const newUsername = process.env.EDULAB_AUTH_REGISTER_USERNAME;
if (newUsername && !/^auth_layout_test_[a-z0-9_]+$/.test(newUsername)) throw new Error('Use a unique auth_layout_test_ username');

(async () => {
    fs.mkdirSync(out, {recursive:true});
    const browser = await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:true});
    try {
        const context = await browser.newContext({viewport:{width:1440,height:1000}});
        const page = await context.newPage();
        const errors = [], assets = [], external = [], submissions = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('console', message => { if (/Content Security Policy|Refused to|violates.*directive/i.test(message.text())) errors.push(message.text()); });
        page.on('response', response => {
            if (/\/(css|js)\//.test(response.url()) && response.status() >= 400) assets.push(response.url());
        });
        page.on('request', request => {
            if (!request.url().startsWith(base) && /^https?:/.test(request.url())) external.push(request.url());
            if (request.method() === 'POST' && /\/auth\/(login|register|logout)$/.test(request.url())) {
                assert.ok(request.headers()['x-xsrf-token'], 'Authentication submission has CSRF header');
                submissions.push(request.url());
            }
        });
        async function navigate(path) {
            const response = await page.goto(base + path, {waitUntil:'networkidle'});
            assert.equal(response.status(), 200, path);
            const html = await response.text();
            for (const pattern of [/<!doctype\s+html/gi, /<html\b/gi, /<head\b/gi, /<body\b/gi]) {
                assert.equal((html.match(pattern) || []).length, 1, `Single document: ${path}`);
            }
            for (const selector of ['header.app-header', 'footer.site-footer', 'main#mainContent', 'h1', '#globalNotice']) {
                assert.equal(await page.locator(selector).count(), 1, `${selector}: ${path}`);
            }
            await page.evaluate(() => document.fonts.ready);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `No overflow: ${path}`);
            const duplicates = await page.evaluate(() => {
                const ids = [...document.querySelectorAll('[id]')].map(el => el.id);
                return ids.filter((id, index) => ids.indexOf(id) !== index);
            });
            assert.deepEqual(duplicates, [], 'No duplicate IDs');
        }
        for (const width of [1440,1024,768,640,390,320]) {
            await page.setViewportSize({width,height:width < 640 ? 844 : 1000});
            for (const name of ['login','register']) {
                await navigate('/' + name);
                assert.equal(await page.title(), name === 'login' ? 'Đăng nhập · EduLab' : 'Đăng ký · EduLab');
                assert.equal(await page.locator('#menuToggle, #sideNav, .utility-column').count(), 0);
                assert.equal(await page.locator('#' + name + 'Form').count(), 1);
                assert.equal(await page.locator('script[src$="app.js"]').count(), 1);
                assert.equal(await page.locator('script[src$="auth.js"]').count(), 1);
                assert.equal(await page.locator('.auth-story').isVisible(), width >= 640);
                assert.ok(await page.evaluate(() => {
                    const header = document.querySelector('header').getBoundingClientRect();
                    const main = document.querySelector('main').getBoundingClientRect();
                    const footer = document.querySelector('footer').getBoundingClientRect();
                    return main.top >= header.bottom && footer.top >= main.bottom - 1;
                }), 'Header, main and footer do not overlap');
                await page.screenshot({path:`${out}/${name}-${width}.png`,fullPage:true});
            }
            await navigate('/');
            assert.equal(await page.locator('.app-header').evaluate(el => Math.round(el.getBoundingClientRect().height)), 56);
            if (width < 992) {
                await page.locator('#menuToggle').click();
                await page.waitForFunction(() => document.activeElement?.id === 'menuClose');
                assert.ok(await page.locator('main').evaluate(el => el.inert));
                await page.keyboard.press('Escape');
                assert.equal(await page.locator('#menuToggle').getAttribute('aria-expanded'), 'false');
                assert.ok(await page.locator('main').evaluate(el => !el.inert));
            }
            await page.screenshot({path:`${out}/home-${width}.png`,fullPage:true});
        }
        await navigate('/login');
        await page.keyboard.press('Tab');
        assert.equal(await page.locator('.skip-link').evaluate(el => el === document.activeElement), true);
        await page.keyboard.press('Enter');
        assert.equal(await page.locator('main').evaluate(el => el === document.activeElement), true);
        for (const endpoint of ['login','register','logout']) {
            const response = await context.request.post(base + '/api/v1/auth/' + endpoint, {data:{}});
            assert.equal(response.status(), 403, `${endpoint} rejects a request without CSRF`);
        }
        const before = submissions.length;
        await page.locator('#loginForm button').click();
        assert.equal(await page.locator('#loginForm').evaluate(el => el.checkValidity()), false);
        assert.equal(submissions.length, before, 'Empty login blocked by native validation');
        await page.locator('#username').fill('hocsinh');
        await page.locator('#password').fill('incorrect-password');
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/auth/login') && r.status() === 401), page.locator('#loginForm button').click()]);
        await page.locator('#error').waitFor({state:'visible'});
        assert.ok(await page.locator('#loginForm button').isEnabled());
        await Promise.all([page.waitForURL(base + '/register'), page.locator('.auth-switch a').click()]);
        await page.locator('#registerForm button').click();
        assert.equal(await page.locator('#registerForm').evaluate(el => el.checkValidity()), false);
        await page.locator('#fullName').fill('Kiểm thử giao diện');
        await page.locator('#role').selectOption('PARENT');
        await page.locator('#username').fill(newUsername || 'hocsinh');
        await page.locator('#email').fill('not-an-email');
        assert.equal(await page.locator('#email').evaluate(el => el.validity.typeMismatch), true);
        await page.locator('#email').fill('auth-layout@example.test');
        await page.locator('#password').fill('short');
        assert.equal(await page.locator('#registerForm').evaluate(el => el.checkValidity()), false);
        await page.locator('#password').fill('LayoutTest123!');
        const status = newUsername ? 201 : 409;
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/auth/register') && r.status() === status), page.locator('#registerForm button').click()]);
        if (newUsername) {
            await page.waitForURL(base + '/login?registered=true');
            console.log(`Created local test account: ${newUsername} (clean up after this run).`);
        } else {
            await page.locator('#notice').waitFor({state:'visible'});
            assert.ok(await page.locator('#registerForm button').isEnabled());
            await Promise.all([page.waitForURL(base + '/login'), page.locator('.auth-switch a').click()]);
        }
        await page.locator('#username').fill(newUsername || 'hocsinh');
        await page.locator('#password').fill(newUsername ? 'LayoutTest123!' : 'ChangeMe123!');
        await Promise.all([page.waitForURL(base + (newUsername ? '/family' : '/dashboard')), page.locator('#loginForm button').click()]);
        assert.ok((await context.cookies()).some(cookie => cookie.name === 'EDULAB_ACCESS' && cookie.httpOnly));
        await navigate('/login');
        assert.equal(await page.locator('#logoutForm').count(), 1, 'Authenticated shared header');
        await page.route('**/api/v1/auth/logout', route => route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({message:'Kiểm thử lỗi đăng xuất'})}));
        await page.locator('#logoutForm button').click();
        await page.locator('#globalNotice').waitFor({state:'visible'});
        assert.equal(await page.locator('#globalNotice').textContent(), 'Kiểm thử lỗi đăng xuất');
        assert.ok(await page.locator('#logoutForm button').isEnabled());
        await page.unroute('**/api/v1/auth/logout');
        // The URL is already /login: wait for logout and the new anonymous DOM instead.
        await Promise.all([page.waitForResponse(r => r.url().endsWith('/auth/logout') && r.status() === 204), page.locator('#logoutForm button').click()]);
        await page.locator('#logoutForm').waitFor({state:'detached'});
        await page.waitForURL(base + '/login');
        assert.equal(await page.locator('#logoutForm').count(), 0);
        assert.ok(!(await context.cookies()).some(cookie => /^EDULAB_(ACCESS|REFRESH)$/.test(cookie.name)), 'Logout clears authentication cookies');
        assert.equal((await context.request.get(base + '/api/v1/auth/me')).status(), 401, 'Logged-out API is unauthenticated');
        await page.setViewportSize({width:1440,height:1000});
        await navigate('/login');
        await page.locator('#headerQuery').fill('Toán');
        await Promise.all([page.waitForURL(/\/courses\?q=/), page.locator('.header-search button').click()]);
        assert.equal(new URL(page.url()).searchParams.get('q'), 'Toán');
        assert.deepEqual(assets, [], 'All local assets loaded');
        assert.deepEqual(external, [], 'No external requests');
        assert.deepEqual(errors, [], 'No JavaScript or CSP errors');
        console.log('PASS: auth/home at 6 widths, document/includes, navigation, validation, CSRF submissions/rejections, login/logout, cookie clearing and error notices.');
    } finally { await browser.close(); }
})().catch(error => {console.error(error);process.exit(1);});