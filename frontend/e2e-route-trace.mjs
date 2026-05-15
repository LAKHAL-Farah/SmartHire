import { chromium } from 'playwright';

const base = 'http://localhost:4200';
const target = `${base}/dashboard/interview/session/34`;

const browser = await chromium.launch({
  headless: true,
  channel: 'chrome',
});

const context = await browser.newContext();
const page = await context.newPage();

const logs = [];
const errors = [];
const navs = [];

page.on('console', (msg) => {
  const entry = `[console:${msg.type()}] ${msg.text()}`;
  logs.push(entry);
});

page.on('pageerror', (err) => {
  errors.push(`[pageerror] ${err?.stack || String(err)}`);
});

page.on('framenavigated', (frame) => {
  if (frame === page.mainFrame()) {
    navs.push(frame.url());
  }
});

await page.goto(target, { waitUntil: 'domcontentloaded', timeout: 60000 });
await page.waitForTimeout(7000);

const finalUrl = page.url();
const title = await page.title();

const report = {
  target,
  finalUrl,
  title,
  navigations: navs,
  console: logs.slice(-80),
  errors,
};

console.log(JSON.stringify(report, null, 2));

await browser.close();
