# Real-Time Smart Manufacturing Quality Management and Defect Tracking System

A Java-based project for managing manufacturing production records,
quality inspections, and product defects in one place. The system is
intended to help users record quality issues, follow defect status, and
maintain organized production-quality information.

## Project Objectives

-   Organize product and production-batch information.
-   Record quality inspections and their results.
-   Register defects and track their progress.
-   Record corrective actions taken to resolve defects.
-   Present quality-related information in a simple, understandable way.

## Key Modules

The project is organized around the following functional areas. The
exact availability of each feature depends on the current
implementation.

-   **Product Management:** Maintain product information.
-   **Production Management:** Record production batches.
-   **Quality Inspection:** Store inspection details and results.
-   **Defect Management:** Register defects identified during
    inspection.
-   **Defect Tracking:** Track defect status through its resolution
    process.
-   **Corrective Actions:** Record actions intended to address defects.
-   **Dashboard and Reports:** View a summary of production and quality
    information, where implemented.

## Technology Stack

| Layer | What it uses |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 (Web, Data JPA, Validation) |
| Persistence | Hibernate 6.6 over Spring Data JPA, 7 repositories |
| Database | MySQL 8 (InnoDB, utf8mb4) — schema written by hand in SQL |
| Passwords | BCrypt via `spring-security-crypto` (no Spring Security filter chain) |
| Frontend | Plain HTML, CSS and JavaScript — 10 screens, no build step |
| UI libraries | Bootstrap 5 and Chart.js, both vendored locally in `frontend/vendor/` |
| Session | Container-managed HTTP session (`JSESSIONID`), 30-minute timeout |
| Build | Maven (`backend/pom.xml`), producing one executable fat JAR |
| Tests | JUnit 5 + AssertJ (14), a curl API suite (113 cases), a jsdom screen suite (57 checks) |

The frontend needs no internet access at runtime: Bootstrap, Chart.js and their
scripts are committed under `frontend/vendor/`.

## Repository Structure

``` text
Real-Time-Smart-Manufacturing-Quality-Management-and-Defect-Tracking-System-Using-Java/
├── backend/
│   ├── pom.xml                     # Java 21, Spring Boot 3.5.16, six dependencies
│   ├── src/main/java/com/project/qms/
│   │   ├── controller/             # 10 REST controllers + the session guard
│   │   ├── service/                # 8 services, where the business rules live
│   │   ├── repository/             # 7 Spring Data JPA repositories
│   │   ├── entity/                 # 7 @Entity classes, 9 enums, 1 audit base class
│   │   ├── dto/                    # 25 request/response records
│   │   └── exception/              # 3 exceptions behind one error contract
│   ├── src/main/resources/
│   │   └── application.properties.sample   # template — copy, do not edit in place
│   ├── src/test/java/              # 14 unit tests
│   └── tests/api-test-cases.sh     # 113 curl API test cases
├── frontend/                       # 10 screens + css/ js/ vendor/  (served by the backend)
│   └── tests/                      # optional jsdom / headless-Chrome checks
├── database/
│   ├── 00_create_database.sql      # database + least-privilege user
│   ├── 01_schema.sql               # 7 tables, 23 foreign keys, 12 CHECK constraints
│   ├── 02_sample_data.sql          # seed data the tests assert against
│   └── 03_verification_queries.sql # read-only checks
├── tools/package-for-github.sh     # packaging helper
└── README.md
```

## How the pieces fit together

One process serves everything. The Spring Boot application on **port 8080**
serves both the REST API (`/api/...`) and the ten screens in `frontend/`.

This is not a detail you can skip. Every screen calls the API with a
**root-relative** address (`/api/products`, `/api/auth/login`). A root-relative
address resolves against the origin of the page that made the call, so the page
and the API must share one origin — and the login session (`JSESSIONID` cookie)
only works on that one origin too.

