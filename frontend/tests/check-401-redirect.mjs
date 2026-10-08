/* ============================================================================
   check-401-redirect.mjs - the session guard, browser side.

   The server half (any /api call without a session answers 401) is checked
   with curl. This file checks what the SHIPPED frontend does with that 401:
   it loads the real frontend/js/api.js and feeds it a 401 response, once from
   a normal screen (must navigate to login.html) and once from login.html
   itself (must NOT navigate, or a wrong password would bounce in a loop).

   jsdom cannot be used here: its window.location is non-configurable and it
   refuses to navigate, so the redirect cannot be observed from inside. Running
   the real file in a small sandbox lets the call be recorded instead.

   Usage: node check-401-redirect.mjs        (no running server needed)
   ========================================================================= */
import fs from 'node:fs';
import vm from 'node:vm';

const SOURCE = fs.readFileSync(new URL('../js/api.js', import.meta.url), 'utf8');
let failed = 0;

async function callFrom(page, status) {
  const navigated = [];
  const sandbox = {
    window: { location: { pathname: page, replace: (u) => navigated.push(u) } },
    fetch: async () => ({
      status,
      ok: status >= 200 && status < 300,
      text: async () => JSON.stringify({
        status, error: 'Unauthorized', details: [],
        message: 'You are not logged in. Please log in and try again.'
      })
    }),
    console, JSON, Error, Promise, setTimeout
  };
  vm.createContext(sandbox);
  vm.runInContext(SOURCE, sandbox);
  const outcome = await vm.runInContext(
    "API.get('/api/products').then(() => 'no error').catch(e => e.message)", sandbox);
  return { navigated, outcome };
}

function check(label, condition, detail) {
  console.log(`  ${condition ? 'PASS' : 'FAIL'}  ${label}${detail ? '  [' + detail + ']' : ''}`);
  if (!condition) failed++;
}

console.log('\n  api.js reaction to a 401 (session gone)');

let r = await callFrom('/dashboard.html', 401);
check('screen + 401 redirects to login.html',
      r.navigated.length === 1 && r.navigated[0] === 'login.html',
      'navigated to ' + JSON.stringify(r.navigated));
check('the caller still receives the server message',
      r.outcome === 'You are not logged in. Please log in and try again.', r.outcome);

r = await callFrom('/login.html', 401);
check('login.html + 401 does NOT redirect (no loop)',
      r.navigated.length === 0, 'navigated ' + r.navigated.length + ' time(s)');

r = await callFrom('/dashboard.html', 200);
check('normal 200 response never redirects', r.navigated.length === 0);

console.log(`\n  api.js session checks:  PASSED ${4 - failed}   FAILED ${failed}\n`);
process.exit(failed ? 1 : 0);
