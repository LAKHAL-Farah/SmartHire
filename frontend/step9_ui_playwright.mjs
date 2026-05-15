import { chromium } from 'playwright';

const BASE = 'http://localhost:4200';
const API = 'http://localhost:8081/interview-service/api/v1';

const result = {
  setup: {},
  codingScreen: {},
  codingRun: {},
  wrongCode: {},
  explanationPanel: {},
  languageSwitch: {},
  cloud: {},
  ai: {},
  network: {},
  notes: []
};

const JAVA_OK = `class Solution {
    public int[] twoSum(int[] nums, int target) {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int c = target - nums[i];
            if (map.containsKey(c)) return new int[]{map.get(c), i};
            map.put(nums[i], i);
        }
        return new int[]{};
    }
}`;

const JAVA_BAD = 'class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{}; } }';

async function safeVisible(page, selector, timeout = 7000) {
  try {
    await page.locator(selector).first().waitFor({ state: 'visible', timeout });
    return true;
  } catch {
    return false;
  }
}

async function startFromSetup(page, role, type = 'TECHNICAL', mode = 'PRACTICE') {
  await page.goto(`${BASE}/dashboard/interview/setup?role=${role}&type=${type}&mode=${mode}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(400);
  const startBtn = page.locator('.start-btn');
  await startBtn.waitFor({ state: 'visible', timeout: 15000 });
  await startBtn.click();
  await page.waitForURL(/\/dashboard\/interview\/session\//, { timeout: 45000 });
  await page.waitForTimeout(1200);
}

async function resetActiveSessions() {
  try {
    const resp = await fetch(`${API}/sessions/user/1`);
    if (!resp.ok) {
      return;
    }

    const sessions = await resp.json();
    const active = Array.isArray(sessions)
      ? sessions.filter((s) => ['IN_PROGRESS', 'PAUSED', 'EVALUATING'].includes(String(s?.status || '')))
      : [];

    for (const s of active) {
      const sid = s?.id;
      if (!sid) continue;
      try {
        await fetch(`${API}/sessions/${sid}/abandon`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: '{}'
        });
      } catch {
        // Ignore cleanup failure and continue.
      }
    }
  } catch {
    // Ignore cleanup failure and continue.
  }
}

async function tryAdvanceUntilCoding(page, maxRounds = 5) {
  for (let i = 0; i < maxRounds; i++) {
    if (await page.locator('.coding-shell').count()) {
      if (await page.locator('.coding-shell').first().isVisible().catch(() => false)) {
        return true;
      }
    }

    const verbalVisible = await page.locator('app-verbal-interview').count() > 0;
    if (!verbalVisible) {
      await page.waitForTimeout(1200);
      continue;
    }

    const textarea = page.locator('app-verbal-interview textarea.answer-text');
    if (await textarea.count()) {
      await textarea.fill('Quick practice answer to advance to next question.');
      await page.locator('app-verbal-interview .submit-btn').click();
      const hasFeedback = await safeVisible(page, '.feedback-overlay .next-btn', 90000);
      if (!hasFeedback) {
        result.notes.push('Could not open feedback drawer while trying to reach coding question.');
        break;
      }
      await page.locator('.feedback-overlay .next-btn').click();
      await page.waitForTimeout(2200);
    } else {
      await page.waitForTimeout(1200);
    }
  }

  return false;
}

async function setMonacoCode(page, code) {
  return await page.evaluate((newCode) => {
    const models = globalThis.monaco?.editor?.getModels?.() || [];
    if (!models.length) {
      return false;
    }
    models[0].setValue(newCode);
    return true;
  }, code);
}

function hasNet(net, method, pattern, status) {
  return net.some((n) => n.method === method && pattern.test(n.url) && n.status === status);
}

const browser = await chromium.launch({
  headless: true,
  args: ['--autoplay-policy=no-user-gesture-required']
});

const context = await browser.newContext();
const page = await context.newPage();
const net = [];

page.on('response', (resp) => {
  const req = resp.request();
  net.push({ method: req.method(), url: resp.url(), status: resp.status() });
});

try {
  // Screen 1 setup checks
  await page.goto(`${BASE}/dashboard/interview/setup`, { waitUntil: 'domcontentloaded' });
  result.setup.screenRenders = await safeVisible(page, '.setup-page .setup-shell');
  result.setup.selectorsWork =
    (await safeVisible(page, '.role-card')) &&
    (await safeVisible(page, '.pill-group .pill')) &&
    (await safeVisible(page, '.mode-card')) &&
    (await safeVisible(page, '.start-btn'));

  // SE flow
  await resetActiveSessions();
  await startFromSetup(page, 'SE', 'TECHNICAL', 'PRACTICE');
  const codingVisible = (await safeVisible(page, '.coding-shell', 5000)) || (await tryAdvanceUntilCoding(page, 6));
  result.codingScreen.codingVisible = codingVisible;

  if (codingVisible) {
    result.codingScreen.splitScreenVisible = (await safeVisible(page, '.coding-shell .left-panel')) && (await safeVisible(page, '.coding-shell .right-panel'));
    result.codingScreen.problemStatementVisible = await safeVisible(page, '.coding-shell .question-title');
    result.codingScreen.difficultyBadgeVisible = await safeVisible(page, '.coding-shell .difficulty-badge');
    result.codingScreen.languageSelectorVisible = await safeVisible(page, '.coding-shell .language-selector');
    result.codingScreen.constraintsVisible = await safeVisible(page, '.coding-shell .constraint-pill');
    result.codingScreen.examplesVisible = await safeVisible(page, '.coding-shell .example-card');
    result.codingScreen.monacoVisible = await safeVisible(page, '.coding-shell .monaco-editor');

    // Starter code preload
    result.codingScreen.starterCodePreloaded = await page.evaluate(() => {
      const model = globalThis.monaco?.editor?.getModels?.()?.[0];
      if (!model) return false;
      return (model.getValue() || '').trim().length > 0;
    });

    // File label
    result.codingScreen.fileLabelVisible = await safeVisible(page, '.coding-shell .file-label');

    // Run correct code
    await setMonacoCode(page, JAVA_OK);
    await page.locator('.coding-shell .run-btn').click();
    result.codingRun.runningStateShown = await safeVisible(page, '.coding-shell .run-btn:has-text("Running...")', 3000);
    await safeVisible(page, '.coding-shell .meta-label:has-text("TEST RESULTS")', 90000);
    result.codingRun.testResultsVisible = await safeVisible(page, '.coding-shell .test-row');
    result.codingRun.hiddenResultsVisible = await safeVisible(page, '.coding-shell .hidden-test-status');
    result.codingRun.summaryPillVisible = await safeVisible(page, '.coding-shell .result-pill');

    const submitBtn = page.locator('.coding-shell .submit-solution-btn');
    result.codingRun.submitEnabledAfterRun = await submitBtn.isEnabled();

    // Wrong code
    await setMonacoCode(page, JAVA_BAD);
    await page.locator('.coding-shell .run-btn').click();
    await safeVisible(page, '.coding-shell .result-pill', 90000);
    result.wrongCode.failRowsVisible = await safeVisible(page, '.coding-shell .test-row.failed');
    const resultText = (await page.locator('.coding-shell .result-pill').innerText()).trim();
    result.wrongCode.redSummaryLikeZero = /0\//.test(resultText) || /✗/.test(resultText);
    result.wrongCode.submitStillEnabled = await submitBtn.isEnabled();

    // Explanation panel
    await setMonacoCode(page, JAVA_OK);
    await page.locator('.coding-shell .run-btn').click();
    await safeVisible(page, '.coding-shell .result-pill', 90000);
    await submitBtn.click();

    result.explanationPanel.panelVisible = await safeVisible(page, '.coding-shell .explanation-panel');
    result.explanationPanel.aiAvatarVisible = await safeVisible(page, '.coding-shell .ai-avatar-row', 10000);
    result.explanationPanel.textareaVisible = await safeVisible(page, '.coding-shell .explanation-textarea');
    result.explanationPanel.micVisible = await safeVisible(page, '.coding-shell .mic-wrap app-mic-button');

    const finalBtn = page.locator('.coding-shell .final-submit-btn');
    result.explanationPanel.finalSubmitInitiallyDisabled = !(await finalBtn.isEnabled());

    await page.locator('.coding-shell .explanation-textarea').fill('I used a HashMap to map each number to its index. For each element I check if the complement exists. O(n) time and O(n) space complexity.');
    result.explanationPanel.finalSubmitEnabledAfterText = await finalBtn.isEnabled();

    await finalBtn.click();
    result.explanationPanel.submittingShown = await safeVisible(page, '.coding-shell .final-submit-btn:has-text("Submitting...")', 4000);
    result.explanationPanel.feedbackDrawerVisible = await safeVisible(page, '.feedback-overlay .feedback-drawer', 90000);
    result.explanationPanel.feedbackTextPresent = await safeVisible(page, '.feedback-overlay .feedback-block p');

    // Language switch
    await page.locator('.feedback-overlay .next-btn').click({ timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(1500);

    const selector = page.locator('.coding-shell .language-selector');
    if (await selector.count()) {
      await selector.selectOption('python');
      await page.waitForTimeout(400);
      const pyFile = (await page.locator('.coding-shell .file-label span').last().innerText()).trim();
      result.languageSwitch.pythonFileName = pyFile;
      result.languageSwitch.pythonFileOk = pyFile === 'solution.py';

      await selector.selectOption('javascript');
      await page.waitForTimeout(400);
      const jsFile = (await page.locator('.coding-shell .file-label span').last().innerText()).trim();
      result.languageSwitch.javascriptFileName = jsFile;
      result.languageSwitch.javascriptFileOk = jsFile === 'solution.js';

      await selector.selectOption('java');
      await page.waitForTimeout(400);
      const javaFile = (await page.locator('.coding-shell .file-label span').last().innerText()).trim();
      result.languageSwitch.javaFileName = javaFile;
      result.languageSwitch.javaFileOk = javaFile === 'solution.java';
    }
  } else {
    const typeText = await page.locator('.type-pill').first().innerText().catch(() => 'UNKNOWN');
    result.notes.push(`Current session did not expose coding view. currentType=${typeText}`);
    result.notes.push('Could not reach a CODING question in SE practice flow.');
  }

  // Cloud coming soon
  await resetActiveSessions();
  await startFromSetup(page, 'CLOUD', 'TECHNICAL', 'PRACTICE');
  result.cloud.comingSoonVisible = await safeVisible(page, '.coming-soon-shell .title:has-text("Coming Soon")');
  result.cloud.cloudIconVisible = await safeVisible(page, '.coming-soon-shell .hero-icon:has-text("☁️")');
  result.cloud.cloudBadgeVisible = await safeVisible(page, '.coming-soon-shell .role-badge:has-text("Cloud Engineer")');
  result.cloud.previewItemsCount = await page.locator('.coming-soon-shell .preview-item').count();
  result.cloud.bottomTextVisible = await safeVisible(page, '.coming-soon-shell .bottom-text:has-text("Behavioral")');

  // AI coming soon
  await resetActiveSessions();
  await startFromSetup(page, 'AI', 'TECHNICAL', 'PRACTICE');
  result.ai.comingSoonVisible = await safeVisible(page, '.coming-soon-shell .title:has-text("Coming Soon")');
  result.ai.robotIconVisible = await safeVisible(page, '.coming-soon-shell .hero-icon:has-text("🤖")');
  result.ai.aiBadgeVisible = await safeVisible(page, '.coming-soon-shell .role-badge:has-text("AI Engineer")');
  const aiItemsText = (await page.locator('.coming-soon-shell .preview-item').allInnerTexts()).join(' | ');
  result.ai.aiItemsMentionMl = /ML pipeline|Concept extraction|MLOps/i.test(aiItemsText);

  // Network checks from recorded responses
  result.network.questionTtsGet200 = hasNet(net, 'GET', /\/interview-service\/audio\/tts_.*\.wav/i, 200);
  result.network.codeExecute200 = hasNet(net, 'POST', /\/interview-service\/api\/v1\/code\/execute$/i, 200);
  result.network.codeSubmit202 = hasNet(net, 'POST', /\/interview-service\/api\/v1\/code\/submit$/i, 202);
  result.network.evalPoll200 = hasNet(net, 'GET', /\/interview-service\/api\/v1\/evaluations\/answer\/\d+$/i, 200);
  result.network.audioDelete204 = hasNet(net, 'DELETE', /\/interview-service\/audio\/tts_.*\.wav/i, 204);
  result.network.explanationPromptTtsGet200 = (() => {
    const hits = net.filter((n) => n.method === 'GET' && /\/interview-service\/audio\/tts_.*\.wav/i.test(n.url) && n.status === 200);
    return hits.length >= 2;
  })();

} catch (err) {
  result.error = String(err?.stack || err);
}

await browser.close();

console.log('STEP9_UI_DONE');
console.log(JSON.stringify(result, null, 2));
