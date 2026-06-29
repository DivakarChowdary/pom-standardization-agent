# POM Standardization Agent

A Spring Boot command-line tool that automates POM standardization across 1,000+ repositories in parallel — updating dependency versions, remediating TRO vulnerabilities, running Maven builds, creating pull requests, assigning reviewers, and sending email summaries.

## What It Does
Load Config → Clone Repos (parallel) → Update POM → Maven Build → Commit → Push → Create PR → Assign Reviewers → Email Summary

One JSON file change fixes 1,000+ repos. Reduced manual effort from ~1000 hours to ~30 minutes.

## Architecture

┌─────────────────────────────────────────────────────┐
│                   Application (CLI)                  │
│           CommandLineRunner → profile arg            │
├──────────────┬──────────────────────────────────────┤
│ RepoFileLoader│ PomMappingLoader                    │
│ (JSON config) │ (dependency/plugin map)              │
├──────────────┴──────────────────────────────────────┤
│              PomUpdaterService                       │
│        (Thread Pool — bounded parallelism)           │
├──────┬──────────┬──────────┬──────────┬─────────────┤
│ Git  │ PomWalker│  Maven   │ GitHub   │   Excel     │
│Helper│ (XML DOM)│  Runner  │ PRClient │   Writer    │
└──────┴──────────┴──────────┴──────────┴─────────────┘
         EmailService (notifications)
         BulkApprovePRs (approve mode)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.3, Java 17 |
| Git | JGit |
| GitHub API | OkHttp |
| POM Manipulation | Java XML DOM |
| Maven Build | Maven Invoker |
| Excel Output | Apache POI |
| Email | JavaMail (SMTP) |
| Build | Maven |
| Test | JUnit 5 |

## Project Structure

src/main/java/com/divakarchowdary/pom/
├── Application.java                  # Entry point (CommandLineRunner)
├── config/
│   ├── JacksonConfig.java            # ObjectMapper bean
│   ├── PomMappingLoader.java         # Loads dependency/plugin mapping JSON
│   └── RepoFileLoader.java           # Loads repo + version config JSON
├── excel/
│   └── ExcelWriter.java              # Writes PR summary to .xlsx
├── exception/                        # Custom exceptions
├── git/
│   ├── GitHelper.java                # JGit operations (clone, branch, commit, push)
│   └── GithubPRClient.java           # GitHub REST API (PR, reviewers, approve, merge)
├── maven/
│   ├── MavenRunner.java              # Spring @Component Maven Invoker
│   └── PomWalker.java                # XML DOM-based POM updater
├── model/
│   ├── PomUpdaterProperties.java     # Spring Boot config properties
│   ├── PomMappingConfig.java         # Mapping file DTO
│   ├── RepoFileConfig.java           # Repos JSON DTO
│   ├── RepoResult.java               # Pipeline result per repo
│   └── PRInfo.java                   # GitHub PR response DTO
└── service/
    ├── PomUpdaterService.java        # Main orchestrator
    ├── RepoProcessor.java            # Per-repo pipeline
    ├── EmailService.java             # SMTP email notifications
    └── BulkApprovePRs.java          # Bulk approve from Excel

## Configuration

### 1. application.yml — global settings (committed)
yaml
pom:
  updater:
    base-branch: main
    thread-pool-size: 7
    maven-goals: [clean, verify]

### 2. src/main/resources/repos/<profile>-repos.json` — per-profile repos and versions
json
{
  "owner": "your-github-org",
  "repos": ["service-auth", "service-api-gateway"],
  "versions": {
    "spring.boot.version": "3.3.0",
    "log4j2.version": "2.23.1"
  }
}

### 3. src/main/resources/pom-mapping/<profile>-pom-mapping.json — dependency → property map
json
{
  "dependencyPropertyMap": {
    "org.apache.logging.log4j:log4j-core": "log4j2.version"
  },
  "pluginPropertyMap": {
    "org.jacoco:jacoco-maven-plugin": "jacoco.plugin.version"
  }
}


## Usage

### Build
bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean package -DskipTests


### Run — Update mode
bash
java -jar target/pom-standardization-agent-1.0.0.jar abc \
  --pom.updater.github-token=ghp_your_token \
  --pom.updater.github-user=your-username \
  --pom.updater.work-dir=/tmp/pom-work \
  --pom.updater.pr-dir=/tmp/pom-prs \
  --pom.updater.feature-branch=feature/pom-standardize \
  --pom.updater.owner=your-org


### Run — Approve mode (reads latest Excel, approves + merges all PRs)
bash
java -jar target/pom-standardization-agent-1.0.0.jar abc approve \
  --pom.updater.approver-token=ghp_approver_token


### Run Tests
bash
mvn test


## Impact

- Processed 1,000+ repositories in a single run
- Reduced manual POM update effort from ~1000 hours to ~30 minutes
- Consistent version standardization across all repos from a single JSON change
- Automated TRO vulnerability remediation with build verification before PR creation

## Author

Divakar Chowdary Kamma
- Website: [divakarchowdary.com](https://divakarchowdary.com)
- LinkedIn: [linkedin.com/in/divakar-chowdary-kamma](https://www.linkedin.com/in/divakar-chowdary-kamma-b6543490/)
- GitHub: [github.com/DivakarChowdary](https://github.com/DivakarChowdary)

## License

MIT License
