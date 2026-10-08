#!/usr/bin/env bash
# =============================================================================
#  package-for-github.sh
#
#  Turns this working folder into a clean Git repository for publishing, and
#  produces a single .bundle file that can be cloned on any other machine.
#
#  It is deliberately boring, and it does three things a plain "git init" does
#  not:
#
#    1. It REFUSES TO COMMIT A SECRET. Before anything is staged, it scans the
#       files that would be published for the database password in the local
#       application.properties and for common key/token shapes. If it finds one,
#       it stops and tells you the file. This runs again on the staged content,
#       so a secret cannot slip in through a path the first scan did not cover.
#
#    2. It makes the history in LOGICAL COMMITS rather than one dump, in the
#       order the project was actually built. A single "initial commit" of 190
#       files tells a reader nothing; fifteen comments tell them the story.
#
#    3. It bundles the repository. The bundle is one file that carries the whole
#       history, so the work can be moved to the machine you will push from
#       without a USB stick full of half-copied folders.
#
#  Usage:
#      bash tools/package-for-github.sh              # create the repository
#      bash tools/package-for-github.sh --recreate   # delete .git and do it again
#
#  Run it from the project root (the folder containing backend/ and frontend/).
# =============================================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
PROJECT_NAME="manufacturing-qms"
BUNDLE="../${PROJECT_NAME}.bundle"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()  { printf '  \033[32m✓\033[0m %s\n' "$*"; }
bad() { printf '  \033[31m✗\033[0m %s\n' "$*"; }

# -----------------------------------------------------------------------------
# 0. Recreate, if asked
# -----------------------------------------------------------------------------
if [ "${1:-}" = "--recreate" ]; then
  say "Removing the existing repository"
  rm -rf .git "$BUNDLE"
  ok "removed .git and any previous bundle"
fi

if [ -d .git ]; then
  bad "This folder is already a Git repository."
  echo "     To start again from scratch:  bash tools/package-for-github.sh --recreate"
  echo "     To just see where it stands:  git log --oneline"
  exit 1
fi

command -v git >/dev/null || { bad "git is not installed"; exit 1; }

# -----------------------------------------------------------------------------
# 1. Initialise first, so the ignore rules can be checked BEFORE anything is
#    staged. If the scan fails, the repository is removed again and nothing
#    has been committed.
# -----------------------------------------------------------------------------
say "Initialising the repository"
git init -q -b main
git config core.autocrlf false
git config core.quotepath false

if ! git config user.email >/dev/null 2>&1; then
  git config user.name  "«Your Name»"
  git config user.email "«your.email@example.com»"
  SKIPPED_IDENTITY=1
else
  SKIPPED_IDENTITY=0
fi

say "Scanning for secrets before anything is committed"
FAILED=0

# 1a. the real database password must not appear in any file that will be tracked
if [ -f backend/src/main/resources/application.properties ]; then
  DB_PASSWORD="$(grep -E '^spring.datasource.password' backend/src/main/resources/application.properties \
                 | head -1 | cut -d= -f2- | tr -d ' ')"
  if [ -n "${DB_PASSWORD:-}" ]; then
    HITS="$(grep -rIl --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
            -F "$DB_PASSWORD" . 2>/dev/null | grep -v '^\./backend/src/main/resources/application.properties$' || true)"
    if [ -n "$HITS" ]; then
      bad "the database password appears in files that would be published:"
      echo "$HITS" | sed 's/^/       /'
      FAILED=1
    else
      ok "the database password exists only in the untracked application.properties"
    fi
  fi
else
  ok "no application.properties here (nothing to compare against)"
fi

# 1b. the configuration file itself must be ignored, not merely absent
if git check-ignore -q backend/src/main/resources/application.properties 2>/dev/null; then
  ok "application.properties is ignored by .gitignore"
else
  bad "application.properties is NOT ignored — check .gitignore before continuing"
  FAILED=1
fi

# 1c. common key and token shapes, anywhere in the tree
TOKENS="$(grep -rInE '(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|BEGIN [A-Z ]*PRIVATE KEY|sk-[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})' \
          --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target . 2>/dev/null || true)"
if [ -n "$TOKENS" ]; then
  bad "token-shaped strings found:"
  echo "$TOKENS" | head -5 | sed 's/^/       /'
  FAILED=1
else
  ok "no access keys, tokens or private keys anywhere in the tree"
fi

# 1d. the folders that must never be published, checked one by one
for path in backend/logs backend/target frontend/tests/node_modules; do
  if [ -e "$path" ]; then
    if git check-ignore -q "$path"; then
      ok "$path exists but is excluded by .gitignore"
    else
      bad "$path would be published — add it to .gitignore"; FAILED=1
    fi
  fi
