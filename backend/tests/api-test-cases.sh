#!/usr/bin/env bash
# ============================================================================
# api-test-cases.sh — the API test cases (Phase 12, Testing)
#
# What it is: every API test case in the Phase 12 document, executed against the
# running application. Each case prints the id used in that document, what it
# does, and what actually came back — so the document's "actual result" column
# and this output are the same thing.
#
# Why curl and not a testing framework: the API is the contract three roles and
# a browser depend on. curl states that contract in the same words the report
# uses ("POST /api/defects as an inspector answers 201"), it needs no build, and
# a marker can read it. The lifecycle's pure logic — which cannot be reached
# exhaustively from outside — is unit-tested in
# backend/src/test/java/com/project/qms/service/DefectLifecycleTest.java.
#
# Run:
#     bash backend/tests/api-test-cases.sh
#
# Needs:
#     1. the application running on http://localhost:8080
#     2. the database reset to the sample data — several cases assert exact
#        counts, and this script writes to the database. The precondition check
#        below refuses to run otherwise.
#
#     mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/01_schema.sql
#     mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/02_sample_data.sql
#
# Exit code: 0 if every case passed, 1 if any failed.
# ============================================================================
set -u

BASE=http://localhost:8080
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT        # /tmp is per-call; keep everything in one place

PASSED=0
FAILED=0
OPEN=0            # cases whose expectation is not met YET: a documented finding
FAILURES=()
FINDINGS=()

# ---------------------------------------------------------------- helpers ---
login() {   # login <cookiejar> <username> <password>  -> prints the status code
  local jar=$1 user=$2 pass=$3
  curl -s -o "$WORK/login_body" -c "$jar" -w '%{http_code}' \
       -X POST "$BASE/api/auth/login" \
       -H 'Content-Type: application/json' \
       --data "{\"username\":\"$user\",\"password\":\"$pass\"}"
}

call() {    # call <METHOD> <path> [cookiejar] [json body] -> sets STATUS and BODY
  local method=$1 path=$2 jar=${3:-} json=${4:-}
  local args=(-s -o "$WORK/body" -w '%{http_code}' -X "$method" "$BASE$path")
  [ -n "$jar" ]  && args+=(-b "$jar")
  [ -n "$json" ] && args+=(-H 'Content-Type: application/json' --data "$json")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat "$WORK/body")
}

report() {  # report <id> <description> <ok 0|1> <what was actually seen>
  local id=$1 desc=$2 ok=$3 detail=$4
  if [ "$ok" = "1" ]; then
    PASSED=$((PASSED + 1))
    printf '  PASS  %-11s %-62s [%s]\n' "$id" "$desc" "$detail"
  else
    FAILED=$((FAILED + 1)); FAILURES+=("$id — $desc")
    printf '  FAIL  %-11s %-62s [%s]\n' "$id" "$desc" "$detail"
  fi
}

tc() {      # tc <id> <description> <expected status> [body must contain ...]
  local id=$1 desc=$2 want=$3 needle=${4:-}
  local ok=1 detail="want $want, got $STATUS"
  [ "$STATUS" = "$want" ] || ok=0
  if [ -n "$needle" ]; then
    if grep -qF -- "$needle" <<<"$BODY"; then detail="$detail, has [$needle]"
    else ok=0; detail="$detail, missing [$needle]"; fi
  fi
  report "$id" "$desc" "$ok" "$detail"
}

count_of() { grep -o -F -- "$1" <<<"$BODY" | wc -l | tr -d ' '; }

tc_open() { # tc_open <id> <finding> <description> <expected status> <body must contain>
  # A case whose expectation the application does not meet yet. It is not a
  # broken test and not a failure of the phase: it is a finding, printed as
  # OPEN and carried into the report. The moment the finding is fixed, this
  # same case starts printing PASS by itself - the check is the fix's proof.
  local id=$1 ref=$2 desc=$3 want=$4 needle=$5
  local ok=1 detail="want $want, got $STATUS"
  [ "$STATUS" = "$want" ] || ok=0
  if grep -qF -- "$needle" <<<"$BODY"; then detail="$detail, has [$needle]"
  else ok=0; detail="$detail, missing [$needle]"; fi
  if [ "$ok" = "1" ]; then
    report "$id" "$desc" 1 "$detail"
  else
    OPEN=$((OPEN + 1)); FINDINGS+=("$ref  $id — $desc  [$detail]")
    printf '  OPEN  %-11s %-62s [%s — %s]\n' "$id" "$desc" "$ref" "$detail"
  fi
}

