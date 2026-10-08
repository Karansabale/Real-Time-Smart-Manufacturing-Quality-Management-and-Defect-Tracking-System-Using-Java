# Tests and developer tooling for the screens

These scripts are **not needed to run or demonstrate the application.** They exist
because the screens are the one part of the system that cannot be checked from the
server side: a page can return HTTP 200 and still show nothing if its script fails.

| File | What it is | Used by |
|---|---|---|
| `verify-pages.sh` + `check-page.mjs` | all ten screens loaded one process at a time into a throwaway DOM (jsdom), asserting what ends up rendered — row counts, values, badges — and that no page logged a JavaScript error. 57 checks. | Phase 9 |
| `check-401-redirect.mjs` | runs the real `js/api.js` in a Node `vm` with a stubbed fetch, to prove a 401 sends the browser to `login.html` — the one behaviour jsdom cannot observe | Phase 9 |
| `integration-journeys.mjs` | **real headless Chrome** driving the real application through six journeys (a full defect lifecycle across two roles, account lifecycle, dashboard/report/list agreement, session expiry), taking 22 screenshots and printing the report to A4 PDF | Phase 10 |
| `check-prefill.mjs` | **real headless Chrome** walking both ways into screen 8 — the "raise a defect" link on an inspection and the "edit" link on a register row — and asserting the form arrives with its boxes already filled in. It is the proof of the D-01 fix, which the count-based journeys could not see. 6 checks. | Phase 11 |

## Requirements

* Node.js 18 or later (Node 20 is what it was written and run on)
* the application running on `http://localhost:8080`, with MySQL up and seeded
* for the jsdom tools: `npm install jsdom canvas` (canvas lets Chart.js draw; without
  it the dashboard charts are skipped, everything else still works)
* for the journeys: `npm install puppeteer`, which downloads its own Chrome (~150 MB)

On a bare Debian/Ubuntu container, puppeteer's Chrome needs system libraries that a
minimal image does not have. Without them it fails with
`error while loading shared libraries: libnspr4.so`. This is the list that fixed it:

```bash
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  libnspr4 libnss3 libatk1.0-0t64 libatk-bridge2.0-0t64 libcups2t64 libdrm2 \
  libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 \
  libasound2t64 libpango-1.0-0 libcairo2 libx11-6 libxcb1 libxext6 libglib2.0-0 \
  fonts-liberation
```

## Running

```bash
# the nine-page jsdom checks (Phase 9)
cd frontend/tests && npm install jsdom canvas && bash verify-pages.sh

# the journeys (Phase 10) - RESET THE DATABASE FIRST, they check exact counts
cd frontend/tests && npm install puppeteer && node integration-journeys.mjs

# the form-prefill checks (Phase 11) - same requirements as the journeys
cd frontend/tests && node check-prefill.mjs
```

The server side has its own two tools, which need no browser at all:
`backend/tests/api-test-cases.sh` (113 API test cases) and the unit tests under
`backend/src/test/java`. See `backend/tests/README.md`.

The journeys leave their output in three places:

| Output | Where |
|---|---|
| the run log | `frontend/tests/run-latest.txt` (and `docs/evidence/Phase10_journey_run.txt`) |
| screenshots | `../../screenshots/01…22_*.png` |
| the printed report | `../../docs/evidence/Phase10_reports_print.pdf` |

Exit code `0` means nothing is outstanding. Exit code `1` means either a check
failed **or** a defect the run found is still listed — the suite prints those
separately, as `FOUND D-01 …`, because a defect in the product is a result, not a
broken test.

## What the journeys need from the database

They register an inspection, a defect, a corrective action and a user, and they
assert exact numbers (11 defects, 2 open, 2 overdue). Run them against a freshly
seeded database or those counts will not match:

```bash
mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/01_schema.sql
mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/02_sample_data.sql
```

## Interpreting a failure

A `no javascript errors` failure usually means a page script threw — open the page
in a real browser with the developer console visible and the same error will be
there. A count failure (`#rows tr == 10` found 7) usually means the seed data has
changed since the expectation was written, or a filter is applied; check the page by
hand before assuming a bug.

For the journeys there are three more failure shapes worth knowing, all of which cost
time while the suite was being written (Phase 10 §12 has the full account):

* **a click that never landed** — the application asks `confirm()` before anything
  destructive, and Bootstrap keeps its backdrop over the page for ~150 ms after a
  modal closes; a click in that window is swallowed. If a move "does nothing",
  check these two before suspecting the server.
* **a wait that always fails** — `page.waitForFunction` runs inside the browser, so
  its check must be plain DOM code and must receive its values as arguments; a check
  that calls back into a Node-side helper can never succeed and the timeout looks
  like a failed assertion.
* **a screenshot showing the previous screen** — the write screens do not navigate;
  they close the modal and re-render the list in place, so a wait for navigation
  there hangs instead of advancing.
