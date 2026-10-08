# Server-side tests

Two tools, no browser needed. Together they are the Phase 12 test suite for
everything behind the screens; the screens themselves have their own tools in
`frontend/tests/`.

| Tool | What it is | Cases |
|---|---|---|
| `api-test-cases.sh` | calls the running application over HTTP with `curl`, one case per check, and prints the result of each | 113 |
| `src/test/java/…/DefectLifecycleTest.java` | unit tests for the defect lifecycle's role table | 9 |
| `src/test/java/…/ReportServiceTest.java` | unit tests for the percentage arithmetic the reports are built on | 5 |

## Running the unit tests

```bash
cd backend
mvn test                    # or mvn package, which runs them and builds the jar
```

They need no database, no server and no Spring context — they are plain JUnit
methods over pure functions, and finish in well under a second. The surefire
report is written to `target/surefire-reports/`.

## Running the API test cases

```bash
# 1. the application must be running (the script talks to it, it does not start it)
cd backend && java -jar target/manufacturing-qms-1.0.0.jar &

# 2. the database must hold the sample data — the cases assert exact counts
mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < ../database/01_schema.sql
mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < ../database/02_sample_data.sql

# 3. run the cases
bash backend/tests/api-test-cases.sh
```

## Reading the output

Each case prints one line — `PASS`, `FAIL` or `OPEN` — with the expectation and
what actually came back, so the output can be pasted straight into a test-case
table:

```
  PASS  TC-API-51   an unknown status filter is refused, not ignored  [want 400, got 400, has "Unknown status filter"]
```

| Result | Meaning | What to do |
|---|---|---|
| `PASS` | the application did what the case expects | nothing |
| `FAIL` | the case expects something the application does not do, **and the expectation was checked by hand and is right** | this is a defect — fix the application, then re-run |
| `OPEN` | a known defect that is already written down; the case is the defect's acceptance test | fix the defect; the case starts printing `PASS` by itself |

The script exits `0` only when nothing is outstanding, which makes it usable as
a gate before a demonstration or a commit.

**The script writes to the database.** It registers an inspection, a defect, a
corrective action, a product, a batch and a user, and drives a defect through
its whole lifecycle, because a test that does not write cannot prove that
writing works. Reset before re-running it, or the counts it asserts will not
match:

```bash
mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/01_schema.sql
mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/02_sample_data.sql
```

## What the cases cover

One section per module, in the order of the report's Chapter 6:

| Section | Module | Cases |
|---|---|---|
| M1 | Login and session (FR-01) | TC-API-01…09 |
| M2 | User management (FR-02) | TC-API-10…20 |
| M3 | Product master (FR-03) | TC-API-21…30 |
| M4 | Production batches (FR-04) | TC-API-31…39 |
| M5 | Quality inspections (FR-05) | TC-API-40…48 |
| M6 | Defect management (FR-06) | TC-API-49…59 |
| M7 | Defect tracking — the lifecycle (FR-07) | TC-API-60…69, 86…91 |
| M8 | Corrective actions (FR-08) | TC-API-70…85 |
| M9 | Dashboard (FR-09) | TC-API-92…93 |
| M10 | Reports (FR-10) | TC-API-94…101 |
| — | the error contract (what every refusal looks like) | TC-API-102…108 |
| — | the remarks endpoint, whose body is not the usual one | TC-API-109…112 |

Every section follows the same shape: who may do it, who may not, what a valid
request returns, the boundary values, and what happens when the input is wrong.

## Adding a case

Use the helpers at the top of the script; a case is one line.

```bash
call GET "/api/defects?status=OPEN" "$ADMIN_JAR"
tc TC-API-99 "the register honours the status filter" 200 '"currentStatus":"OPEN"'

call POST /api/products "$INSP_JAR" '{"productCode":"P-999"}'
tc TC-API-100 "only an admin may create a product" 400 'Only an Administrator'
```

`call` sends the request and leaves the status in `$STATUS` and the body in
`$BODY`; `tc` checks both. `tc_count` compares the number of rows in a list
response, `tc_open` records a case whose expectation the application does not
meet yet. Keep the ids in order and give a new case the next free number.