tc_count() { # tc_count <id> <description> <expected status> <json key> <expected count>
  local id=$1 desc=$2 want=$3 key=$4 want_n=$5
  local got; got=$(count_of "$key")
  local ok=1 detail="want $want with $want_n rows, got $STATUS with $got rows"
  [ "$STATUS" = "$want" ] || ok=0
  [ "$got" = "$want_n" ] || ok=0
  report "$id" "$desc" "$ok" "$detail"
}

value_of() { grep -o "\"$1\":[0-9]\+" <<<"$BODY" | head -1 | cut -d: -f2; }

section() { printf '\n--- %s ---\n' "$1"; }

# ============================================================ preconditions ==
printf 'Phase 12 — API test cases\n=========================\n'

call GET /api/defects ""           # no session: proves 401, not a count
if [ "$STATUS" = "200" ]; then
  echo "The API answered without a session — that is itself a defect. Stopping." >&2
  exit 2
fi

if ! curl -s -o /dev/null --max-time 5 "$BASE/login.html"; then
  echo "The application is not answering on $BASE. Start it first (Phase 8 §1)." >&2
  exit 2
fi

ADMIN_JAR=$WORK/admin.jar
INSP_JAR=$WORK/inspector.jar
SUP_JAR=$WORK/supervisor.jar
INSP2_JAR=$WORK/inspector2.jar
TMP_JAR=$WORK/temporary.jar

s=$(login "$ADMIN_JAR" admin 'Admin@123'); [ "$s" = "200" ] || { echo "Could not sign in as admin (HTTP $s)." >&2; exit 2; }
login "$INSP_JAR" inspector1 'Inspect@123' >/dev/null
login "$SUP_JAR" supervisor1 'Supervise@123' >/dev/null
login "$INSP2_JAR" inspector2 'Inspect@123' >/dev/null

call GET /api/defects "$ADMIN_JAR"
defects_now=$(count_of '"defectId":')
call GET /api/inspections "$ADMIN_JAR"
inspections_now=$(count_of '"inspectionId":')
call GET /api/users "$ADMIN_JAR"
users_now=$(count_of '"userId":')

if [ "$defects_now" != "10" ] || [ "$inspections_now" != "12" ] || [ "$users_now" != "6" ]; then
  cat >&2 <<EOF

  The database is not in its sample state.
      defects:     $defects_now   (expected 10)
      inspections: $inspections_now   (expected 12)
      users:       $users_now   (expected 6)

  Several cases below assert exact counts, and this script writes to the
  database. Reset it first:

      mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/01_schema.sql
      mysql --no-defaults -u root --socket=/tmp/mysql84.sock manufacturing_qms < database/02_sample_data.sql

EOF
  exit 2
fi
echo "Preconditions met: application answering, database in its sample state (10 defects, 12 inspections, 6 users)."

# ============================================================== M1 — login ==
section "M1  Login and session                      (FR-01)"

call POST /api/auth/login "" '{"username":"admin","password":"Admin@123"}'
tc TC-API-01 "POST /api/auth/login with the right password answers 200" 200 '"role":"ADMIN"'

call POST /api/auth/login "" '{"username":"admin","password":"wrong-password"}'
tc TC-API-02 "a wrong password is refused with the same message as an unknown user" 400 'Incorrect username or password'

call POST /api/auth/login "" '{"username":"no-such-person","password":"whatever"}'
tc TC-API-03 "an unknown username is refused without revealing that the user does not exist" 400 'Incorrect username or password'

call POST /api/auth/login "" '{"username":"inspector3","password":"Inspect@123"}'
tc TC-API-04 "a deactivated account cannot sign in, and is told why" 400 'deactivated'

call GET /api/auth/me
tc TC-API-05 "GET /api/auth/me with no session answers 401" 401

call GET /api/auth/me "$ADMIN_JAR"
tc TC-API-06 "GET /api/auth/me with a session returns who is signed in" 200 '"username":"admin"'

call GET /api/defects
tc TC-API-07 "a core read with no session answers 401 instead of leaking data" 401

curl -s -o /dev/null -c "$TMP_JAR" -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
     --data '{"username":"inspector2","password":"Inspect@123"}'