done

if [ "$FAILED" -ne 0 ]; then
  rm -rf .git
  say "STOPPING — fix the findings above, then run this script again"
  echo "  (the empty repository has been removed; nothing was committed)"
  exit 1
fi
ok "scan clean"

# -----------------------------------------------------------------------------
# 2. The identity the commits will carry
# -----------------------------------------------------------------------------
say "The commits will be authored as"
if [ "$SKIPPED_IDENTITY" = "1" ]; then
  bad "$(git config user.name) <$(git config user.email)>  — placeholders, because no Git identity is set on this machine"
  echo "     Before pushing, set yours and re-create the history cleanly:"
  echo "       git config --global user.name  \"Your Name\""
  echo "       git config --global user.email \"you@example.com\""
  echo "       bash tools/package-for-github.sh --recreate"
else
  ok "$(git config user.name) <$(git config user.email)>"
fi

# -----------------------------------------------------------------------------
# 3. The history, as logical commits in the order the project was built
# -----------------------------------------------------------------------------
say "Building the commit history"

git add .gitignore README.md docs/setup/ tools/
git commit -q -m "chore: repository setup — .gitignore, README, environment checker, packager

The .gitignore keeps three things out of the repository from the very first
commit: the configuration file that holds the database password, everything
generated (target/, logs/, node_modules/), and IDE and OS clutter. The
environment checker lists what a fresh machine needs.

tools/package-for-github.sh is the script that produced this history. It
refuses to commit a secret: it scans for the database password and for
key-shaped strings before staging, and again over what is staged. Run it
with --recreate to rebuild the repository from scratch."

git add docs/Phase1_Project_Foundation.md docs/Phase2_Project_Proposal.md
git commit -q -m "docs: phase 1 foundation and phase 2 proposal

Scope, objectives and the plan of record: ten modules, three roles, a
twelve-week single-semester timeline, and seven measurable objectives that
Chapter 9 reports against."

git add docs/Phase3_Literature_Survey.md
git commit -q -m "docs: phase 3 literature survey

Six areas surveyed with verified references only: quality management in SMEs,
paper-based records, commercial QMS and CAPA software, AI-based visual
inspection, and defect tracking in the software domain. Establishes the gap
this project fills."

git add docs/Phase4_Requirement_Analysis.md docs/Phase5_System_Design.md
git commit -q -m "docs: phase 4 requirements and phase 5 system design

Fourteen functional requirements, seven non-functional requirements each with a
measurable criterion, the authoritative role permission matrix, and the design:
architecture, data model, the five-status defect lifecycle, the class structure
and the API contract."

git add docs/diagrams/
git commit -q -m "docs: the eight design diagrams, as SVG and as high-resolution PNG

Architecture, use case, entity-relationship, class, activity (the defect
lifecycle), sequence (the defect workflow) and data flow levels 0 and 1. They
are generated from Python in the same folder, so a design change can be redrawn
rather than hand-edited."

git add docs/Phase6_Technology_Setup.md docs/Phase7_Database_Design.md
git commit -q -m "docs: phase 6 environment setup and phase 7 database design

Why Java 21 and Spring Boot 3.5.16, how each version was chosen, and the
database design: seven tables, the normalisation decisions, and every
constraint with the rule it enforces."

git add database/
git commit -q -m "feat(db): database scripts — create, schema, sample data, verification

00_create_database.sql creates the database and a least-privilege user, and
ships with a token rather than a password because this file is published.
01_schema.sql rebuilds all seven tables with 23 foreign keys and 12 CHECK
constraints. 02_sample_data.sql seeds a self-consistent demo set with dates
relative to today. 03_verification_queries.sql proves the data is sound."

git add backend/pom.xml backend/src/main/resources/application.properties.sample
git add backend/src/main/java/com/project/qms/QmsApplication.java
git add backend/src/main/java/com/project/qms/entity/ \
        backend/src/main/java/com/project/qms/repository/ \
        backend/src/main/java/com/project/qms/dto/
git commit -q -m "feat(backend): application, entities, repositories and DTOs

Six dependencies, no more. The entities mirror the hand-written schema exactly
and ddl-auto=validate makes the application refuse to start if the two ever
disagree. The DTOs are records, so each one is its contract."

git add backend/src/main/java/com/project/qms/service/ \
        backend/src/main/java/com/project/qms/controller/ \
        backend/src/main/java/com/project/qms/exception/
git commit -q -m "feat(backend): services, controllers and the error contract

