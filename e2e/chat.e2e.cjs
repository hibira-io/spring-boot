// Browser e2e: two users in the Spring Boot chat sample against a local Hibira server.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const BASE = process.env.BASE || 'http://127.0.0.1:8089';
const OUT = process.env.OUT || __dirname;
// pusher-js is served from node_modules so the test needs no CDN.
const pusherJs = fs.readFileSync(path.join(__dirname, 'node_modules/pusher-js/dist/web/pusher.min.js'));

async function login(browser, user, opts) {
  const ctx = await browser.newContext(opts);
  await ctx.route('https://cdn.jsdelivr.net/**', (r) => r.fulfill({ body: pusherJs, contentType: 'application/javascript' }));
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log(user, 'console:', m.text()); });
  await page.goto(BASE + '/login');
  await page.fill('input[name=username]', user);
  await page.fill('input[name=password]', 'password');
  await page.click('button[type=submit]');
  await page.waitForURL(BASE + '/**');
  if (!page.url().endsWith('/')) await page.goto(BASE + '/');
  await page.waitForFunction(() => document.getElementById('status').textContent === 'Live', null, { timeout: 15000 });
  return page;
}

(async () => {
  const browser = await chromium.launch();
  const ann = await login(browser, 'ann', { viewport: { width: 900, height: 620 }, colorScheme: 'light' });
  const bob = await login(browser, 'bob', { viewport: { width: 390, height: 700 }, colorScheme: 'dark', deviceScaleFactor: 2 });

  // presence: both see both
  for (const p of [ann, bob]) {
    await p.waitForFunction(() => document.querySelectorAll('#members li').length === 2, null, { timeout: 10000 });
  }
  console.log('presence ok:', await ann.$$eval('#members li', (l) => l.map((x) => x.textContent.trim())));

  // @HibiraBroadcast message from ann reaches bob
  await ann.fill('#text', 'Hi Bob! This went through Spring Boot and Hibira.');
  await ann.press('#text', 'Enter');
  await bob.waitForSelector('.msg:has-text("Hi Bob!")', { timeout: 10000 });
  await bob.fill('#text', 'Hello Ann 👋 got it instantly');
  await bob.press('#text', 'Enter');
  await ann.waitForSelector('.msg.mine:has-text("Hi Bob!")', { timeout: 10000 });
  await ann.waitForSelector('.msg:has-text("Hello Ann")', { timeout: 10000 });
  console.log('broadcast ok');

  // sendToUser via pusher.signin(): bob waves at ann
  await bob.click('#members button.wave');
  await ann.waitForFunction(() => document.getElementById('toast').textContent.includes('Bob waved'), null, { timeout: 10000 });
  console.log('sendToUser ok');

  await ann.waitForTimeout(400);
  await ann.screenshot({ path: path.join(OUT, 'chat-en-desktop-light.png') });
  await bob.screenshot({ path: path.join(OUT, 'chat-en-mobile-dark.png') });

  // a channel the rules refuse
  const refused = await ann.evaluate(async () => {
    const csrf = decodeURIComponent((document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/) || [])[1] || '');
    const r = await fetch('/hibira/auth', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrf },
      body: 'socket_id=1.2&channel_name=presence-chat.secret' });
    return r.status;
  });
  if (refused !== 403) throw new Error('expected 403, got ' + refused);
  console.log('refusal ok');

  await browser.close();
  console.log('E2E PASSED');
})().catch((e) => { console.error(e); process.exit(1); });