call POST /api/auth/logout "$TMP_JAR"
tc TC-API-08 "POST /api/auth/logout ends the session" 204
call GET /api/auth/me "$TMP_JAR"
tc TC-API-09 "after logging out, the same cookie is no longer accepted" 401

# ============================================================== M2 — users ==
section "M2  User management                       (FR-02)"

call GET /api/users "$ADMIN_JAR"
tc_count TC-API-10 "an admin sees all six accounts" 200 '"userId":' 6

call GET /api/users?active=true "$ADMIN_JAR"
tc_count TC-API-11 "?active=true returns only the five usable accounts" 200 '"userId":' 5

call GET /api/users "$INSP_JAR"
tc TC-API-12 "an inspector may read the user list — the assignment dropdown needs it" 200

call POST /api/users "$INSP_JAR" '{"username":"nope_tc","password":"Passw0rd!","fullName":"Not Allowed","role":"INSPECTOR"}'
tc TC-API-13 "only an admin may create an account" 400 'Only an Administrator'

call POST /api/users "$ADMIN_JAR" '{"username":"test_user_tc","password":"Passw0rd!","fullName":"Test User TC","role":"INSPECTOR"}'
tc TC-API-14 "an admin creates an account" 201 '"username":"test_user_tc"'
NEW_USER_ID=$(value_of userId)

call POST /api/users "$ADMIN_JAR" '{"username":"test_user_tc","password":"Passw0rd!","fullName":"Duplicate","role":"INSPECTOR"}'
tc TC-API-15 "the same username twice is refused as a conflict" 409 'is already taken'

call POST /api/users "$ADMIN_JAR" '{"username":"ab","password":"Passw0rd!","fullName":"Too Short","role":"INSPECTOR"}'
tc TC-API-16 "validation: a two-character username is refused with the field named" 400 'Username must be 3 to 50 characters'

call PUT "/api/users/1/active/false" "$ADMIN_JAR"
tc TC-API-17 "an admin cannot deactivate the account they are signed in with" 400 'cannot deactivate your own account'

call PUT "/api/users/$NEW_USER_ID/active/false" "$ADMIN_JAR"
tc TC-API-18 "an admin deactivates somebody else" 200 '"isActive":false'

call POST /api/auth/login "" '{"username":"test_user_tc","password":"Passw0rd!"}'
tc TC-API-19 "the account that was just deactivated can no longer sign in" 400 'deactivated'

call GET /api/users/9999 "$ADMIN_JAR"
tc TC-API-20 "an unknown user id answers 404" 404

# =========================================================== M3 — products ==
section "M3  Product master                        (FR-03)"

call GET /api/products "$ADMIN_JAR"
tc_count TC-API-21 "the product list holds the six sample products" 200 '"productId":' 6

call GET "/api/products?q=P-101" "$ADMIN_JAR"
tc TC-API-22 "the search filter narrows the list" 200 'P-101'

call POST /api/products "$INSP_JAR" '{"productCode":"P-TC-1","productName":"Test Product","category":"MACHINED"}'
tc TC-API-23 "only an admin may create a product" 400 'Only an Administrator'

call POST /api/products "$ADMIN_JAR" '{"productCode":"P-TC-1","productName":"Test Product TC","category":"MACHINED","specification":"created by the Phase 12 test cases"}'
tc TC-API-24 "an admin creates a product" 201 '"productCode":"P-TC-1"'
NEW_PRODUCT_ID=$(value_of productId)

call POST /api/products "$ADMIN_JAR" '{"productCode":"P-TC-1","productName":"Same code again","category":"MACHINED"}'
tc TC-API-25 "a duplicate product code is refused as a conflict" 409 'already exists'

call POST /api/products "$ADMIN_JAR" '{"productCode":"","productName":"","category":""}'
tc TC-API-26 "validation: every required field is reported at once" 400 'Product code is required'

call DELETE /api/products/1 "$ADMIN_JAR"
tc TC-API-27 "a product that batches refer to cannot be deleted" 400 'cannot be deleted because'

call DELETE "/api/products/$NEW_PRODUCT_ID" "$ADMIN_JAR"
tc TC-API-28 "a product with no batches can be deleted" 204

call GET "/api/products/$NEW_PRODUCT_ID" "$ADMIN_JAR"
tc TC-API-29 "the deleted product is really gone" 404

