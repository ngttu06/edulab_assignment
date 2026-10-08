/* Self-contained UI regression test: no database, server, or remote assets required. */
const {chromium} = require(process.env.EDULAB_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/resources/static');
const css = fs.readFileSync(path.join(root, 'css/motion.css'), 'utf8');
const js = fs.readFileSync(path.join(root, 'js/motion.js'), 'utf8');
const markup = `<!doctype html><html lang="vi"><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="/css/motion.css"><script src="/js/motion.js" defer></script></head><body>
<nav class="side-links"><a href="#bottom"><span class="nav-icon"><svg class="icon" width="20" height="20"></svg></span>Đi đến thẻ cuối</a></nav>
<div class="home-discovery"><section id="first" class="panel" style="height:700px">Nội dung đầu trang</section>
<section id="second" class="panel" style="height:700px">Nội dung tiếp theo</section></div>
<div id="communityFeed" class="community-feed" style="margin-top:750px"><article id="bottom" class="feed-card" style="height:300px"><a href="#bottom">Bài viết</a></article></div>
<button class="btn" type="button">Bắt đầu</button><div class="home-banner"><div class="banner-book">EduLab</div></div>
</body></html>`;

async function run() {
    const browser = await chromium.launch({executablePath: process.env.EDULAB_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true});
    try {
        const context = await browser.newContext({viewport: {width: 1280, height: 600}, reducedMotion: 'no-preference'});
        await context.route('http://motion.test/**', route => {
            const pathname = new URL(route.request().url()).pathname;
            if (pathname === '/css/motion.css') return route.fulfill({body: css, contentType: 'text/css'});
            if (pathname === '/js/motion.js') return route.fulfill({body: js, contentType: 'text/javascript'});
            return route.fulfill({body: markup, contentType: 'text/html'});
        });
        const page = await context.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.goto('http://motion.test/');
        assert.equal(await page.locator('#first').evaluate(el => el.classList.contains('motion-pending')), false, 'On-screen content must stay visible');
        assert.equal(await page.locator('#second').evaluate(el => el.classList.contains('motion-pending')), true, 'Off-screen content is queued');
        await page.locator('#second').scrollIntoViewIfNeeded();
        await page.waitForFunction(() => !document.querySelector('#second').classList.contains('motion-pending'));
        assert.equal(await page.locator('#second').evaluate(el => getComputedStyle(el).opacity), '1');
        await page.evaluate(() => document.querySelector('#bottom a').focus());
        await page.waitForFunction(() => !document.querySelector('#bottom').classList.contains('motion-pending'));
        assert.equal(await page.locator('#bottom a').evaluate(el => el === document.activeElement), true, 'Focus must not be concealed');

        await page.evaluate(() => {
            const card = document.createElement('article');
            card.id = 'added'; card.className = 'feed-card'; card.textContent = 'Bài mới';
            document.querySelector('#communityFeed').append(card);
        });
        // Dynamically added off-screen cards join the same one-shot reveal.
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.evaluate(() => {
            const card = document.createElement('article');
            card.id = 'late'; card.className = 'feed-card'; card.textContent = 'Bài tiếp theo';
            document.querySelector('#communityFeed').append(card);
        });
        await page.waitForFunction(() => document.querySelector('#late').classList.contains('motion-pending'));
        await page.emulateMedia({reducedMotion: 'reduce'});
        await page.waitForFunction(() => !document.querySelector('#late').classList.contains('motion-pending'));
        assert.equal(await page.locator('#late').evaluate(el => getComputedStyle(el).opacity), '1');
        await page.emulateMedia({reducedMotion: 'no-preference'});
        await page.locator('.side-links a').hover();
        await page.waitForTimeout(300);
        assert.notEqual(await page.locator('.side-links .icon').evaluate(el => getComputedStyle(el).transform), 'none', 'Icon responds to hover');
        assert.deepEqual(errors, []);

        const reduced = await browser.newContext({viewport: {width: 375, height: 667}, reducedMotion: 'reduce'});
        await reduced.route('http://motion.test/**', route => {
            const pathname = new URL(route.request().url()).pathname;
            return route.fulfill({body: pathname.endsWith('.css') ? css : pathname.endsWith('.js') ? js : markup,
                contentType: pathname.endsWith('.css') ? 'text/css' : pathname.endsWith('.js') ? 'text/javascript' : 'text/html'});
        });
        const mobile = await reduced.newPage();
        await mobile.goto('http://motion.test/');
        assert.equal(await mobile.locator('#bottom').evaluate(el => el.classList.contains('motion-pending')), false);
        assert.equal(await mobile.locator('.banner-book').evaluate(el => getComputedStyle(el).animationName), 'none');
        assert.equal(await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'No horizontal overflow on mobile');
        await reduced.close();
        const noScript = await browser.newContext({javaScriptEnabled: false});
        await noScript.route('http://motion.test/**', route => {
            const pathname = new URL(route.request().url()).pathname;
            return route.fulfill({body: pathname.endsWith('.css') ? css : markup,
                contentType: pathname.endsWith('.css') ? 'text/css' : 'text/html'});
        });
        const staticPage = await noScript.newPage();
        await staticPage.goto('http://motion.test/');
        assert.equal(await staticPage.locator('#second').evaluate(el => getComputedStyle(el).opacity), '1', 'Without JS content is visible');
        await noScript.close();
        await context.close();
        console.log('PASS: first paint, scroll reveal, focus, dynamic feed, changed preference, icon hover, mobile, reduced motion and no JS.');
    } finally { await browser.close(); }
}
run().catch(error => { console.error(error); process.exitCode = 1; });