import { chromium } from 'playwright';

const sessionId = Number(process.argv[2] || 291);
const baseUrl = 'http://localhost:4200';
const checks = [];
const record = (name, pass, detail = '') => checks.push({ name, pass, detail });
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    await page.goto(`${baseUrl}/dashboard/interview/session/${sessionId}`, { waitUntil: 'networkidle', timeout: 120000 });
    await page.waitForSelector('app-ml-interview', { timeout: 30000 });

    const qBefore = ((await page.locator('.scenario-card .question-text').textContent()) || '').trim();
    await page.locator('.answer-textarea').fill('I would ingest from source systems, clean and encode data, use XGBoost, evaluate with F1 and AUC, then deploy behind an API with monitoring.');
    await page.locator('.submit-btn').click();

    await page.waitForSelector('.results-panel', { timeout: 150000 });
    await page.locator('.followup-btn').click();
    await page.waitForSelector('.followup-composer', { timeout: 10000 });
    record('Follow-up composer opens', (await page.locator('.followup-composer').count()) > 0);

    await page.locator('.next-btn').click();
    await wait(2500);
    const qAfter = ((await page.locator('.scenario-card .question-text').textContent()) || '').trim();
    record('Next question advances in parent session', qBefore !== qAfter, `${qBefore} => ${qAfter}`);

    console.log(JSON.stringify({ passed: checks.filter((c) => c.pass).length, total: checks.length, checks }, null, 2));
  } catch (error) {
    console.log(JSON.stringify({ passed: checks.filter((c) => c.pass).length, total: checks.length, checks, fatal: String(error) }, null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