**Therefore: do not open the `.html` files by double-clicking them, and do not
serve `frontend/` from a separate server such as VS Code Live Server.** Both
give the page a different origin from the API, every request goes to the wrong
place, and every screen renders empty. Always use `http://localhost:8080`.

The Maven build copies `frontend/` into the application automatically (see the
`copy-frontend-into-static` execution in `backend/pom.xml`), so `frontend/`
stays the only copy you edit — you never copy files by hand.

## Prerequisites

| Tool | Version | Check with |
|---|---|---|
| JDK | **21** (required — the `pom.xml` targets Java 21) | `java -version` |
| Maven | 3.6 or later | `mvn -version` |
| MySQL | 8.0 or later (8.4 works) | `mysql --version` |

A browser is all you need for the frontend — there is no Node build step and no
npm install for the application itself. Node is only needed for the optional
test tools in `frontend/tests/`.

## Setup and Run

### 1. Clone the repository

```bash
git clone https://github.com/Karansabale/Real-Time-Smart-Manufacturing-Quality-Management-and-Defect-Tracking-System-Using-Java.git
cd Real-Time-Smart-Manufacturing-Quality-Management-and-Defect-Tracking-System-Using-Java
```

### 2. Create the database and its user

Open `database/00_create_database.sql` and **replace the `<<CHANGE_ME>>`
password token** on the `CREATE USER` line with a password of your own. MySQL
rejects the token as written — that is deliberate, so the file can stay in Git
without ever holding a real credential.

Then run the three scripts **in this order**, as a MySQL administrator:

```bash
mysql -u root -p < database/00_create_database.sql   # database + qms_user
mysql -u root -p manufacturing_qms < database/01_schema.sql      # 7 tables, 23 FKs, 12 CHECKs
mysql -u root -p manufacturing_qms < database/02_sample_data.sql # 6 users, 6 products, 10 defects...
```

`03_verification_queries.sql` is read-only — run it any time to confirm the
counts. Note that `01` and `02` drop and recreate tables, so run them as `root`,
not as `qms_user` (which is granted only `SELECT, INSERT, UPDATE, DELETE`).

On Windows, from the MySQL Command Line Client:

```sql
source C:/path/to/database/00_create_database.sql
```

### 3. Create the backend configuration file

`application.properties` is **not in the repository** — it would contain your
database password, so `.gitignore` excludes it. Create it from the template:

```bash
# Linux / macOS
cp backend/src/main/resources/application.properties.sample \
   backend/src/main/resources/application.properties
```

```bat
:: Windows (Command Prompt)
copy backend\src\main\resources\application.properties.sample ^
     backend\src\main\resources\application.properties
```

Open the new `application.properties` and set **one** value — the password you
chose in step 2:

```properties
spring.datasource.password=YourPasswordHere
```

Leave `spring.datasource.username=qms_user` and the JDBC URL as they are unless
your MySQL is not on `localhost:3306`.

> `spring.jpa.hibernate.ddl-auto=validate` means Hibernate checks your tables
> against the entities and **fails at startup** if they disagree. So step 2 must
> be done properly — the application will not create the schema for you.

### 4. Run the application

```bash
cd backend
mvn spring-boot:run
```

Or build the executable JAR once and run that instead:

```bash
cd backend
mvn clean package        # also runs the 14 unit tests
java -jar target/manufacturing-qms-1.0.0.jar
```

Startup takes a few seconds. You are looking for these two lines:

```
o.s.b.a.w.WelcomePageHandlerMapping : Adding welcome page: class path resource [static/index.html]
com.project.qms.QmsApplication      : Started QmsApplication in 4.5 seconds
```

The first line is the proof that the screens were packaged. If it is missing,
the build did not pick up `frontend/` — run `mvn clean package` again.

### 5. Open the application

Browse to **<http://localhost:8080>** — it redirects you to the login screen.

Sign in with one of the seeded accounts:

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | everything, including user management |
| `supervisor1` | `Supervise@123` | SUPERVISOR | batches, corrective actions, defect movement |
| `inspector1` | `Inspect@123` | INSPECTOR | inspections, raising defects |