call GET /api/products/9999 "$ADMIN_JAR"
tc TC-API-30 "an unknown product id answers 404" 404

# ============================================================ M4 — batches ==
section "M4  Production batches                    (FR-04)"

call GET /api/batches "$ADMIN_JAR"
tc_count TC-API-31 "the batch list holds the ten sample batches" 200 '"batchId":' 10

call GET "/api/batches?productId=1" "$ADMIN_JAR"
tc TC-API-32 "batches can be filtered by product" 200 'BATCH-2026-006'

call POST /api/batches "$ADMIN_JAR" '{"productId":1,"batchNumber":"BATCH-TC-001","quantityProduced":100,"productionLine":"LINE-1","startDate":"2026-10-01"}'
tc TC-API-33 "only a supervisor may create a batch" 400 'Only a Production Supervisor'

call POST /api/batches "$SUP_JAR" '{"productId":1,"batchNumber":"BATCH-TC-001","quantityProduced":100,"productionLine":"LINE-1","startDate":"2026-10-01"}'
tc TC-API-34 "a supervisor creates a batch" 201 '"batchNumber":"BATCH-TC-001"'
NEW_BATCH_ID=$(value_of batchId)

call POST /api/batches "$SUP_JAR" '{"productId":1,"batchNumber":"BATCH-TC-002","quantityProduced":0,"productionLine":"LINE-1","startDate":"2026-10-01"}'
tc TC-API-35 "validation: a quantity of zero is refused" 400 'greater than zero'

call PUT "/api/batches/$NEW_BATCH_ID/status" "$SUP_JAR" '{"productionStatus":"IN_PROGRESS"}'
tc TC-API-36 "a supervisor moves a batch on" 200 '"productionStatus":"IN_PROGRESS"'

call PUT "/api/batches/$NEW_BATCH_ID/status" "$SUP_JAR" '{"productionStatus":"PLANNED"}'
tc TC-API-37 "the batch status is free-form within its vocabulary, as decided in Phase 8 D14" 200 '"productionStatus":"PLANNED"'

call PUT "/api/batches/$NEW_BATCH_ID/status" "$SUP_JAR" '{"productionStatus":""}'
tc TC-API-38 "validation: an empty status is refused" 400 'Production status is required'

call GET /api/batches/9999 "$ADMIN_JAR"
tc TC-API-39 "an unknown batch id answers 404" 404

# ======================================================== M5 — inspections ==
section "M5  Quality inspections                   (FR-05)"

call GET /api/inspections "$ADMIN_JAR"
tc_count TC-API-40 "the inspection register holds the twelve sample inspections" 200 '"inspectionId":' 12

call POST /api/inspections "$SUP_JAR" '{"productId":1,"inspectionType":"FINAL","inspectedQty":10,"rejectedQty":1,"result":"FAIL"}'
tc TC-API-41 "only an inspector may record an inspection" 400 'Only a Quality Inspector'

call POST /api/inspections "$INSP_JAR" '{"productId":1,"batchId":1,"inspectionType":"FINAL","inspectedQty":10,"rejectedQty":12,"result":"FAIL"}'
tc TC-API-42 "an inspection cannot reject more units than it inspected" 400 'cannot be greater than the inspected quantity'

call POST /api/inspections "$INSP_JAR" '{"productId":1,"batchId":1,"inspectionType":"IN_PROCESS","inspectedQty":60,"rejectedQty":7,"result":"PASS"}'
tc TC-API-43 "a PASS with rejects must be confirmed explicitly (FR-05.9)" 400 'Please confirm this is correct'

call POST /api/inspections "$INSP_JAR" '{"productId":1,"batchId":1,"inspectionType":"IN_PROCESS","inspectedQty":60,"rejectedQty":7,"result":"PASS","confirmPassWithRejects":true}'
tc TC-API-44 "the same inspection is accepted once it is confirmed" 201 '"result":"PASS"'
NEW_INSPECTION_ID=$(value_of inspectionId)

call POST /api/inspections "$INSP_JAR" '{"productId":1,"batchId":1,"inspectionType":"IN_PROCESS","inspectedQty":60,"rejectedQty":7,"result":"FAIL","remarks":"Phase 12 test case: surface roughness above the limit"}'
tc TC-API-45 "the failed inspection the defect cases below are raised from" 201 '"result":"FAIL"'
FAILED_INSPECTION_ID=$(value_of inspectionId)

