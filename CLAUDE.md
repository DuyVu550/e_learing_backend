# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Use the Maven wrapper; this repository is a Java 21 / Spring Boot 4.1.0 single-module app.

Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Dtest=LearningBackendApplicationTests test
.\mvnw.cmd package
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

POSIX shell:

```sh
./mvnw test
./mvnw -Dtest=LearningBackendApplicationTests test
./mvnw package
./mvnw verify
./mvnw spring-boot:run
```

There is no separate lint/checkstyle plugin configured in [pom.xml](pom.xml); use `test` or `verify` for the current automated checks.

## Runtime configuration

- Main config lives in [application.properties](src/main/resources/application.properties).
- MySQL is the development/runtime database; create `e_learning` before running locally.
- Environment variables supported by config: `MYSQL_URL`, `MYSQL_USER`, `JWT_SECRET`. The password currently comes from `spring.datasource.password` in the properties file.
- Flyway is enabled with migrations under [src/main/resources/db/migration/](src/main/resources/db/migration/).
- JPA uses `spring.jpa.hibernate.ddl-auto=validate`, so schema changes need Flyway migrations.
- App runs on port `8080` by default.

## Architecture overview

This is a layered Spring Boot backend under `com.example.learning_backend`:

- `auth`: auth REST API, login/register/logout/refresh/change-password/forgot-reset flows, JWT creation/validation, refresh and password-reset token persistence.
- `user`: `User`, `Role`, user status, and repositories used by auth and ownership relationships.
- `course`: course, section, lesson entities plus course controller/service/repositories.
- `assessment`: assessment, question, option, topic, rule, selection entities plus assessment/question bank controllers/services/repositories.
- `enrollment`: enrollment and lesson progress entities/repositories/service/controller.
- `submission`: assessment attempt and answer persistence; exam-taking flow, auto-save drafts, auto-grading, manual essay grading, and result review.
- `analytics`: read-only leaderboards (per assessment and system-wide) and instructor reports (score distribution, per-question wrong-rate). Owns no tables.
- `forum`: lesson Q&A comments, one level of replies.
- `notification`: in-app notifications. DB-backed only — there is no mail dependency, scheduler, or async executor in this project.
- `common`: health endpoint, `BaseEntity`, API error model, global exception handling.
- `config`: Spring Security, JPA auditing, and role seed initialization.

Cross-cutting authorization rules live in two shared components rather than being copied per service: [CourseAccessPolicy.java](src/main/java/com/example/learning_backend/course/service/CourseAccessPolicy.java) ("may this caller manage this course?" — `canManage` returns a boolean, `ensureCanManage` throws) and [EnrollmentAccessPolicy.java](src/main/java/com/example/learning_backend/enrollment/service/EnrollmentAccessPolicy.java) ("is this user an active member?"). Use them instead of writing a new ownership check.

Request flow is controller -> service -> Spring Data JPA repository -> entity. DTOs define request/response boundaries. Services are the right place for transaction boundaries and business rules; controllers should stay thin.

## Security model

- [SecurityConfig.java](src/main/java/com/example/learning_backend/config/SecurityConfig.java) disables CSRF/basic auth, uses stateless sessions, and installs `JwtAuthenticationFilter` before Spring's username/password filter.
- Public routes: `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/forgot-password`, `/api/auth/reset-password`.
- All other routes require authentication.
- Course and assessment creation, manual answer grading, and the instructor assessment report use `@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")`. Role gating is only the first gate — per-course ownership is then enforced in the service via `CourseAccessPolicy`.
- JWTs include email subject, `userId`, roles, and token type. Refresh/password-reset tokens are stored hashed, not raw.

## Persistence model

All main entities extend [BaseEntity.java](src/main/java/com/example/learning_backend/common/entity/BaseEntity.java), which provides `id`, `createdAt`, and `updatedAt` via JPA lifecycle callbacks.

Core relationships:

- `User` <-> `Role` through `user_roles`.
- `Course` belongs to an instructor `User`.
- `CourseSection` belongs to `Course`; `Lesson` belongs to `CourseSection`.
- `Enrollment` links `User` and `Course`; `LessonProgress` links `User` and `Lesson`.
- `Assessment` belongs to `Course` and optionally `Lesson`.
- `Question` belongs to `Assessment`; `QuestionOption` belongs to `Question`.
- `AssessmentAttempt` links `Assessment` and `User`; `Answer` links attempts, questions, and selected options.
- `LessonComment` belongs to `Lesson` and `User`, with an optional self-reference `parent` (replies are one level deep).
- `Notification` belongs to `User`; `referenceId` + `type` point at the subject without encoding a frontend route.
- `RefreshToken` and `PasswordResetToken` belong to `User`.

Note the test suite runs H2 with `spring.flyway.enabled=false` and `ddl-auto=create-drop`, so migrations are never exercised by `mvnw test` and a migration/entity mismatch only fails at real startup against MySQL. After any schema change, also run `spring-boot:run` once to confirm Flyway applies and `ddl-auto=validate` passes.

## Graphify navigation

`graphify-out/graph.json` exists in this repo. For architecture, component relationship, or flow questions, prefer Graphify before broad file reads when the CLI is available:

```sh
graphify query "<question>"
graphify path "<node1>" "<node2>"
graphify --update
```

In this environment `graphify` may not be on `PATH`; fall back to targeted file reads/searches if the command is unavailable. After structural code changes, update the graph if Graphify is installed.

## Project rules: Ponytail philosophy

Adhere to Ponytail principles for coding, refactoring, architecture, and review work:

1. Does this need to exist at all? Speculative need = skip it.
2. Reuse existing codebase patterns/helpers before adding anything.
3. Prefer standard library and native Spring/JPA/database features over custom logic.
4. Use already-installed dependencies before considering new ones.
5. Write the smallest working change; avoid single-implementation interfaces, factories, or premature abstractions.
6. Prefer deleting redundant code over adding workaround layers.
7. Fix root causes where all callers route through.

Mark intentional simplifications with `// ponytail: <rationale & upgrade path>` when the trade-off is not obvious.
