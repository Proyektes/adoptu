# anatomy.md

> Auto-maintained by OpenWolf. Last scanned: 2026-07-04T18:01:13.878Z
> Files: 977 tracked | Anatomy hits: 0 | Misses: 0

## ../../.claude/jobs/34544b15/tmp/

- `loadtest.py` — worker, percentile, run_stage (~572 tok)
- `nginx-cdn-sim-v2.conf` (~736 tok)
- `nginx-cdn-sim.conf` (~267 tok)
- `stats_sampler.sh` — Samples docker stats for a container every 1s until killed. (~118 tok)

## ../../.claude/jobs/3616329a/tmp/petsui/

- `check.mjs` — Declares dir (~865 tok)
- `index.html` — Declares dict (~847 tok)

## ../../.claude/jobs/95b8a41d/tmp/

- `migrate_country.sql` (~554 tok)

## ../../.claude/plans/

- `enumerated-yawning-cloud.md` — Country enum as single source of truth (~1699 tok)

## ../../.claude/projects/-home-laf-Proyektes-adoptu/memory/

- `feedback_background_job_merge_restriction.md` (~405 tok)
- `MEMORY.md` (~131 tok)

## ./

- `.dockerignore` — Docker ignore rules (~22 tok)
- `.gitignore` — Git ignore rules (~246 tok)
- `AGENTS.md` — Adopt-U - Agent Guidelines (~1284 tok)
- `build.gradle.kts` — Gradle Kotlin build configuration (~2307 tok)
- `buildspec.yml` — CodeBuild spec: logs into ECR, builds/tags/pushes the image, emits imagedefinitions.json for ECS deploy (~520 tok)
- `CLAUDE.md` — OpenWolf (~57 tok)
- `docker-compose.test.yml` — Docker Compose: 1 services (~251 tok)
- `docker-compose.yml` — Docker Compose services (~241 tok)
- `Dockerfile` — Multi-stage build: musl-based Corretto 25 builder (Shadow-plugin fat jar) -> jdeps-derived jlink minimal JRE on Alpine, no dead Sass step, container-aware JVM flags (~520 tok)
- `gradle.properties` (~159 tok)
- `gradlew` — you may not use this file except in compliance with the License. (~2292 tok)
- `gradlew.bat` (~748 tok)
- `local-start.sh` (~103 tok)
- `package-lock.json` — npm lock file (~801 tok)
- `package.json` — Node.js package manifest (~198 tok)
- `playwright.config.ts` — Playwright test configuration (~626 tok)
- `README.md` — Project documentation (~821 tok)
- `settings.gradle.kts` — Gradle Kotlin settings (~8 tok)

## .claude/

- `settings.json` (~441 tok)

## .claude/rules/

