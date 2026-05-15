import { chromium } from 'playwright';

const baseUrl = 'http://localhost:4200';
const verbalSessionId = Number(process.argv[2] || 286);
const pipelineSessionId = Number(process.argv[3] || 285);

const checks = [];
const record = (name, pass, detail = '') => checks.push({ name, pass, detail });

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function safeText(locator) {
  try {
    return (await locator.textContent())?.trim() ?? '';
  } catch {
    return '';
  }
}

async function isDisabled(locator) {
  try {
    return await locator.isDisabled();
  } catch {
    return true;
  }
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();
  const consoleErrors = [];

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  try {
    await page.goto(`${baseUrl}/dashboard/interview/session/${verbalSessionId}`, { waitUntil: 'networkidle', timeout: 120000 });
    await wait(1500);

    record(
      'Check 1 verbal view appears',
      (await page.locator('app-verbal-interview').count()) > 0,
      'Verbal component should render for verbal mode AI questions'
    );

    record(
      'Check 1 ml view not shown for verbal',
      (await page.locator('app-ml-interview').count()) === 0,
      'ML pipeline component should not render for verbal mode'
    );

    await page.goto(`${baseUrl}/dashboard/interview/session/${pipelineSessionId}`, { waitUntil: 'networkidle', timeout: 120000 });
    await page.waitForSelector('app-ml-interview', { timeout: 30000 });

    record('Check 2 two-panel layout renders', (await page.locator('.ml-interview-layout').count()) > 0);
    record('Check 2 scenario card visible', (await page.locator('.scenario-card').count()) > 0);
    record('Check 2 textarea visible', (await page.locator('.answer-textarea').count()) > 0);
    record('Check 2 five stage nodes visible', (await page.locator('.stage-node').count()) === 5);

    const stageText = ((await page.locator('.stage-title').allTextContents()) || []).join(' | ');
    record(
      'Check 2 stage labels complete',
      ['Data Ingestion', 'Preprocessing', 'Model Selection', 'Training & Evaluation', 'Deployment'].every((name) => stageText.includes(name)),
      stageText
    );

    record('Check 3 no autoplay console error seen', !consoleErrors.some((line) => /autoplay|audioBlocked/i.test(line)), consoleErrors.join(' || '));

    const textarea = page.locator('.answer-textarea');
    await textarea.fill('');
    await wait(400);
    record('Check 4 empty text keeps all stages inactive', (await page.locator('.stage-node.active').count()) === 0);
    record('Check 4 coverage is 0/5', (await safeText(page.locator('.coverage-text').first())).includes('0 of 5'));

    await textarea.fill('I would clean the data and remove missing values');
    await wait(450);
    record('Check 4 preprocessing activates', (await page.locator('.stage-node.active').count()) === 1);
    record('Check 4 preprocessing detected badge shown', (await page.locator('.stage-node').nth(1).locator('.detected-badge').count()) > 0);
    record('Check 4 coverage is 1/5', (await safeText(page.locator('.coverage-text').first())).includes('1 of 5'));

    await textarea.fill('I would clean the data and remove missing values then I would use XGBoost as my model');
    await wait(450);
    record('Check 4 model selection activates', (await page.locator('.stage-node.active').count()) >= 2);

    await textarea.fill('I would clean the data and remove missing values then I would use XGBoost as my model and evaluate using F1-macro and AUC-ROC');
    await wait(450);
    record('Check 4 training eval activates', (await page.locator('.stage-node.active').count()) >= 3);

    await textarea.fill('I would clean the data and remove missing values then I would use XGBoost as my model and evaluate using F1-macro and AUC-ROC and deploy via Docker container with FastAPI');
    await wait(450);
    record('Check 4 deployment activates', (await page.locator('.stage-node.active').count()) >= 4);

    await textarea.fill('I would clean the data and remove missing values then I would use XGBoost as my model and evaluate using F1-macro and AUC-ROC and deploy via Docker container with FastAPI and load data from the source database');
    await wait(450);
    record('Check 4 data ingestion activates for full coverage', (await page.locator('.stage-node.active').count()) === 5);
    record('Check 4 coverage is 5/5', (await safeText(page.locator('.coverage-text').first())).includes('5 of 5'));

    const transitionValue = await page.locator('.stage-node').first().evaluate((el) => getComputedStyle(el).transitionDuration);
    record('Check 4 smooth transition configured', String(transitionValue).includes('0.4'));

    const submitBtn = page.locator('.submit-btn');
    await textarea.fill('');
    await wait(100);
    record('Check 5 submit disabled when empty', await isDisabled(submitBtn));

    await textarea.fill('too short answer');
    await wait(100);
    record('Check 5 submit disabled under 30 chars', await isDisabled(submitBtn));

    await textarea.fill('I would clean and engineer features, choose XGBoost, evaluate with F1-macro and AUC, then monitor in production.');
    await wait(120);
    record('Check 5 submit enabled for valid answer', !(await isDisabled(submitBtn)));

    const hintBtn = page.locator('.hint-btn');
    await hintBtn.click();
    await wait(180);
    record('Check 6 hints panel opens', (await page.locator('.hint-panel').count()) > 0);
    record('Check 6 hints list has content', (await page.locator('.hint-panel li').count()) > 0);
    await page.locator('.hint-hide').click();
    await wait(180);
    record('Check 6 hints panel collapses', (await page.locator('.hint-panel').count()) === 0);

    await submitBtn.click();
    await page.waitForSelector('.loading-state', { timeout: 20000 });
    record('Check 7 loading state appears', (await page.locator('.loading-state').count()) > 0);
    record('Check 7 loading message appears', (await safeText(page.locator('.loading-state p'))).includes('NVIDIA is analyzing your ML approach'));
    record('Check 7 spinner visible', (await page.locator('.spinner').count()) > 0);

    await page.waitForSelector('.results-panel', { timeout: 150000 });
    record('Check 8 results panel appears', (await page.locator('.results-panel').count()) > 0);
    record('Check 8 score ring appears', (await page.locator('.score-ring').count()) > 0);
    record('Check 8 dimension rows appear', (await page.locator('.dimension-row').count()) === 4);
    record('Check 8 extracted concepts shown', (await page.locator('.concept-row').count()) === 4);
    record('Check 8 result stage blocks shown', (await page.locator('.result-stage-block').count()) === 5);
    record('Check 8 feedback shown', (await page.locator('.feedback-block').count()) > 0);
    record('Check 8 follow-up bubble shown', (await page.locator('.followup-bubble').count()) > 0);

    const modelText = (await safeText(page.locator('.concept-row').nth(0))).toLowerCase();
    const metricsText = (await safeText(page.locator('.concept-row').nth(2))).toLowerCase();
    const deploymentText = (await safeText(page.locator('.concept-row').nth(3))).toLowerCase();

    record('Check 9 model concept includes xgboost', modelText.includes('xgboost'), modelText);
    record('Check 9 metrics concept includes f1', metricsText.includes('f1'), metricsText);
    record('Check 9 deployment row present', deploymentText.length > 0, deploymentText);

    await page.locator('.followup-btn').click();
    await page.waitForSelector('.followup-composer', { timeout: 10000 });
    await page.locator('.followup-composer textarea').fill('I would focus on threshold tuning and monitoring false negatives in production.');
    await page.locator('.followup-composer button').click();
    await page.waitForSelector('.followup-status', { timeout: 20000 });
    record('Check 10 follow-up textarea opens', (await page.locator('.followup-composer').count()) > 0);
    record('Check 10 follow-up submission accepted', (await safeText(page.locator('.followup-status'))).toLowerCase().includes('submitted'));

    await page.locator('.retry-btn').click();
    await wait(300);
    record('Check 11 try again resets to pipeline state', (await page.locator('.pipeline-header').count()) > 0);
    record('Check 11 try again clears answer textarea', (await page.locator('.answer-textarea').inputValue()) === '');
    record('Check 11 try again resets active stages', (await page.locator('.stage-node.active').count()) === 0);

    await textarea.fill('I would ingest data from logs, clean and encode features, choose XGBoost, evaluate with F1-macro, and deploy with monitoring and drift alerts.');
    await wait(300);
    await submitBtn.click();
    await page.waitForSelector('.results-panel', { timeout: 150000 });
    const previousQuestion = await safeText(page.locator('.question-text').first());
    await page.locator('.next-btn').click();
    await wait(2000);
    const nextQuestion = await safeText(page.locator('.question-text').first());
    record('Check 11 next button advances question', previousQuestion !== nextQuestion, `${previousQuestion} => ${nextQuestion}`);

    const scenarioToggle = page.locator('.scenario-toggle');
    await scenarioToggle.click();
    await wait(120);
    const expanded = await page.locator('.scenario-text').evaluate((el) => el.classList.contains('expanded'));
    await scenarioToggle.click();
    await wait(120);
    const collapsed = !(await page.locator('.scenario-text').evaluate((el) => el.classList.contains('expanded')));
    record('Check 12 scenario expands', expanded);
    record('Check 12 scenario collapses', collapsed);

    const summary = {
      passed: checks.filter((item) => item.pass).length,
      total: checks.length,
      checks,
    };

    console.log(JSON.stringify(summary, null, 2));
  } catch (error) {
    const summary = {
      passed: checks.filter((item) => item.pass).length,
      total: checks.length,
      checks,
      fatal: String(error),
    };
    console.log(JSON.stringify(summary, null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
