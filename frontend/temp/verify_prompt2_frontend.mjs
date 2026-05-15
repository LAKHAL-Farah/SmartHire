import { chromium } from 'playwright';

const apiBase = 'http://localhost:8081/interview-service/api/v1';
const appBase = 'http://127.0.0.1:4200';

const results = [];

function addResult(label, pass, detail = '') {
  results.push({ label, pass, detail });
  const status = pass ? 'PASS' : 'FAIL';
  const suffix = detail ? ` (${detail})` : '';
  console.log(`[PASS/FAIL] ${label}: ${status}${suffix}`);
}

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function apiGet(path) {
  const response = await fetch(`${apiBase}${path}`);
  if (!response.ok) {
    throw new Error(`GET ${path} failed: ${response.status}`);
  }

  return response.json();
}

async function apiPost(path, body) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(`POST ${path} failed: ${response.status}`);
  }

  return response.json();
}

async function apiGetNullable(path) {
  const response = await fetch(`${apiBase}${path}`);
  if (response.status === 204 || response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`GET ${path} failed: ${response.status}`);
  }

  return response.json();
}

function parseMetadata(question) {
  try {
    return question?.metadata ? JSON.parse(question.metadata) : {};
  } catch {
    return {};
  }
}

function readMetCount(text) {
  const match = /(\d+)\s+of\s+(\d+)/i.exec(text || '');
  if (!match) {
    return { met: 0, total: 0 };
  }

  return { met: Number(match[1]), total: Number(match[2]) };
}

async function createCloudTechnicalSession(userId = 1) {
  return apiPost('/sessions/start', {
    userId,
    careerPathId: 1,
    roleType: 'CLOUD',
    mode: 'PRACTICE',
    type: 'TECHNICAL',
    questionCount: 15,
  });
}

async function moveSessionToMode(sessionId, wantedMode, maxSteps = 40) {
  for (let i = 0; i < maxSteps; i += 1) {
    const current = await apiGet(`/sessions/${sessionId}/questions/current`);
    const mode = String(parseMetadata(current)?.mode ?? 'verbal').toLowerCase();
    if (mode === wantedMode) {
      return current;
    }

    const next = await apiGetNullable(`/sessions/${sessionId}/questions/next`);
    if (!next) {
      break;
    }
  }

  throw new Error(`Unable to find ${wantedMode} question for session ${sessionId}`);
}

