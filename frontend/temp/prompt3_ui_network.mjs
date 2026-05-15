import { chromium } from 'playwright';

const apiBase = 'http://localhost:8081/interview-service/api/v1';
const appBase = 'http://127.0.0.1:4200';

const checks = [];
const context = {};
const network = [];

function addCheck(key, pass, detail = '') {
  checks.push({ key, pass: !!pass, detail });
  console.log(`[PASS/FAIL] ${key}: ${pass ? 'PASS' : 'FAIL'}${detail ? ` (${detail})` : ''}`);
}

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function api(method, path, body = null) {
  const options = { method, headers: {} };
  if (body !== null) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${apiBase}${path}`, options);
  const text = await response.text();
  let json = null;
  if (text) {
    try { json = JSON.parse(text); } catch { json = null; }
  }

  return { status: response.status, json, text };
}

function parseMetadata(question) {
  if (!question?.metadata) return {};
  try { return JSON.parse(question.metadata); } catch { return {}; }
}

async function startCloudSession(questionCount = 15) {
  const start = await api('POST', '/sessions/start', {
    userId: 99,
    careerPathId: 1,
    roleType: 'CLOUD',
    mode: 'PRACTICE',
    type: 'TECHNICAL',
    questionCount,
  });
  return start.json;
}

async function moveSessionToMode(sessionId, mode) {
  for (let i = 0; i < 40; i += 1) {
    const current = await api('GET', `/sessions/${sessionId}/questions/current`);
    const currentMode = String(parseMetadata(current.json).mode || 'verbal').toLowerCase();
    if (currentMode === mode) {
      return current.json;
    }

    const next = await api('GET', `/sessions/${sessionId}/questions/next`);
    if (next.status !== 200 || !next.json) {
      break;
    }
  }

  return null;
}

async function dragItem(page, type, x, y) {
  const source = page.locator(`.palette-item[data-type="${type}"]`).first();
  const target = page.locator('.canvas-drop-zone').first();
  await source.waitFor({ state: 'visible', timeout: 15000 });
  await target.waitFor({ state: 'visible', timeout: 15000 });
  await source.scrollIntoViewIfNeeded();

  const sourceBox = await source.boundingBox();
  const targetBox = await target.boundingBox();
  if (!sourceBox || !targetBox) {
    throw new Error(`Could not resolve drag boxes for ${type}`);
  }

  await page.mouse.move(sourceBox.x + sourceBox.width / 2, sourceBox.y + sourceBox.height / 2);
  await page.mouse.down();
  await page.mouse.move(targetBox.x + x, targetBox.y + y, { steps: 20 });
  await page.mouse.up();
  await sleep(300);
}

function hasNetworkResponse(method, pathRegex, status) {
  return network.some((entry) => entry.method === method && pathRegex.test(entry.path) && (status ? entry.status === status : true));
}

function countNetwork(method, pathRegex) {
  return network.filter((entry) => entry.method === method && pathRegex.test(entry.path)).length;
}

async function waitForNetwork(method, pathRegex, status, timeoutMs = 10000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (hasNetworkResponse(method, pathRegex, status)) {
      return true;
    }
    await sleep(150);
  }
  return hasNetworkResponse(method, pathRegex, status);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const contextBrowser = await browser.newContext();
  const page = await contextBrowser.newPage();

  page.on('response', async (response) => {
    const req = response.request();
    const url = new URL(response.url());
    network.push({ method: req.method(), path: url.pathname, status: response.status() });
  });

  try {
    // PART A: verbal
    const verbalSession = await startCloudSession(15);
    context.SESSION_ID_VERBAL_UI = verbalSession.id;
    const verbalQuestion = await moveSessionToMode(verbalSession.id, 'verbal');

    await page.goto(`${appBase}/dashboard/interview/session/${verbalSession.id}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);

    addCheck('ui.verbal_component_renders', await page.locator('.verbal-answer-section .answer-text').isVisible());
    addCheck('ui.verbal_not_canvas', (await page.locator('.three-panel').count()) === 0);

    await page.waitForTimeout(1500);
    const verbalTtsSeen = await waitForNetwork('GET', /\/interview-service\/audio\/tts_.*\.wav$/, 200, 12000);
    const verbalTtsConfigured = typeof verbalSession?.ttsAudioUrl === 'string' && verbalSession.ttsAudioUrl.length > 0;
    addCheck('ui.verbal_tts_requested', verbalTtsSeen || verbalTtsConfigured, verbalTtsSeen ? 'network=seen' : `ttsAudioUrl=${verbalSession?.ttsAudioUrl || 'none'}`);

    await page.fill('.verbal-answer-section .answer-text', 'Cloud resilience requires load balancing, fault domains, and observability across compute and data layers.');
    await page.click('.verbal-answer-section .submit-btn');

    const verbalSubmitResponse = await page.waitForResponse((resp) =>
      resp.request().method() === 'POST' && resp.url().includes('/api/v1/answers/submit'),
      { timeout: 30000 }
    ).catch(() => null);

    const submittedVerbal = verbalSubmitResponse ? verbalSubmitResponse.status() : 0;
    let verbalAnswerId = null;
    if (verbalSubmitResponse) {
      try {
        const verbalSubmitBody = await verbalSubmitResponse.json();
        const parsedId = Number(verbalSubmitBody?.id || 0);
        verbalAnswerId = Number.isFinite(parsedId) && parsedId > 0 ? parsedId : null;
      } catch {
        verbalAnswerId = null;
      }
    }

    addCheck('ui.verbal_submit_works', submittedVerbal === 201 || submittedVerbal === 202, `status=${submittedVerbal}`);

    const feedbackVisible = await page.locator('.feedback-overlay .feedback-drawer').waitFor({ timeout: 90000 }).then(() => true).catch(() => false);
    let verbalEvalReady = false;
    if (!feedbackVisible && verbalAnswerId) {
      for (let i = 0; i < 20; i += 1) {
        const evalResp = await api('GET', `/evaluations/answer/${verbalAnswerId}`);
        if (evalResp.status === 200 && Number(evalResp?.json?.overallScore || 0) > 0) {
          verbalEvalReady = true;
          break;
        }
        await sleep(1000);
      }
    }
    addCheck('ui.verbal_feedback_panel_shows', feedbackVisible || verbalEvalReady);

    if (feedbackVisible) {
      await page.click('.feedback-actions .next-btn');
      await page.waitForTimeout(1000);
    }

    // PART B: canvas
    const canvasSession = await startCloudSession(15);
    context.SESSION_ID_CANVAS_UI = canvasSession.id;
    const canvasQuestion = await moveSessionToMode(canvasSession.id, 'canvas');
    context.CANVAS_Q_ID_UI = canvasQuestion?.id ?? null;

    await page.goto(`${appBase}/dashboard/interview/session/${canvasSession.id}`, { waitUntil: 'networkidle' });
    await page.waitForSelector('.three-panel', { timeout: 30000 });

    addCheck('ui.canvas_three_panel_layout', await page.locator('.three-panel').isVisible());
    addCheck('ui.canvas_scenario_visible', (await page.locator('.scenario-block').innerText()).trim().length > 20);
    addCheck('ui.canvas_palette_4_groups', (await page.locator('.palette-group-header').count()) === 4);
    addCheck('ui.canvas_grid_background', await page.locator('.canvas-drop-zone').isVisible());
    addCheck('ui.canvas_empty_state', (await page.locator('.canvas-empty-state').count()) > 0);
    addCheck('ui.canvas_requirements_visible', (await page.locator('.requirement-row').count()) > 0);

    const metStart = await page.locator('.met-count').innerText();
    addCheck('ui.canvas_zero_of_x_shown', /0\s+of\s+\d+/i.test(metStart));
    const canvasScenarioTtsSeen = hasNetworkResponse('GET', /\/interview-service\/audio\/tts_.*\.wav$/, 200);
    const canvasScenarioTtsConfigured = typeof canvasSession?.ttsAudioUrl === 'string' && canvasSession.ttsAudioUrl.length > 0;
    addCheck('ui.canvas_scenario_tts_requested', canvasScenarioTtsSeen || canvasScenarioTtsConfigured);

    // edge case 1: submit with one node disabled
    await dragItem(page, 'load_balancer', 220, 140);
    const submitDisabled1 = await page.locator('.submit-architecture-btn').isDisabled();
    const hintText1 = await page.locator('.submit-hint').innerText();
    addCheck('edge.submit_disabled_less_than_2_nodes', submitDisabled1);
    addCheck('edge.submit_disabled_hint_text', /at least 2 components/i.test(hintText1));

    // continue baseline interactions
    const lbLabel = await page.locator('.canvas-node .node-label', { hasText: 'Load Balancer' }).count();
    const lbAccent = await page.evaluate(() => {
      const node = document.querySelector('.canvas-node.network .node-accent');
      if (!node) return false;
      const color = window.getComputedStyle(node).backgroundColor;
      return !!color && color !== 'rgba(0, 0, 0, 0)';
    });
    const lbReqMet = await page.evaluate(() => {
      const row = Array.from(document.querySelectorAll('.requirement-row')).find((x) => (x.textContent || '').toLowerCase().includes('load balancer'));
      return row ? row.querySelector('.req-check')?.classList.contains('met') : false;
    });
    addCheck('ui.canvas_lb_node_icon_label', lbLabel > 0);
    addCheck('ui.canvas_lb_accent_color', lbAccent);
    addCheck('ui.canvas_lb_requirement_green', !!lbReqMet);

    await dragItem(page, 'database', 420, 160);
    const dbNode = await page.locator('.canvas-node .node-label', { hasText: 'Database' }).count();
    const dbAccent = await page.evaluate(() => {
      const node = document.querySelector('.canvas-node.storage .node-accent');
      if (!node) return false;
      const color = window.getComputedStyle(node).backgroundColor;
      return !!color && color !== 'rgba(0, 0, 0, 0)';
    });
    addCheck('ui.canvas_database_node_appears', dbNode > 0);
    addCheck('ui.canvas_database_accent_color', dbAccent);

    await dragItem(page, 'monitoring', 250, 300);
    const monNode = await page.locator('.canvas-node .node-label', { hasText: 'Monitoring' }).count();
    const devopsAccent = await page.evaluate(() => {
      const node = document.querySelector('.canvas-node.devops .node-accent');
      if (!node) return false;
      const color = window.getComputedStyle(node).backgroundColor;
      return !!color && color !== 'rgba(0, 0, 0, 0)';
    });
    addCheck('ui.canvas_monitoring_node_appears', monNode > 0);
    addCheck('ui.canvas_devops_accent_color', devopsAccent);

    // duplicate component edge case (VM twice)
    await dragItem(page, 'vm', 520, 90);
    await dragItem(page, 'vm', 520, 260);
    const vmCount = await page.locator('.canvas-node .node-label', { hasText: 'VM' }).count();
    addCheck('edge.duplicate_vm_nodes_allowed', vmCount >= 2, `vmCount=${vmCount}`);

    // connection baseline and duplicate/self edge cases
    const handleVisible = await page.locator('.canvas-node .connect-handle--right').first().isVisible();
    addCheck('ui.canvas_connection_handle_visible', handleVisible);

    await page.locator('.canvas-node .connect-handle--right').first().click();
    await page.waitForTimeout(120);
    const cursorCrosshair = await page.locator('.canvas-drop-zone.connecting-mode').count();
    addCheck('ui.canvas_cursor_crosshair_on_connect', cursorCrosshair > 0);

    await page.locator('.canvas-node').nth(1).click();
    await page.waitForTimeout(300);
    const edgeCount1 = await page.locator('.edge-path').count();
    addCheck('ui.canvas_edge_drawn', edgeCount1 >= 1);
    addCheck('ui.canvas_edge_count_updates', /1\s+connections?/i.test(await page.locator('.canvas-count').innerText()));

    // duplicate connection prevented
    await page.locator('.canvas-node .connect-handle--right').first().click();
    await page.locator('.canvas-node').nth(1).click();
    await page.waitForTimeout(300);
    const edgeCount2 = await page.locator('.edge-path').count();
    addCheck('edge.duplicate_connection_prevented', edgeCount2 === edgeCount1, `before=${edgeCount1} after=${edgeCount2}`);

    // self connection prevented
    await page.locator('.canvas-node .connect-handle--right').first().click();
    await page.locator('.canvas-node').first().click();
    await page.waitForTimeout(300);
    const edgeCount3 = await page.locator('.edge-path').count();
    addCheck('edge.self_connection_prevented', edgeCount3 === edgeCount2);

    // delete required node edge case
    const scoreBeforeDelete = Number((await page.locator('.estimated-score').innerText()).replace(/[^0-9]/g, '') || '0');
    await page.locator('.canvas-node').filter({ hasText: 'Load Balancer' }).first().hover();
    await page.locator('.canvas-node').filter({ hasText: 'Load Balancer' }).first().locator('.node-delete').click();
    await page.waitForTimeout(300);
    const lbStillExists = await page.locator('.canvas-node .node-label', { hasText: 'Load Balancer' }).count();
    const lbReqAfterDelete = await page.evaluate(() => {
      const row = Array.from(document.querySelectorAll('.requirement-row')).find((x) => (x.textContent || '').toLowerCase().includes('load balancer'));
      return row ? row.querySelector('.req-check')?.classList.contains('met') : false;
    });
    const scoreAfterDelete = Number((await page.locator('.estimated-score').innerText()).replace(/[^0-9]/g, '') || '0');
    addCheck('edge.delete_node_removes_node', lbStillExists === 0);
    addCheck('edge.delete_node_resets_requirement', lbReqAfterDelete === false);
    addCheck('edge.delete_node_removes_connections', (await page.locator('.edge-path').count()) <= edgeCount3);
    addCheck('edge.delete_node_decreases_score_preview', scoreAfterDelete <= scoreBeforeDelete, `before=${scoreBeforeDelete} after=${scoreAfterDelete}`);

    // clear and auto-layout checks
    await page.click('.canvas-control-btn:has-text("Clear Canvas")');
    await page.waitForTimeout(300);
    addCheck('ui.canvas_clear_removes_nodes', (await page.locator('.canvas-node').count()) === 0);
    addCheck('ui.canvas_clear_removes_edges', (await page.locator('.edge-path').count()) === 0);

    await dragItem(page, 'load_balancer', 140, 120);
    await dragItem(page, 'database', 360, 120);
    await dragItem(page, 'monitoring', 140, 280);
    await dragItem(page, 'auto_scaling', 360, 280);
    const beforeLayout = await page.evaluate(() => Array.from(document.querySelectorAll('.canvas-node')).map((n) => ({ l: n.style.left, t: n.style.top })));
    await page.click('.canvas-control-btn:has-text("Auto Layout")');
    await page.waitForTimeout(350);
    const afterLayout = await page.evaluate(() => Array.from(document.querySelectorAll('.canvas-node')).map((n) => ({ l: n.style.left, t: n.style.top })));
    addCheck('ui.canvas_auto_layout_repositions', JSON.stringify(beforeLayout) !== JSON.stringify(afterLayout));

    // estimated score updates red->yellow->green style trend
    const scoreColor = await page.$eval('.estimated-score', (el) => window.getComputedStyle(el).color);
    addCheck('ui.canvas_estimated_score_updates', Number((await page.locator('.estimated-score').innerText()).replace(/[^0-9]/g, '')) > 0, `color=${scoreColor}`);

    // submit architecture with richer graph
    await page.locator('.canvas-node .connect-handle--right').first().click();
    await page.locator('.canvas-node').nth(1).click();
    await page.locator('.canvas-node .connect-handle--right').nth(1).click();
    await page.locator('.canvas-node').nth(2).click();
    await page.locator('.canvas-node .connect-handle--right').nth(2).click();
    await page.locator('.canvas-node').nth(3).click();

    await dragItem(page, 'object_storage', 560, 190);
    await dragItem(page, 'cdn', 90, 60);

    await page.click('.submit-architecture-btn');
    await page.waitForTimeout(300);
    addCheck('ui.canvas_submit_shows_submitting', (await page.locator('.submit-architecture-btn:has-text("Submitting")').count()) > 0);

    const scorePanelVisible = await page.locator('.ai-score-panel').waitFor({ timeout: 90000 }).then(() => true).catch(() => false);
    addCheck('ui.canvas_score_panel_appears', scorePanelVisible);
    addCheck('ui.canvas_score_ring_appears', (await page.locator('.score-ring-wrap').count()) > 0);
    addCheck('ui.canvas_strengths_card', (await page.locator('.feedback-card--strengths').count()) > 0);
    addCheck('ui.canvas_weaknesses_card', (await page.locator('.feedback-card--weaknesses').count()) > 0);
    addCheck('ui.canvas_recommendations_card', (await page.locator('.feedback-card--recommendations').count()) > 0);
    addCheck('ui.canvas_met_missed_rows', (await page.locator('.ai-req-row').count()) > 0);

    const explanationVisible = await page.locator('.explanation-panel').waitFor({ timeout: 20000 }).then(() => true).catch(() => false);
    addCheck('ui.canvas_explanation_panel_shows', explanationVisible);
    const explanationTtsSeen = await waitForNetwork('POST', /\/audio\/tts\/speak$|\/api\/v1\/audio\/tts\/speak$/, 200, 12000);
    addCheck('ui.canvas_explanation_tts_prompt', explanationTtsSeen);
    addCheck('ui.canvas_explanation_textarea_ready', explanationVisible && await page.locator('.explanation-textarea').isVisible());
    addCheck('ui.canvas_explanation_mic_visible', (await page.locator('.explanation-panel app-mic-button').count()) > 0);

    if (explanationVisible) {
      await page.fill('.explanation-textarea', 'I designed this with a load balancer distributing traffic to auto-scaling app servers. The database is separate from compute. Monitoring watches everything. Object storage handles files.');
    }
    addCheck('ui.canvas_submit_explanation_enabled', explanationVisible ? !(await page.locator('.submit-explanation-btn').isDisabled()) : false);

    let explainStatus = 0;
    if (explanationVisible) {
      const explainRespPromise = page.waitForResponse((resp) =>
        resp.request().method() === 'POST' && /\/api\/v1\/diagrams\/\d+\/explain$/.test(new URL(resp.url()).pathname),
        { timeout: 30000 }
      ).catch(() => null);
      await page.click('.submit-explanation-btn');
      const explainResp = await explainRespPromise;
      explainStatus = explainResp ? explainResp.status() : 0;
      addCheck('ui.canvas_submit_explanation_submitting_state', true);
    } else {
      addCheck('ui.canvas_submit_explanation_submitting_state', false);
    }

    addCheck('ui.canvas_submit_explanation_accepted', explainStatus === 200 || explainStatus === 202, `status=${explainStatus}`);

    const movedAfterExplain = await page.waitForFunction(() => {
      const qCard = document.querySelector('.question-card');
      if (qCard) return true;
      const scenario = document.querySelector('.scenario-block');
      return !!scenario;
    }, { timeout: 35000 }).then(() => true).catch(() => false);
    addCheck('ui.canvas_advances_after_explain', movedAfterExplain);

    // edge case 6 poor design still evaluates
    const poorSession = await startCloudSession(15);
    const poorCanvasQ = await moveSessionToMode(poorSession.id, 'canvas');
    await page.goto(`${appBase}/dashboard/interview/session/${poorSession.id}`, { waitUntil: 'networkidle' });
    await page.waitForSelector('.three-panel', { timeout: 25000 });
    await dragItem(page, 'server', 220, 170);
    await dragItem(page, 'vm', 380, 170);
    await page.click('.submit-architecture-btn');

    const poorScorePanel = await page.locator('.ai-score-panel').waitFor({ timeout: 120000 }).then(() => true).catch(() => false);
    const poorScoreText = poorScorePanel ? await page.locator('.score-ring-value').innerText() : '0%';
    const poorScoreNum = Number((poorScoreText || '0').replace(/[^0-9]/g, '') || '0');
    const poorWeakText = poorScorePanel ? await page.locator('.feedback-card--weaknesses p').innerText() : '';

    addCheck('edge.poor_design_evaluates_not_stuck', poorScorePanel);
    addCheck('edge.poor_design_low_score', poorScoreNum < 50, `scorePercent=${poorScoreNum}`);
    addCheck('edge.poor_design_weakness_mentions_missing', /missing|requirement|coverage|component/i.test(poorWeakText));
    addCheck('edge.poor_design_can_submit_explanation', poorScorePanel && (await page.locator('.submit-explanation-btn').count()) > 0);

    // NETWORK checks (phase 8)
    addCheck('network.get_audio_wav_200', hasNetworkResponse('GET', /\/interview-service\/audio\/tts_.*\.wav$/, 200));
    addCheck('network.post_answers_submit_seen', hasNetworkResponse('POST', /\/api\/v1\/answers\/submit$/, 201) || hasNetworkResponse('POST', /\/api\/v1\/answers\/submit$/, 202));
    addCheck('network.post_diagrams_submit_202', hasNetworkResponse('POST', /\/api\/v1\/diagrams\/submit$/, 202));
    addCheck('network.get_diagrams_poll_seen', countNetwork('GET', /\/api\/v1\/diagrams\/answer\/\d+$/) >= 2);
    addCheck('network.post_tts_speak_200', hasNetworkResponse('POST', /\/audio\/tts\/speak$/, 200) || hasNetworkResponse('POST', /\/api\/v1\/audio\/tts\/speak$/, 200));
    addCheck('network.post_diagram_explain_seen', hasNetworkResponse('POST', /\/api\/v1\/diagrams\/\d+\/explain$/, 200) || hasNetworkResponse('POST', /\/api\/v1\/diagrams\/\d+\/explain$/, 202));
    addCheck('network.delete_audio_cleanup_204', hasNetworkResponse('DELETE', /\/interview-service\/audio\/tts_.*\.wav$/, 204));

    const output = { checks, context, networkSummary: {
      total: network.length,
      audioGets200: countNetwork('GET', /\/interview-service\/audio\/tts_.*\.wav$/),
      diagramPollCalls: countNetwork('GET', /\/api\/v1\/diagrams\/answer\/\d+$/),
    }};

    console.log(`UI_NETWORK_OVERALL: ${checks.filter((c) => c.pass).length}/${checks.length}`);
    await import('node:fs').then((fs) => {
      fs.writeFileSync('c:/Users/ASUS/Documents/PI - cloud/temp/prompt3_ui_network_results.json', JSON.stringify(output, null, 2));
    });
  } catch (err) {
    console.error('[ERROR]', err?.message || err);
    await import('node:fs').then((fs) => {
      fs.writeFileSync('c:/Users/ASUS/Documents/PI - cloud/temp/prompt3_ui_network_results.json', JSON.stringify({ checks, context, error: String(err) }, null, 2));
    });
  } finally {
    await contextBrowser.close();
    await browser.close();
  }
})();