- `openwolf.md` (~313 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/adapters/db/

- `Models.kt` (~3434 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` — PetRepositoryImpl: rowToPetDto, getPetImages, getAll, getAllUnfiltered (~4397 tok)
- `UserRepository.kt` — UserRepository: getActiveRolesForUser, getById, getByEmail, getAllUsers (~6264 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/dto/input/

- `PetDto.kt` — Data class: Gender (~1024 tok)
- `UserDto.kt` — Data class: UserRole (~1119 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/pages/

- `PetsPage.kt` — HTML (~644 tok)
- `ProfilePage.kt` — HTML (~4822 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/ports/

- `PetRepositoryPort.kt` — getAll, getAllUnfiltered, getById, create, update (~682 tok)
- `UserRepositoryPort.kt` — getById, getByEmail, getAllUsers, getPhotographers, getRescuers (~614 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/routes/

- `PetsRoutes.kt` — Route (~3615 tok)
- `UsersRoutes.kt` — Data class: UpdateProfileRequest (~3462 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/kotlin/com/adoptu/services/

- `PetService.kt` — PetService: getAll, getMine, getById, create (~3030 tok)
- `UserService.kt` — UserService: getById, getByEmail, getAllUsers, getRescuers (~871 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/main/resources/static/js/

- `api.js` — Declares api (~2504 tok)
- `index.js` — emoji: updateCountryHint, loadPets, initCountry (~1036 tok)
- `my-pets.js` — API routes: GET (2 endpoints) (~4229 tok)
- `profile.js` — load: loadPhotographer, loadTemporalHome, loadShelter, loadSterilization (~9633 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/test/kotlin/com/adoptu/routes/

- `PetsRoutesE2ETest.kt` — Ktor routing (~16513 tok)
- `UIRoutesDataTest.kt` — UIRoutesDataTest: setup (~4628 tok)

## .claude/worktrees/agent-a49663a67d3c8a3ea/backend/src/test/kotlin/com/adoptu/services/

- `PetServiceTest.kt` — PetServiceTest: setup (~7222 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` — PetRepositoryImpl: rowToPetDto, getPetImages, getAll, getById (~4175 tok)
- `PhotographerRepository.kt` — Data class: PhotographerRepositoryImpl (~3826 tok)
- `ShelterRepository.kt` — ShelterRepository: rowToDto, getById, getAll, create (~2134 tok)
- `SterilizationLocationRepository.kt` — SterilizationLocationRepository: rowToDto, getById, getAll, create (~2661 tok)
- `TemporalHomeRepository.kt` — Data class: TemporalHomeRepositoryImpl (~3016 tok)
- `UserRepository.kt` — UserRepository: getActiveRolesForUser, getById, getByEmail, getAllUsers (~6146 tok)
- `UserShelterRepository.kt` — UserShelterRepository: rowToDto, getByUserId, create, update (~2304 tok)
- `UserSterilizationLocationRepository.kt` — UserSterilizationLocationRepository: rowToDto, getByUserId, create, update (~2105 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/main/kotlin/com/adoptu/routes/

- `UIRoutes.kt` — Data class: NavParams (~2430 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/main/kotlin/com/adoptu/services/

- `EmailChangeService.kt` — EmailChangeService: requestEmailChange, verifyEmailChange, generateToken, getLocalizedEmailChangeCon (~1816 tok)
- `EmailVerificationService.kt` — EmailVerificationService: getLocalizedContent, generateAndSendVerificationEmail, verifyToken, verify (~1773 tok)
- `MagicLinkService.kt` — Data class: MagicLinkService (~2662 tok)
- `PasswordService.kt` — PasswordService: hasPassword, extractPassword, setPassword, changePassword (~2988 tok)
- `PetService.kt` — PetService: getAll, getById, create, update (~2824 tok)
- `PhotographerService.kt` — PhotographerService: getPhotographers, getPhotographerById, updatePhotographerSettings, canSendMessa (~2627 tok)
- `TemporalHomeService.kt` — TemporalHomeService: getTemporalHome, createTemporalHome, updateTemporalHome, searchTemporalHomes (~1143 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/main/kotlin/com/adoptu/services/auth/

- `WebAuthnService.kt` — Data class: WebAuthnService (~5433 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/main/kotlin/com/adoptu/services/validation/

- `AuthValidationService.kt` — AuthValidationService: validateAndDecryptEmail, validateEmailAndUser, validateSession, validateUserB (~673 tok)
- `PetsValidationService.kt` — PetsValidationService: validateSession, validateUserById, validateUser, validateId (~639 tok)
- `PhotographersValidationService.kt` — PhotographersValidationService: validateSession, validateUserById, validateUser, validateId (~481 tok)
- `TemporalHomesValidationService.kt` — TemporalHomesValidationService: validateSession, validateUserById, validateUser, validateId (~1194 tok)
- `UsersValidationService.kt` — UsersValidationService: validateSession, validateUserById, validateUser, validateId (~631 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/test/kotlin/com/adoptu/routes/

- `PetsRoutesE2ETest.kt` — Ktor routing (~6098 tok)
- `UIRoutesDataTest.kt` — UIRoutesDataTest: setup (~4470 tok)

## .claude/worktrees/agent-aa2e565a38703b52b/backend/src/test/kotlin/com/adoptu/services/

- `PetServiceTest.kt` — PetServiceTest: setup (~6373 tok)
- `ShelterServiceTest.kt` — ShelterServiceTest: setup, createTestShelter (~3345 tok)
- `SterilizationLocationServiceTest.kt` — SterilizationLocationServiceTest: setup, cleanup (~3602 tok)
- `TemporalHomeServiceTest.kt` — TemporalHomeServiceTest: setup, cleanup (~4011 tok)
- `UserServiceTest.kt` — UserServiceTest: setup (~4229 tok)

## .claude/worktrees/bump-task-cpu/backend/src/main/kotlin/com/adoptu/routes/

- `PetsRoutes.kt` — Route (~3423 tok)
- `PhotographerRoutes.kt` — Route, RoutingContext (~2148 tok)
- `ShelterRoutes.kt` — Route, Route (~1197 tok)
- `SterilizationLocationRoutes.kt` — Route, Route (~1309 tok)
- `TemporalHomeRoutes.kt` — Route (~2396 tok)

## .claude/worktrees/bump-task-cpu/infra/

- `cloudfront.tf` (~2707 tok)
- `variables.tf` (~1167 tok)

## .claude/worktrees/cached-hopping-sutton/

- `.dockerignore` — Docker ignore rules (~0 tok)
- `.gitignore` — Git ignore rules (~114 tok)
- `build.gradle.kts` — Gradle Kotlin build configuration (~0 tok)
- `buildspec.yml` (~353 tok)
- `cookies.txt` (~0 tok)
- `Dockerfile` — Docker container definition (~0 tok)
- `gradlew` (~0 tok)
- `gradlew.bat` (~0 tok)
- `README.md` — Project documentation (~0 tok)
- `settings.gradle.kts` — Gradle Kotlin settings (~0 tok)
- `start-local.sh` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/gradle/wrapper/

- `gradle-wrapper.properties` (~68 tok)

## .claude/worktrees/cached-hopping-sutton/scripts/

- `truncate-db.sh` (~436 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/

- `Application.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/adapters/db/

- `DatabaseFactory.kt` (~0 tok)
- `Models.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` (~0 tok)
- `PhotographerRepository.kt` (~0 tok)
- `ShelterRepository.kt` (~0 tok)
- `SterilizationLocationRepository.kt` (~0 tok)
- `TemporalHomeRepository.kt` (~0 tok)
- `UserRepository.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/adapters/notification/

- `SesEmailAdapter.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/adapters/storage/

- `S3ImageStorageAdapter.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/di/

- `AppModule.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/dto/input/

- `AdoptionRequestDto.kt` (~0 tok)
- `AuthDto.kt` (~0 tok)
- `PetDto.kt` (~0 tok)
- `ShelterDto.kt` (~0 tok)
- `SterilizationLocationDto.kt` (~0 tok)
- `UserDto.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/dto/output/

- `AuthResponses.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/pages/

- `AdminPage.kt` (~0 tok)
- `AdminSheltersPage.kt` (~0 tok)
- `EmailVerificationPage.kt` (~0 tok)
- `IndexPage.kt` (~0 tok)
- `LoginPage.kt` (~0 tok)
- `MyPetsPage.kt` (~0 tok)
- `PetDetailPage.kt` (~0 tok)
- `PetsPage.kt` (~0 tok)
- `PhotographersPage.kt` (~0 tok)
- `PrivacyPage.kt` (~0 tok)
- `ProfilePage.kt` (~0 tok)
- `RegisterPage.kt` (~0 tok)
- `Shared.kt` (~0 tok)
- `SheltersPage.kt` (~0 tok)
- `SterilizationLocationsPage.kt` (~0 tok)
- `TemporalHomePage.kt` (~0 tok)
- `TermsPage.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/plugins/

- `Responses.kt` (~0 tok)
- `Routing.kt` (~0 tok)
- `Serialization.kt` (~0 tok)
- `Sessions.kt` (~0 tok)
- `WebAuthn.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/ports/

- `ImageStoragePort.kt` (~0 tok)
- `NotificationPort.kt` (~0 tok)
- `PetRepositoryPort.kt` (~0 tok)
- `PhotographerRepositoryPort.kt` (~0 tok)
- `ShelterRepositoryPort.kt` (~0 tok)
- `SterilizationLocationRepositoryPort.kt` (~0 tok)
- `TemporalHomeRepositoryPort.kt` (~0 tok)
- `UserRepositoryPort.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/routes/

- `AuthRoutes.kt` (~0 tok)
- `PetsRoutes.kt` (~0 tok)
- `PhotographerRoutes.kt` (~0 tok)
- `ShelterRoutes.kt` (~0 tok)
- `SterilizationLocationRoutes.kt` (~0 tok)
- `TemporalHomeRoutes.kt` (~0 tok)
- `UIRoutes.kt` (~0 tok)
- `UsersRoutes.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/services/

- `EmailVerificationService.kt` (~0 tok)
- `ImageCompressor.kt` (~0 tok)
- `PetService.kt` (~0 tok)
- `PhotographerService.kt` (~0 tok)
- `ServiceResult.kt` (~0 tok)
- `ShelterService.kt` (~0 tok)
- `SterilizationLocationService.kt` (~0 tok)
- `TemporalHomeService.kt` (~0 tok)
- `UserService.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/services/auth/

- `SessionUser.kt` (~0 tok)
- `WebAuthnService.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/kotlin/com/adoptu/services/validation/

- `AuthValidationService.kt` (~0 tok)
- `PetsValidationService.kt` (~0 tok)
- `PhotographersValidationService.kt` (~0 tok)
- `SheltersValidationService.kt` (~0 tok)
- `SterilizationLocationsValidationService.kt` (~0 tok)
- `TemporalHomesValidationService.kt` (~0 tok)
- `UsersValidationService.kt` (~0 tok)
- `ValidationConstants.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/resources/

- `application.conf` (~0 tok)
- `logback.xml` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/resources/static/js/

- `api.js` — Declares api (~1844 tok)
- `i18n.js` — Declares i18n (~22790 tok)
- `webauthn.js` — Declares webauthn (~1130 tok)

## .claude/worktrees/cached-hopping-sutton/src/main/scss/

- `style.scss` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/

- `ApplicationContainerTest.kt` (~0 tok)
- `ApplicationIntegrationTest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/adapters/db/

- `DatabaseFactoryInitIT.kt` (~0 tok)
- `DatabaseFactoryTest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepositoryIT.kt` (~0 tok)
- `PhotographerRepositoryIT.kt` (~0 tok)
- `ShelterRepositoryIT.kt` (~0 tok)
- `TemporalHomeRepositoryIT.kt` (~0 tok)
- `UserRepositoryIT.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/adapters/notification/

- `SesEmailAdapterIT.kt` (~0 tok)
- `SesEmailAdapterTest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/adapters/storage/

- `ImageStorageIT.kt` (~0 tok)
- `S3ImageStorageAdapterTest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/di/

- `AppModuleTest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/mocks/

- `MockImageStorage.kt` (~0 tok)
- `MockNotificationAdapter.kt` (~0 tok)
- `MockNotificationAdapterTest.kt` (~0 tok)
- `TestClock.kt` (~0 tok)
- `TestDatabase.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/routes/

- `ApplicationTestcontainersIT.kt` (~0 tok)
- `EmailVerificationRoutesE2ETest.kt` (~0 tok)
- `PetsRoutesE2ETest.kt` (~0 tok)
- `PetsRoutesIntegrationTest.kt` (~0 tok)
- `SheltersRoutesE2ETest.kt` (~0 tok)
- `UIRoutesDataTest.kt` (~0 tok)
- `UsersRoutesE2ETest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/services/

- `EmailVerificationServiceTest.kt` (~0 tok)
- `ImageCompressorTest.kt` (~0 tok)
- `PetServiceTest.kt` (~0 tok)
- `PhotographerServiceTest.kt` (~0 tok)
- `ShelterServiceTest.kt` (~0 tok)
- `SterilizationLocationServiceTest.kt` (~0 tok)
- `TemporalHomeServiceTest.kt` (~0 tok)
- `UserServiceTest.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/kotlin/com/adoptu/test/

- `SterilizationLocationRepositoryIT.kt` (~0 tok)

## .claude/worktrees/cached-hopping-sutton/src/test/resources/

- `application.conf` (~0 tok)
- `logback-test.xml` (~0 tok)

## .claude/worktrees/clever-forging-teacup/.wolf/

- `cerebrum.md` — Cerebrum (~768 tok)

## .claude/worktrees/clever-forging-teacup/backend/src/main/kotlin/com/adoptu/pages/

- `EmailVerificationPage.kt` — HTML (~1236 tok)
- `Shared.kt` — HTML, A, BODY, DIV, NAV (~3173 tok)

## .claude/worktrees/country-detect/.wolf/

- `anatomy.md` — anatomy.md (~19416 tok)
- `cerebrum.md` — Cerebrum (~9898 tok)

## .claude/worktrees/country-detect/backend/src/main/kotlin/com/adoptu/common/

- `Country.kt` — Country: fromDisplayName, fromIso2, normalize (~1783 tok)

## .claude/worktrees/country-detect/backend/src/main/kotlin/com/adoptu/plugins/

- `Routing.kt` — Ktor routing (~186 tok)

## .claude/worktrees/country-detect/backend/src/main/kotlin/com/adoptu/routes/

- `CountryRoutes.kt` — Route, regionFromLocale (~276 tok)

## .claude/worktrees/country-detect/backend/src/main/resources/static/js/

- `api.js` — Declares api (~2546 tok)
- `index.js` — emoji: updateCountryHint, showEmptyState, hideEmptyState + 6 more (~1997 tok)

## .claude/worktrees/country-detect/infra/

- `cloudfront.tf` (~2963 tok)

## .claude/worktrees/country-enum-fix/.wolf/

- `cerebrum.md` — Cerebrum (~1059 tok)

## .claude/worktrees/country-enum-fix/backend/src/main/kotlin/com/adoptu/adapters/db/

- `Models.kt` (~3276 tok)

## .claude/worktrees/country-enum-fix/backend/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PhotographerRepository.kt` — PhotographerRepositoryImpl: canSendMessage, createPhotographyRequest, getMyRequests, getRequestsForP (~3336 tok)
- `ShelterRepository.kt` — ShelterRepository: rowToDto, getById, getAll, create (~1877 tok)
- `SterilizationLocationRepository.kt` — SterilizationLocationRepository: rowToDto, getById, getAll, create (~2347 tok)
- `TemporalHomeRepository.kt` — TemporalHomeRepositoryImpl: getTemporalHome, createTemporalHome, updateTemporalHome, searchTemporalH (~2482 tok)
- `UserRepository.kt` — UserRepository: getActiveRolesForUser, getById, getByEmail, getAllUsers (~5228 tok)
- `UserShelterRepository.kt` — UserShelterRepository: rowToDto, getByUserId, create, update (~2093 tok)
- `UserSterilizationLocationRepository.kt` — UserSterilizationLocationRepository: rowToDto, getByUserId, create, update (~1908 tok)

## .claude/worktrees/country-enum-fix/backend/src/main/kotlin/com/adoptu/pages/

- `Shared.kt` — HTML, A, BODY, DIV, NAV (~1955 tok)

## .claude/worktrees/country-enum-fix/backend/src/test/kotlin/com/adoptu/routes/

- `SheltersRoutesE2ETest.kt` — SheltersRoutesE2ETest: findAvailablePort, createTestConfig, initDatabase, setUp (~6267 tok)

## .claude/worktrees/country-enum-fix/backend/src/test/kotlin/com/adoptu/services/

- `ShelterServiceTest.kt` — ShelterServiceTest: setup, createTestShelter (~3232 tok)

## .claude/worktrees/country-hint-fix-v2/.claude/worktrees/fix-pets-country-selector/backend/src/main/kotlin/com/adoptu/pages/

- `IndexPage.kt` — HTML (~583 tok)

## .claude/worktrees/country-hint-fix-v2/.claude/worktrees/fix-pets-country-selector/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~18544 tok)

## .claude/worktrees/country-hint-fix-v2/backend/src/main/kotlin/com/adoptu/pages/

- `LocationSearchFilters.kt` — DIV (~927 tok)

## .claude/worktrees/country-selector-default-persist/backend/src/main/kotlin/com/adoptu/dto/output/

- `AuthResponses.kt` — Data class: AuthMeResponse (~439 tok)

## .claude/worktrees/country-selector-default-persist/backend/src/main/kotlin/com/adoptu/routes/

- `AuthRoutes.kt` — Data class: EncryptedLoginRequest (~6922 tok)

## .claude/worktrees/country-selector-default-persist/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `Common.kt` — NodeList, onCountryChange, initI18n, initDropdowns, checkProfileCompletion (~1464 tok)

## .claude/worktrees/country-selector-default-persist/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `IndexPage.kt` — init, loadPets, updateCountryHint, showEmptyState, hideEmptyState (~2200 tok)
- `PhotographersPage.kt` — init, search, load, render, createRequestModal (~1706 tok)
- `SheltersPage.kt` — init, search, renderShelters (~1292 tok)
- `SterilizationLocationsPage.kt` — init, search, render, locationCard, init (~2634 tok)
- `TemporalHomePage.kt` — init, search, displayResults, init, loadRequests (~1344 tok)

## .claude/worktrees/e2e-verify-expand/.claude/skills/

- `e2e-verify.md` — E2E Verification Skill (~593 tok)

## .claude/worktrees/e2e-verify-expand/.wolf/

- `buglog.json` — Declares constant (~2533 tok)
- `cerebrum.md` — Cerebrum (~10900 tok)
- `memory.md` — Memory (~26210 tok)

## .claude/worktrees/e2e-verify-expand/backend/src/main/kotlin/com/adoptu/common/

- `Country.kt` — Country: fromDisplayName, normalize (~1532 tok)

## .claude/worktrees/e2e-verify-expand/backend/src/main/kotlin/com/adoptu/dto/input/

- `PetDto.kt` — Data class: Gender (~1025 tok)

## .claude/worktrees/e2e-verify-expand/backend/src/main/kotlin/com/adoptu/dto/output/

- `AuthResponses.kt` — Data class: AuthMeResponse (~439 tok)

## .claude/worktrees/e2e-verify-expand/backend/src/main/kotlin/com/adoptu/pages/

- `LocationSearchFilters.kt` — DIV (~1089 tok)
- `MyPetsPage.kt` — HTML (~2581 tok)
- `ProfilePage.kt` — HTML (~4809 tok)

## .claude/worktrees/e2e-verify-expand/backend/src/main/kotlin/com/adoptu/routes/

- `AuthRoutes.kt` — Data class: EncryptedLoginRequest (~6925 tok)

## .claude/worktrees/e2e-verify-expand/backend/src/main/resources/static/js/

- `index.js` — emoji: updateCountryHint, loadPets, initCountry (~1215 tok)
- `temporal-home-search.js` — params: executedFunction (~664 tok)

## .claude/worktrees/e2e-verify-expand/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` — apiFetch, me, logout, getPets, getPet (~1668 tok)
- `I18n.kt` (~20865 tok)

## .claude/worktrees/e2e-verify-expand/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `ProfilePage.kt` — init, loadProfile, updateProfileUI, checkProfileExists, setupRoleToggles (~9647 tok)

## .claude/worktrees/e2e-verify-expand/frontend/src/tests/

- `e2e-verify.spec.ts` — Adoptu — End-to-End Verification Suite (~12989 tok)

## .claude/worktrees/elegant-coalescing-kurzweil/

- `docker-compose.yml` — Docker Compose services (~118 tok)
- `local-start.sh` (~49 tok)

## .claude/worktrees/elegant-coalescing-kurzweil/.wolf/

- `buglog.json` (~1465 tok)
- `memory.md` — Memory (~913 tok)

## .claude/worktrees/elegant-coalescing-kurzweil/backend/

- `build.gradle.kts` (~1930 tok)

## .claude/worktrees/filter-country-hint/backend/src/main/kotlin/com/adoptu/pages/

- `LocationSearchFilters.kt` — DIV (~703 tok)

## .claude/worktrees/filter-country-hint/backend/src/main/scss/

- `_location-search-form.scss` — Styles: 2 rules (~502 tok)

## .claude/worktrees/filter-country-hint/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~17510 tok)

## .claude/worktrees/fix-country-i18n-typo/backend/src/main/kotlin/com/adoptu/common/

- `Country.kt` — Country: fromDisplayName, normalize (~1532 tok)

## .claude/worktrees/fix-fido-cancel-error/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `LoginPage.kt` — init, getPublicKey, setupPasskeyButton, setupMagicLinkButton, setupPasswordLoginButton (~1477 tok)

## .claude/worktrees/fix-multimodule-migration/

- `.dockerignore` — Docker ignore rules (~22 tok)
- `.gitignore` — Git ignore rules (~149 tok)
- `AGENTS.md` — Adopt-U - Agent Guidelines (~1284 tok)
- `build.gradle.kts` — Gradle Kotlin build configuration (~73 tok)
- `buildspec.yml` (~353 tok)
- `docker-compose.test.yml` — Docker Compose: 1 services (~251 tok)
- `Dockerfile` — Docker container definition (~372 tok)
- `gradle.properties` (~159 tok)
- `gradlew` — you may not use this file except in compliance with the License. (~2292 tok)
- `gradlew.bat` (~748 tok)
- `README.md` — Project documentation (~821 tok)
- `settings.gradle.kts` — Gradle Kotlin settings (~19 tok)

## .claude/worktrees/fix-multimodule-migration/.aiassistant/rules/

- `main.md` (~20 tok)

## .claude/worktrees/fix-multimodule-migration/.gradle/

- `file-system.probe` (~2 tok)

## .claude/worktrees/fix-multimodule-migration/.gradle/9.3.0/

- `gc.properties` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/.gradle/buildOutputCleanup/

- `cache.properties` — Sat Jun 20 23:22:01 CST 2026 (~14 tok)

## .claude/worktrees/fix-multimodule-migration/.gradle/vcs-1/

- `gc.properties` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/backend/

- `build.gradle.kts` — Gradle Kotlin build configuration (~1872 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/

- `Application.kt` — main, Application (~278 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/adapters/db/

- `DatabaseFactory.kt` — init, createDefaultAdmin (~890 tok)
- `Models.kt` (~3231 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` — PetRepositoryImpl: rowToPetDto, getPetImages, getAll, getById + 9 more (~3800 tok)
- `PhotographerRepository.kt` — PhotographerRepositoryImpl: canSendMessage, createPhotographyRequest, getMyRequests, getRequestsForPhotographer + 7 more (~3242 tok)
- `ShelterRepository.kt` — ShelterRepository: rowToDto, getById, getAll, create + 4 more (~1745 tok)
- `SterilizationLocationRepository.kt` — SterilizationLocationRepository: rowToDto, getById, getAll, create + 6 more (~2185 tok)
- `TemporalHomeRepository.kt` — TemporalHomeRepositoryImpl: getTemporalHome, createTemporalHome, updateTemporalHome, searchTemporalHomes + 4 more (~2360 tok)
- `UserRepository.kt` — UserRepository: getActiveRolesForUser, getById, getByEmail, getAllUsers + 15 more (~4850 tok)
- `UserShelterRepository.kt` — UserShelterRepository: rowToDto, getByUserId, create, update + 2 more (~1979 tok)
- `UserSterilizationLocationRepository.kt` — UserSterilizationLocationRepository: rowToDto, getByUserId, create, update + 2 more (~1788 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/adapters/dynamodb/

- `DynamoDBAdapter.kt` — DynamoDBAdapter: tableName, createUser, getUserById, getUserByEmail + 14 more (~8323 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/adapters/notification/

- `SesEmailAdapter.kt` — SesEmailAdapter: sendEmail, sendEmailViaSmtp, sendEmailViaSes, sendPhotographerRequest + 2 more (~2660 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/adapters/storage/

- `S3ImageStorageAdapter.kt` — S3ImageStorageAdapter: uploadImage, uploadImage, createBucket, deleteImage + 2 more (~1204 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/di/

- `AppModule.kt` — appModule, getOrigins, createImageStorageAdapter (~1036 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/dto/input/

- `AdoptionRequestDto.kt` — Data class: AdoptionRequestDto (7 properties) (~91 tok)
- `AuthDto.kt` — Data class: AssertionOptionsDto (13 properties) (~175 tok)
- `PetDto.kt` — Data class: Gender (91 properties) (~926 tok)
- `ShelterDto.kt` — Data class: ShelterDto (119 properties) (~1120 tok)
- `SterilizationLocationDto.kt` — Data class: SterilizationLocationDto (81 properties) (~841 tok)
- `UserDto.kt` — Data class: UserRole (94 properties) (~1111 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/dto/output/

- `AuthResponses.kt` — Data class: AuthMeResponse (36 properties) (~430 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/pages/

- `AdminPage.kt` — HTML (~810 tok)
- `AdminSheltersPage.kt` — HTML (~349 tok)
- `EmailVerificationPage.kt` — HTML (~878 tok)
- `ForgotPasswordPage.kt` — HTML, HTML, HTML, HTML (~1083 tok)
- `Icons.kt` — Declares val (~187 tok)
- `IndexPage.kt` — HTML (~448 tok)
- `LocationSearchFilters.kt` — DIV (~486 tok)
- `LoginPage.kt` — HTML (~962 tok)
- `MyPetsPage.kt` — HTML (~2310 tok)
- `PetDetailPage.kt` — HTML (~179 tok)
- `PetFoodPage.kt` — Data class: FoodCategory (7 properties) (~2077 tok)
- `PetsPage.kt` — HTML (~448 tok)
- `PhotographersPage.kt` — HTML (~360 tok)
- `PrivacyPage.kt` — HTML (~2143 tok)
- `ProfilePage.kt` — HTML (~4436 tok)
- `RegisterPage.kt` — HTML (~1594 tok)
- `Shared.kt` — HTML, A, BODY, DIV, NAV (~3144 tok)
- `SheltersPage.kt` — HTML (~366 tok)
- `SterilizationLocationsPage.kt` — HTML, HTML (~1591 tok)
- `TemporalHomePage.kt` — HTML, HTML (~519 tok)
- `TermsPage.kt` — HTML (~1810 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/plugins/

- `Responses.kt` — Data class: ErrorResponse (3 properties) (~964 tok)
- `Routing.kt` — Ktor routes: 1 endpoints (~180 tok)
- `Serialization.kt` — Application (~100 tok)
- `Sessions.kt` — Application (~155 tok)
- `WebAuthn.kt` — Application (~40 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/ports/

- `DynamoDBPort.kt` — createUser, getUserById, getUserByEmail, updateUser, deleteUser (~641 tok)
- `EmailVerificationTokenInfo.kt` (~0 tok)
- `ImageStoragePort.kt` — uploadImage, deleteImage, getImageUrl (~83 tok)
- `NotificationPort.kt` — sendEmail, sendPhotographerRequest, sendAdoptionRequestNotification, sendTemporalHomeRequest (~219 tok)
- `PetRepositoryPort.kt` — getAll, getById, create, update, delete (~576 tok)
- `PhotographerRepositoryPort.kt` — canSendMessage, createPhotographyRequest, getMyRequests, getRequestsForPhotographer, getRequestById (~290 tok)
- `ShelterRepositoryPort.kt` — getById, getAll, create, update, delete (~171 tok)
- `SterilizationLocationRepositoryPort.kt` — getById, getAll, create, update, delete (~267 tok)
- `TemporalHomeRepositoryPort.kt` — getTemporalHome, createTemporalHome, updateTemporalHome, searchTemporalHomes, createTemporalHomeRequest (~255 tok)
- `UserRepositoryPort.kt` — getById, getByEmail, getAllUsers, getPhotographers, getRescuers (~510 tok)
- `UserShelterRepositoryPort.kt` — getByUserId, create, update, delete, search (~156 tok)
- `UserSterilizationLocationRepositoryPort.kt` — getByUserId, create, update, delete, search (~193 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/routes/

- `AuthRoutes.kt` — Data class: EncryptedLoginRequest (3 properties) (~5480 tok)
- `PetsRoutes.kt` — Ktor routes: 10 endpoints (~3297 tok)
- `PhotographerRoutes.kt` — Ktor routes: 6 endpoints (~1999 tok)
- `ShelterRoutes.kt` — Ktor routes: 6 endpoints (~1083 tok)
- `SterilizationLocationRoutes.kt` — Ktor routes: 8 endpoints (~1183 tok)
- `TemporalHomeRoutes.kt` — Ktor routes: 7 endpoints (~2243 tok)
- `UIRoutes.kt` — Data class: NavParams (29 properties) (~2116 tok)
- `UserShelterRoutes.kt` — Ktor routes: 4 endpoints (~721 tok)
- `UsersRoutes.kt` — Data class: UpdateProfileRequest (6 properties) (~3489 tok)
- `UserSterilizationLocationRoutes.kt` — Ktor routes: 4 endpoints (~764 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/services/

- `EmailChangeService.kt` — EmailChangeService: requestEmailChange, verifyEmailChange, generateToken, getLocalizedEmailChangeContent (~1814 tok)
- `EmailVerificationService.kt` — EmailVerificationService: getLocalizedContent, generateAndSendVerificationEmail, verifyToken, verifyTokenAndGetLanguage + 4 more (~1765 tok)
- `ImageCompressor.kt` — compress, calculateDimensions (~536 tok)
- `MagicLinkService.kt` — Data class: MagicLinkService (9 properties) (~1941 tok)
- `PasswordService.kt` — PasswordService: hasPassword, setPassword, changePassword, verifyPassword + 11 more (~2820 tok)
- `PetService.kt` — PetService: getAll, getById, create, update + 11 more (~2807 tok)
- `PhotographerService.kt` — PhotographerService: getPhotographers, getPhotographerById, updatePhotographerSettings, canSendMessage + 8 more (~2601 tok)
- `ServiceResult.kt` — Data class: ServiceResult (2 properties) (~82 tok)
- `ShelterService.kt` — ShelterService: getAll, getById, create, update + 3 more (~492 tok)
- `SterilizationLocationService.kt` — SterilizationLocationService: getAll, getById, create, update + 5 more (~585 tok)
- `TemporalHomeService.kt` — TemporalHomeService: getTemporalHome, createTemporalHome, updateTemporalHome, searchTemporalHomes + 6 more (~1125 tok)
- `UserService.kt` — UserService: getById, getByEmail, getAllUsers, getRescuers + 18 more (~836 tok)
- `UserShelterService.kt` — UserShelterService: getByUserId, create, update, delete + 1 more (~460 tok)
- `UserSterilizationLocationService.kt` — UserSterilizationLocationService: getByUserId, create, update, delete + 1 more (~504 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/services/auth/

- `SessionUser.kt` — Data class: SessionUser (3 properties) (~51 tok)
- `WebAuthnService.kt` — Data class: WebAuthnService (33 properties) (~5132 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/services/crypto/

- `CryptoService.kt` — getOaepParameterSpec, initialize, generateKeyPair, getPublicKey, encrypt (~1199 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/kotlin/com/adoptu/services/validation/

- `AuthValidationService.kt` — AuthValidationService: validateAndDecryptEmail, validateEmailAndUser, validateSession, validateUserById + 2 more (~638 tok)
- `PetsValidationService.kt` — PetsValidationService: validateSession, validateUserById, validateUser, validateId + 4 more (~637 tok)
- `PhotographersValidationService.kt` — PhotographersValidationService: validateSession, validateUserById, validateUser, validateId + 2 more (~479 tok)
- `SheltersValidationService.kt` — SheltersValidationService: validateId, validateRequired (~158 tok)
- `SterilizationLocationsValidationService.kt` — SterilizationLocationsValidationService: validateId, validateRequired (~162 tok)
- `TemporalHomesValidationService.kt` — TemporalHomesValidationService: validateSession, validateUserById, validateUser, validateId + 8 more (~1183 tok)
- `UsersValidationService.kt` — UsersValidationService: validateSession, validateUserById, validateUser, validateId + 4 more (~629 tok)
- `ValidationConstants.kt` — Declares val (~246 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/resources/

- `application.conf` (~646 tok)
- `logback.xml` (~92 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/resources/static/css/

- `mypets.css` (~0 tok)
- `mypets.css.map` (~0 tok)
- `pet-detail.css` (~0 tok)
- `pet-detail.css.map` (~0 tok)
- `pet-food.css` — Styles: 97 rules, 8 vars (~3682 tok)
- `pet-food.css.map` (~0 tok)
- `photographers.css` — Styles: 90 rules, 8 vars, 1 media queries (~3484 tok)
- `photographers.css.map` (~0 tok)
- `policy.css` — Styles: 88 rules, 8 vars, 1 media queries (~3398 tok)
- `policy.css.map` (~0 tok)
- `shelters.css` — Styles: 91 rules, 8 vars, 1 media queries (~3477 tok)
- `shelters.css.map` (~0 tok)
- `sterilization.css` — Styles: 93 rules, 8 vars, 1 media queries (~3462 tok)
- `sterilization.css.map` (~0 tok)
- `style.css` — Styles: 91 rules, 8 vars (~5935 tok)
- `style.css.map` (~0 tok)
- `temporal-home.css` — Styles: 92 rules, 8 vars, 1 media queries (~3537 tok)
- `temporal-home.css.map` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/resources/static/js/

- `admin-shelters.js` — escapeHtml: loadShelters, showMessage, getFormData + 4 more (~2281 tok)
- `admin.js` — b: T, C, L + 83 more (~26095 tok)
- `admin.js.map` (~45888 tok)
- `api.js` — Declares api (~2422 tok)
- `common.js` — isLoggedIn: setLang, initI18n, checkProfileCompletion (~1027 tok)
- `common.js.map` (~79037 tok)
- `crypto.js` — rsaCrypto: getPublicKey (~678 tok)
- `email-change-verification.js` — API routes: GET (1 endpoints) (~274 tok)
- `email-verification.js` — Declares countdownEl (~84 tok)
- `forgot-password.js` — Declares msg (~357 tok)
- `index.js` — emoji: loadPets (~595 tok)
- `login.js` — updateLangButton: checkProfileCompletion, showResendButton (~2429 tok)
- `magic-link-login.js` — API routes: GET (1 endpoints) (~278 tok)
- `my-pets.js` — API routes: GET (2 endpoints) (~4125 tok)
- `pet-detail.js` — API routes: GET (1 endpoints) (~1864 tok)
- `pet-food.js` — foodData: showFoodInfo (~2032 tok)
- `photographers.js` — country: loadPhotographers, createRequestModal, showRequestModal, executedFunction (~1841 tok)
- `profile.js` — load: loadPhotographer, loadTemporalHome, loadShelter, loadSterilization (~9666 tok)
- `register.js` — Declares validatePassword (~1244 tok)
- `reset-password.js` — API routes: GET (1 endpoints) (~891 tok)
- `shelters.js` — country: executedFunction (~1452 tok)
- `sterilization-locations.js` — escapeHtml: loadCountries, loadLocations, showForm + 3 more (~1326 tok)
- `sterilization.js` — country: executedFunction (~964 tok)
- `temporal-home-profile.js` — user: loadRequests (~461 tok)
- `temporal-home-search.js` — country: executedFunction (~659 tok)
- `temporal-home.js` — Declares blockRescuerAndRedirect (~168 tok)
- `webauthn.js` — Declares webauthn (~1130 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/resources/static/js/i18n/

- `i18n-en.js` (~6722 tok)
- `i18n-es.js` (~7231 tok)
- `i18n-fr.js` (~7328 tok)
- `i18n-pt.js` (~7026 tok)
- `i18n-zh.js` (~4780 tok)
- `i18n.js` — i18n: setLang, t (~1017 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/main/scss/

- `_base.scss` — Styles: 13 rules, 8 vars (~1163 tok)
- `_layout.scss` — Styles: 11 rules (~2061 tok)
- `_location-search-form.scss` — Styles: 2 rules, 1 media queries (~481 tok)
- `_variables.scss` — Variables (~47 tok)
- `mypets.scss` — Styles: 5 rules (~833 tok)
- `pet-detail.scss` — Styles: 2 rules (~33 tok)
- `pet-food.scss` — Styles: 11 rules (~946 tok)
- `photographers.scss` — Styles: 6 rules (~214 tok)
- `policy.scss` — Styles: 5 rules (~129 tok)
- `shelters.scss` — Styles: 6 rules (~208 tok)
- `sterilization.scss` — Styles: 6 rules (~187 tok)
- `style.scss` — Styles: 41 rules (~2264 tok)
- `temporal-home.scss` — Styles: 8 rules (~269 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/

- `ApplicationContainerTest.kt` — ApplicationContainerTest: testAppConfig (~911 tok)
- `ApplicationIntegrationTest.kt` — ApplicationIntegrationTest: findAvailablePort, createTestConfig, initDatabase, setUpAll + 3 more (~3523 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/adapters/db/

- `DatabaseFactoryInitIT.kt` — DatabaseFactoryInitIT: startContainer, stopContainer, createConfig (~1979 tok)
- `DatabaseFactoryTest.kt` — Declares DatabaseFactoryTest (~3335 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepositoryIT.kt` (~0 tok)
- `PhotographerRepositoryIT.kt` (~0 tok)
- `ShelterRepositoryIT.kt` (~0 tok)
- `TemporalHomeRepositoryIT.kt` (~0 tok)
- `UserRepositoryIT.kt` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/adapters/notification/

- `SesEmailAdapterIT.kt` (~0 tok)
- `SesEmailAdapterTest.kt` — Declares SesEmailAdapterTest (~2868 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/adapters/storage/

- `ImageStorageIT.kt` (~0 tok)
- `S3ImageStorageAdapterTest.kt` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/di/

- `AppModuleTest.kt` — Declares AppModuleTest (~790 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/e2e/

- `BaseE2ETest.kt` — BaseE2ETest: getBaseUrl, setupServer, startTestServer, findFreePort + 7 more (~1284 tok)
- `FrontendE2ETest.kt` — Declares FrontendE2ETest (~346 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/mocks/

- `MockImageStorage.kt` — MockImageStorage: setFailMode, uploadImage, deleteImage, getImageUrl + 2 more (~328 tok)
- `MockNotificationAdapter.kt` — Data class: MockNotificationAdapter (4 properties) (~644 tok)
- `MockNotificationAdapterTest.kt` — MockNotificationAdapterTest: setup (~1506 tok)
- `TestClock.kt` — TestClock: now, setTime, setTimeMillis, advanceMillis (~161 tok)
- `TestDatabase.kt` — initH2, clearAllData (~861 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/routes/

- `ApplicationTestcontainersIT.kt` — ApplicationTestcontainersIT: createAppConfig, initDatabase, TestApplicationBuilder, setUp (~7394 tok)
- `EmailVerificationRoutesE2ETest.kt` — Data class: RegistrationResponse (6 properties) (~1791 tok)
- `PasswordRegistrationRoutesE2ETest.kt` — Data class: SuccessResponse (6 properties) (~3004 tok)
- `PetsRoutesE2ETest.kt` — Ktor routes: 16 endpoints (~6067 tok)
- `SheltersRoutesE2ETest.kt` — SheltersRoutesE2ETest: findAvailablePort, createTestConfig, initDatabase, setUp + 3 more (~6151 tok)
- `UIRoutesDataTest.kt` — UIRoutesDataTest: setup (~4388 tok)
- `UsersRoutesE2ETest.kt` — Ktor routes: 3 endpoints (~1625 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/services/

- `EmailChangeServiceTest.kt` — EmailChangeServiceTest: setup, createTestUser, createEmailChangeToken, createExpiredEmailChangeToken (~2663 tok)
- `EmailVerificationServiceTest.kt` — EmailVerificationServiceTest: setup, createTestUser, createVerificationToken (~2861 tok)
- `ImageCompressorTest.kt` — ImageCompressorTest: createTestImage (~747 tok)
- `MagicLinkServiceTest.kt` — MagicLinkServiceTest: setup, createTestUser, createMagicLinkToken, createExpiredMagicLinkToken (~3062 tok)
- `PasswordServiceTest.kt` — PasswordServiceTest: setup, encryptPassword, createTestUser, createPasswordResetToken + 1 more (~3160 tok)
- `PetServiceTest.kt` — PetServiceTest: setup (~6211 tok)
- `PhotographerServiceTest.kt` — PhotographerServiceTest: setup (~8506 tok)
- `ShelterServiceTest.kt` — ShelterServiceTest: setup, createTestShelter (~3108 tok)
- `SterilizationLocationServiceTest.kt` — SterilizationLocationServiceTest: setup, cleanup, createLocation (~3406 tok)
- `TemporalHomeServiceTest.kt` — TemporalHomeServiceTest: setup, cleanup (~3849 tok)
- `UserServiceTest.kt` — UserServiceTest: setup (~4066 tok)
- `WebAuthnServiceTest.kt` — WebAuthnServiceTest: setup, userService, encryptPassword, createTestUser + 1 more (~2874 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/services/auth/

- `SessionUserTest.kt` (~0 tok)
- `WebAuthnServiceChallengeStoreTest.kt` — WebAuthnServiceChallengeStoreTest: setup (~1962 tok)
- `WebAuthnServiceTest.kt` — WebAuthnServiceTest: setup (~640 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/services/validation/

- `AuthValidationServiceTest.kt` (~0 tok)
- `PetsValidationServiceTest.kt` (~0 tok)
- `PhotographersValidationServiceTest.kt` (~0 tok)
- `SheltersValidationServiceTest.kt` (~0 tok)
- `SterilizationLocationsValidationServiceTest.kt` (~0 tok)
- `TemporalHomesValidationServiceTest.kt` (~0 tok)
- `UsersValidationServiceTest.kt` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/adoptu/test/

- `SterilizationLocationRepositoryIT.kt` — SterilizationLocationRepositoryIT: setup, cleanup (~2074 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/kotlin/com/hash_net/beelinecrypto/

- `CryptoServiceTest.kt` — CryptoServiceTest: setup (~1358 tok)

## .claude/worktrees/fix-multimodule-migration/backend/src/test/resources/

- `application.conf` (~192 tok)
- `logback-test.xml` (~163 tok)

## .claude/worktrees/fix-multimodule-migration/frontend/

- `build.gradle.kts` — Gradle Kotlin build configuration (~237 tok)

## .claude/worktrees/fix-multimodule-migration/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` — Error: RequestInit, request, me, logout + 36 more (~2664 tok)
- `Common.kt` — fetch, json, text, log, error (~1169 tok)
- `Crypto.kt` — importKey, encrypt, atob, btoa, pemToBase64 (~828 tok)
- `FetchUtils.kt` — Data class: FetchResult (3 properties) (~404 tok)
- `I18n.kt` — loadLang, t, setLang, loadLang, updatePage (~1389 tok)
- `Main.kt` — main (~490 tok)
- `WebAuthn.kt` — encodeURIComponent, create, get, toJSON, parseCreationOptionsFromJSON (~1697 tok)

## .claude/worktrees/fix-multimodule-migration/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `AllPages.kt` — Data class: FoodItem (8 properties) (~14798 tok)
- `IndexPage.kt` — init, loadPets, getPrimaryImage (~1156 tok)
- `LoginPage.kt` — init, loginWithPasskey, resendVerification, sendMagicLink, loginWithPassword (~3865 tok)
- `MyPetsPage.kt` — init, loadPets, renderPets, getPrimaryImage, loadAdoptionRequests (~5785 tok)
- `ProfilePage.kt` — init, loadProfile, isProfileComplete, setupCompletionGuard, setupRoleToggles (~7082 tok)
- `RegisterPage.kt` — init, validatePassword, handleRegister (~1531 tok)

## .claude/worktrees/fix-multimodule-migration/frontend/src/tests/

- `example.spec.ts` (~0 tok)

## .claude/worktrees/fix-multimodule-migration/gradle/wrapper/

- `gradle-wrapper.jar` (~11592 tok)
- `gradle-wrapper.properties` (~68 tok)

## .claude/worktrees/fix-multimodule-migration/scripts/

- `start-local.sh` (~175 tok)
- `truncate-db.sh` (~436 tok)

## .claude/worktrees/fix-multimodule-migration/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` — Error: fetch, text, json, request + 40 more (~2798 tok)
- `Common.kt` — fetch, json, text, Promise, Promise (~1186 tok)
- `Crypto.kt` — importKey, encrypt, atob, btoa, pemToBase64 (~1057 tok)
- `FetchUtils.kt` — Data class: FetchResult (3 properties) (~557 tok)
- `I18n.kt` — loadLang, t, setLang, loadLang, updatePage (~1364 tok)
- `Main.kt` — fetch, json, text, stringify, parse (~762 tok)
- `WebAuthn.kt` — create, get, toJSON, parseCreationOptionsFromJSON, parseRequestOptionsFromJSON (~1819 tok)

## .claude/worktrees/fix-multimodule-migration/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `AllPages.kt` — Data class: FoodItem (8 properties) (~14216 tok)
- `IndexPage.kt` — init, loadPets, getPrimaryImage (~1156 tok)
- `LoginPage.kt` — init, loginWithPasskey, resendVerification, sendMagicLink, loginWithPassword (~3865 tok)
- `MyPetsPage.kt` — init, loadPets, renderPets, getPrimaryImage, loadAdoptionRequests (~5785 tok)
- `ProfilePage.kt` — init, loadProfile, isProfileComplete, setupCompletionGuard, setupRoleToggles (~7082 tok)
- `RegisterPage.kt` — init, validatePassword, handleRegister (~1525 tok)

## .claude/worktrees/fix-nopets-i18n/.wolf/

- `cerebrum.md` — Cerebrum (~2051 tok)

## .claude/worktrees/fix-nopets-i18n/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~17715 tok)

## .claude/worktrees/fix-photographers-i18n/.wolf/

- `cerebrum.md` — Cerebrum (~9950 tok)

## .claude/worktrees/fix-photographers-i18n/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~20376 tok)

## .claude/worktrees/fix-sterilization-js-syntax/backend/src/main/resources/static/js/

- `sterilization-locations.js` — escapeHtml: loadCountries, loadLocations, showForm + 3 more (~1318 tok)
- `sterilization.js` — country: executedFunction (~957 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/.wolf/

- `cerebrum.md` — Cerebrum (~10713 tok)
- `memory.md` — Memory (~30223 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/backend/src/main/kotlin/com/adoptu/pages/

- `TemporalHomePage.kt` — HTML, HTML, HTML (~639 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/backend/src/main/kotlin/com/adoptu/routes/

- `TemporalHomeRoutes.kt` — Route (~2579 tok)
- `UIRoutes.kt` — Data class: NavParams (~2523 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/backend/src/main/scss/

- `temporal-home.scss` — Styles: 12 rules (~627 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/backend/src/test/kotlin/com/adoptu/routes/

- `TemporalHomeRoutesE2ETest.kt` — E2E tests for [temporalHomeRoutes]. (~7352 tok)
- `UIRoutesE2ETest.kt` — End-to-end tests for [uiRoutes]: mounts the real route tree in a Ktor (~6074 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` — apiFetch, me, logout, getPets, getPet (~2447 tok)
- `I18n.kt` (~20914 tok)
- `Main.kt` — main (~1138 tok)

## .claude/worktrees/fix-temporal-home-view-details-v2/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `TemporalHomePage.kt` — init, search, displayResults, init, render (~2408 tok)

## .claude/worktrees/fix-temporal-home-view-details/backend/src/main/kotlin/com/adoptu/pages/

- `TemporalHomePage.kt` — HTML, HTML, HTML (~639 tok)

## .claude/worktrees/fix-temporal-home-view-details/backend/src/main/kotlin/com/adoptu/routes/

- `TemporalHomeRoutes.kt` — Route (~2579 tok)
- `UIRoutes.kt` — Data class: NavParams (~2523 tok)

## .claude/worktrees/fix-test-task/

- `build.gradle.kts` (~137 tok)

## .claude/worktrees/fix-wolf-merge-conflicts/

- `.gitattributes` — OpenWolf metadata logs are appended to independently by every parallel (~70 tok)

## .claude/worktrees/floating-crafting-charm/

- `.dockerignore` — Docker ignore rules (~0 tok)
- `.gitignore` — Git ignore rules (~122 tok)
- `AGENTS.md` — Adopt-U - Agent Guidelines (~1073 tok)
- `build.gradle.kts` — Gradle Kotlin build configuration (~0 tok)
- `buildspec.yml` (~353 tok)
- `docker-compose.test.yml` — Docker Compose: 0 services (~192 tok)
- `Dockerfile` — Docker container definition (~314 tok)
- `gradle.properties` (~149 tok)
- `gradlew` — you may not use this file except in compliance with the License. (~2292 tok)
- `gradlew.bat` (~748 tok)
- `README.md` — Project documentation (~716 tok)
- `settings.gradle.kts` — Gradle Kotlin settings (~0 tok)
- `start-local.sh` (~175 tok)

## .claude/worktrees/floating-crafting-charm/.aiassistant/rules/

- `main.md` (~0 tok)

## .claude/worktrees/floating-crafting-charm/gradle/wrapper/

- `gradle-wrapper.properties` (~68 tok)

## .claude/worktrees/floating-crafting-charm/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` (~0 tok)
- `Common.kt` (~0 tok)
- `Crypto.kt` (~0 tok)
- `FetchUtils.kt` (~0 tok)
- `I18n.kt` (~0 tok)
- `Main.kt` (~0 tok)
- `WebAuthn.kt` (~0 tok)

## .claude/worktrees/floating-crafting-charm/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `AllPages.kt` (~0 tok)
- `IndexPage.kt` (~0 tok)
- `LoginPage.kt` (~0 tok)
- `MyPetsPage.kt` (~0 tok)
- `ProfilePage.kt` (~0 tok)
- `RegisterPage.kt` (~0 tok)

## .claude/worktrees/foamy-discovering-papert/backend/src/main/kotlin/com/adoptu/pages/

- `Shared.kt` — HTML, A, BODY, DIV, NAV (~3184 tok)

## .claude/worktrees/graalvm-native-image/

- `settings.gradle.kts` (~105 tok)

## .claude/worktrees/graalvm-native-image/.wolf/

- `anatomy.md` — anatomy.md (~21873 tok)
- `buglog.json` — Declares putPromise (~13876 tok)
- `cerebrum.md` — Cerebrum (~14431 tok)
- `memory.md` — Memory (~33272 tok)

## .claude/worktrees/graalvm-native-image/backend/

- `build.gradle.kts` (~2709 tok)

## .claude/worktrees/graalvm-native-image/backend/src/main/kotlin/com/adoptu/

- `ApplicationNative.kt` — main (~148 tok)

## .claude/worktrees/graalvm-native-image/backend/src/main/resources/META-INF/native-image/com.adoptu/adoptu-backend/

- `native-image.properties` (~368 tok)

## .claude/worktrees/graalvm-native-image/scripts/

- `build-native-image.sh` — Builds the GraalVM native-image executable for the backend using the (~273 tok)

## .claude/worktrees/graalvm-native-image/scripts/benchmark-results/

- `20260703T211336Z_graalvm-native-home-retest.txt` (~410 tok)
- `20260703T211336Z_graalvm-native-pets-retest.txt` (~378 tok)

## .claude/worktrees/helidon-nima-migration/.wolf/

- `anatomy.md` — anatomy.md (~22897 tok)
- `buglog.json` — Declares putPromise (~6694 tok)
- `cerebrum.md` — Cerebrum (~15780 tok)

## .claude/worktrees/helidon-nima-migration/backend/

- `build.gradle.kts` (~2220 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/

- `Application.kt` — main, configureRouting (~770 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/adapters/db/

- `DatabaseFactory.kt` — init, createDefaultAdmin (~1424 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/adapters/notification/

- `SesEmailAdapter.kt` — SesEmailAdapter: sendEmail, sendEmailViaSmtp, sendEmailViaSes, sendPhotographerRequest (~2156 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/config/

- `AppConfig.kt` — Drop-in replacement for Ktor's `io.ktor.server.config.ApplicationConfig` (same (~341 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/di/

- `AppModule.kt` — appModule, getOrigins, createImageStorageAdapter (~1109 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/routes/

- `AuthRoutes.kt` — Data class: EncryptedLoginRequest (~6722 tok)
- `PetsRoutes.kt` — HttpRules (~3723 tok)
- `PhotographerRoutes.kt` — HttpRules, validateUser (~2267 tok)
- `ShelterRoutes.kt` — HttpRules, HttpRules (~1219 tok)
- `SterilizationLocationRoutes.kt` — HttpRules, HttpRules (~1338 tok)
- `TemporalHomeRoutes.kt` — HttpRules (~2688 tok)
- `UIRoutes.kt` — Data class: NavParams (~2378 tok)
- `UserShelterRoutes.kt` — HttpRules (~728 tok)
- `UsersRoutes.kt` — Data class: UpdateProfileRequest (~3552 tok)
- `UserSterilizationLocationRoutes.kt` — HttpRules (~782 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/main/kotlin/com/adoptu/web/

- `AccessLogFilter.kt` — Replaces Ktor's `install(CallLogging) { ... }` (plugins/Logging.kt). (~261 tok)
- `Deps.kt` — Replaces Ktor's `org.koin.ktor.ext.inject` (which resolved via the Application's attached (~122 tok)
- `Html.kt` — Replaces Ktor's `call.respondHtml { ... }` (ktor-server-html-builder). (~157 tok)
- `JsonSupport.kt` — Jackson's DefaultPrettyPrinter differs from kotlinx.serialization's prettyPrint - what every (~650 tok)
- `RequestExtensions.kt` — Path template parameter, e.g. `req.pathParam("id")` for a route registered as `"/pets/{id}"`. (~673 tok)
- `Responses.kt` — Sends [result].data as JSON on success, or the matching error status otherwise. (~516 tok)
- `Sessions.kt` — Replaces Ktor's `install(Sessions) { cookie<SessionUser>(...) }` (plugins/Sessions.kt). (~756 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/

- `ApplicationContainerTest.kt` — ApplicationContainerTest: testAppConfig (~818 tok)
- `ApplicationIntegrationTest.kt` — ApplicationIntegrationTest: createTestConfig, initDatabase, setUpAll, setUp (~3018 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/adapters/db/

- `DatabaseFactoryInitIT.kt` — DatabaseFactoryInitIT: startContainer, stopContainer, createConfig (~1983 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/e2e/

- `BaseE2ETest.kt` — BaseE2ETest: getBaseUrl, setupServer, startTestServer, setupBrowser (~865 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/plugins/

- `RoutingTest.kt` — Exercises the real production route wiring in configureRouting() -- every other E2E test (~435 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/routes/

- `ApplicationTestcontainersIT.kt` — ApplicationTestcontainersIT: createConfigOverrides, initDatabase, startTestServer, setUp (~2374 tok)
- `AuthRoutesE2ETest.kt` — Covers endpoints/branches in AuthRoutes.kt not already exercised by (~13460 tok)
- `EmailVerificationRoutesE2ETest.kt` — EmailVerificationRoutesE2ETest: setup, testModules, startTestServer, createVerifiedUser (~1699 tok)
- `PasswordRegistrationRoutesE2ETest.kt` — PasswordRegistrationRoutesE2ETest: setup, testModules, startServer, encryptPassword (~2804 tok)
- `PetsRoutesE2ETest.kt` — PetsRoutesE2ETest: setup, createTestUsers, startTestServer, generateTestImageBytes (~16362 tok)
- `PhotographerRoutesE2ETest.kt` — E2E tests for [photographerRoutes]. (~7394 tok)
- `ShelterRoutesE2ETest.kt` — ShelterRoutesE2ETest: setup, createShelterInDb (~3971 tok)
- `SheltersRoutesE2ETest.kt` — Ported from the old Ktor test-application + Testcontainers (Postgres/LocalStack) harness to (~4276 tok)
- `SterilizationLocationRoutesE2ETest.kt` — SterilizationLocationRoutesE2ETest: setup, testModules, startServer, createLocationInDb (~4742 tok)
- `TemporalHomeRoutesE2ETest.kt` — E2E tests for [temporalHomeRoutes]. (~6909 tok)
- `UIRoutesE2ETest.kt` — End-to-end tests for [uiRoutes]: starts a real Helidon Nima [TestServer] mounting the full (~6247 tok)
- `UserShelterRoutesE2ETest.kt` — UserShelterRoutesE2ETest: setup, createTestUsers, startServer, createShelterInDb (~3676 tok)
- `UsersRoutesE2ETest.kt` — Verbatim port of the old Ktor test's inline `module { ... }` block of mocked adapters. (~10985 tok)
- `UserSterilizationLocationRoutesE2ETest.kt` — UserSterilizationLocationRoutesE2ETest: setup, createTestUsers, createLocationInDb (~4221 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/services/auth/

- `SessionUserTest.kt` — Data class: SessionUserTest (~510 tok)

## .claude/worktrees/helidon-nima-migration/backend/src/test/kotlin/com/adoptu/testsupport/

- `TestHttp.kt` — Thin java.net.http.HttpClient helpers replacing Ktor's test HttpClient, for use against a (~1172 tok)
- `TestServer.kt` — Replaces Ktor's `testApplication { ... }` / `embeddedServer(Netty, ...)` test bootstrapping. (~1334 tok)

## .claude/worktrees/idempotent-juggling-orbit/backend/src/main/kotlin/com/adoptu/pages/

- `LocationSearchFilters.kt` — DIV (~699 tok)

## .claude/worktrees/imperative-roaming-starfish/backend/

- `build.gradle.kts` (~1948 tok)

## .claude/worktrees/imperative-roaming-starfish/backend/src/main/kotlin/com/adoptu/

- `Application.kt` — main, Application (~295 tok)

## .claude/worktrees/imperative-roaming-starfish/backend/src/main/kotlin/com/adoptu/plugins/

- `Logging.kt` — Application (~217 tok)

## .claude/worktrees/imperative-roaming-starfish/backend/src/main/kotlin/com/adoptu/routes/

- `AuthRoutes.kt` — Data class: EncryptedLoginRequest (~6912 tok)

## .claude/worktrees/imperative-roaming-starfish/backend/src/main/kotlin/com/adoptu/services/

- `MagicLinkService.kt` — Data class: MagicLinkService (~2660 tok)

## .claude/worktrees/imperative-roaming-starfish/backend/src/main/resources/

- `logback.xml` (~230 tok)

## .claude/worktrees/implement-perf-lessons/

- `.gitignore` — Git ignore rules (~200 tok)

## .claude/worktrees/implement-perf-lessons/.wolf/

- `anatomy.md` — anatomy.md (~18890 tok)
- `buglog.json` (~1317 tok)
- `cerebrum.md` — Cerebrum (~11509 tok)
- `memory.md` — Memory (~27107 tok)

## .claude/worktrees/implement-perf-lessons/backend/

- `build.gradle.kts` (~2384 tok)

## .claude/worktrees/implement-perf-lessons/backend/src/main/kotlin/com/adoptu/adapters/db/

- `DatabaseFactory.kt` — init, createDefaultAdmin (~1427 tok)
- `DbDispatcher.kt` — Shared, bounded dispatcher for repository DB calls. (~705 tok)
- `PoolSizing.kt` — Single source of truth for how many DB-bound worker threads/connections this (~365 tok)

## .claude/worktrees/implement-perf-lessons/backend/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` — PetRepositoryImpl: buildPetDto, rowToPetDto, getPetImages, getImagesForPetIds (~4766 tok)

## .claude/worktrees/implement-perf-lessons/backend/src/main/kotlin/com/adoptu/routes/

- `PetsRoutes.kt` — Route (~3731 tok)

## .claude/worktrees/implement-perf-lessons/backend/src/main/kotlin/com/adoptu/services/

- `EmailChangeService.kt` — EmailChangeService: requestEmailChange, verifyEmailChange, generateToken, getLocalizedEmailChangeCon (~1867 tok)
- `MagicLinkService.kt` — Data class: MagicLinkService (~2743 tok)
- `PasswordService.kt` — PasswordService: hasPassword, extractPassword, setPassword, changePassword (~3122 tok)

## .claude/worktrees/implement-perf-lessons/backend/src/main/kotlin/com/adoptu/services/auth/

- `WebAuthnService.kt` — Data class: WebAuthnService (~5649 tok)

## .claude/worktrees/implement-perf-lessons/backend/src/test/kotlin/com/adoptu/services/

- `MagicLinkServiceTest.kt` — MagicLinkServiceTest: setup, createTestUser, createMagicLinkToken, createExpiredMagicLinkToken (~3125 tok)
- `PasswordServiceTest.kt` — PasswordServiceTest: setup, encryptPassword, createTestUser, createPasswordResetToken (~3208 tok)
- `WebAuthnServiceTest.kt` — WebAuthnServiceTest: setup, userService, encryptPassword, createTestUser (~2952 tok)

## .claude/worktrees/implement-perf-lessons/scripts/

- `benchmark.sh` — Load-tests the backend under the SAME resource ceiling as production (ECS Fargate), (~1530 tok)

## .claude/worktrees/layout-fix/backend/src/main/resources/static/css/

- `style.css` — Styles: 91 rules, 8 vars (~5932 tok)

## .claude/worktrees/layout-fix/backend/src/main/scss/

- `_layout.scss` — Styles: 11 rules (~1915 tok)

## .claude/worktrees/look-and-feel/

- `.gitignore` — Git ignore rules (~202 tok)

## .claude/worktrees/look-and-feel/.wolf/

- `buglog.json` — Declares lives (~20296 tok)

## .claude/worktrees/look-and-feel/backend/src/main/scss/

- `_layout.scss` — Styles: 13 rules (~2018 tok)
- `style.scss` — Styles: 45 rules (~2524 tok)

## .claude/worktrees/memoized-herding-badger/.wolf/

- `cerebrum.md` — Cerebrum (~849 tok)

## .claude/worktrees/memoized-herding-badger/backend/src/main/kotlin/com/adoptu/services/crypto/

- `CryptoService.kt` — getOaepParameterSpec, initialize, generateKeyPair, getPublicKey, encrypt (~1031 tok)

## .claude/worktrees/migrate-legacy-js-to-kotlin/backend/src/main/kotlin/com/adoptu/dto/input/

- `PetDto.kt` — Data class: Gender (~1027 tok)

## .claude/worktrees/migrate-legacy-js-to-kotlin/backend/src/main/kotlin/com/adoptu/pages/

- `AdminPage.kt` — HTML (~797 tok)
- `AdminSheltersPage.kt` — HTML (~1719 tok)
- `ForgotPasswordPage.kt` — HTML, HTML, HTML, HTML (~994 tok)
- `IndexPage.kt` — HTML (~632 tok)
- `MyPetsPage.kt` — HTML (~2296 tok)
- `PetDetailPage.kt` — HTML (~165 tok)
- `PetFoodPage.kt` — Data class: FoodCategory (~2063 tok)
- `PetsPage.kt` — HTML (~632 tok)
- `PhotographersPage.kt` — HTML (~346 tok)
- `Shared.kt` — HTML, A, BODY, DIV, NAV (~2005 tok)
- `SheltersPage.kt` — HTML (~367 tok)
- `SterilizationLocationsPage.kt` — HTML, HTML (~1559 tok)
- `TemporalHomePage.kt` — HTML, HTML (~486 tok)

## .claude/worktrees/migrate-legacy-js-to-kotlin/backend/src/test/kotlin/com/adoptu/pages/

- `SharedPageElementsTest.kt` — Covers small page-builder helpers that aren't reached through any HTTP route in production (~519 tok)

## .claude/worktrees/migrate-legacy-js-to-kotlin/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` — apiFetch, me, logout, getPets, getPet (~2346 tok)
- `Common.kt` — NodeList, onCountryChange, initI18n, initDropdowns, checkProfileCompletion (~1110 tok)
- `Main.kt` — main (~1096 tok)

## .claude/worktrees/migrate-legacy-js-to-kotlin/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `AdminPage.kt` — init, switchTab, loadUsers, renderUsers, showBanModal (~1801 tok)
- `AdminSheltersPage.kt` — init, loadShelters, renderShelters, showMessage, getFormData (~2981 tok)
- `AuthFlowPages.kt` — init, submit, init, tokenFromUrl, submit (~2010 tok)
- `IndexPage.kt` — init, loadPets, updateCountryHint, showEmptyState, hideEmptyState (~2240 tok)
- `MyPetsPage.kt` — init, clampNonNegative, load, onPetsLoaded, renderPetCard (~5802 tok)
- `PetDetailPage.kt` — init, render, renderImages (~2131 tok)
- `PetFoodPage.kt` — Data class: FoodItem (~1890 tok)
- `PhotographersPage.kt` — init, search, load, render, createRequestModal (~1680 tok)
- `SheltersPage.kt` — init, search, renderShelters (~1266 tok)
- `SterilizationLocationsPage.kt` — init, search, render, locationCard, init (~2608 tok)
- `TemporalHomePage.kt` — init, search, displayResults, init, loadRequests (~1318 tok)

## .claude/worktrees/migrate-legacy-js-to-kotlin/frontend/src/tests/

- `e2e-verify.spec.ts` — Adoptu — End-to-End Verification Suite (~9262 tok)

## .claude/worktrees/mutable-enchanting-moler/scripts/

- `test_data.sql` — ============================================================================= (~22562 tok)

## .claude/worktrees/pets-listing-ux/backend/src/main/kotlin/com/adoptu/pages/

- `IndexPage.kt` — HTML (~726 tok)
- `PetsPage.kt` — HTML (~726 tok)

## .claude/worktrees/pets-listing-ux/backend/src/main/resources/static/js/

- `index.js` — emoji: updateCountryHint, showEmptyState, hideEmptyState + 6 more (~1841 tok)

## .claude/worktrees/pets-listing-ux/backend/src/main/scss/

- `_location-search-form.scss` — Styles: 2 rules (~587 tok)
- `style.scss` — Styles: 48 rules (~2756 tok)

## .claude/worktrees/pets-listing-ux/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~20221 tok)

## .claude/worktrees/profile-completion-guard/

- `.dockerignore` — Docker ignore rules (~0 tok)
- `.gitignore` — Git ignore rules (~122 tok)
- `AGENTS.md` (~0 tok)
- `build.gradle.kts` — Gradle Kotlin build configuration (~0 tok)
- `buildspec.yml` (~353 tok)
- `docker-compose.test.yml` — Docker Compose: 0 services (~192 tok)
- `Dockerfile` — Docker container definition (~314 tok)
- `gradle.properties` (~149 tok)
- `gradlew` — you may not use this file except in compliance with the License. (~2292 tok)
- `gradlew.bat` (~748 tok)
- `README.md` — Project documentation (~716 tok)
- `settings.gradle.kts` — Gradle Kotlin settings (~0 tok)
- `start-local.sh` (~263 tok)

## .claude/worktrees/profile-completion-guard/.aiassistant/rules/

- `main.md` (~0 tok)

## .claude/worktrees/profile-completion-guard/gradle/wrapper/

- `gradle-wrapper.properties` (~68 tok)

## .claude/worktrees/profile-completion-guard/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` (~0 tok)
- `Common.kt` (~0 tok)
- `Crypto.kt` (~0 tok)
- `FetchUtils.kt` (~0 tok)
- `I18n.kt` (~0 tok)
- `Main.kt` (~0 tok)
- `WebAuthn.kt` (~0 tok)

## .claude/worktrees/profile-completion-guard/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `AllPages.kt` (~0 tok)
- `IndexPage.kt` (~0 tok)
- `LoginPage.kt` — init, loginWithPasskey, resendVerification, sendMagicLink, loginWithPassword (~3048 tok)
- `MyPetsPage.kt` (~0 tok)
- `ProfilePage.kt` — init, loadProfile, isProfileComplete, setupCompletionGuard, setupRoleToggles (~7078 tok)
- `RegisterPage.kt` (~0 tok)

## .claude/worktrees/quiet-booping-hare/scripts/

- `load_test_data.sh` (~122 tok)
- `test_data.sql` — ============================================================================= (~22381 tok)

## .claude/worktrees/quizzical-swimming-clarke/backend/src/main/kotlin/com/adoptu/services/

- `UserShelterService.kt` — UserShelterService: getByUserId, create, update, delete (~689 tok)
- `UserSterilizationLocationService.kt` — UserSterilizationLocationService: getByUserId, create, update, delete (~669 tok)

## .claude/worktrees/quizzical-swimming-clarke/frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `ProfilePage.kt` — init, loadProfile, updateProfileUI, checkProfileExists, setupRoleToggles (~9591 tok)

## .claude/worktrees/replicated-yawning-lantern/.wolf/

- `buglog.json` (~4796 tok)
- `cerebrum.md` — Cerebrum (~1097 tok)

## .claude/worktrees/replicated-yawning-lantern/backend/src/main/resources/static/js/

- `my-pets.js` — API routes: GET (2 endpoints) (~4164 tok)

## .claude/worktrees/replicated-yawning-lantern/backend/src/main/scss/

- `style.scss` — Styles: 44 rules (~2338 tok)

## .claude/worktrees/rosy-tumbling-plum/.wolf/

- `buglog.json` (~4791 tok)
- `cerebrum.md` — Cerebrum (~1045 tok)

## .claude/worktrees/rosy-tumbling-plum/backend/src/main/scss/

- `_location-search-form.scss` — Styles: 2 rules (~540 tok)
- `style.scss` — Styles: 43 rules (~2496 tok)

## .claude/worktrees/scalable-nibbling-hopper/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~18336 tok)

## .claude/worktrees/serialized-wandering-phoenix/.wolf/

- `cerebrum.md` — Cerebrum (~870 tok)

## .claude/worktrees/serialized-wandering-phoenix/backend/src/main/kotlin/com/adoptu/pages/

- `SheltersPage.kt` — HTML (~381 tok)

## .claude/worktrees/serialized-wandering-phoenix/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~17728 tok)

## .claude/worktrees/sparkling-snuggling-pixel/.wolf/

- `buglog.json` (~5098 tok)

## .claude/worktrees/sparkling-snuggling-pixel/backend/src/main/resources/static/js/

- `photographers.js` — t: loadPhotographers, createRequestModal, showRequestModal, executedFunction (~2098 tok)

## .claude/worktrees/sprightly-splashing-blum/.wolf/

- `buglog.json` — Declares lives (~18561 tok)
- `memory.md` — Memory (~22642 tok)

## .claude/worktrees/sprightly-splashing-blum/backend/src/main/kotlin/com/adoptu/pages/

- `Shared.kt` — HTML, A, BODY, DIV, NAV (~2016 tok)

## .claude/worktrees/sprightly-splashing-blum/backend/src/main/resources/static/js/

- `admin-shelters.js` — escapeHtml: loadShelters, showMessage, getFormData + 4 more (~2284 tok)
- `shelters.js` — errorDiv: executedFunction (~1290 tok)
- `sterilization-locations.js` — escapeHtml: loadCountries, loadLocations, showForm + 3 more (~1327 tok)
- `sterilization.js` — params: executedFunction (~797 tok)
- `temporal-home-search.js` — params: executedFunction (~504 tok)

## .claude/worktrees/sprightly-splashing-blum/backend/src/main/resources/static/js/i18n/

- `i18n.js` — i18n: setLang, t (~1017 tok)

## .claude/worktrees/sprightly-splashing-blum/frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `I18n.kt` (~19749 tok)

## .claude/worktrees/swift-orbiting-crab/

- `.dockerignore` — Docker ignore rules (~0 tok)
- `.gitignore` — Git ignore rules (~114 tok)
- `build.gradle.kts` — Gradle Kotlin build configuration (~0 tok)
- `buildspec.yml` (~353 tok)
- `cookies.txt` (~0 tok)
- `Dockerfile` — Docker container definition (~0 tok)
- `gradlew` (~0 tok)
- `gradlew.bat` (~0 tok)
- `README.md` — Project documentation (~0 tok)
- `settings.gradle.kts` — Gradle Kotlin settings (~0 tok)
- `start-local.sh` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/db/

- `run_seed.sh` — run_seed.sh – Load resource seed data into the adoptu PostgreSQL database. (~667 tok)
- `seed_resources.sql` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/gradle/wrapper/

- `gradle-wrapper.properties` (~68 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/

- `Application.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/adapters/db/

- `DatabaseFactory.kt` (~0 tok)
- `Models.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` (~0 tok)
- `PhotographerRepository.kt` (~0 tok)
- `ShelterRepository.kt` (~0 tok)
- `SterilizationLocationRepository.kt` (~0 tok)
- `TemporalHomeRepository.kt` (~0 tok)
- `UserRepository.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/adapters/notification/

- `SesEmailAdapter.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/adapters/storage/

- `S3ImageStorageAdapter.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/di/

- `AppModule.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/dto/input/

- `AdoptionRequestDto.kt` (~0 tok)
- `AuthDto.kt` (~0 tok)
- `PetDto.kt` (~0 tok)
- `ShelterDto.kt` (~0 tok)
- `SterilizationLocationDto.kt` (~0 tok)
- `UserDto.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/dto/output/

- `AuthResponses.kt` (~0 tok)

## .claude/worktrees/swift-orbiting-crab/src/main/kotlin/com/adoptu/pages/

- `AdminPage.kt` (~0 tok)
- `AdminSheltersPage.kt` (~0 tok)
- `EmailVerificationPage.kt` (~0 tok)
- `IndexPage.kt` (~0 tok)
- `LoginPage.kt` (~0 tok)
- `MyPetsPage.kt` (~0 tok)
- `PetDetailPage.kt` (~0 tok)
- `PetsPage.kt` (~0 tok)
- `PhotographersPage.kt` (~0 tok)
- `PrivacyPage.kt` (~0 tok)
- `ProfilePage.kt` (~0 tok)
- `RegisterPage.kt` (~0 tok)
- `Shared.kt` (~0 tok)

## .claude/worktrees/swift-wiggling-conway/.wolf/

- `buglog.json` (~1446 tok)

## .claude/worktrees/swift-wiggling-conway/backend/src/main/resources/static/js/

- `login.js` — updateLangButton: checkProfileCompletion, showResendButton (~2440 tok)
- `profile.js` — load: loadPhotographer, loadTemporalHome, loadShelter, loadSterilization (~9709 tok)
- `register.js` — Declares validatePassword (~1255 tok)
- `webauthn.js` — Declares webauthn (~1323 tok)

## .claude/worktrees/swift-wiggling-conway/backend/src/main/resources/static/js/i18n/

- `i18n-en.js` (~6774 tok)
- `i18n-es.js` (~7289 tok)
- `i18n-fr.js` (~7386 tok)
- `i18n-pt.js` (~7082 tok)
- `i18n-zh.js` (~4810 tok)

## .claude/worktrees/test-coverage-95/

- `build.gradle.kts` (~154 tok)

## .claude/worktrees/test-coverage-95/.wolf/

- `anatomy.md` — anatomy.md (~11957 tok)
- `cerebrum.md` — Cerebrum (~3476 tok)
- `memory.md` — Memory (~8610 tok)

## .claude/worktrees/test-coverage-95/backend/

- `build.gradle.kts` (~2088 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/adapters/dynamodb/

- `DynamoDBAdapterTest.kt` — DynamoDBAdapterTest: setup (~13642 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/adapters/storage/

- `ImageStorageIT.kt` — Integration test for [S3ImageStorageAdapter] backed by a real LocalStack S3 service. (~822 tok)
- `S3ImageStorageAdapterTest.kt` — Declares S3ImageStorageAdapterTest (~385 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/mocks/

- `TestDatabase.kt` — initH2, clearAllData (~931 tok)
- `TestDatabase.kt` — Modified initH2()/clearAllData() to register UserShelters and UserSterilizationLocations tables (previously missing from schema create/drop lists, which would have made any test touching those tables fail with "table not found") (~900 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/routes/

- `PhotographerRoutesE2ETest.kt` — New testApplication E2E test for `photographerRoutes()`: GET /api/photographers (+ country/state filters), POST /profile (activate/deactivate, 401/404), PUT /settings (401/404/403/400 negative fee/200, incl. ADMIN-bypasses-role-check-but-404s-without-PHOTOGRAPHER-active-role edge case), POST /requests, POST /requests/multiple (400 empty/>3/rate-limited, 200), GET /requests (401/404/200 for both photographer and requester branches), PUT /requests/{id} (401/400 bad id/404/403 unrelated user/400 bad status transition/200). Session via test-only `/test-login` route + `call.sessions.set(SessionUser(...))`, cookie captured from the real signed Set-Cookie header. 32 tests. Discovered and documented (not fixed) a pre-existing kotlinx-serialization bug: several PhotographerService methods return `Map<String, Any?>` with heterogeneous per-key value types, which Ktor's `guessSerializer()` fallback cannot serialize (throws `IllegalStateException`, surfaces as 500) — see bug-052 in buglog.json. Those specific success-path assertions tolerate 200 or 500 (~5400 tok)
- `ShelterRoutesE2ETest.kt` — Ktor routing (~3585 tok)
- `ShelterRoutesE2ETest.kt` — New testApplication E2E test for `shelterRoutes()` (public GET list/countries/states/{id}) and `adminShelterRoutes()` (admin CRUD at /api/admin/shelters, incl. the empty-list-when-country-missing quirk on GET /api/admin/shelters). Deliberately separate from the pre-existing Testcontainers-based `SheltersRoutesE2ETest.kt` (plural, excluded from both `test` and `integrationTest` Gradle tasks) which was left untouched. 22 tests (~3500 tok)
- `SterilizationLocationRoutesE2ETest.kt` — Ktor routing (~4224 tok)
- `SterilizationLocationRoutesE2ETest.kt` — New testApplication E2E test for `sterilizationLocationRoutes()` (public GET list/grouped/countries/states/cities/{id}) and `adminSterilizationLocationRoutes()` (admin CRUD at /api/admin/sterilization-locations). No session needed — confirmed via plugins/Routing.kt that admin route functions have no auth/role guard at this layer. 24 tests (~3600 tok)
- `TemporalHomeRoutesE2ETest.kt` — New testApplication E2E test for `temporalHomeRoutes()`: POST/GET/PUT /api/users/temporal-home (create incl. already-exists/blank-alias validation, get incl. 404 no-profile, update), GET /api/users/temporal-home/requests (401/404/403 role check/200), GET /api/temporal-homes search (no auth, filters), POST /api/temporal-homes/request (401/404/403 not-rescuer/400 blank message/400 not-found target/400 blocked/200), GET /api/temporal-homes/block/{id} (400 invalid id/missing rescuer param, 200 idempotent block, no auth required), POST /api/temporal-homes/block (401/404/403/200). Same test-login session-cookie pattern as PhotographerRoutesE2ETest. 30 tests. Hits the same heterogeneous-Map serialization issue (bug-052) only on the POST /request success payload (`mapOf("success" to Boolean, "requestId" to Int)`); all other success responses are single-key maps or proper `@Serializable` DTOs so are asserted strictly as 200 (~5100 tok)
- `UIRoutesE2ETest.kt` — End-to-end tests for [uiRoutes]: mounts the real route tree in a Ktor (~5835 tok)
- `UIRoutesE2ETest.kt` — New H2-backed Ktor testApplication E2E suite mounting real `uiRoutes()` and issuing actual HTTP GETs against every route (/, /login, /register, /photographers, /pet-food, /pet/{id}, /pets, /my-pets, /profile, /admin, /admin/shelters, /privacy, /terms, /temporal-home(s), /shelters, /sterilization-locations, /admin/sterilization-locations, /verify, /verify-email, /forgot-password, /reset-password, /magic-link-login, /verify-email-change, /temporal-home/block/{id}), each both unauthenticated and authenticated (admin/rescuer/temporalHome sessions) via a test-only `/__test/login/{id}` route that calls `call.sessions.set(SessionUser(...))`. 37 @Test functions. Targets the 0%-covered `com.adoptu.pages.*` (~1500 instr) + `UIRoutes.kt` gap (~3400 tok)
- `UserShelterRoutesE2ETest.kt` — Ktor routing (~4048 tok)
- `UserShelterRoutesE2ETest.kt` — New testApplication E2E test for `userShelterRoutes()`: POST/GET/PUT/DELETE /api/users/shelter (401 unauth, 400 blank name, 404 not-found, upsert-on-repeat-POST, happy path) + GET /api/user-shelters search (400 missing/blank country, filtered results, empty results). Session established via a test-only `/test/login/{userId}` route that calls `call.sessions.set(SessionUser(...))` and captures the real signed Set-Cookie header (no prior proven session-cookie mechanism existed in this repo's E2E tests for protected routes — UsersRoutesE2ETest/PetsRoutesE2ETest only tested the 401 path). 17 tests (~3200 tok)
- `UserSterilizationLocationRoutesE2ETest.kt` — Ktor routing (~4341 tok)
- `UserSterilizationLocationRoutesE2ETest.kt` — Same pattern as UserShelterRoutesE2ETest but for `userSterilizationLocationRoutes()` / /api/users/sterilization-location / /api/user-sterilization-locations. 17 tests (~3200 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/services/

- `UserShelterServiceTest.kt` — UserShelterServiceTest: setup (~4418 tok)
- `UserShelterServiceTest.kt` — New H2-backed test suite for UserShelterService: getByUserId, create (incl. upsert-on-duplicate), update, delete, search (country/state/city/neighborhood/zip combos), validation errors. 23 tests (~3400 tok)
- `UserSterilizationLocationServiceTest.kt` — UserSterilizationLocationServiceTest: setup (~4323 tok)
- `UserSterilizationLocationServiceTest.kt` — New H2-backed test suite for UserSterilizationLocationService: getByUserId, create (incl. upsert-on-duplicate), update, delete, search (country/state/city/neighborhood/zip combos), validation errors. 23 tests (~3300 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/services/auth/

- `SessionUserTest.kt` — Data class: SessionUserTest (~512 tok)

## .claude/worktrees/test-coverage-95/backend/src/test/kotlin/com/adoptu/services/validation/

- `AuthValidationServiceTest.kt` — AuthValidationServiceTest: setup, tearDown, createTestUser, encrypted (~2100 tok)
- `PetsValidationServiceTest.kt` — PetsValidationServiceTest: setup, tearDown, createTestUser, userDto (~2099 tok)
- `PhotographersValidationServiceTest.kt` — PhotographersValidationServiceTest: setup, tearDown, createTestUser, userDto (~1735 tok)
- `SheltersValidationServiceTest.kt` — SheltersValidationServiceTest: setup (~626 tok)
- `SterilizationLocationsValidationServiceTest.kt` — SterilizationLocationsValidationServiceTest: setup (~638 tok)
- `TemporalHomesValidationServiceTest.kt` — TemporalHomesValidationServiceTest: setup, tearDown, createTestUser, createTemporalHomeRequest (~3805 tok)
- `UsersValidationServiceTest.kt` — UsersValidationServiceTest: setup, tearDown, createTestUser, userDto (~1975 tok)

## .claude/worktrees/update-libs-latest/

- `build.gradle.kts` (~136 tok)
- `package.json` — Node.js package manifest (~198 tok)

## .claude/worktrees/update-libs-latest/.wolf/

- `cerebrum.md` — Cerebrum (~1315 tok)

## .claude/worktrees/update-libs-latest/backend/

- `build.gradle.kts` (~1948 tok)

## .claude/worktrees/update-libs-latest/frontend/

- `build.gradle.kts` (~284 tok)

## .claude/worktrees/update-libs-latest/gradle/wrapper/

- `gradle-wrapper.properties` (~68 tok)

## .claude/worktrees/warm-crafting-canyon/

- `playwright.config.ts` — Read environment variables from file. (~289 tok)

## .claude/worktrees/warm-crafting-canyon/.claude/skills/

- `e2e-verify.md` — E2E Verification Skill (~199 tok)

## .claude/worktrees/warm-crafting-canyon/backend/src/main/kotlin/com/adoptu/dto/input/

- `PetDto.kt` — Data class: Gender (~948 tok)

## .claude/worktrees/warm-crafting-canyon/backend/src/main/kotlin/com/adoptu/pages/

- `LocationSearchFilters.kt` — DIV (~931 tok)
- `Shared.kt` — HTML, A, BODY, DIV, NAV (~3206 tok)
- `SterilizationLocationsPage.kt` — HTML, HTML (~1591 tok)

## .claude/worktrees/warm-crafting-canyon/backend/src/main/kotlin/com/adoptu/services/

- `PasswordService.kt` — PasswordService: hasPassword, extractPassword, setPassword, changePassword (~2986 tok)

## .claude/worktrees/warm-crafting-canyon/backend/src/main/kotlin/com/adoptu/services/crypto/

- `CryptoService.kt` — getOaepParameterSpec, initialize, generateKeyPair, getPublicKey, encrypt (~1031 tok)

## .claude/worktrees/warm-crafting-canyon/backend/src/main/resources/static/js/

- `pet-detail.js` — API routes: GET (1 endpoints) (~1879 tok)
- `photographers.js` — params: loadPhotographers, createRequestModal, showRequestModal, executedFunction (~1676 tok)
- `shelters.js` — errorDiv: executedFunction (~1287 tok)
- `sterilization.js` — params: executedFunction (~801 tok)
- `temporal-home-search.js` — params: executedFunction (~502 tok)

## .claude/worktrees/warm-crafting-canyon/frontend/src/tests/

- `debug-pet-edit.spec.ts` — Declares BASE (~628 tok)
- `e2e-verify.spec.ts` — Adoptu — End-to-End Verification Suite (~9016 tok)

## .claude/worktrees/webauthn-e2e-test/frontend/src/tests/

- `e2e-verify.spec.ts` — Adoptu — End-to-End Verification Suite (~9827 tok)

## .claude/worktrees/wiggly-dazzling-graham/backend/src/main/kotlin/com/adoptu/pages/

- `IndexPage.kt` — HTML (~645 tok)

## .claude/worktrees/woolly-mixing-catmull/

- `local-start.sh` (~163 tok)

## .claude/worktrees/zippy-rolling-cocke/.wolf/

- `cerebrum.md` — Cerebrum (~763 tok)

## .claude/worktrees/zippy-rolling-cocke/backend/src/main/resources/static/css/

- `pet-food.css` — Styles: 97 rules, 8 vars (~3726 tok)
- `photographers.css` — Styles: 91 rules, 8 vars (~3528 tok)
- `policy.css` — Styles: 89 rules, 8 vars (~3442 tok)
- `shelters.css` — Styles: 92 rules, 8 vars (~3521 tok)
- `sterilization.css` — Styles: 94 rules, 8 vars (~3505 tok)
- `temporal-home.css` — Styles: 91 rules, 8 vars (~3581 tok)

## .claude/worktrees/zippy-rolling-cocke/backend/src/main/scss/

- `_layout.scss` — Styles: 11 rules (~1961 tok)

## .idea/

- `workspace.xml` (~7543 tok)

## backend/

- `build.gradle.kts` (~2471 tok)

## backend/ (canonical)

- `build.gradle.kts` — Backend Gradle module: Ktor/Exposed/AWS SDK deps, application plugin, com.gradleup.shadow 9.4.3 (fat jar -> *-all.jar), jvmToolchain(25) (~750 tok)

## backend/src/main/kotlin/com/adoptu/

- `Application.kt` — main, configureRouting (~900 tok)

## backend/src/main/kotlin/com/adoptu/ (graalvm-native-image worktree)

- `ApplicationNative.kt` — GraalVM native-image entry point: same Application.module() as production, Ktor CIO engine instead of Netty (unsupported under native-image) (~150 tok)

## backend/src/main/kotlin/com/adoptu/ (helidon-nima-migration worktree)

- `Application.kt` — `fun main()` (no more Ktor `EngineMain`): loads `AppConfig`, `startKoin`, `DatabaseFactory.init`, `CryptoService.initialize`, then `WebServer.builder().port(...).mediaContext(JsonSupport.mediaContext()).routing(::configureRouting).build().start()`. `internal fun configureRouting(routing: HttpRouting.Builder)` registers the `NotFoundException`→404 and `Throwable`→500 error handlers (order matters — specific before generic, see bug-038), `/static` (legacy `StaticContentService`), `/health`, and all 10 route groups; `internal` visibility so `testsupport/TestServer.kt` can reuse it directly (~400 tok)

## backend/src/main/kotlin/com/adoptu/adapters/db/

- `Models.kt` (~3434 tok)

## backend/src/main/kotlin/com/adoptu/adapters/db/ (canonical)

- `DatabaseFactory.kt` — `init()`: now opens a pooled `HikariDataSource` (was raw unpooled `Database.connect(url,...)`), closes any previous pool first (init can re-run in the same JVM, e.g. per-test in IT suites), sets Postgres prepared-statement caching properties; `createDefaultAdmin` unchanged (~950 tok)
- `DbDispatcher.kt` — Shared `dbDispatcher`, now a dedicated daemon-thread `Executor.asCoroutineDispatcher()` (was `Dispatchers.IO.limitedParallelism(4)`) sized via `PoolSizing.computeSize()`, reused by all repository/service `withContext(dbDispatcher) { transaction {...} }` calls (~300 tok)
- `Models.kt` — Exposed table defs; `country` columns on AnimalShelters, SterilizationLocations, UserShelters, UserSterilizationLocations, TemporalHomes, Photographers now use `enumerationByName("country", 100, Country::class)` instead of free-text varchar (~3300 tok)
- `PoolSizing.kt` — Single source of truth for DB pool/dispatcher size: `cores * 4` floored at 4; used by both DbDispatcher and DatabaseFactory's Hikari `maximumPoolSize` so they stay coupled (~150 tok)

## backend/src/main/kotlin/com/adoptu/adapters/db/repositories/

- `PetRepository.kt` — PetRepositoryImpl: rowToPetDto, getPetImages, getAll, getAllUnfiltered (~4397 tok)
- `UserRepository.kt` — UserRepository: getActiveRolesForUser, getById, getByEmail, getAllUsers (~6264 tok)

## backend/src/main/kotlin/com/adoptu/adapters/db/repositories/ (canonical)


## backend/src/main/kotlin/com/adoptu/common/

- `Country.kt` — Country enum: canonical list of 112 countries (displayName + i18nKey), `fromDisplayName()` resolves exact/accent-insensitive/case-insensitive input to an enum value. Single source of truth for the country dropdown (Shared.kt), validation, and DB storage (~1700 tok)

## backend/src/main/kotlin/com/adoptu/config/ (helidon-nima-migration worktree)

- `AppConfig.kt` — drop-in replacement for Ktor's `ApplicationConfig` (same `propertyOrNull(path)?.getString()`/`property(path).getString()`/`.getList()` shape), backed by `com.typesafe.config.Config` directly. `AppConfig.load()` reads `application.conf` unchanged; `AppConfig.fromMap(Map<String,Any>)` is the test-only equivalent of Ktor's `MapApplicationConfig` (~200 tok)

## backend/src/main/kotlin/com/adoptu/dto/input/

- `PetDto.kt` — Data class: Gender (~1025 tok)
- `UserDto.kt` — Data class: UserRole (~1119 tok)

## backend/src/main/kotlin/com/adoptu/pages/

- `PetsPage.kt` — HTML (~644 tok)
- `ProfilePage.kt` — HTML (~4822 tok)

## backend/src/main/kotlin/com/adoptu/pages/ (canonical)

- `Shared.kt` — `countrySelect()` now renders `<option>`s from `Country.entries` instead of a hardcoded 112-line list (~3000 tok)

## backend/src/main/kotlin/com/adoptu/ports/

- `PetRepositoryPort.kt` — getAll, getAllUnfiltered, getById, create, update (~682 tok)
- `UserRepositoryPort.kt` — getById, getByEmail, getAllUsers, getPhotographers, getRescuers (~614 tok)

## backend/src/main/kotlin/com/adoptu/routes/

- `CountryRoutes.kt` — HttpRules, regionFromLocale (~320 tok)

## backend/src/main/kotlin/com/adoptu/routes/ (ported Ktor Route DSL → Helidon HttpRules/Handler, helidon-nima-migration worktree)

- `PetsRoutes.kt` — `fun HttpRules.petsRoutes()`, flattened from Ktor's nested `route("/api/pets"){}`; multipart image upload via `req.receiveMultipart()`; literal `/mine`/`/my-adoption-requests` deliberately registered before `/{id}` (Helidon matches routes in registration order, unlike Ktor's specificity-first tree — see Key Learnings) (~3615 tok)
- `UsersRoutes.kt` — `fun HttpRules.usersRoutes()`/`adminUsersRoutes()`; local request DTOs (UpdateProfileRequest etc.) now plain data classes, no `@Serializable` (~3500 tok)

## backend/src/main/kotlin/com/adoptu/services/

- `PetService.kt` — PetService: getAll, getMine, getById, create (~3030 tok)
- `UserService.kt` — UserService: getById, getByEmail, getAllUsers, getRescuers (~892 tok)

## backend/src/main/kotlin/com/adoptu/web/ (helidon-nima-migration worktree — replaces the deleted `plugins/` package)

- `AccessLogFilter.kt` — Helidon `Filter` logging `METHOD path → status (ms)`, skipping `/health`/`/static`/`/css`/`/js`, replacing `plugins/Logging.kt`'s `CallLogging` (~150 tok)
- `Deps.kt` — `object Deps : KoinComponent`, giving route files `by Deps.inject<T>()` as a drop-in for Ktor's `org.koin.ktor.ext.inject` (~80 tok)
- `Html.kt` — `ServerResponse.respondHtml { }` rendering a kotlinx.html `HTML` builder to bytes, replacing Ktor's `ktor-server-html-builder` (~100 tok)
- `JsonSupport.kt` — Jackson `ObjectMapper` (registerKotlinModule, ignore-unknown-properties) + a custom `DefaultPrettyPrinter` subclass fixing two Jackson-vs-kotlinx.serialization pretty-print formatting mismatches (empty `[]`/`{}` vs `[ ]`/`{ }`, `": "` vs `" : "` field separator — see bug-036); `mediaContext()` wires Jackson + multipart support into Helidon's `MediaContext` (~250 tok)
- `RequestExtensions.kt` — `ServerRequest` extensions: `pathParam`/`queryParam`/`receiveJson<T>()`/`receiveText()` (defensive against empty/absent body, see bug-037)/`receiveFormParameters()`/`receiveMultipart()` (~300 tok)
- `Responses.kt` — `ServerResponse` extensions: `respondError`/`respondUnauthorized`/`respondForbidden`/`respondNotFound`/`respondInvalidId`/`respondRedirect`/`respondData`/`respondSuccess`, replacing `plugins/Responses.kt` (dropped the confirmed-dead `DataResponder`/`SuccessResponder`/`CustomResponder` classes) (~250 tok)
- `Sessions.kt` — HMAC-SHA256-signed cookie session (`getSession()`/`setSession()`/`clearSession()` on `ServerRequest`/`ServerResponse`), replacing Ktor's `Sessions` plugin; not wire-compatible with old Ktor session cookies (one-time silent logout on cutover, expected) (~350 tok)

## backend/src/main/resources/META-INF/native-image/com.adoptu/adoptu-backend/ (graalvm-native-image worktree)

- `native-image.properties` — resource includes for static/js/css assets + ~30 logback classes requiring --initialize-at-build-time (Netty's own build-time-init classes transitively reach the app's live LoggerContext) (~300 tok)
- `reachability-metadata.json` — agent-traced reflection/resource config from hitting real routes (pages, static assets, password-login/crypto path) against live Postgres with -agentlib:native-image-agent (~2000 tok)

## backend/src/main/resources/static/js/

- `api.js` — Declares api (~2504 tok)
- `index.js` — emoji: updateCountryHint, loadPets, initCountry (~1036 tok)
- `my-pets.js` — API routes: GET (2 endpoints) (~4244 tok)
- `profile.js` — load: loadPhotographer, loadTemporalHome, loadShelter, loadSterilization (~9709 tok)

## backend/src/test/kotlin/com/adoptu/routes/


## backend/src/test/kotlin/com/adoptu/services/

- `PetServiceTest.kt` — PetServiceTest: setup (~7228 tok)

## backend/src/test/kotlin/com/adoptu/testsupport/ (helidon-nima-migration worktree)

- `TestHttp.kt` — `java.net.http.HttpClient`-based replacement for Ktor's test `HttpClient`: `get/delete/post/postJson/postForm/put/putJson/putForm(url, cookie)` returning `HttpResponse<String>` (use `.statusCode()`/`.body()`), `loginAs(baseUrl, userId)` hitting the harness's built-in `/test/login/{userId}` route, and `buildMultipartBody(boundary, fields, files)` for multipart upload tests (~400 tok)
- `TestServer.kt` — replaces Ktor's `testApplication{}`/`embeddedServer(Netty,...)`. `TestServer.start(configOverrides, modules, initDatabase, withTestLogin)` starts a real Helidon `WebServer` on a random port through the same `configureRouting` production path, against H2 (`MODE=PostgreSQL`). Always appends a `single { config }` Koin fallback so route files that eagerly resolve `AppConfig` (namely `authRoutes()`) don't crash when a test supplies its own narrow custom module (see bug-035); cleans up Koin via `stopKoin()` both on `TestServerHandle.stop()` and on any startup failure (~600 tok)

## frontend/src/jsMain/kotlin/com/adoptu/frontend/

- `ApiClient.kt` — apiFetch, me, logout, detectCountry, getPets (~2523 tok)
- `Common.kt` — NodeList, onCountryChange, initI18n, initDropdowns, checkProfileCompletion (~1820 tok)
- `I18n.kt` (~22656 tok)

## frontend/src/jsMain/kotlin/com/adoptu/frontend/pages/

- `IndexPage.kt` — init, loadPets, updateCountryHint, showEmptyState, hideEmptyState (~2198 tok)
- `TemporalHomePage.kt` — init, search, displayResults, init, render (~2461 tok)

## infra/

- `cloudfront.tf` (~2999 tok)

## infra/ (OpenTofu - AWS deployment)

- `_layout.scss` — Header/logo/nav/footer/dropdowns. `.logo` now normal flex flow (was `position:absolute`, causing the image to overflow past the fixed 70px header and the wordmark to drift into the nav-right area on mobile). Added `@media (max-width:640px)` (shrink logo/donate) and `@media (max-width:400px)` (hide wordmark, icon-only) so header items never overlap/overflow on phones (~2000 tok)
- `cloudfront.tf` — 3 distributions: static/dynamic image origins (S3+OAC), app origin is `backend.<domain>` directly (custom origin, `http-only`/8080 — verified to match the live distribution's actual config exactly, NOT an ALB). `backend.<domain>` is internal-only — distinct from the public `api.<domain>` alias, which still goes through this same distribution (~450 tok)
- `data.tf` — read-only lookups: Route53 zone, ACM cert (us-east-1, CloudFront), ECR repo, default VPC, DB subnet group, rds-monitoring-role (~300 tok)
- `dns_updater.tf` — EventBridge rule (`ECS Task State Change`, `lastStatus=RUNNING`, filtered to the `adoptu` cluster) -> Lambda that looks up the new task's IPv6 (ecs:DescribeTasks -> ec2:DescribeNetworkInterfaces) and UPSERTs `backend.<domain>`. Fully automatic — no manual DNS step, ever. Source at `lambda/dns_updater/index.py`, zipped via `data.archive_file` (~550 tok)
- `ecs.tf` — new `adoptu` ECS cluster/service/task def, Fargate, deployed into the IPv6-only subnets; container_port fixed to 8080 (live task def had 80, app actually listens on 8080); no `load_balancer` block (~400 tok)
- `iam.tf` — split execution role (ECR/logs/secrets) vs task role (S3 scoped to real buckets + SES) — fixes the live policy's unscoped `your-bucket-name` placeholder (~400 tok)
- `lambda/dns_updater/index.py` — the Lambda handler itself (~30 lines, boto3, no deps beyond the runtime) (~250 tok)
- `network.tf` — 2 new IPv6-only ECS subnets (ipv6_native, carved from the VPC's existing /56), no NAT/EIGW — relies on the default VPC main route table's existing `::/0 -> igw` route (~250 tok)
- `outputs.tf` — backend_origin_record, rds_endpoint, cloudfront domains, ecs subnet ids (~80 tok)
- `providers.tf` — aws provider (us-east-1) + us_east_1-aliased provider for CloudFront/ACM (~120 tok)
- `rds.tf` — `aws_db_instance.postgres` matching live `adoptu` instance (db.t4g.micro/postgres17.9), `network_type = "DUAL"` added so IPv6-only ECS tasks can reach it (~350 tok)
- `README.md` — full import plan (this is a brownfield account, not greenfield — S3/RDS/CloudFront/IAM-role names already exist live and must be `tofu import`ed, not created fresh), 4-step migration/cutover plan (Lambda+EventBridge must be applied before the ECS service so the first task's RUNNING event isn't missed), list of live-config findings/fixes, old-resource decommission commands (~2000 tok)
- `route53.tf` — `backend.<domain>` AAAA record (origin for the app CloudFront dist; internal-only, brand new name, `lifecycle.ignore_changes` on `records` since `dns_updater.tf`'s Lambda owns its value, not Terraform) + A/AAAA alias records for apex/www/`api.<domain>` -> app CloudFront (api stays public/CloudFront-fronted, never the origin hostname), static/dynamic -> their CloudFront dists. Other ~20 zone records (mail, SES verification, NS/SOA) deliberately untouched (~400 tok)
- `s3.tf` — `adoptu-static-images`/`adoptu-dynamic-images` buckets, public access block, CloudFront-OAC-only bucket policies (~250 tok)
- `secrets.tf` — Secrets Manager: generated RDS master password, `var.db_app_password` (the live `adoptu` Postgres role's password — must be supplied, not generated) (~200 tok)
- `security_groups.tf` — ECS task SG (port 8080 open to ::/0 — no CloudFront IPv6 managed prefix list exists, matches live), RDS SG (from ECS task only). **No ALB/load-balancer SG — user explicitly rejected one twice, see [[feedback-no-load-balancer]] in global memory** (~250 tok)
- `ses.tf` — comment-only; SES domain identity already verified live, intentionally not managed here (~80 tok)
- `style.scss` — now `@use`s `location-search-form` (was missing, so `.location-search-country select` — the home-page country dropdown — rendered as an unstyled native white control on any page loading only `style.css`, e.g. IndexPage) (~2500 tok)
- `terraform.tfvars.example` — sample values matching the live account (~60 tok)
- `variables.tf` — all configurable inputs: region/profile, domain, container image/port, RDS sizing, db_app_password (sensitive, no default) (~700 tok)
- `versions.tf` — OpenTofu/AWS+archive provider version pins, backend notes (local by default) (~170 tok)

## scripts/ (graalvm-native-image worktree)

- `benchmark.sh` — Load-tests the backend in Docker under `--cpus=0.5 --memory=1024m` (matches ECS Fargate, same profile that produced the validated 18.2 RPS baseline in cerebrum.md). Prefers `hey`, falls back to `wrk`/`ab`/plain curl+xargs. Usage: `scripts/benchmark.sh <label> [endpoint] [duration_s] [concurrency]`; results land in `scripts/benchmark-results/`. Change one thing per run. (~650 tok)
- `build-native-image.sh` — builds the native binary via ghcr.io/graalvm/native-image-community Docker image; runs :backend:jar and :backend:nativeCompile as two separate Gradle invocations to avoid an OOM (native-image's own JVM competing with the Kotlin-compile JVM for container memory) (~150 tok)
