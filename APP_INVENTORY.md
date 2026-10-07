# Shake to Report — Complete App Inventory

A full inventory of the app: identity, tech stack, every feature, data model, architecture,
security measures, known limitations, and the major bugs found and fixed during development.

Built from `INFRASTRUCTURE_INTELLIGENCE_REPORT.md`, `REPORT_NOTES.md`, `PLACEHOLDERS_TODO.csv`,
and a direct read of the current source tree — reflects what the app actually contains today, not
the original plan.

---

## 1. App identity

- **Name**: Shake to Report (package `com.king.mob_app_assignment`; built APK renamed to
  `shake-to-report`).
- **Purpose**: a civic-issue reporting app — shake the phone, the camera opens, location/heading
  get tagged automatically, AI suggests a category, and the report submits in two taps.
- **Pitch**: collapses "notice a pothole/streetlight/etc." into one physical gesture plus minimal
  taps, removing the friction of a phone call or a web form. Citizens report; a small admin team
  triages and resolves reports on the same app — no separate web dashboard.
- **Platform**: native Android/Kotlin, Jetpack Compose (Material 3), single-Activity.
  `minSdk 28`, `targetSdk`/`compileSdk 36`, AGP 9.2.1, Kotlin 2.3.10, versionName 1.0.

---

## 2. Tech stack

