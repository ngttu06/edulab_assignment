/* Read-only theme regression, apart from local authentication. Requires existing Playwright tooling. */
const { chromium } = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.EDULAB_SMOKE_URL || 'http://127.0.0.1:8083/edulab';
if (!/^http:\/\/127\.0\.0\.1:\d+\/edulab$/.test(base)) throw new Error('Theme tests are local-only');
const out = process.env.EDULAB_SCREENSHOT_DIR || '.tools/screenshots/theme';

(async () => {
    fs.mkdirSync(out, {recursive:true});
    const browser = await chromium.launch({executablePath:process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:true});
    try {
        const context = await browser.newContext({viewport:{width:1440,height:1000}});
        const page = await context.newPage();
        const errors = [], assets = [], external = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('console', message => { if (/Content Security Policy|Refused to|violates.*directive/i.test(message.text())) errors.push(message.text()); });
        page.on('response', response => {
            if (/\/(css|js)\//.test(response.url()) && response.status() >= 400) assets.push(response.url());
        });
        page.on('request', request => { if (!request.url().startsWith(base) && /^https?:/.test(request.url())) external.push(request.url()); });
        async function navigate(path, expected = 200) {
            const response = await page.goto(base + path, {waitUntil:'networkidle'});
            assert.equal(response.status(), expected, path);
            assert.equal(await page.locator('h1').count(), 1, `One main heading: ${path}`);
            await page.evaluate(() => document.fonts.ready);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `No overflow: ${path}`);
        }
        for (const width of [1440,1024,768,390,320]) {
            await page.setViewportSize({width,height:width < 640 ? 844 : 1000});
            await navigate('/');
            assert.equal(await page.locator('.app-header').evaluate(el => Math.round(el.getBoundingClientRect().height)), 56);
            assert.ok(await page.evaluate(() => document.fonts.check('500 14px Quicksand')));
            await page.screenshot({path:`${out}/home-${width}.png`,fullPage:true});
            await navigate('/courses');
            const count = await page.locator('.course-card').count();
            assert.ok(count > 0, 'Seeded courses rendered');
            if (width < 992) {
                assert.ok(await page.locator('#sideNav').evaluate(el => el.inert));
                await page.locator('#menuToggle').click();
                await page.waitForFunction(() => document.activeElement?.id === 'menuClose');
                assert.equal(await page.locator('#menuToggle').getAttribute('aria-expanded'), 'true');
                assert.ok(await page.locator('main').evaluate(el => el.inert));
                await page.keyboard.press('Shift+Tab');
                assert.ok(await page.locator('#sideNav').evaluate(el => el.contains(document.activeElement)), 'Focus stays in drawer');
                await page.keyboard.press('Escape');
                assert.equal(await page.locator('#menuToggle').getAttribute('aria-expanded'), 'false');
                assert.equal(await page.locator('#menuToggle').evaluate(el => el === document.activeElement), true);
                assert.ok(await page.locator('main').evaluate(el => !el.inert));
                await page.locator('#menuToggle').click();
                await page.locator('#sideNav a[data-nav][href$="/courses"]').click();
                await page.waitForLoadState('networkidle');
                assert.equal(await page.locator('body').evaluate(el => el.classList.contains('menu-open')), false);
            }
            const link = await page.locator('.course-action').first().getAttribute('href');
            await navigate(link.slice(new URL(base).pathname.length));
            assert.ok(await page.locator('.curriculum-row').count() > 0);
            assert.ok(await page.locator('a[href$="/login"]').count() > 0);
            await page.screenshot({path:`${out}/course-${width}.png`,fullPage:true});
            await navigate('/login');
            await page.screenshot({path:`${out}/login-${width}.png`,fullPage:true});
            await navigate('/register');
        }
        await page.setViewportSize({width:1440,height:1000});
        await navigate('/courses');
        await page.locator('#filterQuery').fill('Toán');
        await Promise.all([page.waitForURL(/q=/), page.locator('.catalog-filters button').click()]);
        assert.ok(await page.locator('.course-card').count() > 0);
        for (const text of await page.locator('.course-card h3').allTextContents()) assert.match(text, /Toán/);
        await page.locator('#filterGrade').selectOption('10');
        await Promise.all([page.waitForURL(/grade=10/), page.locator('.catalog-filters button').click()]);
        for (const text of await page.locator('.course-type').allTextContents()) assert.match(text, /10/);
        await navigate('/courses?q=' + encodeURIComponent('<script>no-result</script>'));
        assert.ok(await page.locator('.empty-state').isVisible());
        assert.equal(await page.locator('#filterQuery').inputValue(), '<script>no-result</script>');
        await navigate('/courses/99999999', 404);
        await page.screenshot({path:`${out}/error.png`,fullPage:true});
        async function login(name) {
            await navigate('/login');
            await page.locator('#username').fill(name);
            await page.locator('#password').fill('ChangeMe123!');
            await Promise.all([page.waitForURL(url => !url.pathname.endsWith('/login')), page.locator('#loginForm button').click()]);
        }
        const roles = {
            hocsinh:['/dashboard','/courses','/lessons','/roadmap','/quizzes','/forum','/family'],
            phuhuynh:['/family','/courses'],
            giaovien:['/teacher/classes','/forum'],
            admin:['/admin','/teacher/classes']
        };
        for (const [role,paths] of Object.entries(roles)) {
            await login(role);
            await page.waitForLoadState('networkidle');
            // Exercise detail/report views as well as landing pages at all role viewports.
            const reportLink = page.locator('main a[href*="/school/students/"]').first();
            if (await reportLink.count()) paths.push((await reportLink.getAttribute('href')).slice(new URL(base).pathname.length));
            if (role === 'hocsinh') {
                const attemptLink = page.locator('main a[href*="/quizzes/attempts/"]').first();
                if (await attemptLink.count()) paths.push((await attemptLink.getAttribute('href')).slice(new URL(base).pathname.length));
                const lessons = await page.evaluate(() => window.edulab.api('/api/v1/lessons'));
                paths.push('/lessons/' + lessons[0].id, '/courses/' + lessons[0].courseId);
            }
            if (role === 'giaovien') {
                const classLink = page.locator('main a[href*="/teacher/classes/"]').first();
                if (await classLink.count()) paths.push((await classLink.getAttribute('href')).slice(new URL(base).pathname.length));
            }
            for (const width of [1440,768,390]) {
                await page.setViewportSize({width,height:1000});
                for (const path of paths) {
                    await navigate(path);
                    await page.screenshot({path:`${out}/${role}-${path.slice(1).replaceAll('/','-')}-${width}.png`,fullPage:true});
                }
            }
            await page.evaluate(() => window.edulab.api('/api/v1/auth/logout',{method:'POST'}));
        }
        assert.deepEqual(assets, [], 'All theme assets loaded');
        assert.deepEqual(external, [], 'No external font, stylesheet or script requests');
        assert.deepEqual(errors, [], 'No JavaScript or CSP errors');
        console.log('PASS: 5 viewports, local assets/fonts, catalog/filter/empty/error pages, drawer focus, and four-role page rendering.');
    } finally { await browser.close(); }
})().catch(error => {console.error(error);process.exit(1);});