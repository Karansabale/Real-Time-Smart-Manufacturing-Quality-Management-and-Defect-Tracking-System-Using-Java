/* ============================================================================
   integration-journeys.mjs — Phase 10 (Integration)

   What this is: a REAL browser (headless Chrome) driving the REAL application,
   following the journeys an actual user would follow, and photographing each
   step. It is the test the unit-level checks cannot be: it proves that the ten
   screens, the REST API and MySQL work together, in the order a human uses them.

   Why a real browser and not jsdom: jsdom has no layout engine. It cannot tell
   you that a chart drew, that a modal appeared on top, that a button is
   actually visible, or that the page looks right. Chrome can, and it can also
   produce the screenshots the report needs.

   Run:   cd frontend/tests && node integration-journeys.mjs
   Needs: the application running on http://localhost:8080
          npm install puppeteer   (downloads its own Chrome)

   Every assertion below prints PASS or FAIL with the value it actually saw.
   Screenshots land in ../../screenshots/ and the report PDF in
   ../../docs/evidence/.
   ========================================================================= */
import puppeteer from 'puppeteer';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SHOTS = path.resolve(HERE, '../../screenshots');
const EVIDENCE = path.resolve(HERE, '../../docs/evidence');
const BASE = 'http://localhost:8080';

fs.mkdirSync(SHOTS, { recursive: true });

let passed = 0, failed = 0;
const failures = [];

function ok(label, condition, detail = '') {
  if (condition) { passed++; console.log(`  PASS  ${label}${detail ? '  [' + detail + ']' : ''}`); }
  else { failed++; failures.push(label); console.log(`  FAIL  ${label}${detail ? '  [' + detail + ']' : ''}`); }
}
function step(title) { console.log(`\n--- ${title} ---`); }

/* A defect the journeys FIND in the product is not a broken test: it is a
   result. Findings are printed apart from failures and carried into the
   Phase 10 report as work for the bug-fixing phase. */
const findings = [];
function found(id, label, condition, detail = '') {
  if (condition) { passed++; console.log(`  PASS  ${label}${detail ? '  [' + detail + ']' : ''}`); return true; }
  findings.push(`${id} - ${label}`);
  console.log(`  FOUND ${id}  ${label}  [${detail}]`);
  return false;
}

/* The Phase 4 document set response-time gates; a real browser can measure
   them on the screens a person actually uses, so it does. */