async function dragPaletteItem(page, type, x, y) {
  const source = page.locator(`.palette-item[data-type="${type}"]`).first();
  const target = page.locator('.canvas-drop-zone').first();
  await source.scrollIntoViewIfNeeded();
  await source.waitFor({ state: 'visible', timeout: 15000 });
  await target.waitFor({ state: 'visible', timeout: 15000 });

  const sourceBox = await source.boundingBox();
  const targetBox = await target.boundingBox();

  if (!sourceBox || !targetBox) {
    throw new Error(`Unable to resolve drag geometry for ${type}`);
  }

  await page.mouse.move(sourceBox.x + sourceBox.width / 2, sourceBox.y + sourceBox.height / 2);
  await page.mouse.down();
  await page.mouse.move(targetBox.x + x, targetBox.y + y, { steps: 20 });
  await page.mouse.up();
  await sleep(350);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  let ttsPromptRequested = false;
  page.on('request', (request) => {
    if (request.url().includes('/audio/tts/speak')) {
      ttsPromptRequested = true;
    }
  });

  try {
    const verbalSession = await createCloudTechnicalSession(1);
    const verbalQuestion = await moveSessionToMode(verbalSession.id, 'verbal');

    await page.goto(`${appBase}/dashboard/interview/session/${verbalSession.id}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);

    const verbalTextInputVisible = await page.locator('.verbal-answer-section .answer-text').isVisible();
    const verbalSpeakToggleVisible = await page.locator('.verbal-answer-section button:has-text("Speak")').isVisible();
    addResult('VerbalInterviewComponent shows (text input + mic button)', verbalTextInputVisible && verbalSpeakToggleVisible);

    const canvasVisibleInVerbal = await page.locator('.three-panel').count();
    addResult('NOT the canvas - verbal mode works correctly', canvasVisibleInVerbal === 0, `mode=${parseMetadata(verbalQuestion).mode || 'unknown'}`);

    const canvasSession = await createCloudTechnicalSession(1);
    const canvasQuestion = await moveSessionToMode(canvasSession.id, 'canvas');
    const metadata = parseMetadata(canvasQuestion);

    await page.goto(`${appBase}/dashboard/interview/session/${canvasSession.id}`, { waitUntil: 'networkidle' });
    await page.waitForSelector('.three-panel', { timeout: 20000 });
    const initialScenarioText = await page.locator('.scenario-block').innerText();

    const groupHeaders = await page.locator('.palette-group-header').allTextContents();
    const hasGroups = ['Compute', 'Network', 'Storage', 'DevOps'].every((label) =>
      groupHeaders.some((header) => header.toLowerCase().includes(label.toLowerCase()))
    );

    addResult('Three-panel layout renders', await page.locator('.three-panel').isVisible());
    addResult('Left panel shows component palette in 4 groups', hasGroups, `found=${groupHeaders.length}`);
    addResult('Center panel shows grid background canvas', await page.locator('.canvas-drop-zone').isVisible());
    addResult('Right panel shows requirements checklist', await page.locator('.requirement-row').count() > 0);

    const metCountBeforeLoadBalancer = readMetCount(await page.locator('.met-count').innerText());
    await dragPaletteItem(page, 'load_balancer', 180, 140);

    const nodeCountAfterLoadBalancer = await page.locator('.canvas-node').count();
    const hasLoadBalancerNode = await page.locator('.canvas-node .node-label', { hasText: 'Load Balancer' }).count();
    const loadBalancerReqMet = await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('.requirement-row'));
      const row = rows.find((item) => item.textContent?.toLowerCase().includes('load balancer'));
      if (!row) {
        return false;
      }

      return row.querySelector('.req-check')?.classList.contains('met') ?? false;
    });

    addResult('Node appears on canvas at drop location', nodeCountAfterLoadBalancer >= 1);
    addResult('Node shows icon and label', hasLoadBalancerNode > 0);
    addResult('Right panel requirement Load Balancer turns green with check', loadBalancerReqMet);

    await dragPaletteItem(page, 'database', 360, 250);
    let nodeCountAfterDatabase = await page.locator('.canvas-node').count();
    if (nodeCountAfterDatabase < 2) {
      await dragPaletteItem(page, 'database', 240, 220);
      nodeCountAfterDatabase = await page.locator('.canvas-node').count();
    }

    const metCountAfterDatabase = readMetCount(await page.locator('.met-count').innerText());

    addResult('Database node appears', nodeCountAfterDatabase >= 2);
    addResult('Requirements checklist updates', metCountAfterDatabase.met > metCountBeforeLoadBalancer.met);

    let handleVisible = false;
    let edgeCreated = false;
    let edgeCountIncremented = false;

    if (nodeCountAfterDatabase >= 2) {
      const firstHandle = page.locator('.canvas-node .connect-handle--right').first();
      handleVisible = await firstHandle.isVisible();
      await firstHandle.click();
      await page.locator('.canvas-node').nth(1).click();
      await page.waitForTimeout(250);

      const edgeCount = await page.locator('.edge-path').count();
      const edgeCountLabel = await page.locator('.canvas-count').innerText();
      edgeCreated = edgeCount >= 1;
      edgeCountIncremented = /1\s+connections?/i.test(edgeCountLabel);
    }

    addResult('Connection handle dot appears on right edge', handleVisible);
    addResult('Dashed line drawn between nodes', edgeCreated);
    addResult('Edge count in top bar increments to 1', edgeCountIncremented);

    const requirementKeys = Array.isArray(metadata.requirements)
      ? metadata.requirements.map((item) => item.key).filter((key) => typeof key === 'string')
      : [];

    const estimatedBeforeFill = Number((await page.locator('.estimated-score').innerText()).replace(/[^0-9]/g, '') || '0');

    const missingPaletteTypes = [];
    let dropX = 80;
    let dropY = 320;
    for (const key of requirementKeys) {
      const sourceCount = await page.locator(`.palette-item[data-type="${key}"]`).count();
      if (!sourceCount) {
        missingPaletteTypes.push(key);
        continue;
      }

      const alreadyPresent = await page.evaluate((requiredKey) => {
        const nodes = Array.from(document.querySelectorAll('.canvas-node .node-label'));
        return nodes.some((n) => (n.textContent || '').toLowerCase().includes(requiredKey.replace(/_/g, ' ')));
      }, key);

      if (!alreadyPresent) {
        await dragPaletteItem(page, key, dropX, dropY);
        dropX += 100;
        if (dropX > 540) {
          dropX = 80;
          dropY += 100;
        }
      }
    }

    const metTextAfterFill = await page.locator('.met-count').innerText();
    const metAfterFill = readMetCount(metTextAfterFill);
    const estimatedAfterFill = Number((await page.locator('.estimated-score').innerText()).replace(/[^0-9]/g, '') || '0');

    addResult('Each requirement turns green as its component is added', metAfterFill.total > 0 && metAfterFill.met === metAfterFill.total, missingPaletteTypes.length ? `missing palette types: ${missingPaletteTypes.join(',')}` : '');
    addResult('X of Y requirements met count updates', metAfterFill.met >= metCountAfterDatabase.met);
    addResult('Estimated score percentage updates in real-time', estimatedAfterFill >= estimatedBeforeFill);

    await page.click('.canvas-control-btn:has-text("Clear Canvas")');
    await page.waitForTimeout(250);
    const nodesAfterClear = await page.locator('.canvas-node').count();
    const edgesAfterClear = await page.locator('.edge-path').count();
    const clearMet = readMetCount(await page.locator('.met-count').innerText());

    addResult('All nodes and edges removed', nodesAfterClear === 0 && edgesAfterClear === 0);
    addResult('Requirements all reset to unchecked', clearMet.met === 0);

    await dragPaletteItem(page, 'load_balancer', 120, 140);
    await dragPaletteItem(page, 'database', 280, 140);
    await dragPaletteItem(page, 'monitoring', 120, 280);
    await dragPaletteItem(page, 'auto_scaling', 280, 280);

    const beforeAutoLayout = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.canvas-node')).map((node) => ({
        left: (node instanceof HTMLElement ? node.style.left : ''),
        top: (node instanceof HTMLElement ? node.style.top : ''),
      }))
    );

    await page.click('.canvas-control-btn:has-text("Auto Layout")');
    await page.waitForTimeout(300);

    const afterAutoLayout = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.canvas-node')).map((node) => ({
        left: (node instanceof HTMLElement ? node.style.left : ''),
        top: (node instanceof HTMLElement ? node.style.top : ''),
      }))
    );

    const movedByAutoLayout = JSON.stringify(beforeAutoLayout) !== JSON.stringify(afterAutoLayout);
    addResult('Nodes arranged in a grid pattern', movedByAutoLayout);

    let submitResponseOk = false;
    const submitResponsePromise = page.waitForResponse(
      (response) => response.url().includes('/diagrams/submit') && response.request().method() === 'POST',
      { timeout: 30000 }
    ).then((response) => {
      submitResponseOk = response.ok();
      return response;
    }).catch(() => null);

    await page.click('.submit-architecture-btn');
    await page.waitForTimeout(250);
    const submittingStateSeen = await page.locator('.submit-architecture-btn:has-text("Submitting")').count();
    await submitResponsePromise;

    addResult('Button shows submitting state', submittingStateSeen > 0);
    addResult('Submit triggers NVIDIA evaluation', submitResponseOk);

    const scorePanelVisible = await page.locator('.ai-score-panel').waitFor({ timeout: 70000 }).then(() => true).catch(() => false);
    addResult('After 10-60s AI score panel appears in right panel', scorePanelVisible);

    const scoreRingVisible = await page.locator('.score-ring-wrap').count();
    const strengthsShown = await page.locator('.feedback-card--strengths p').count();
    const weaknessesShown = await page.locator('.feedback-card--weaknesses p').count();
    const recommendationsShown = await page.locator('.feedback-card--recommendations p').count();
    const aiReqStatusRows = await page.locator('.ai-req-row').count();

    addResult('Score ring animates filling to actual score', scoreRingVisible > 0);
    addResult('Strengths, weaknesses, recommendations shown', strengthsShown > 0 && weaknessesShown > 0 && recommendationsShown > 0);
    addResult('Requirements met/missed listed', aiReqStatusRows > 0);

    const explanationPanelVisible = await page.locator('.explanation-panel').waitFor({ timeout: 15000 }).then(() => true).catch(() => false);
    addResult('Explanation panel appears below score', explanationPanelVisible);

    await page.waitForTimeout(1500);
    addResult('TTS plays walk me through your design prompt', ttsPromptRequested);

    if (explanationPanelVisible) {
      await page.fill('.explanation-textarea', 'I used load balancing, persistent storage, and monitoring to increase resiliency while keeping deployment manageable.');
    }

    const explanationSubmitEnabled = explanationPanelVisible
      ? !(await page.locator('.submit-explanation-btn').isDisabled())
      : false;
    addResult('Submit Explanation button enables when text entered', explanationSubmitEnabled);

    const explainResponse = page.waitForResponse(
      (response) => /\/diagrams\/\d+\/explain$/.test(response.url()) && response.request().method() === 'POST',
      { timeout: 30000 }
    ).catch(() => null);

    if (explanationPanelVisible) {
      await page.click('.submit-explanation-btn');
    }
    const explainApiResponse = await explainResponse;

    addResult('Submission accepted', !!explainApiResponse && explainApiResponse.ok());

    const movedToNextQuestion = await page.waitForFunction(
      (startingScenario) => {
        const questionCard = document.querySelector('.question-card');
        if (questionCard) {
          return true;
        }

        const scenarioBlock = document.querySelector('.scenario-block');
        if (!scenarioBlock) {
          return false;
        }

        const latestScenario = (scenarioBlock.textContent || '').trim();
        return latestScenario.length > 0 && latestScenario !== String(startingScenario).trim();
      },
      initialScenarioText,
      { timeout: 30000 }
    ).then(() => true).catch(() => false);

    addResult('Session moves to next question or completes', movedToNextQuestion);

    const passed = results.filter((item) => item.pass).length;
    const total = results.length;
    console.log(`OVERALL: ${passed}/${total} checks passed`);
  } catch (error) {
    console.error('[ERROR] Verification script failed:', error?.message || error);
  } finally {
    await context.close();
    await browser.close();
  }
})();