Business rules live in the services and HTTP lives in the controllers. One
exception handler answers every refusal with a status, a sentence a person can
act on, and the fields at fault — including 405 with the methods that ARE
allowed, which the testing phase found missing."

git add frontend/js frontend/css frontend/vendor frontend/*.html
git commit -q -m "feat(frontend): the ten screens, shared JavaScript and vendored libraries

Plain HTML, CSS and JavaScript against the API — no build step, so the screens
can be read and changed by anybody. Bootstrap 5.3.8 and Chart.js 4.5.1 are
vendored locally so the demo works with no internet connection."

git add backend/src/test backend/tests
git commit -q -m "test: unit tests and 113 API test cases

Unit tests cover the two pure rules worth testing exhaustively — the defect
lifecycle's role table (all nine cases) and the reports' percentage arithmetic.
The API suite drives 113 cases across all ten modules plus the error contract,
asserts the exact refusal sentence as well as the status, and exits non-zero if
anything is outstanding."

git add frontend/tests
git commit -q -m "test: screen checks, integration journeys and the pre-fill guard

Three browser-level tools: 57 jsdom checks that each screen renders the right
rows and badges, 47 journey checks in real headless Chrome that follow a person
across screens and roles, and a focused guard proving the defect form arrives
pre-filled from a failed inspection."

git add docs/Phase8_Backend_Development.md docs/Phase9_Frontend_Development.md \
        docs/Phase10_Integration.md docs/Phase11_Bug_Fixing.md \
        docs/Phase12_Testing.md
git add docs/evidence ':!docs/evidence/Phase14_repository_packaging.txt'
git commit -q -m "docs: phase 8 to 12 records and the raw evidence behind them

How the backend and the screens were built, what integration testing found
(four defects and their repairs, each with a before-and-after run), and the
testing phase: 231 checks, their expected and actual results, and an explicit
list of what was not tested."

git add docs/report/
git commit -q -m "docs: project report — front matter and Chapters 1 and 2

The report as a document rather than as working notes, with every figure taken
from a phase record. Front matter and Chapters 1 and 2; the rest follow next."

git add docs/Phase13_Documentation.md docs/report/
git commit -q -m "docs: phase 13 — the project report, Chapters 1 to 9

The report as a document rather than as working notes: front matter, nine
chapters and an assembly guide, roughly 28,700 words. Every figure in it was
copied from a phase record or an evidence file, and the four rules the report
keeps are stated in the assembly guide: nothing claimed that was not run, no
invented numbers, no invented citations, and no feature described that does
not exist.

Four items are flagged as outstanding rather than described as done — the
author's details, rewriting Chapter 2 in the author's own words, the usability
check and the backup/restore exercise, and two references still to be
confirmed at the library. Chapter 9 reports objective MO-7 as partly met,
because the Postman collection it named was replaced by the automated API
suite."

git add screenshots/
git commit -q -m "chore: the 22 screenshots from the final verification run

Taken by the journey suite in real headless Chrome during the last green run,
so the screenshots and the test results describe the same build."

git add docs/Phase14_GitHub.md docs/evidence/Phase14_repository_packaging.txt
git commit -q -m "docs: phase 14 — the repository record and its packaging evidence

The audit: what was scanned, the database password that was about to be
published in two files and how it was fixed, the .gitignore group by group,
the fifteen commits and why the history is a logical reconstruction rather
than a day-by-day record, and the three commands that publish it."

git add docs/Phase17_Viva_Preparation.md docs/viva/
git commit -q -m "docs: phase 17 — the viva question bank and the one-page sheet

Around ninety questions across Java, Spring Boot and Spring, MySQL and database
design, the project itself and testing — every answer traceable to code in this
repository or to a phase document, rather than a textbook answer where a
specific one exists.

Section 8 answers the questions this project invites by existing: why an
end-of-life Spring Boot, what 'Smart' means if not AI, why seven tables, why
sessions rather than JWT, why any signed-in user may read the user list, and
what is genuinely untested. Section 10 covers how to not-know well, which is
the skill a viva actually tests."

git add docs/Phase16_Demo_Script.md docs/demo/
git commit -q -m "docs: phase 16 — the demo script, the cue card and the pre-flight check

Ten minutes of live demonstration: a failed inspection becomes a defect, a
supervisor investigates and assigns the work, the work is completed, an
inspector verifies it and closes the defect — with two refusals in front of
the audience, because the refusals are the argument. A five-minute cut is
rehearsed in advance, every number spoken is a number on the screen, and
there is an answer for every failure the system can plausibly show.

preflight.sh checks the tools, MySQL, the sample data, the application and
the demo login ten minutes before presenting; it only changes anything when
given --reset or --start-app."

git add docs/Phase15_Final_PPT.md docs/ppt/
git commit -q -m "feat(ppt): the twenty-slide final presentation, and the script that builds it

The deck for the final-year presentation: problem, gap, objectives, scope,
architecture, database, roles, the defect lifecycle, four screenshot pairs,
the four test layers, the four defects found and repaired, the measured
response-time gates, what was not tested, and the future scope. Speaker notes
on every slide (about eleven minutes of talking).

It is generated by docs/ppt/build_deck.py rather than drawn by hand, so every
figure comes from a phase document and can be corrected in one place. The PDF
export is kept as a fallback for presenting without PowerPoint."

# -----------------------------------------------------------------------------
# 4. Verify the repository itself
# -----------------------------------------------------------------------------
say "Verifying the repository"

# nothing sensitive tracked
if git ls-files | grep -q 'application\.properties$'; then
  bad "application.properties is tracked — this must not happen"; exit 1
fi
ok "application.properties is not tracked"

if git ls-files | grep -qE '\.(jar|war|class)$|^logs/|node_modules/|^target/'; then
  bad "build output, logs or dependencies are tracked"; git ls-files | grep -E '\.(jar|war|class)$|^logs/|node_modules/|^target/' | head; exit 1
fi
ok "no build output, logs, dependencies or compiled classes are tracked"

# and re-scan what is actually staged in the history
TRACKED_SECRETS="$(git grep -InE '(ghp_[A-Za-z0-9]{20,}|BEGIN [A-Z ]*PRIVATE KEY)' $(git rev-list --all) 2>/dev/null | head -3 || true)"
if [ -n "$TRACKED_SECRETS" ]; then bad "a secret is inside a commit:"; echo "$TRACKED_SECRETS"; exit 1; fi
ok "no key-shaped strings in any commit"

say "The repository"
printf '  commits      : %s\n' "$(git rev-list --count HEAD)"
printf '  tracked files: %s\n' "$(git ls-files | wc -l)"
printf '  size on disk : %s\n' "$(du -sh .git | cut -f1)"
printf '  history      :\n'
git log --oneline | sed 's/^/    /'

# -----------------------------------------------------------------------------
# 5. Bundle it, so it can be moved to the machine you push from
# -----------------------------------------------------------------------------
say "Writing the bundle"
git bundle create "$BUNDLE" --all HEAD >/dev/null
BUNDLE_MB="$(awk -v b="$(stat -c%s "$BUNDLE")" 'BEGIN { printf "%.1f", b/1000000 }')"
git bundle verify "$BUNDLE" >/dev/null 2>&1 && ok "bundle verified: $BUNDLE (${BUNDLE_MB} MB)"

# and prove it: clone the bundle back and check what arrives
CLONE_DIR="$(mktemp -d)/clone"
if git clone -q "$BUNDLE" "$CLONE_DIR" 2>/dev/null; then
  CLONE_BRANCH="$(git -C "$CLONE_DIR" rev-parse --abbrev-ref HEAD)"
  CLONE_FILES="$(git -C "$CLONE_DIR" ls-files | wc -l)"
  if [ "$CLONE_BRANCH" = "main" ] && [ "$CLONE_FILES" = "$(git ls-files | wc -l)" ]; then
    ok "cloned back from the bundle: branch $CLONE_BRANCH, $CLONE_FILES files"
  else
    bad "the clone came back on branch '$CLONE_BRANCH' with $CLONE_FILES files"
    exit 1
  fi
  rm -rf "$(dirname "$CLONE_DIR")"
fi

cat <<'NEXT'

────────────────────────────────────────────────────────────────────────────
  NEXT — publish it

  1. Move the bundle to the machine you will push from, then:

       git clone manufacturing-qms.bundle manufacturing-qms
       cd manufacturing-qms
       git log --oneline          # the whole history is inside

  2. Create an empty repository on GitHub (no README, no .gitignore — this
     project already has both), then:

       git remote add origin https://github.com/<your-username>/manufacturing-qms.git
       git push -u origin main

  3. Check the push: on GitHub, open the repository and confirm
     backend/src/main/resources/application.properties is NOT there
     (only application.properties.sample), and that no password appears in
     any file. If it does, the repository is public — remove it immediately,
     rotate the password, and start again with --recreate.

  If you ever need to push again after committing more work locally:

       git add -A && git commit -m "your message" && git push
────────────────────────────────────────────────────────────────────────────
NEXT