call PUT "/api/inspections/$FAILED_INSPECTION_ID/remarks" "$INSP_JAR" '{"remarks":"updated by the inspector who recorded it"}'
tc TC-API-46 "the inspector who recorded an inspection may amend its remarks (FR-05.12)" 200

call PUT "/api/inspections/$FAILED_INSPECTION_ID/remarks" "$INSP2_JAR" '{"remarks":"somebody else trying to edit it"}'
tc TC-API-47 "another inspector may not amend somebody else's record" 400 'inspections you performed'

call GET /api/inspections/9999 "$ADMIN_JAR"
tc TC-API-48 "an unknown inspection id answers 404" 404

# ============================================================ M6 — defects ==
section "M6  Defect management                     (FR-06)"

call GET /api/defects "$ADMIN_JAR"
tc_count TC-API-49 "the defect register holds the ten sample defects" 200 '"defectId":' 10

call GET "/api/defects?status=OPEN" "$ADMIN_JAR"
tc_count TC-API-50 "filtering by status works" 200 '"defectId":' 2

call GET "/api/defects?status=BOGUS" "$ADMIN_JAR"
tc TC-API-51 "an unknown status filter is refused, not ignored" 400 'Unknown status filter'

call POST /api/defects "$SUP_JAR" '{"productId":1,"defectCategory":"SURFACE_FINISH","description":"a supervisor trying to register a defect","severity":"LOW","unitsAffected":1}'
tc TC-API-52 "only an inspector may register a defect" 400 'Only a Quality Inspector'

call POST /api/defects "$INSP_JAR" '{"productId":1,"defectCategory":"SURFACE_FINISH","description":"short","severity":"HIGH","unitsAffected":3}'
tc TC-API-53 "validation: a description shorter than ten characters is refused" 400 'Description must be 10 to 500 characters'

call POST /api/defects "$INSP_JAR" "{\"productId\":1,\"batchId\":1,\"inspectionId\":$FAILED_INSPECTION_ID,\"defectCategory\":\"SURFACE_FINISH\",\"description\":\"Phase 12 test case: roughness above the drawing limit on three housings\",\"severity\":\"HIGH\",\"unitsAffected\":3}"
tc TC-API-54 "an inspector registers a defect against the failed inspection" 201 '"defectRef":"DEF-2026-0011"'
DEFECT_ID=$(value_of defectId)
DEFECT_REF=$(grep -o '"defectRef":"[^"]*"' <<<"$BODY" | head -1 | cut -d'"' -f4)

call GET "/api/defects/lookup/$DEFECT_REF" "$ADMIN_JAR"
tc TC-API-55 "a defect can be looked up by its reference" 200 "\"defectRef\":\"$DEFECT_REF\""

call GET "/api/defects/$DEFECT_ID" "$ADMIN_JAR"
tc TC-API-56 "the detail view returns the defect, its history and its actions" 200 '"history"'

call GET "/api/defects/$DEFECT_ID/history" "$ADMIN_JAR"
tc_count TC-API-57 "a new defect has exactly one history entry, the registration" 200 '"toStatus":' 1

call GET /api/defects/9999 "$ADMIN_JAR"
tc TC-API-58 "an unknown defect id answers 404" 404 'not found'

call GET /api/defects/abc "$ADMIN_JAR"
tc TC-API-59 "a non-numeric id is a type mismatch, answered 400" 400 'not valid for'

# ========================================================= M7 — lifecycle ==
section "M7  Defect tracking — the lifecycle        (FR-07)"

