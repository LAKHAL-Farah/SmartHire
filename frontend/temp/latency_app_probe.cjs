const { chromium } = require('playwright');

const apiBase = 'http://localhost:8081/interview-service/api/v1';
const appBase = 'http://127.0.0.1:4200';

async function api(path, method='GET', body=null){
  const opts = { method, headers: {} };
  if(body){ opts.headers['Content-Type']='application/json'; opts.body=JSON.stringify(body); }
  const r = await fetch(apiBase + path, opts);
  const t = await r.text();
  let j = null; try { j = t ? JSON.parse(t) : null; } catch {}
  return { status:r.status, json:j };
}

(async()=>{
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const samples = [];

  for(let i=1;i<=3;i++){
    const start = await api('/sessions/start','POST',{
      userId:1, careerPathId:1, roleType:'CLOUD', mode:'PRACTICE', type:'TECHNICAL', questionCount:5
    });
    const sid = start.json.id;

    await page.goto(`${appBase}/dashboard/interview/session/${sid}`, { waitUntil:'networkidle' });
    await page.waitForSelector('.verbal-answer-section .answer-text', { timeout: 30000 });

    const submitRespPromise = page.waitForResponse(r =>
      r.request().method() === 'POST' && r.url().includes('/api/v1/answers/submit'),
      { timeout: 60000 }
    ).catch(() => null);

    await page.fill('.verbal-answer-section .answer-text', 'IaaS gives raw compute/network/storage, PaaS gives managed runtime/platform, and SaaS delivers complete software to end users.');
    const t0 = Date.now();
    await page.click('.verbal-answer-section .submit-btn');

    const submitResp = await submitRespPromise;
    const feedbackShown = await page.locator('.feedback-overlay .feedback-drawer').waitFor({ timeout: 120000 }).then(()=>true).catch(()=>false);
    const elapsedSec = Math.round(((Date.now()-t0)/1000)*100)/100;

    samples.push({ run:i, sessionId:sid, submitStatus: submitResp ? submitResp.status() : 0, feedbackShown, elapsedSec });

    if(feedbackShown){
      const nextBtn = page.locator('.feedback-actions .next-btn');
      if(await nextBtn.count()) await nextBtn.first().click().catch(()=>{});
    }
  }

  const times = samples.map(s=>s.elapsedSec);
  const avg = times.reduce((a,b)=>a+b,0)/times.length;
  const min = Math.min(...times);
  const max = Math.max(...times);

  console.log(JSON.stringify({ samples, avgSec: Math.round(avg*100)/100, minSec:min, maxSec:max }, null, 2));
  await ctx.close();
  await browser.close();
})();
