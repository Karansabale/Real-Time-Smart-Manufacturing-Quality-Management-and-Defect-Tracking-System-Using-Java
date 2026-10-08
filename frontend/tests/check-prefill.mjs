/* ============================================================================
   check-prefill.mjs — the focused regression check for D-01 (Phase 11)

   What it is: the smallest possible test of the one hand-off that Phase 10
   found broken. The inspections screen sends the inspector to

       defects.html?new=1&productId=..&batchId=..&inspectionId=..

   on a FAIL row, and the register is supposed to open with those three values
   already chosen. This opens exactly that address in a real browser and looks
   at what a person would see.

   Why it exists separately from integration-journeys.mjs: that suite proves the
   whole system and takes two minutes and a database reset. This takes ten
   seconds, writes nothing, and answers one question - so it can be run every
   time defects.html is touched.

   Run:   cd frontend/tests && node check-prefill.mjs
   Needs: the application running on http://localhost:8080
   Exits: 0 if the form arrived filled, 1 if it did not.
   ========================================================================= */
import puppeteer from 'puppeteer';

const BASE = 'http://localhost:8080';

let passed = 0, failed = 0;
function ok(label, condition, detail = '') {
  if (condition) { passed++; console.log(`  PASS  ${label}${detail ? '  [' + detail + ']' : ''}`); }
  else { failed++; console.log(`  FAIL  ${label}${detail ? '  [' + detail + ']' : ''}`); }
}

const browser = await puppeteer.launch({
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage', '--disable-gpu']
});

try {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: 1366, height: 900 });

  await page.goto(`${BASE}/login.html`, { waitUntil: 'domcontentloaded' });
  await page.type('#username', 'inspector1');
  await page.type('#password', 'Inspect@123');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle0' }),
    page.click('#btn-login')
  ]);

  /* Find a real failed inspection the way the inspections screen would: the row
     the inspector would click. */
  const fail = await page.evaluate(async () => {
    const rows = await (await fetch('/api/inspections')).json();
    const row = rows.find(r => r.result === 'FAIL');
    return row ? { id: row.inspectionId, productId: row.productId, batchId: row.batchId } : null;
  });
  ok('there is a failed inspection to raise a defect from', !!fail,
     fail ? `inspection ${fail.id}, product ${fail.productId}, batch ${fail.batchId}` : 'none found');

  const url = `${BASE}/defects.html?new=1&productId=${fail.productId}` +
              `&batchId=${fail.batchId}&inspectionId=${fail.id}`;
  await page.goto(url, { waitUntil: 'networkidle0' });
  const opened = await page.waitForFunction(
    () => document.querySelector('#defect-modal')?.classList.contains('show'), { timeout: 10000 })
    .then(() => true).catch(() => false);
  ok('the register opens its form for a new defect', opened);
  await new Promise(r => setTimeout(r, 1200));   // let the option lists settle

  const form = await page.evaluate(() => {
    const q = id => document.getElementById(id);
    return {
      product: q('d-product').value,
      batch: q('d-batch').value,
      inspection: q('d-inspection').value,
      batchOffered: [...q('d-batch').options].some(o => o.value !== ''),
      inspectionOffered: [...q('d-inspection').options].some(o => o.value !== '')
    };
  });

  ok('the product from the link is already chosen',
     form.product === String(fail.productId), `#d-product = "${form.product}"`);
  ok('the production batch from the link is already chosen',
     form.batch === String(fail.batchId), `#d-batch = "${form.batch}"`);
  ok('the failed inspection from the link is already chosen',
     form.inspection === String(fail.id), `#d-inspection = "${form.inspection}"`);
  ok('the batch and inspection lists were filled for that product, not left empty',
     form.batchOffered && form.inspectionOffered,
     `batch list filled: ${form.batchOffered}, inspection list filled: ${form.inspectionOffered}`);

  await ctx.close();
} finally {
  await browser.close();
}

console.log('\n==================================================');
console.log(`  D-01 pre-fill check:  PASSED ${passed}   FAILED ${failed}`);
console.log('==================================================\n');
process.exit(failed ? 1 : 0);