| Library / service | Used for |
|---|---|
| Jetpack Compose + Material 3 | Entire UI, no XML views |
| Hilt (+ hilt-navigation-compose, hilt-work) | Dependency injection throughout; `@HiltAndroidApp`; `@HiltWorker` for background workers |
| Firebase Auth | Email/password + username-resolution login, session tokens |
| Firebase Firestore | Primary backend — reports, incidents, work orders, audit log, risk scores, users, support tickets, username/email lookups |
| Firebase Storage | Present (`storage.rules`) but **unused/undeployed** — photos are device-local only |
| Firebase Cloud Messaging + Cloud Functions | Two server-side functions (`functions/src/index.ts`): `sendReportStatusPush` (real server-triggered push on status change, delivered even if the app is killed) and `promoteToAdmin` (a privileged `onCall` function, Admin SDK, that resolves the target user via lookup docs and grants admin — the app's one server-side privilege-escalation path, not just a client Firestore write) |
| Firebase Crashlytics | Crash/breadcrumb reporting, correlated by uid only (no PII) |
| Room | Offline submission queue (`PendingReportEntity`/`PendingReportDao`/`AppDatabase`) |
| DataStore Preferences | Shake sensitivity, language, onboarding/privacy flags, and other lightweight settings |
| AndroidX Security Crypto (Keystore/EncryptedSharedPreferences) | Remember-me email, biometric-gated password store, salted PIN hash |
| AndroidX Biometric | `BiometricPrompt` for login and the Settings toggle |
| WorkManager (+ Hilt-Work) | Runs AI classification, incident clustering, and periodic risk-score refresh as background workers |
| ML Kit Image Labeling | On-device generic image labels → keyword-mapped category suggestion (offline fallback) |
| ML Kit Translate | Downloadable on-device translation for ~35 languages, additive to hand-written EN/ES/FR |
| OkHttp | HTTP client for the AI calls |
| OpenRouter API | Hosted vision LLM (`google/gemini-2.5-flash`) — photo classification + chatbot backend |
| Google Play Services Location | GPS fix capture + reverse geocoding |
| Google Maps SDK / maps-compose | Citizen-facing map screen — pins colored by report category |
| MapLibre + Geoapify | Still present in the project from an earlier map implementation; no longer the active screen |
| Coil | Async image loading in Compose |
| AndroidX Media3 (ExoPlayer) | Video report playback |
| AndroidX ExifInterface | Strips GPS/device metadata from every captured or picked photo |
| Accompanist Permissions | Runtime permission handling helpers |

---

## 3. Full feature list

### Citizen features

**Getting in**
- **Onboarding & privacy notice** — 3-page swipeable first-launch walkthrough, plus a separate
  one-time consent screen. *(`ui/onboarding/OnboardingScreen.kt`, `PrivacyNoticeScreen.kt`)*
- **Login & registration** — email or username login (username resolved server-side), Remember Me,
  forgot-username flow, Firebase password-reset email.
  *(`ui/auth/LoginScreen.kt`, `RegisterScreen.kt`, `ForgotCredentialsScreen.kt`)*
- **Biometric login** — fingerprint/face sign-in backed by a Keystore key that hardware-enforces a
  live biometric scan before it will decrypt anything.
  *(`util/BiometricAuthManager.kt`, `BiometricCredentialStore.kt`)*
- **App Lock / PIN** — a daily reopen gate for an already-signed-in account; biometric first, a
  salted-hash PIN as the mandatory fallback.
  *(`ui/auth/AppLockScreen.kt`, `SetupAppLockScreen.kt`, `util/AppPinStore.kt`)*
- **First-run permission gate** — walks the user through Camera → Location → Notifications one at
  a time right after first sign-in, each with its own plain-language explainer screen before the
  OS prompt fires. Tracks whether the OS dialog was ever actually shown (fixing a real bug where
  "never asked yet" was indistinguishable from "permanently denied").
  *(`ui/permissions/StartupPermissionGate.kt`, `PermissionExplainerScreen.kt`, `util/PermissionRequestHistory.kt`)*
- **Home screen** — the landing screen after sign-in: hero illustration plus the "Shake Now" /
  "New Report" entry points. Also where the live status-change listener that push notifications
  depend on gets started.
  *(`ui/home/HomeScreen.kt`, `HomeViewModel.kt`)*

**Reporting an issue**
- **Shake-to-report gesture** — accelerometer-based detection, adjustable sensitivity, low-pass
  noise filter, optional pocket-guard; works even with the phone locked. A manual "Report Issue"
  button is always available too.
  *(`sensor/ShakeDetector.kt`, `ShakeBackgroundService.kt`, `BootShakeReceiver.kt`)*
- **Camera, video & gallery capture** — photo or video capture, gallery picker (Photo Picker
  API — no broad storage permission needed), freehand red-ink annotation baked into the photo.
  *(`ui/capture/CaptureScreen.kt`, `util/PhotoAnnotator.kt`)*
- **Location & heading tagging** — real GPS fix (rejects readings worse than 50m accuracy) with
  reverse geocoding, plus compass heading, tagged automatically on every report.
  *(`data/location/LocationProvider.kt`, `sensor/CompassProvider.kt`)*
- **AI photo classification** — a hosted vision LLM suggests the category first; on-device ML Kit
  is the fallback. Runs in the background after submission via `AiReviewCoordinator` (decides
  agreement vs. "needs review" vs. outright failure); flags the report for the citizen's review
  if the AI disagrees or is unsure.
  *(`ai/VisionClassifierService.kt`, `ai/ClassificationWorker.kt`, `ai/AiReviewCoordinator.kt`, `sensor/MlKitCategoryClassifier.kt`)*
- **Duplicate detection** — checks nearby, recent reports in the same category before submitting;
  offers to view the existing report or submit anyway.
  *(`ReportRepositoryImpl.findNearbyRecentReports()`)*
- **Offline queueing** — no connection? The report queues locally and flushes automatically on
  reconnect, with a visible "waiting to sync" indicator — never a silent, fake-looking success.
  *(`data/local/PendingReportEntity.kt`, `util/ConnectivityObserver.kt`)*
- **Anonymous reporting** — a genuinely anonymous report stores no owner at all; it can never be
  edited or deleted afterward by anyone, and never triggers a personal notification.
  *(`domain/model/Report.kt` — `isAnonymous`)*

**Tracking & getting help**
- **My Reports** — a solved/not-solved view of everything filed, with filters (including "Needs
  your input") and search; the bottom-nav tab carries a live badge counting the citizen's own
  still-`SUBMITTED` reports. *(`ui/reports/MyReportsScreen.kt`, `ui/nav/PendingReportsBadgeViewModel.kt`)*
- **Report detail & timeline** — full metadata, append-only status history, inline edit/delete
  while still unreviewed, and a plain-language "part of a wider tracked issue" card when clustered.
  *(`ui/reports/ReportDetailScreen.kt`, `StatusTimeline.kt`)*
- **Notifications** — real push (works even if the app is killed) plus local notifications for
  status changes and AI classification results, each independently toggle-able from a dedicated
  settings screen. *(`util/NotificationHelper.kt`, `FcmTokenService.kt`,
  `ui/notifications/NotificationSettingsScreen.kt`, `NotificationSettingsViewModel.kt`)*
- **Map** — Google Maps view of nearby reports, pins colored by category. *(`ui/map/MapScreen.kt`)*
- **AI chat assistant** — an in-app chatbot scoped to app/report questions, able to summarize the
  citizen's own recent reports; conversation persists across app exits, with a "new chat" option.
  *(`ui/chat/ChatScreen.kt`, `ai/ChatMessageService.kt`, `util/ChatPreferences.kt`)*
- **Contact support** — real multi-turn chat threads with an admin, not a single-reply form.
  *(`ui/support/SupportThreadScreen.kt`)*
- **Settings & permissions** — language (EN/ES/FR plus ~35 more via downloadable ML Kit packs),
  shake sensitivity slider, background-shake toggle, biometric on/off, and a live permission
  dashboard. *(`ui/settings/SettingsScreen.kt`, `ui/permissions/PermissionDashboardScreen.kt`)*
- **Profile** — a "Your Impact" card (reports filed/resolved), notification shortcuts, sign-out
  with confirmation; the bottom-nav tab carries a live badge counting unread admin support
  replies. *(`ui/profile/ProfileScreen.kt`, `ui/nav/SupportUnreadBadgeViewModel.kt`)*

### Admin features

- **Admin dashboard** — stats by status/category/department, a "needs attention" queue,
  drill-downs into active incidents and high-risk areas. *(`ui/admin/AdminDashboardScreen.kt`)*
- **Reports management** — department/priority triage; status changes require a note, with an
  optional resolution photo. *(`ui/admin/AdminReportsScreen.kt`)*
- **Support ticket management** — reply in real multi-turn threads, close/resolve, delete resolved
  tickets; a background notifier fires a local notification on an admin's device the moment a new
  open support message arrives, so admins don't have to keep the screen open to notice one.
  *(`ui/admin/AdminSupportMessagesScreen.kt`, `util/AdminSupportNotifier.kt`)*
- **Promote admin** — grants another account admin access via a privileged server-side Cloud
  Function (`promoteToAdmin`, Admin SDK), not a plain client-side Firestore write — the function
  resolves the target through the username/email lookup docs and records the change to the audit
  log itself. *(`ui/admin/PromoteAdminScreen.kt`, `functions/src/index.ts`)*

### The four systems (infrastructure intelligence upgrade)

1. **Infrastructure Incident Intelligence** *(Admin)* — clusters related reports into a single
   tracked **Incident**, rule-based on proximity/category/timing, so several reports of the same
   underlying problem become one incident instead of duplicate tickets. Manual merge/unmerge; an
   8-state lifecycle from *Detected* to *Closed* (or *Dismissed*).
   *(`domain/model/Incident.kt`, `ai/IncidentClusteringWorker.kt`, `ui/admin/AdminIncidentsScreen.kt`)*
2. **Complete Resolution Lifecycle** *(Admin)* — once validated, a **Work Order** tracks the
   actual repair through a 10-state lifecycle (inspection notes, repair notes, materials used,
   resolution photos). A **Priority Scorer** computes a transparent, inspectable 0–100 score.
   Every change writes to one shared, append-only **Audit Log**.
   *(`domain/model/WorkOrder.kt`, `PriorityScorer.kt`, `AuditLogEntry.kt`)*
3. **Predictive Intelligence** *(Admin)* — scores every area of the city (a coarse ~550m grid
   cell) 0–100 across four risk bands, from report history — surfacing recurring problem zones
   proactively. Refreshed periodically in the background; explicitly flags `insufficientData`
   rather than fabricating a score.
   *(`domain/model/RiskScore.kt`, `RiskScorer.kt`, `ai/RiskRefreshWorker.kt`, `ui/admin/AdminRiskAreasScreen.kt`)*
4. **Digital Map** — **removed** 2026-08-31, replaced by the simpler citizen-facing Google Maps
   screen. The 5-layer admin map (`MapLayer.kt`, `ReportsMapScreen.kt`, `ReportsMapViewModel.kt`)
   no longer exists in the codebase — kept here only as historical record.

---

## 4. Data model

**Firestore collections:**

- `reports/{reportId}` — id, userId, reporterName, category, customLabel, isAnonymous;
  localPhotoPath, additionalLocalPhotoPaths[], mediaType, localVideoPath; latitude, longitude,
  address, headingDegrees, headingCardinal, timestamp; status, department, priority, note,
  resolutionNote, statusHistory[]; aiSuggestedCategory, needsAiReview,
  wasResubmittedAfterAiReview; incidentId.
- `users/{uid}` — role (CITIZEN/ADMIN, immutable), email, displayName, username, fcmToken.
- `usernames/{username}`, `emails/{email}`, `loginLookup/{uid}` — pre-auth lookup docs enabling
  username login and forgot-username recovery.
- `incidents/{incidentId}` — title, category, status, severity, department, center lat/lng,
  address, reportCount, members[], history[], priorityScore, priorityFactors[].
- `workOrders/{workOrderId}` — incidentId, department, assignedTeam, priority, status, dueDate,
  inspectionNotes, repairNotes, materialsUsed, resolutionPhotoPaths[], history[].
- `auditLog/{entryId}` — entityType, entityId, action, performedByUid, timestamp, previousValue,
  newValue, note — create-only, never edited or deleted.
- `riskScores/{riskScoreId}` — areaKey, areaLabel, category, center lat/lng, score, level,
  factors[], insufficientData, contributingReportIds[], history[].
- `supportMessages/{messageId}` + `/turns/{turnId}` subcollection — ticket metadata plus
  individual chat turns; status (open/resolved), unread flags per side.

**Room entity (offline queue):** `PendingReportEntity` — mirrors `Report` with enums flattened to
strings, `additionalLocalPhotoPaths` as a pipe-delimited string, plus its own `createdAt` for
first-in-first-out sync draining.

---

## 5. Architecture

- **MVVM + Repository pattern** throughout — one ViewModel per screen, almost all Hilt-injected.
- **Dependency injection** via Hilt modules (`AppScopeModule`, `DatabaseModule`, `FirebaseModule`,
  `RepositoryModule`); the Application class implements `Configuration.Provider` so background
  workers get their dependencies injected too.
- **Navigation** — single-Activity, Jetpack Navigation Compose with nested graphs for onboarding,
  the privacy notice, auth, and the main signed-in app. The starting graph is derived only once
  Firebase's real auth state is actually known, to avoid a cold-start race that could otherwise
  bounce an already-logged-in user to the login screen.
- **Offline-first** throughout — Firestore's own listener cache plus a Room-backed queue that
  flushes automatically on reconnect. Repositories wrap Firestore's live listeners as Kotlin Flows
  end to end, not one-shot reads, so the UI always reflects the latest state.
- **Manifest-level plumbing** — a `FileProvider` handles camera/gallery URI sharing; WorkManager's
  default auto-init is explicitly disabled in the manifest so Hilt's own `Configuration.Provider`
  is the only configuration it sees, which is what lets background workers receive injected
  dependencies at all.

---

## 6. Security & privacy

- **Keystore-encrypted credential storage** — remember-me email, biometric-gated password, and PIN
  are each encrypted at rest via Android's hardware Keystore, never plain `SharedPreferences`.
- **Hardware-enforced biometric gate** — the Keystore key itself refuses to decrypt anything
  without a fresh, real biometric scan, enforced at the OS/hardware level.
- **PIN stored as a salted hash** — one-way SHA-256; the actual PIN is never recoverable.
- **Field-level Firestore security rules** — citizens can only edit specific fields
  (category/note) on their own still-unreviewed report, never its status, never anyone else's.
- **Genuine anonymous reporting** — an anonymous report stores no owner field at all, not a hidden
  one, an absent one.
- **EXIF stripping** — GPS coordinates and device metadata removed from every photo before use.
- **Note sanitization** — all free-text input stripped of HTML/script-like content, length-limited.
- **Privacy notice** — a dedicated, one-time consent screen before the app is used.
- **Anonymized crash reporting** — Crashlytics correlates crashes only by an internal user ID,
  never by email or display name.
- **Server-enforced admin promotion** — the `promoteToAdmin` Cloud Function, not a client write,
  is the only way an account's role can change to admin; nothing in the client app can grant
  itself admin access no matter what it sends to Firestore directly.
- **`HIGH_SAMPLING_RATE_SENSORS` permission** — declared specifically so Android 12+ doesn't
  silently cap the accelerometer's delivery rate below what shake detection needs; its absence was
  a real contributing cause behind bugs #2 and #13 below.

---

## 7. Known limitations & explicit decisions

Tracked in full in `PLACEHOLDERS_TODO.csv` (118 rows) — documented honestly as scope decisions
made on purpose, not bugs.

- **No cloud photo/video storage** — Firebase Storage needs paid billing, declined; every photo
  and video stays device-local, never uploaded.
- **No custom-trained AI model** — classification uses a general-purpose hosted vision model plus
  generic on-device labels, not a model trained on civic-issue photos specifically.
- **Incident clustering, priority scoring, and risk scoring are rule-based, not machine learning**
  — a deliberate choice to keep the logic transparent and inspectable; each formula is written so
  a real model could later replace it without touching anything that calls it.
- **Clustering and risk-refresh run on-device, not server-side** — whichever citizen's phone
  happens to be online runs the background work; no dedicated backend compute.
- **"Risk area" is a coarse grid cell** — roughly 550 meters square, not a real administrative
  boundary or GIS shape.
- **Single push token per account** — signing into a second device silently takes over
  notification delivery.
- **Anonymous reports are permanently locked** — can never be edited or deleted by anyone, by
  design, since there's no owner to authorize it.
- **Video reports skip AI classification entirely** — always manually categorized, a deliberate
  scope decision.
- **Admin provisioning is manual** — no in-app self-service path to becoming an admin from zero; a
  role change is always initiated by an existing admin.

---

## 8. Notable bugs found & fixed

The headline list, in the order they were found — see `REPORT_NOTES.md` for full rationale on
each.

1. **Camera permission crash** — the capture screen fired the camera intent with no runtime
   permission check at all.
2. **Shake detection unreliable on real devices** — fixed across several rounds: window timing,
   mismatched thresholds, and a broken reversal-counting algorithm that could mathematically never
   trigger.
3. **Shake detector concurrency bug** — a shared singleton let the foreground and background
   listeners clobber each other's detection state.
4. **Offline queue looked identical to a real success** — a failed submission silently vanished
   into the local queue with no visible sign anything was different.
5. **Firestore listener misread a transient error as "zero reports"** — report counts flashed to
   0 and back on reconnect.
6. **"Insufficient permissions" on login** — a Firestore rule threw on a missing user document
   instead of failing gracefully, traced via live device logs.
7. **App re-asked for login on every cold start** — a placeholder auth state was indistinguishable
   from "really signed out" before Firebase's listener had actually fired.
8. **Biometric enable silently never worked** — three separate screens authenticated the
   fingerprint scan in isolation from the encryption key it was supposed to unlock; the prompt
   succeeded, but nothing was ever actually enabled.
9. **Language setting didn't apply** — the Activity type didn't support the automatic recreate a
   locale change needs.
10. **Incident clustering picked the first match, not the best one** — fixed to score every
    nearby candidate and pick the strongest.
11. **Department suggestion matched partial words** — "flooding the street" matched "tree"
    instead of "flood"; fixed to match whole words only.
12. **Report form's Submit button was clipped off-screen** — the form had no scroll container, so
    it silently cut off below the fold on some real devices.
13. **Shake could open the camera twice** — the foreground and background shake listeners both
    reacted to the same physical shake independently.

---

*Compiled from `INFRASTRUCTURE_INTELLIGENCE_REPORT.md`, `REPORT_NOTES.md`,
`PLACEHOLDERS_TODO.csv`, and a direct read of the current source tree.*
