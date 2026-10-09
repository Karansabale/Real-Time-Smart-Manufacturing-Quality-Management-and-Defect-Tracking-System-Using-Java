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

-   **Backend:** Java, Spring Boot
-   **Database:** MySQL
-   **Frontend:** HTML, CSS, JavaScript
-   **Build and dependency management:** Maven, if configured in the
    backend project

> Keep this list aligned with the actual source code. Remove any
> technology that is not used in the repository.

## Repository Structure

``` text
Real-Time-Smart-Manufacturing-Quality-Management-and-Defect-Tracking-System-Using-Java/
├── backend/       # Java backend source code
├── frontend/      # User interface files
├── database/      # Database scripts or schema files
├── tools/         # Supporting development tools, if required
└── README.md      # Project documentation
```

## Prerequisites

Before running the project, install the tools required by the code in
this repository:

-   Java Development Kit (JDK) compatible with the project
-   MySQL Server
-   Maven, if the backend uses Maven
-   A web browser
-   An IDE such as IntelliJ IDEA, Eclipse, or VS Code

Check the backend configuration files for the exact Java version and
dependencies.

## Setup and Run

### 1. Clone the repository

``` bash
git clone https://github.com/Karansabale/Real-Time-Smart-Manufacturing-Quality-Management-and-Defect-Tracking-System-Using-Java.git
cd Real-Time-Smart-Manufacturing-Quality-Management-and-Defect-Tracking-System-Using-Java
```

### 2. Configure the database

1.  Start MySQL Server.
2.  Open the SQL script or schema file provided in the `database/`
    directory, if available.
3.  Create the database and tables according to the supplied SQL script.
4.  Update the backend database configuration with your local database
    name, username, and password.

**Important:** Do not commit real database passwords or other secrets to
GitHub. Use local configuration or environment variables where
supported.

### 3. Start the backend

Open the `backend/` directory and inspect its files.

-   If it contains a `pom.xml`, it is likely a Maven project.
-   Use the Java version and run instructions specified by the project
    configuration.
-   If it is a Spring Boot Maven project, the usual command is:

``` bash
mvn spring-boot:run
```

Run this command from the directory containing the backend `pom.xml`. If
the project uses a different setup, follow its actual configuration
instead.

### 4. Start the frontend

Open the `frontend/` directory and check its files:

-   For a plain HTML/CSS/JavaScript frontend, open the main HTML file in
    a browser or use the local server recommended by the project.
-   If the frontend has its own package configuration or build process,
    follow the instructions in that directory.

### 5. Verify the application

After starting the required parts, check that the frontend loads and
that the backend can connect to MySQL. Test the features that are
implemented, such as creating records, viewing inspections, and tracking
defects.

> The commands and configuration above are general guidance. Confirm
> ports, database names, entry-point files, and exact run commands from
> the project source before publishing this README as final
> documentation.

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
