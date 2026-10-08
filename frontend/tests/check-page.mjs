/* Loads ONE page in a fresh process, runs its scripts against the live
   server, and checks what the page ends up showing.
   Usage: node check-page.mjs <path> <username> <password> <expectations...>
   Expectation forms:  "#sel==3"  exact count   |   "#sel>=2"  at least
                       "text:substring"  page text   |  "notext:substring" */
import { JSDOM, VirtualConsole } from 'jsdom';

const [path, username, password, ...expectations] = process.argv.slice(2);
const BASE = 'http://localhost:8080';
let ok = true;
const results = [];

function claim(name, good, detail) {
  results.push((good ? 'PASS  ' : 'FAIL  ') + name + (good ? '' : '  [' + detail + ']'));
  if (!good) ok = false;
}

let cookie = null;
if (username !== '-') {
  const login = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  claim('login as ' + username, login.ok, 'status ' + login.status);
  if (login.ok) cookie = login.headers.getSetCookie()[0].split(';')[0];
}

const errors = [];
const vc = new VirtualConsole();
vc.on('jsdomError', e => { if (!/Could not parse CSS|Not implemented: navigation/.test(e.message)) errors.push(e.message.slice(0, 120)); });
vc.on('error', (...a) => errors.push(String(a.join(' ')).slice(0, 160)));

const dom = await JSDOM.fromURL(BASE + '/' + path, {
  runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true, virtualConsole: vc,
  beforeParse(window) {
    window.fetch = (url, options = {}) => {
      const headers = new Headers(options.headers || {});
      if (cookie) headers.set('Cookie', cookie);
      return fetch(new URL(url, BASE + '/' + path), { ...options, headers });
    };
    window.confirm = () => true;
    window.print = () => {};
    window.alert = () => {};
    window.matchMedia = window.matchMedia || (() => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
    window.ResizeObserver = window.ResizeObserver || class { observe() {} unobserve() {} disconnect() {} };
    window.addEventListener('error', e => errors.push('onerror: ' + e.message));
    window.addEventListener('unhandledrejection', e => errors.push('unhandled: ' + (e.reason && e.reason.message)));
  }
});

// wait until the page asks for data and finishes painting
await new Promise(r => setTimeout(r, 1800));
const document = dom.window.document;
const text = document.body.textContent;

for (const expectation of expectations) {
  if (expectation.startsWith('text:')) {
    const needle = expectation.slice(5);
    claim('page says "' + needle + '"', text.includes(needle), 'not found');
  } else if (expectation.startsWith('notext:')) {
    const needle = expectation.slice(7);
    claim('page does not say "' + needle + '"', !text.includes(needle), 'unexpectedly present');
  } else {
    const [selector, rule] = expectation.split(/(==|>=)/);
    const count = document.querySelectorAll(selector).length;
    const wanted = Number(rule === '==' ? expectation.split('==')[1] : expectation.split('>=')[1]);
    const good = rule === '==' ? count === wanted : count >= wanted;
    claim(selector + ' ' + rule + ' ' + wanted, good, 'found ' + count);
  }
}

claim('no javascript errors', errors.length === 0, errors.join(' | '));

console.log('--- ' + path + ' ---');
results.forEach(r => console.log('  ' + r));
dom.window.close();
process.exit(ok ? 0 : 1);