call PUT "/api/defects/$DEFECT_ID/status" "$INSP_JAR" '{"status":"UNDER_INVESTIGATION"}'
tc TC-API-60 "the inspector cannot start the investigation — that is the supervisor's step" 400 'cannot move a defect from OPEN to UNDER_INVESTIGATION'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"UNDER_INVESTIGATION"}'
tc TC-API-61 "the supervisor moves it to UNDER_INVESTIGATION" 200 '"currentStatus":"UNDER_INVESTIGATION"'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"UNDER_INVESTIGATION"}'
tc TC-API-62 "moving a defect to the status it already has is refused" 400 'already UNDER_INVESTIGATION'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"CLOSED"}'
tc TC-API-63 "a defect cannot skip steps to CLOSED" 400 'cannot move from UNDER_INVESTIGATION to CLOSED'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"VERIFIED"}'
tc TC-API-64 "the lifecycle table is consulted before the role table (Phase 12 §6.4)" 400 'cannot move from UNDER_INVESTIGATION to VERIFIED'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"OPEN"}'
tc TC-API-65 "moving a defect backwards without a reason is refused (FR-07.6)" 400 'A reason is required when moving a defect back'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"OPEN","remark":"Phase 12 test case: reopening to prove the backward move works with a reason"}'
tc TC-API-66 "the same backward move is accepted once a reason is given" 200 '"currentStatus":"OPEN"'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"UNDER_INVESTIGATION"}'
tc TC-API-67 "and the defect can move forward again" 200 '"currentStatus":"UNDER_INVESTIGATION"'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"CORRECTIVE_ACTION"}'
tc TC-API-68 "it may not move to CORRECTIVE_ACTION before an action exists" 400 'At least one corrective action must be assigned'

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"NONSENSE"}'
tc TC-API-69 "an unknown status value is refused with the valid ones listed" 400 'Valid values are: OPEN, UNDER_INVESTIGATION'

# ============================================== M8 — corrective actions ==
section "M8  Corrective actions                    (FR-08)"

call GET /api/corrective-actions "$ADMIN_JAR"
tc_count TC-API-70 "the action list holds the eight sample actions" 200 '"actionId":' 8

call GET /api/corrective-actions/overdue "$ADMIN_JAR"
tc_count TC-API-71 "the overdue list holds the two overdue actions" 200 '"actionId":' 2

call GET "/api/corrective-actions?overdueOnly=true" "$ADMIN_JAR"
tc_count TC-API-72 "the same two come back through the filter" 200 '"actionId":' 2

call POST "/api/defects/$DEFECT_ID/corrective-actions" "$INSP_JAR" '{"responsiblePersonId":4,"actionDescription":"an inspector trying to assign work","targetDate":"2026-11-30"}'
tc TC-API-73 "only a supervisor may assign a corrective action" 400 'Only a Production Supervisor'

call POST "/api/defects/$DEFECT_ID/corrective-actions" "$SUP_JAR" '{"responsiblePersonId":6,"actionDescription":"assigned to a deactivated person","targetDate":"2026-11-30"}'
tc TC-API-74 "work cannot be given to a deactivated person (FR-08.3)" 400 'deactivated'

call POST "/api/defects/$DEFECT_ID/corrective-actions" "$SUP_JAR" '{"responsiblePersonId":4,"actionDescription":"re-set the finishing tool and re-check the first fifty parts","targetDate":"2020-01-01"}'
tc TC-API-75 "a target date in the past is refused (FR-08.4)" 400 'today or a future date'

call POST "/api/defects/$DEFECT_ID/corrective-actions" "$SUP_JAR" '{"responsiblePersonId":4,"actionDescription":"re-set the finishing tool and re-check the first fifty parts","targetDate":"2026-11-30"}'
tc TC-API-76 "the supervisor assigns the corrective action" 201 '"progressStatus":"PENDING"'
ACTION_ID=$(value_of actionId)

call PUT "/api/corrective-actions/$ACTION_ID/verify" "$INSP_JAR" '{"verificationStatus":"EFFECTIVE","verificationRemark":"trying to verify work that is not finished"}'
tc TC-API-77 "an action that is not COMPLETED cannot be verified (FR-08.5)" 400 'Only a COMPLETED corrective action can be verified'

call PUT "/api/corrective-actions/$ACTION_ID/progress" "$INSP_JAR" '{"progressStatus":"IN_PROGRESS","progressRemark":"the inspector trying to do the supervisor\u0027s job"}'
tc TC-API-78 "only a supervisor may update progress" 400 'Only a Production Supervisor'

call PUT "/api/corrective-actions/$ACTION_ID/progress" "$SUP_JAR" '{"progressStatus":"IN_PROGRESS","progressRemark":"tool re-set on the line"}'
tc TC-API-79 "the supervisor starts the work" 200 '"progressStatus":"IN_PROGRESS"'

call PUT "/api/corrective-actions/$ACTION_ID/progress" "$SUP_JAR" '{"progressStatus":"COMPLETED","progressRemark":"fifty parts measured, all within the drawing limit"}'
tc TC-API-80 "and completes it" 200 '"progressStatus":"COMPLETED"'

