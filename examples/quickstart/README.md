# Muer Quick Start consumer

This project maps 1:1 to the Muer quick-start tutorial at
https://muer.cloud/getting-started/quick-start/ — a real third-party Spring Boot
application (Document System) that consumes `cloud.muer:muer-spring-boot-starter`.

1. **Install Muer** (Release Candidate, not yet on Maven Central):
   ```bash
   git clone https://github.com/wbh123/Muer-IAM-Framework.git
   cd Muer-IAM-Framework
   mvn clean install -DskipTests
   ```
2. **Prepare MySQL / Redis** — or bring up the optional local infra:
   ```bash
   cd examples/quickstart
   docker compose up -d
   ```
3. **Configure environment** (defaults already match the local compose):
   ```bash
   export MUER_DB_PASSWORD=iam-secret
   export MUER_QUICKSTART_SEED_DEMO=true
   export SPRING_PROFILES_ACTIVE=dev
   ```
4. **Run**:
   ```bash
   mvn -f examples/quickstart/pom.xml spring-boot:run
   ```

Then follow the tutorial: log in as `alice / demo-pass` and verify
`GET /api/documents/1001` returns 200 while `POST /api/documents/1001` returns 403.

> The authorization seeder is **dev + opt-in only** (`MUER_QUICKSTART_SEED_DEMO=true`).
> Never enable it outside local development.