(`inspector2` shares `inspector1`'s password; `supervisor2` shares
`supervisor1`'s. `inspector3` is seeded **inactive**, so it cannot log in — that
is the account `users.html` uses to show the deactivate feature.)

These are demo credentials in a public repository. Change them before this is
ever used for anything real.

### 6. Check that it worked

| Check | Expected |
|---|---|
| `http://localhost:8080/login.html` | the login screen, not a 404 |
| Log in as `admin` | you land on the dashboard with tiles and two charts |
| Dashboard tiles | 10 total defects, 2 open, 6 products, 10 batches |
| Defects screen | 10 rows; opening one shows its status timeline |
| Reports screen | a 10-row table with an 8-column header |

If the login screen loads but every list is empty and the browser console shows
a network error, the page is almost certainly not being served from port 8080 —
see *"How the pieces fit together"* above.

## Running the tests

All three suites are optional and none of them is needed to run the application.
They need the application running on `http://localhost:8080` with the sample
data freshly loaded, because several cases assert exact counts.

```bash
# 1. Unit tests — no database, no server. 14 cases.
cd backend && mvn test

# 2. API test cases — needs the running application. 113 cases.
mysql -u root -p manufacturing_qms < database/01_schema.sql
mysql -u root -p manufacturing_qms < database/02_sample_data.sql
bash backend/tests/api-test-cases.sh

# 3. Screen checks — needs the running application and Node 18+. 57 checks.
cd frontend/tests && npm install jsdom canvas && bash verify-pages.sh
```

Suites 2 and 3 **write to the database**. Re-run the two `mysql` commands above
before each run, or the counts they assert will not match. See
`backend/tests/README.md` and `frontend/tests/README.md`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unknown column 'RESERVED' in 'WHERE'` warning at startup | You are on MariaDB, not MySQL. Harmless — Hibernate probes a MySQL system table MariaDB does not have. | Ignore it, or install MySQL 8 |
| `Public Key Retrieval is not allowed` | MySQL 8.4 uses `caching_sha2_password` over a non-SSL connection | Already fixed by `allowPublicKeyRetrieval=true` in the JDBC URL — make sure you did not remove it |
| `Schema-validation: missing table ...` at startup | Step 2 was skipped or run against the wrong database | Run `01_schema.sql` against `manufacturing_qms` |
| `Access denied for user 'qms_user'@'localhost'` | The password in `application.properties` does not match the one in `00_create_database.sql` | Make the two agree |
| Every screen is blank; console shows failed `/api/...` calls | The page is not being served from port 8080 | Use `http://localhost:8080`, not the file or a Live Server |
| Login screen loads, then bounces back to login | Wrong password, or you are using `inspector3` (seeded inactive) | Use a table account from step 5 |
| `Port 8080 was already in use` | Another process holds the port | `server.port=8081` in `application.properties`, then use that port in the browser |
| Charts do not draw on the dashboard | A browser extension blocking scripts | Try a private window |

## Typical Workflow

1.  Open the application.
2.  Add or select a product.
3.  Record a production batch.
4.  Enter a quality inspection.
5.  Register a defect if an issue is found.
6.  Record corrective action and update the defect status.
7.  Review available quality summaries or reports.

## Learning Outcomes

This project provides practical experience with Java application
development, database integration, CRUD operations, frontend-backend
communication, and documenting a software project.

## Future Enhancements

Possible improvements, if they are not already implemented, include:

-   More detailed search and filtering for defects
-   Exportable quality reports
-   Improved input validation and error messages
-   Additional dashboard summaries
-   Role-based access for different users

## Author

**Karan Sabale**

-   GitHub: [Karansabale](https://github.com/Karansabale)

## Disclaimer

This is an academic/final-year project. Features and setup instructions
should be considered alongside the current source code in the
repository.