call PUT "/api/corrective-actions/$ACTION_ID/verify" "$SUP_JAR" '{"verificationStatus":"EFFECTIVE","verificationRemark":"the supervisor trying to verify their own work"}'
tc TC-API-81 "the supervisor who did the work may not verify it — that is the inspector's" 400 'Only a Quality Inspector'

call PUT "/api/corrective-actions/$ACTION_ID/verify" "$INSP_JAR" '{"verificationStatus":"EFFECTIVE","verificationRemark":"re-inspection of the next fifty parts passed"}'
tc TC-API-82 "the inspector verifies the completed action" 200 '"verificationStatus":"EFFECTIVE"'

call PUT "/api/corrective-actions/$ACTION_ID/progress" "$SUP_JAR" '{"progressStatus":"COMPLETED","progressRemark":"trying to change it after verification"}'
tc TC-API-83 "a verified action can no longer be changed" 400 'already been verified'

call GET "/api/corrective-actions/$ACTION_ID" "$ADMIN_JAR"
tc TC-API-84 "the action can be read back by id" 200 '"verificationStatus":"EFFECTIVE"'

call GET /api/corrective-actions/9999 "$ADMIN_JAR"
tc TC-API-85 "an unknown action id answers 404" 404

# ================================================ M7 — closing the loop ==
section "M7  Defect tracking — closing the loop"

call PUT "/api/defects/$DEFECT_ID/status" "$SUP_JAR" '{"status":"CORRECTIVE_ACTION"}'
tc TC-API-86 "now that an action exists, the defect may move to CORRECTIVE_ACTION" 200 '"currentStatus":"CORRECTIVE_ACTION"'

call PUT "/api/defects/$DEFECT_ID/status" "$INSP_JAR" '{"status":"VERIFIED"}'
tc TC-API-87 "with the action verified effective, the inspector moves it to VERIFIED" 200 '"currentStatus":"VERIFIED"'

call PUT "/api/defects/$DEFECT_ID/status" "$INSP_JAR" '{"status":"CLOSED"}'
tc TC-API-88 "and closes it" 200 '"currentStatus":"CLOSED"'

call PUT "/api/defects/$DEFECT_ID/status" "$INSP_JAR" '{"status":"OPEN","remark":"trying to reopen a closed defect"}'
tc TC-API-89 "a closed defect cannot be reopened by anybody" 400 'cannot be reopened'

call DELETE "/api/defects/$DEFECT_ID" "$INSP_JAR"
tc TC-API-90 "a closed defect cannot be deleted — it is a record of what happened" 400 'cannot be deleted'

call GET "/api/defects/$DEFECT_ID/history" "$ADMIN_JAR"
tc_count TC-API-91 "the history kept every step of the journey" 200 '"toStatus":' 7

# ===================================================== M9 — dashboard ==
section "M9  Dashboard                            (FR-09)"

call GET /api/dashboard/summary "$ADMIN_JAR"
tc TC-API-92 "the dashboard answers with the defect counts" 200 '"totalDefects"'
if grep -qF '"defectsBySeverity"' <<<"$BODY" && grep -qF '"defectsByCategory"' <<<"$BODY" \
   && grep -qF '"recentDefects"' <<<"$BODY" && grep -qF '"overdueActions"' <<<"$BODY"; then
  report TC-API-93 "it also carries both chart series, the overdue count and the recent list" 1 "all four keys present"
else
  report TC-API-93 "it also carries both chart series, the overdue count and the recent list" 0 "a key is missing"
fi

# ======================================================= M10 — reports ==
section "M10  Reports                             (FR-10)"

call GET /api/reports "$ADMIN_JAR"
tc TC-API-94 "the report catalogue lists what can be generated" 200 'defect-register'

call GET /api/reports/defects "$ADMIN_JAR"
tc_count TC-API-95 "the defect register report returns every defect" 200 '"cells":' 11

call GET "/api/reports/defects?status=OPEN" "$ADMIN_JAR"
tc_count TC-API-96 "the register report honours a status filter" 200 '"cells":' 2

call GET "/api/reports/defects?status=BOGUS" "$ADMIN_JAR"
tc_open TC-API-97 D-04 "D-04: a nonsense status filter on a report is refused, as it is on the register" 400 'Unknown status filter'

call GET "/api/reports/defects?severity=BOGUS" "$ADMIN_JAR"
tc_open TC-API-113 D-04 "D-04: and the same for a severity filter" 400 'Unknown severity filter'