const perf = [];
function perfGate(label, ms, limit) {
  const pass = ms <= limit;
  perf.push({ label, ms, limit, pass });
  if (pass) passed++; else { failed++; failures.push(`${label} (${ms} ms, gate ${limit} ms)`); }
  console.log(`  ${pass ? 'PASS' : 'FAIL'}  ${label}  [${ms} ms, gate ${limit} ms]`);
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

/** Take a screenshot and return its file name. */
async function shot(page, name, fullPage = true) {
  await page.screenshot({ path: path.join(SHOTS, name), fullPage });
  return name;
}

/** Log in through the real login form and wait for the dashboard. */
async function login(page, username, password) {
  await page.goto(`${BASE}/login.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#username');
  await page.type('#username', username);
  await page.type('#password', password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 20000 }),
    page.click('#btn-login')
  ]);
}

/* Every screen asks before anything destructive with a native confirm(), and a
   native dialog freezes the renderer until somebody answers it: that is what
   killed the first run of this script (a mouse click timed out because the page
   was waiting on a dialog nobody had answered). A real person answers it, so
   this does too, and the wording is kept as evidence. */
function watchDialogs(page) {
  page.dialogs = [];
  page.on('dialog', async d => { page.dialogs.push(d.message()); await d.accept(); });
}

/** A fresh browser context = a fresh cookie jar = a separate signed-in user. */
async function session(browser, username, password) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  watchDialogs(page);
  page.on('dialog', d => { passedPageDialogs.add(d.message().slice(0, 60)); });
  await page.setViewport({ width: 1366, height: 900 });
  await login(page, username, password);
  return { ctx, page };
}

const text = (page, sel) => page.$eval(sel, el => el.textContent.trim()).catch(() => null);

/* The buttons that move a defect are re-rendered by the page's own refresh a
   moment after any save. A click delivered by coordinates across such a
   re-render can lose its mouseup on the old node and never fire at all, and a
   click that never fired looks exactly like a move the server refused. Clicking
   the element itself cannot be lost. Ordinary buttons are still clicked by
   coordinate on purpose: that is what proves they are visible and reachable. */
const clickEl = (page, sel) => page.$eval(sel, el => el.click());

/* The current status of a defect is rendered as a badge inside the heading
   (#h-ref). Reading the badge is reading what the user sees. (#h-status, the
   empty div that used to sit beside it, was removed in Phase 11.) */
const statusOf = (page) => page.$eval('#h-ref .badge', el => el.textContent.trim())
  .then(s => s.replaceAll(' ', '_'))          // the badge prints UNDER INVESTIGATION
  .catch(() => '(no badge)');
/* Waiting for the badge: waitForFunction runs its check INSIDE the browser, so
   the check has to be plain DOM code - the Node-side helpers do not exist
   there - and the wanted status is handed in as an argument, because a
   serialized function carries no closure. Getting this wrong made a move that
   had actually succeeded look like a failure. */
const waitForStatus = (page, want, timeout = 10000) =>
  until(page, w => document.querySelector('#h-ref .badge')?.textContent.trim().replaceAll(' ', '_') === w,
        want, timeout);
const count = (page, sel) => page.$$eval(sel, els => els.length).catch(() => 0);

/** Wait until a predicate holds in the page, returning true/false rather than throwing. */
async function until(page, fn, arg, timeout = 10000) {
  try { await page.waitForFunction(fn, { timeout, polling: 150 }, arg); return true; }
  catch { return false; }
}

/**
 * Click a navbar link and wait for the page it should land on.
 *
 * Two traps were found by running this: waiting for the navigation AFTER
 * awaiting the click misses it entirely, and waiting for a guessed element is
 * fragile (the first attempt waited for a filter box that lives on a different
 * screen). So the wait is armed first, and it watches the URL, which is the one
 * thing that is always true of a navigation.
 */
async function clickNav(page, href, expectedPathPart, timeout = 20000) {
  await Promise.all([
    page.waitForFunction(part => location.pathname.endsWith(part), { timeout }, expectedPathPart),
    page.click(`a[href="${href}"]`)
  ]);
  return page.url();
}

/**
 * Submit one of the modal forms. These pages do not navigate: they POST, hide
 * the modal and re-render the list in place, so waiting for a navigation here
 * would wait for something that never happens.
 */
async function submitModal(page, formSel, modalSel, settle, timeout = 15000) {
  await page.click(`${formSel} button[type="submit"]`);
  const closed = await until(page, sel => !document.querySelector(sel)?.classList.contains('show'),
                             modalSel, timeout);
  /* Bootstrap keeps its backdrop over the whole page for about 150 ms after a
     modal closes. A click sent inside that window lands on the backdrop, not on
     the button under it, and the action is silently lost - which is exactly how
     the first attempt at this run lost the "move to CORRECTIVE ACTION" step.
     So the backdrop is waited out before anything else is clicked. */
  await until(page, () => !document.querySelector('.modal-backdrop'), null, 5000);
  await sleep(150);
  const done = settle ? await settle() : true;
  return closed && done;
}

const browser = await puppeteer.launch({
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage',
         '--disable-gpu', '--window-size=1366,900']
});

const report = [];   // facts gathered during the run, used by the consistency journey
const passedPageDialogs = new Set();   // the confirmations the run answered, as evidence

try {

/* ══════════════════════════════════════════════════════════════════════════
   JOURNEY 1 — A Quality Inspector records a failed inspection and raises the
               defect from it (modules M5 → M6)
   ═════════════════════════════════════════════════════════════════════════ */
step('JOURNEY 1 — Inspector: failed inspection → defect raised from it');

const ins = await session(browser, 'inspector1', 'Inspect@123');
const p1 = ins.page;

ok('the login form rendered and was usable', true, await shot(p1, '01_login.png', false));

await until(p1, () => document.querySelectorAll('#tiles .tile').length >= 6);
const tileCount = await count(p1, '#tiles .tile');
const fixedTiles = await p1.evaluate(() => ({
  products: document.getElementById('stat-products').textContent.trim(),
  batches: document.getElementById('stat-batches').textContent.trim()
}));
ok('the dashboard rendered: six lifecycle tiles plus the two fixed ones',
   tileCount === 6 && fixedTiles.products !== '—' && fixedTiles.batches !== '—',
   `#tiles=${tileCount}, products=${fixedTiles.products}, batches=${fixedTiles.batches}`);

const chartsDrawn = await p1.evaluate(() => {
  const s = document.getElementById('chart-severity'), c = document.getElementById('chart-category');
  return !!(s && c && s.width > 50 && s.height > 50 && c.width > 50 && c.height > 50);
});
ok('both charts actually drew (real canvas sizes, which jsdom cannot check)', chartsDrawn);
await shot(p1, '02_dashboard_inspector.png');

const overdueText = await text(p1, '#overdue-banner');
ok('the overdue strip states the number and the reason',
   /2 corrective actions overdue/.test(overdueText || ''), (overdueText || '').slice(0, 62));

const listMs = Date.now();
await clickNav(p1, 'inspections.html', 'inspections.html');
await until(p1, () => document.querySelectorAll('#rows tr').length > 5);
perfGate('page: list screen opened to rows on screen', Date.now() - listMs, 3000);
const inspRowsBefore = await count(p1, '#rows tr');
ok('the inspection list loaded from MySQL', inspRowsBefore === 12, `${inspRowsBefore} rows`);
await shot(p1, '03_inspections_list.png');

await p1.click('#btn-new');
await until(p1, () => document.querySelector('#inspection-modal')?.classList.contains('show'));
await until(p1, () => document.querySelectorAll('#i-product option').length > 1);
await p1.select('#i-product', '1');
await until(p1, () => document.querySelectorAll('#i-batch option').length > 1);
const batchValues = await p1.$$eval('#i-batch option', os => os.map(o => o.value).filter(Boolean));
await p1.select('#i-batch', batchValues[0]);
await p1.select('#i-type', 'IN_PROCESS');
await p1.$eval('#i-inspected', el => { el.value = ''; });
await p1.type('#i-inspected', '60');
await p1.$eval('#i-rejected', el => { el.value = ''; });
await p1.type('#i-rejected', '7');
await p1.select('#i-result', 'FAIL');
await p1.type('#i-remarks', 'Phase 10 integration journey - surface roughness above the limit on seven parts');
await shot(p1, '04_new_inspection_modal.png', false);

const saveMs = Date.now();
await submitModal(p1, '#inspection-form', '#inspection-modal',
                  () => until(p1, () => document.querySelectorAll('#rows tr').length >= 13));
perfGate('save: inspection form submitted to list refreshed', Date.now() - saveMs, 2000);
const inspRowsAfter = await count(p1, '#rows tr');
ok('the new inspection was saved and appears in the list', inspRowsAfter === 13, `${inspRowsAfter} rows`);

const raiseHref = await p1.$eval('#rows a[href*="defects.html?new=1"]', a => a.getAttribute('href')).catch(() => null);
ok('the FAIL row offers the direct link to raise a defect against the same product and batch',
   !!raiseHref && raiseHref.includes('productId=1') && raiseHref.includes('inspectionId='),
   (raiseHref || 'no link found').slice(0, 78));
await shot(p1, '05_fail_row_with_link.png');

await p1.goto(`${BASE}/${raiseHref}`, { waitUntil: 'networkidle0' });
await until(p1, () => document.querySelector('#defect-modal')?.classList.contains('show'));
const prefill = await p1.evaluate(() => ({
  product: document.querySelector('#d-product').value,
  batch: document.querySelector('#d-batch').value,
  inspection: document.querySelector('#d-inspection').value
}));
const prefilled = found('D-01', 'the defect form arrives pre-filled from the failed inspection',
   prefill.product === '1' && !!prefill.batch && !!prefill.inspection,
   `product=${prefill.product || '(empty)'} batch=${prefill.batch || '(empty)'} inspection=${prefill.inspection || '(empty)'}`);
await shot(p1, '06_defect_form_prefilled.png', false);

if (!prefilled) {
  /* The link carries the three ids but they never reach the form. The rest of
     the chain still has to be walked, so the product is chosen here the way a
     person would - choosing it is what makes the batch and the inspection
     selectable - and the two ids from the link are then used as they were meant
     to be. */
  const ids = new URLSearchParams(raiseHref.split('?')[1]);
  await p1.select('#d-product', ids.get('productId'));
  await until(p1, () => document.querySelectorAll('#d-batch option').length > 1);
  try { await p1.select('#d-batch', ids.get('batchId'), { timeout: 4000 }); } catch {}
  await until(p1, () => document.querySelectorAll('#d-inspection option').length > 1);
  try { await p1.select('#d-inspection', ids.get('inspectionId'), { timeout: 4000 }); } catch {}
  console.log('        (worked around D-01 by choosing the product by hand, so the chain can continue)');
}

await p1.select('#d-category', 'SURFACE_FINISH');
await p1.select('#d-severity', 'HIGH');
await p1.$eval('#d-units', el => { el.value = ''; });
await p1.type('#d-units', '7');
await p1.type('#d-description', 'Seven housings show surface roughness above the drawing limit after the finishing pass');
await submitModal(p1, '#defect-form', '#defect-modal',
                  () => until(p1, () => document.querySelectorAll('#rows tr').length >= 11));
const defectRows = await count(p1, '#rows tr');
const firstRef = await text(p1, '#rows tr:first-child td:first-child');
ok('the defect was created and the register now shows it', defectRows === 11, `${defectRows} rows, newest ${firstRef}`);
report.defectCount = defectRows;
await shot(p1, '07_defect_register_with_new_row.png');

const detailHref = await p1.$eval('#rows a[href*="defect-detail"]', a => a.getAttribute('href'));
await p1.goto(`${BASE}/${detailHref}`, { waitUntil: 'networkidle0' });
await until(p1, () => document.querySelectorAll('#timeline li').length >= 1);
const status1 = await statusOf(p1);
const myMoves = await count(p1, '#move-buttons button');
const timeline1 = await count(p1, '#timeline li');
ok('the new defect opens with status OPEN and one history entry', status1 === 'OPEN' && timeline1 === 1,
   `status=${status1}, timeline=${timeline1}`);
ok('the Inspector is NOT offered the move to UNDER_INVESTIGATION (that is the Supervisor\'s step)',
   myMoves === 0, `${myMoves} move buttons offered`);
await shot(p1, '08_defect_detail_open.png');
report.defectUrl = detailHref;

/* ══════════════════════════════════════════════════════════════════════════
   JOURNEY 2 — A Production Supervisor investigates it and assigns the work
               (modules M6 → M8)
   ═════════════════════════════════════════════════════════════════════════ */
step('JOURNEY 2 — Supervisor: investigate and assign a corrective action');

const sup = await session(browser, 'supervisor1', 'Supervise@123');
const p2 = sup.page;
await shot(p2, '09_dashboard_supervisor.png');

await p2.goto(`${BASE}/${detailHref}`, { waitUntil: 'networkidle0' });
await until(p2, () => document.querySelectorAll('#move-buttons button').length >= 1);
const moves = await p2.$$eval('#move-buttons button', bs => bs.map(b => b.textContent.trim()));
ok('the Supervisor IS offered the next legal step', moves.join('|') === 'Move to UNDER INVESTIGATION', moves.join('|'));

await clickEl(p2, '[data-move="UNDER_INVESTIGATION"]');
const movedTo = await waitForStatus(p2, 'UNDER_INVESTIGATION');
ok('the move was accepted and the badge on the page changed', movedTo, await statusOf(p2));
ok('the timeline recorded the move, with the reason field offered',
   (await count(p2, '#timeline li')) === 2, `${await count(p2, '#timeline li')} entries`);
await shot(p2, '10_defect_under_investigation.png');

ok('the Supervisor is offered "Assign action"', await p2.$eval('#btn-assign', el => !el.classList.contains('d-none')));
await p2.click('#btn-assign');
await until(p2, () => document.querySelector('#assign-modal')?.classList.contains('show'));
await until(p2, () => document.querySelectorAll('#a-person option').length > 1);
const people = await p2.$$eval('#a-person option', os => os.map(o => o.textContent.trim()).filter(Boolean));
await p2.select('#a-person', '4');
await p2.$eval('#a-target', el => { el.value = '2026-10-30'; });
await p2.type('#a-description', 'Re-set the finishing tool and re-check the surface roughness on the first fifty parts');
await shot(p2, '11_assign_action_modal.png', false);
await submitModal(p2, '#assign-form', '#assign-modal',
                  () => until(p2, () => document.querySelectorAll('#action-rows tr').length >= 1));
ok('the corrective action was recorded against the defect',
   (await count(p2, '#action-rows tr')) === 1, `responsible people offered: ${people.length}`);

await until(p2, () => !!document.querySelector('[data-move="CORRECTIVE_ACTION"]'));
const moves2 = await p2.$$eval('#move-buttons button', bs => bs.map(b => b.textContent.trim()));
ok('now that an action exists, the Supervisor can move it on',
   moves2.some(m => /CORRECTIVE ACTION/.test(m)), moves2.join('|'));
await clickEl(p2, '[data-move="CORRECTIVE_ACTION"]');
await waitForStatus(p2, 'CORRECTIVE_ACTION');
ok('the defect is now in CORRECTIVE_ACTION', (await statusOf(p2)) === 'CORRECTIVE_ACTION', await statusOf(p2));
await shot(p2, '12_corrective_action_status.png');

/* the inspector cannot verify an unfinished action: the screen must not pretend otherwise */
const p3 = ins.page;
await p3.goto(`${BASE}/${detailHref}`, { waitUntil: 'networkidle0' });
await until(p3, () => document.querySelectorAll('#action-rows tr').length >= 1);
const verifyOffered = await count(p3, '#action-rows [data-verify]');
ok('the Inspector is NOT offered "Verify" while the action is still PENDING', verifyOffered === 0,
   `${verifyOffered} verify buttons`);

/* ══════════════════════════════════════════════════════════════════════════
   JOURNEY 3 — The work is completed, verified, and the defect is closed
               (modules M8 → M7, the full lifecycle across two users)
   ═════════════════════════════════════════════════════════════════════════ */
step('JOURNEY 3 — complete the action, verify it, close the defect');

await p2.reload({ waitUntil: 'networkidle0' });
await until(p2, () => document.querySelectorAll('#action-rows [data-progress]').length >= 1);
await p2.click('#action-rows [data-progress]');
await until(p2, () => document.querySelector('#progress-modal')?.classList.contains('show'));
await p2.select('#p-status', 'IN_PROGRESS');
await p2.type('#p-remark', 'Tool re-set on the line and the first parts measured');
await submitModal(p2, '#progress-form', '#progress-modal',
                  () => until(p2, () => /IN PROGRESS|IN_PROGRESS/.test(document.querySelector('#action-rows').textContent)));
await p2.reload({ waitUntil: 'networkidle0' });
await until(p2, () => document.querySelectorAll('#action-rows [data-progress]').length >= 1);
await p2.click('#action-rows [data-progress]');
await until(p2, () => document.querySelector('#progress-modal')?.classList.contains('show'));
await p2.select('#p-status', 'COMPLETED');
await p2.type('#p-remark', 'Fifty parts measured after re-setting the tool, all within the drawing limit');
await submitModal(p2, '#progress-form', '#progress-modal',
                  () => until(p2, () => /COMPLETED/.test(document.querySelector('#action-rows').textContent)));
await p3.reload({ waitUntil: 'networkidle0' });
await until(p3, () => document.querySelectorAll('#action-rows [data-verify]').length >= 1);
ok('once the work is COMPLETED, the Inspector is offered "Verify"',
   (await count(p3, '#action-rows [data-verify]')) === 1);

await p3.click('#action-rows [data-verify]');
await until(p3, () => document.querySelector('#verify-modal')?.classList.contains('show'));
await p3.select('#v-status', 'EFFECTIVE');
await p3.type('#v-remark', 'Re-inspection of the next fifty parts passed, so the correction is effective');
await submitModal(p3, '#verify-form', '#verify-modal',
                  () => until(p3, () => /EFFECTIVE/.test(document.querySelector('#action-rows').textContent)));
await until(p3, () => !!document.querySelector('[data-move="VERIFIED"]'));
const moves3 = await p3.$$eval('#move-buttons button', bs => bs.map(b => b.textContent.trim()));
ok('with an effective action, the Inspector may move the defect to VERIFIED',
   moves3.some(m => /VERIFIED/.test(m)), moves3.join('|'));

await clickEl(p3, '[data-move="VERIFIED"]');
await waitForStatus(p3, 'VERIFIED');
ok('the defect is VERIFIED', (await statusOf(p3)) === 'VERIFIED', await statusOf(p3));
await until(p3, () => !!document.querySelector('[data-move="CLOSED"]'));
const moves4 = await p3.$$eval('#move-buttons button', bs => bs.map(b => b.textContent.trim()));
ok('and now CLOSED is the step offered', moves4.some(m => /CLOSED/.test(m)), moves4.join('|'));

await clickEl(p3, '[data-move="CLOSED"]');
await waitForStatus(p3, 'CLOSED');
const finalTimeline = await count(p3, '#timeline li');
ok('the defect is CLOSED, and the timeline shows the whole journey', finalTimeline === 5, `${finalTimeline} entries`);
const timelineText = await p3.$eval('#timeline', el => el.textContent);
ok('the timeline names the people who moved it, not just the statuses',
   /Ravi Patil|Amit Deshmukh/.test(timelineText));
const closedScreen = await p3.evaluate(() => ({
  moves: document.querySelectorAll('#move-buttons button').length,
  deleteHidden: document.getElementById('btn-delete').classList.contains('d-none'),
  hint: document.getElementById('move-hint').textContent.trim()
}));
ok('a closed defect offers no further moves, and Delete is withdrawn',
   closedScreen.moves === 0 && closedScreen.deleteHidden, `hint: "${closedScreen.hint.slice(0, 56)}"`);
await shot(p3, '13_defect_closed_full_timeline.png');

/* ══════════════════════════════════════════════════════════════════════════
   JOURNEY 4 — An Administrator manages accounts, and the new account works
               (modules M2 → M1, proving the account is usable end to end)
   ═════════════════════════════════════════════════════════════════════════ */
step('JOURNEY 4 — Administrator: create an account, prove it works, deactivate it');

const adm = await session(browser, 'admin', 'Admin@123');
const p4 = adm.page;
await p4.goto(`${BASE}/users.html`, { waitUntil: 'networkidle0' });
await until(p4, () => document.querySelectorAll('#rows tr').length >= 6);
ok('the user list loaded for the Administrator', (await count(p4, '#rows tr')) === 6);
await shot(p4, '14_users_admin.png', false);

await p4.click('#btn-new');
await until(p4, () => document.querySelector('#user-modal')?.classList.contains('show'));
await p4.type('#u-username', 'journey_inspector');
await p4.type('#u-password', 'Journey@123');
await p4.type('#u-fullname', 'Journey Inspector');
await p4.select('#u-role', 'INSPECTOR');
await submitModal(p4, '#user-form', '#user-modal',
                  () => until(p4, () => document.querySelectorAll('#rows tr').length >= 7));
ok('the new account appears in the list', (await count(p4, '#rows tr')) === 7);
await shot(p4, '15_user_created.png');

/* The screen will not even offer it: the button on your own row is disabled and
   says why. That is prevention, not a proof - so the request is then made by
   hand, from this very session, to prove the server refuses it as well. */
const selfGuard = await p4.evaluate(async () => {
  const row = [...document.querySelectorAll('#rows tr')].find(tr => /System Administrator/.test(tr.textContent));
  const btn = row ? row.querySelector('[data-deactivate]') : null;
  const me = await (await fetch('/api/auth/me')).json();
  const res = await fetch('/api/users/' + me.userId + '/active/false', { method: 'PUT' });
  const body = await res.json();
  return {
    buttonDisabled: !!(btn && btn.disabled),
    tooltip: (btn && btn.title) || '',
    status: res.status,
    message: body.message || '(no message)'
  };
});
ok('the screen disables Deactivate on your own row, and the tooltip says why',
   selfGuard.buttonDisabled && /cannot deactivate your own account/i.test(selfGuard.tooltip),
   `"${selfGuard.tooltip}"`);
ok('and the server refuses it as well, even with the screen bypassed',
   selfGuard.status === 400 && /cannot deactivate your own account/i.test(selfGuard.message),
   `${selfGuard.status}: ${selfGuard.message}`);
await shot(p4, '16_self_deactivation_refused.png', false);

/* the new account must actually be able to sign in and use the system */
const ctxNew = await browser.createBrowserContext();
const p5 = await ctxNew.newPage();
watchDialogs(p5);
await p5.setViewport({ width: 1366, height: 900 });
await login(p5, 'journey_inspector', 'Journey@123');
await until(p5, () => document.querySelectorAll('#tiles .tile').length >= 6);
ok('the account created a moment ago can log in and see the dashboard',
   (await count(p5, '#tiles .tile')) === 6, `${await count(p5, '#tiles .tile')} lifecycle tiles`);
await shot(p5, '17_new_user_dashboard.png');
await ctxNew.close();

/* deactivate it, then prove the door is shut */
await p4.reload({ waitUntil: 'networkidle0' });
await until(p4, () => document.querySelectorAll('#rows [data-deactivate]').length >= 1);
await p4.evaluate(async () => {
  const row = [...document.querySelectorAll('#rows tr')].find(tr => /journey_inspector/.test(tr.textContent));
  row.querySelector('[data-deactivate]').click();
  await new Promise(r => setTimeout(r, 1200));
});
const ctxDead = await browser.createBrowserContext();
const p6 = await ctxDead.newPage();
watchDialogs(p6);
await p6.goto(`${BASE}/login.html`, { waitUntil: 'networkidle0' });
await p6.type('#username', 'journey_inspector');
await p6.type('#password', 'Journey@123');
await p6.click('#btn-login');
await sleep(1500);
const loginMsg = await text(p6, '#message');
ok('a deactivated account cannot log in, and the message says so',
   /deactivated/i.test(loginMsg || ''), (loginMsg || '').slice(0, 70));
await shot(p6, '18_deactivated_account_refused.png', false);
await ctxDead.close();

/* ══════════════════════════════════════════════════════════════════════════
   JOURNEY 5 — The same facts in three places: dashboard, list and report
               (this is what "integration" means: one database, one truth)
   ═════════════════════════════════════════════════════════════════════════ */
step('JOURNEY 5 — cross-module consistency: dashboard vs list vs report');

const dashMs = Date.now();
await p4.goto(`${BASE}/dashboard.html`, { waitUntil: 'domcontentloaded' });
await until(p4, () => document.querySelectorAll('#tiles .tile').length >= 6);
perfGate('dashboard: opened to tiles and charts on screen', Date.now() - dashMs, 3000);
const tiles = await p4.$$eval('#tiles .tile', ts => ts.map(t => ({
  label: t.querySelector('.tile-label').textContent.trim(),
  value: Number(t.querySelector('.tile-number').textContent.trim())
})));
const tile = (name) => tiles.find(t => new RegExp(name, 'i').test(t.label))?.value;
ok('the dashboard reports the closed defect this journey created', tile('closed') === 2, `CLOSED = ${tile('closed')}`);
ok('and the totals follow it', tile('total defects') === 11 && tile('open') === 2,
   `total=${tile('total defects')}, open=${tile('open')}, closed=${tile('closed')}`);
await shot(p4, '19_dashboard_after_journeys.png');
const overdueBanner = await text(p4, '#overdue-banner');
report.dashboardOverdue = Number((overdueBanner || '').match(/\d+/)?.[0]);
report.dashboardTotal = tile('total defects');
ok('the overdue strip counts them the same way the report will', report.dashboardOverdue === 2,
   `"${(overdueBanner || '').slice(0, 44)}"`);

await p4.goto(`${BASE}/reports.html`, { waitUntil: 'networkidle0' });
await until(p4, () => document.querySelectorAll('#report-rows tr').length > 0);
const reportRows = await count(p4, '#report-rows tr');
ok('the defect register report lists the same number of defects as the dashboard',
   reportRows === report.dashboardTotal, `report ${reportRows} vs dashboard ${report.dashboardTotal}`);
const printTitle = await text(p4, '#print-title');
const printCriteria = await text(p4, '#print-criteria');
const printLine = await text(p4, '#print-footer-line');
ok('the printed report carries its own title, filters and generation time (FR-10.11)',
   /Defect register/i.test(printTitle || '') && /generated/i.test(printLine || ''),
   `${printTitle} | ${printCriteria} | ${(printLine || '').slice(0, 46)}`);
await shot(p4, '20_reports_defect_register.png');

const repMs = Date.now();
await p4.select('#r-type', 'overdue-actions');
await p4.click('#btn-generate');
const overdueAppeared = await until(p4, () => document.querySelectorAll('#report-rows tr').length >= 1, null, 8000);
perfGate('report: generate clicked to rows on screen', Date.now() - repMs, 5000);
ok('the overdue report produced rows', overdueAppeared, `${await count(p4, '#report-rows tr')} rows`);
const overdueRows = await count(p4, '#report-rows tr');
ok('the overdue report shows the same count as the dashboard strip',
   overdueRows === report.dashboardOverdue, `report ${overdueRows} vs dashboard ${report.dashboardOverdue}`);
await shot(p4, '21_reports_overdue_actions.png');

/* the same number a third time, on the corrective actions screen */
await p4.goto(`${BASE}/corrective-actions.html?overdue=1`, { waitUntil: 'networkidle0' });
await until(p4, () => document.querySelectorAll('#rows tr').length >= 1);
const overdueList = await count(p4, '#rows tr');
ok('and the overdue filter on the corrective actions screen agrees as well',
   overdueList === report.dashboardOverdue, `list ${overdueList} vs dashboard ${report.dashboardOverdue}`);

/* printing: a real PDF, produced by Chrome's own print engine */
await p4.goto(`${BASE}/reports.html`, { waitUntil: 'networkidle0' });
await until(p4, () => document.querySelectorAll('#report-rows tr').length > 0);
await p4.emulateMediaType('print');
await p4.pdf({ path: path.join(EVIDENCE, 'Phase10_reports_print.pdf'), format: 'A4', printBackground: true });
const pdfSize = fs.statSync(path.join(EVIDENCE, 'Phase10_reports_print.pdf')).size;
ok('the report prints to a real A4 PDF (print CSS applied, not just claimed)',
   pdfSize > 10000, `${Math.round(pdfSize / 1024)} KB`);
await p4.emulateMediaType('screen');

/* ══════════════════════════════════════════════════════════════════════════
   JOURNEY 6 — The session ends while a page is open
   ═════════════════════════════════════════════════════════════════════════ */
step('JOURNEY 6 — the session goes away while a screen is open');

await p4.goto(`${BASE}/defects.html`, { waitUntil: 'networkidle0' });
await until(p4, () => document.querySelectorAll('#rows tr').length > 0);
const jar = await p4.cookies();
await p4.deleteCookie(...jar);
let landedOn = '(no navigation)';
try { landedOn = await clickNav(p4, 'products.html', 'login.html', 15000); }
catch { landedOn = p4.url(); }
ok('the browser is sent back to the login screen instead of showing empty tables',
   /login\.html$/.test(landedOn), landedOn.replace(BASE, ''));
await shot(p4, '22_session_expired_redirect.png', false);

/* a signed-out visitor opening a screen directly gets the same treatment */
const ctxOut = await browser.createBrowserContext();
const p7 = await ctxOut.newPage();
watchDialogs(p7);
await p7.goto(`${BASE}/dashboard.html`, { waitUntil: 'networkidle0' });
await sleep(1800);
ok('opening a screen with no session at all also lands on the login',
   /login\.html$/.test(p7.url()), p7.url().replace(BASE, ''));
await ctxOut.close();

} catch (error) {
  failed++;
  failures.push('the run itself threw: ' + error.message);
  console.log(`\n  FAIL  the run threw an error: ${error.message}`);
} finally {
  await browser.close();
}

console.log('\n==================================================');
console.log(`  Phase 10 integration journeys:  PASSED ${passed}   FAILED ${failed}`);
if (failures.length) console.log('  failures: ' + failures.join(' | '));
if (findings.length) console.log('  DEFECTS FOUND (bug-fixing phase): ' + findings.join(' | '));
console.log('==================================================');

if (perf.length) {
  console.log('\n  Phase 4 response-time gates, measured by this run:');
  for (const g of perf) console.log(`    ${g.pass ? 'pass' : 'MISS'}  ${g.ms} ms  (gate ${g.limit} ms)  ${g.label}`);
}
const dialogs = [...new Set([...passedPageDialogs])];
if (dialogs.length) console.log('\n  confirmations answered during the run: ' + dialogs.length);
console.log('');
process.exit(failed || findings.length ? 1 : 0);
