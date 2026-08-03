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
- PayOS credentials (`app.payos.client-id`, `app.payos.api-key`, `app.payos.checksum-key`) live in [application-local.properties](src/main/resources/application-local.properties), which is git-ignored and pulled in by `spring.config.import=optional:classpath:application-local.properties`. They default to empty, so the app still starts without them and only checkout fails. Any new `app.payos.*` key read at bean construction must also be given a value in [application-test.properties](src/test/resources/application-test.properties), or every `@SpringBootTest` fails to load its context. The same applies to `app.admin.*`.
- `app.admin.email` / `app.admin.password` / `app.admin.full-name` seed the bootstrap ADMIN account on startup. The seed only creates the account when it is missing, so restarting never resets an existing admin's password. Override the dev default (`admin@learning.local` / `Admin@12345`) before any real deployment.
- Flyway is enabled with migrations under [src/main/resources/db/migration/](src/main/resources/db/migration/).
- JPA uses `spring.jpa.hibernate.ddl-auto=validate`, so schema changes need Flyway migrations.
- App runs on port `8080` by default.

## Architecture overview

This is a layered Spring Boot backend under `com.example.learning_backend`:

- `auth`: auth REST API, login/register/logout/refresh/change-password/change-role/forgot-reset flows, JWT creation/validation, refresh and password-reset token persistence.
- `user`: `User`, `Role`, user status, and repositories used by auth and ownership relationships.
- `course`: course, section, lesson entities plus course controller/service/repositories.
- `assessment`: assessment, question, option, topic, rule, selection entities plus assessment/question bank controllers/services/repositories.
- `enrollment`: enrollment and lesson progress entities/repositories/service/controller.
- `submission`: assessment attempt and answer persistence; exam-taking flow, auto-save drafts, auto-grading, manual essay grading, and result review.
- `analytics`: read-only leaderboards (per assessment and system-wide), instructor reports (score distribution, per-question wrong-rate), and the admin revenue report. Owns no tables.
- `payment`: buying a paid course through PayOS. Creates the order, calls the gateway for a checkout link, and verifies the webhook that promotes the order to `PAID` and grants the enrollment.
- `forum`: lesson Q&A comments, one level of replies.
- `notification`: in-app notifications. DB-backed only — there is no mail dependency, scheduler, or async executor in this project.
- `common`: health endpoint, `BaseEntity`, API error model, global exception handling.
- `config`: Spring Security, JPA auditing, and role seed initialization.

Cross-cutting authorization rules live in two shared components rather than being copied per service: [CourseAccessPolicy.java](src/main/java/com/example/learning_backend/course/service/CourseAccessPolicy.java) ("may this caller manage this course?" — `canManage` returns a boolean, `ensureCanManage` throws) and [EnrollmentAccessPolicy.java](src/main/java/com/example/learning_backend/enrollment/service/EnrollmentAccessPolicy.java) ("is this user an active member?"). Use them instead of writing a new ownership check.

Request flow is controller -> service -> Spring Data JPA repository -> entity. DTOs define request/response boundaries. Services are the right place for transaction boundaries and business rules; controllers should stay thin.

## Security model

- [SecurityConfig.java](src/main/java/com/example/learning_backend/config/SecurityConfig.java) disables CSRF/basic auth, uses stateless sessions, and installs `JwtAuthenticationFilter` before Spring's username/password filter.
- Public routes: `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/payments/payos/webhook`.
- All other routes require authentication.
- Course and assessment creation, manual answer grading, and the instructor assessment report use `@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")`. Role gating is only the first gate — per-course ownership is then enforced in the service via `CourseAccessPolicy`.
- The revenue report is `@PreAuthorize("hasRole('ADMIN')")` — it is system-wide, so instructors are excluded rather than scoped to their own courses.
- The PayOS webhook is unauthenticated by necessity, so its only gate is the HMAC-SHA256 signature check in `PayosClient.verifyWebhookSignature`. It always answers HTTP 200 (`{"success":false}` on rejection) so the gateway stops retrying a payload that would fail identically; rejections are logged instead. Never let a gateway callback grant access on the strength of its `code` field alone — verify the signature and the amount first.
- JWTs include email subject, `userId`, roles, a random `jti`, and token type. Refresh/password-reset tokens are stored hashed, not raw. The `jti` is what keeps two tokens minted for the same user in the same second distinct — JWT timestamps only carry second precision, and identical tokens would collide on the unique `refresh_tokens.token_hash` index.
- Roles a caller may give themselves are limited by [AssignableRole.java](src/main/java/com/example/learning_backend/user/enums/AssignableRole.java) (`STUDENT`, `INSTRUCTOR`). `ADMIN` is deliberately not in that enum, so neither `POST /api/auth/register` nor `POST /api/auth/change-role` can grant it; the only ADMIN is the one seeded by [RoleDataInitializer.java](src/main/java/com/example/learning_backend/config/RoleDataInitializer.java) from `app.admin.*`. Keep it that way — widening the enum would hand the revenue report and any future user administration to anyone who can reach the public register endpoint.
- `changeRole` swaps only assignable roles and leaves `ADMIN` in place, so an admin experimenting with the endpoint cannot lock the system out of its own admin-only routes.
- **Authorization reads the database on every request**, not the JWT: `JwtAuthenticationFilter` re-loads the user and `CustomUserPrincipal` rebuilds authorities from `user_roles`. A role change therefore takes effect on the caller's very next request, even with their old access token — a demotion needs no revocation. The stale `roles` claim in an already-issued token is cosmetic, which is why `changeRole` returns a fresh token pair for clients that read the claim to pick what to render.

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
- `Payment` links `User` and `Course`; `courses.price` (`DECIMAL(12,2)`, `0` = free) is the only server-side source of the amount, copied into `payments.amount` at checkout so a later price change cannot rewrite past revenue. `order_code` is unique so a redelivered webhook resolves to exactly one payment.
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