call GET /api/reports/rejection-rate "$ADMIN_JAR"
tc TC-API-98 "the rejection-rate report answers with rows" 200

call GET /api/reports/production-quality "$ADMIN_JAR"
tc TC-API-99 "the production-quality report answers with rows" 200

call GET /api/reports/overdue-actions "$ADMIN_JAR"
tc_count TC-API-100 "the overdue report returns the two overdue actions" 200 '"cells":' 2

call GET /api/reports/defects ""
tc TC-API-101 "reports need a session too" 401

# =============================================== error contract (FR-12) ==
section "Error contract"

call POST /api/corrective-actions "$ADMIN_JAR" '{}'
tc TC-API-102 "a method the address does not support answers 405, not 500" 405 'Allowed:'

call DELETE /api/defects "$ADMIN_JAR"
tc TC-API-103 "and the same for the collection address" 405 'does not accept DELETE'

call GET /api/no-such-thing "$ADMIN_JAR"
tc TC-API-104 "an address that does not exist answers 404" 404 'No endpoint or page matches'

call POST /api/products "$ADMIN_JAR" '{"productCode": bad json'
tc TC-API-105 "a malformed body answers 400 with a sentence a person can act on" 400 'not valid JSON'

call POST /api/auth/login "" "{\"username\":\"admin' OR 1=1 --\",\"password\":\"x\"}"
tc TC-API-106 "an injection-style username is refused like any other wrong one, not 500" 400 'Incorrect username or password'

call GET "/api/defects?productId=abc" "$ADMIN_JAR"
tc TC-API-107 "a non-numeric query parameter is a type mismatch, answered 400" 400 'not valid for'

call GET /api/defects/9999/lookup "$ADMIN_JAR"
tc TC-API-108 "a path that does not exist under a real controller answers 404" 404

# ======================================= remarks, the one path without @Valid ==
section "Remarks — the one endpoint whose body is not validated (D-03)"

LONG_REMARK=$(printf 'x%.0s' $(seq 1 600))

call PUT "/api/inspections/$FAILED_INSPECTION_ID/remarks" "$INSP_JAR" '{"remarks":"a short remark, the way the screen sends it"}'
tc TC-API-109 "the screen's own partial body is accepted (it sends only {remarks})" 200

call PUT "/api/inspections/$FAILED_INSPECTION_ID/remarks" "$INSP_JAR" "{\"remarks\":\"$LONG_REMARK\"}"
tc TC-API-110 "a 600-character remark is at least refused, not stored (no 500)" 400

call PUT "/api/inspections/$FAILED_INSPECTION_ID/remarks" "$INSP_JAR" "{\"remarks\":\"$LONG_REMARK\"}"
tc_open TC-API-111 D-03 "D-03: it should say the remark is too long, not talk about record links" 400 'Remarks must be at most 500 characters'

call POST /api/inspections "$INSP_JAR" "{\"productId\":1,\"inspectionType\":\"FINAL\",\"inspectedQty\":10,\"rejectedQty\":0,\"result\":\"PASS\",\"remarks\":\"$LONG_REMARK\"}"
tc TC-API-112 "the control case: the same value on a validated endpoint names the field" 400 'Remarks must be at most 500 characters'

# ============================================================== summary ==
printf '\n==================================================\n'
printf '  Phase 12 API test cases:  PASSED %d   FAILED %d   OPEN %d\n' "$PASSED" "$FAILED" "$OPEN"
if [ "$FAILED" -gt 0 ]; then
  printf '  failures (expectations that are simply wrong — a bug in the test, fix these):\n'
  for f in "${FAILURES[@]}"; do printf '    %s\n' "$f"; done
fi
if [ "$OPEN" -gt 0 ]; then
  printf '  OPEN FINDINGS (the application does not meet these yet — Phase 12 §7):\n'
  for f in "${FINDINGS[@]}"; do printf '    %s\n' "$f"; done
fi
printf '==================================================\n'
printf '\nThis run wrote to the database (a user, a batch, two inspections, a defect,\n'
printf 'an action and a full lifecycle). Reset it before running anything that\n'
printf 'asserts exact counts, or before running this script again.\n\n'

exit $([ "$FAILED" -eq 0 ] && [ "$OPEN" -eq 0 ] && echo 0 || echo 1)
