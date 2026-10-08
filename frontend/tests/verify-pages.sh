#!/usr/bin/env bash
# Phase 9 verification: every screen, loaded in its own process.
cd "$(dirname "$0")"
total=0; failed=0
run() {   # run <path> <user> <password> <expectations...>
  local line
  line=$(node check-page.mjs "$@" 2>&1)
  echo "$line"
  total=$((total + $(echo "$line" | grep -c '  PASS')))
  failed=$((failed + $(echo "$line" | grep -c '  FAIL')))
}
run index.html        -          -           2>/dev/null   # signpost: redirects, nothing to assert
run login.html        -          -           "#login-form==1" "text:Demo accounts"
run dashboard.html    inspector1 'Inspect@123'   "#recent-rows tr==5" "#tiles .tile>=6" "text:2 corrective actions overdue"
run defects.html      inspector1 'Inspect@123'   "#rows tr==10" "#f-status option==6" "text:+ New Defect"
run 'defect-detail.html?id=3' inspector1 'Inspect@123'  "#timeline li>=4" "text:DEF-2026-0003" "text:Move to CLOSED"
run 'defect-detail.html?id=9' supervisor1 'Supervise@123' "#timeline li>=2" "text:DEF-2026-0009" "text:Move to CORRECTIVE ACTION"
run users.html        admin      'Admin@123'    "#rows tr==6" "#rows [data-deactivate]>=1"
run users.html        inspector1 'Inspect@123'   "text:Administrators only"
run products.html     admin      'Admin@123'    "#rows tr==6" "#rows [data-edit]==6"
run products.html     inspector1 'Inspect@123'   "#rows tr==6"
run batches.html      supervisor1 'Supervise@123' "#rows tr==10" "#rows [data-status]==10"
run inspections.html  inspector1 'Inspect@123'   "#rows tr==12" "#rows a[href*='defects.html?new=1']>=1"
run corrective-actions.html supervisor1 'Supervise@123' "#rows tr==8" "#rows tr.overdue==2"
run corrective-actions.html?overdue=1 supervisor1 'Supervise@123' "#rows tr==2"
run reports.html      admin      'Admin@123'    "#report-rows tr==10" "#report-head th==8"
echo
echo "=================================================="
echo "  Phase 9 page checks:  PASSED $total   FAILED $failed"
exit $((failed > 0))
