# Shake to Report — Report Notes

Running log of design decisions, features, and rationale for the technical report (2500-5000 words).
Organized to map directly onto the required report sections (5.1-5.7).

---

## 5.1 Introduction

- **Purpose**: Reduce the friction of reporting civic issues (potholes, broken streetlights,
  garbage, water leaks). The core insight: people see problems constantly but don't report them
  because the reporting process itself is too slow/annoying.
- **Problem statement**: Existing municipal reporting (forms, phone calls, generic 311-style apps)
  requires many taps/fields before a report is even started. This app collapses "notice a
  problem -> file a report" into a single physical gesture (shake) + two taps.
- **Target users**: General public / citizens who encounter civic infrastructure issues in daily
  life; secondary user role: municipal admin/reviewer who triages and resolves reports.

---

## 5.2 System Design

- **Architecture**: MVVM + Repository pattern, Jetpack Compose UI, Hilt for dependency injection.
  Reason: industry-standard on Android, testable, satisfies "modular and reusable code"
  non-functional requirement (Section 4).
- **Backend**: Firebase (Auth, Firestore, Storage) — chosen over local-only Room DB so the app has
  real multi-device sync, real token-based auth, and a real offline-queue story (Firestore's
  built-in offline persistence), which maps directly to marking criteria on functionality and
  security.
- **Local persistence**: Room DB used as an offline cache/queue layer on top of Firestore (pending
  reports created while offline are staged locally, then synced).
- **Navigation**: Jetpack Navigation Compose, single-Activity architecture.

## 5.3 Features Description (full list, with hardware/API mapping)

### Core reporting flow
- Shake-to-trigger detection (accelerometer via SensorManager, debounced, foreground-only)
- Pocket-guard false-positive filter (checks multiple axes / ambient light to avoid pocket jostling
  triggering a false report)
- Manual report button (fallback trigger, doesn't depend on shake reliability — added after
  scoping review so the demo/core flow never depends on a gesture landing correctly)
- Auto-launch camera on valid shake, auto-capture with retake/confirm step
- GPS auto-tag (lat/long via FusedLocationProviderClient + reverse geocoding to street address)
- Compass/heading tag at capture time (via magnetometer + accelerometer sensor fusion,
  `SensorManager.getRotationMatrix`/`getOrientation`)
- Auto-timestamp
- **AI-assisted category suggestion**: ML Kit on-device Image Labeling API suggests a category
  (pothole/streetlight/garbage/water leak/other) with a confidence score; below a confidence
  threshold, the UI falls back to manual category selection. This is the "AI integration" bonus
  feature from the assignment brief — implemented as a lightweight on-device model call, not a
  custom-trained model, to keep scope realistic for a 2-person coursework project.
- Optional text note, sanitized/validated before submission (length limits, stripped of
  script/HTML-like content) to satisfy "data validation and sanitisation" requirement (3.3)
- Two-tap submit flow (confirm photo -> submit)

### Reports & tracking
- Map view of reports (pins), list view (filter/sort by status/category/date)
- Report detail screen (photo, map pin, address, status)
- Status tracking: submitted -> in review -> resolved
- "My Reports" personal history screen
- **Duplicate/spam handling (simplified)**: client-side check — if a similar-category report exists
  within a small radius and short time window, user is warned and shown the existing report instead
  of filing a duplicate. (Full backend geo-clustering was scoped out as unnecessary complexity for
  this size of project — documented as a "future improvement" in report Section 5.7.)
- **Offline resilience (simplified)**: reports created without network are queued locally (Room)
  and auto-submitted to Firestore when connectivity returns. Full bidirectional sync/conflict
  resolution was scoped out; one-way queue-and-flush was judged sufficient to demonstrate the
  concept without over-engineering.

### Auth & roles
- Firebase Authentication (email/password), token-based session
- Secure token storage via Android Keystore-backed EncryptedSharedPreferences /
  androidx.security.crypto (not raw SharedPreferences) — satisfies "secure login" + "protection of
  sensitive user data" (3.3)
- Profile screen, logout
- **Role toggle (citizen/admin)** instead of a separate admin web dashboard: admin role sees all
  reports across users and can change status. Decision: a full separate admin product was out of
  scope for a 2-person mobile coursework assignment; folding it into the same app as a role-gated
  view closes the reporting loop (someone has to resolve issues) without building second product.
- Permission status dashboard: shows camera/location/motion permission state with a re-request
  flow if denied

### Security & privacy
- Runtime permission requests for Camera, Location (fine + background if needed), and
  Activity Recognition/motion sensors, each preceded by a **consent/explainer screen** stating why
  the permission is needed before the system dialog appears (best practice, also a Play Store
  policy expectation for sensitive permissions)
- **EXIF metadata stripping** from captured photos before upload — prevents leaking device
  make/model/GPS-in-EXIF beyond what the app explicitly and visibly attaches to the report
- Input sanitization on all free-text fields

### Extras
- Local notification when a report's status changes (submitted -> in review -> resolved)
- Shake sensitivity setting (accessibility: not everyone can shake a phone hard; adjustable
  threshold)

### Explicitly cut from scope (with reasoning, for report Section 5.7 "Limitations")
- **Rate limiting per user/device** — not implemented; would require server-side enforcement
  (Cloud Functions) which was judged disproportionate effort for coursework scope. Documented as a
  future improvement.
- **Separate admin/municipal web dashboard** — replaced with in-app admin role (see above) to avoid
  building and maintaining a second product surface.
- **Full AI-trained custom model for category classification** — replaced with ML Kit's pretrained
  on-device Image Labeling API. Reasoning: building/training a custom vision model was out of scope
  for the timeframe and would not meaningfully improve the demonstrated concept; ML Kit still
  satisfies the "AI integration" bonus criterion with a real on-device inference call.

## 5.4 Security Implementation

- Permission handling strategy: explainer screen -> runtime request -> graceful denial handling ->
  re-request via Settings deep-link if permanently denied
- Authentication method: Firebase Auth (email/password), ID token used for authenticated Firestore
  reads/writes via security rules (Firestore rules restrict report writes to the authenticated
  owner; admin role has broader read/update access)
- Data protection measures: encrypted token storage (Keystore-backed), EXIF stripping, input
  sanitization, Firestore security rules as server-side enforcement (not just client-side checks)

## 5.5 Development Tools & Technologies

- Framework: Android (native), Kotlin
- UI: Jetpack Compose (Material 3)
- Architecture: MVVM + Repository, Hilt (dependency injection)
- Local DB: Room (offline queue/cache)
- Backend: Firebase (Authentication, Firestore, Cloud Storage)
- Location: FusedLocationProviderClient (Google Play Services Location)
- Sensors: Android SensorManager (accelerometer, magnetometer)
- AI: ML Kit Image Labeling (on-device)
- Image loading: Coil
- Notifications: local notifications via NotificationManager (WorkManager/AlarmManager if
  scheduled reminders are added)
- IDE: Android Studio, Gradle Kotlin DSL, version catalogs (libs.versions.toml)

## 5.6 Testing (to fill in as build progresses)

- Functional testing: manual test of shake-trigger -> capture -> submit -> status change flow
- Device testing: test on physical device (sensors/camera/GPS do not work reliably on emulator)
- UI testing: Compose UI tests for key screens (to be added)

## 5.7 Conclusion (draft points to expand later)

- Achieved: full core flow (shake/manual trigger -> camera -> GPS/compass tagging -> AI category
  suggestion -> submit -> track status), auth, role-based views, offline queue, security measures
- Limitations: no server-side rate limiting, no full geo-clustering dedupe, no custom-trained ML
  model, no push notifications from a real backend trigger (local notifications only, since
  push requires a Cloud Function trigger which was out of scope)
- Future improvements: Cloud Functions for rate limiting + real duplicate clustering, push
  notifications via FCM triggered server-side on status change, multi-language support, offline
  full sync

---

## Decision log (chronological, for reference)

1. Chose "Shake to Report" over suggested themes — satisfies mandatory 2+ hardware integration
   with 4 features (accelerometer, camera, GPS, compass) in one cohesive flow, not bolted on.
2. Reviewed full feature list against assignment brief; identified gaps (offline handling,
   dedupe, AI, notifications, consent screens, EXIF stripping) and cut/simplified items that would
   have been disproportionate scope for 2 students (rate limiting, full admin dashboard removed;
   AI kept per explicit user request; offline + dedupe simplified to client-side/one-way versions).
3. Chose Firebase over local-only Room for backend — real auth/sync/security story worth more
   marks than added complexity.
4. Chose Jetpack Compose over XML Views — modern, less boilerplate, current industry standard.
5. Chose MVVM + Repository + Hilt over manual DI — testable, modular, matches non-functional
   "modular and reusable" requirement directly.

---

## 2026-07-28 — Foundation phase

- **Domain models** (`domain.model`): `User`, `Report`, `ReportCategory` (POTHOLE, STREETLIGHT,
  GARBAGE, WATER_LEAK, OTHER), `ReportStatus` (SUBMITTED, IN_REVIEW, RESOLVED), `UserRole`
  (CITIZEN, ADMIN). Enums expose a `fromFirestoreValue` helper that defaults safely (to OTHER /
  SUBMITTED / CITIZEN) if Firestore ever contains an unexpected/legacy string, rather than
  crashing on `valueOf`.
- **Report model** carries both `photoUrl` (remote, Firebase Storage download URL, nullable until
  uploaded) and `localPhotoPath` (nullable, used while a photo is only on-device/pending upload).
  Heading is split into `headingDegrees` (Float) and `headingCardinal` (String, e.g. "NE") per
  spec rather than computing cardinal at display time, since the reading is a point-in-time
  sensor fact that belongs on the record.
- **Room offline queue**: `PendingReportEntity` mirrors `Report` fields but flattens enums to
  `String` (Room doesn't need custom TypeConverters this way, and it matches how the value will be
  re-serialized to Firestore on flush) and always carries a `localPhotoPath` (non-null, since a
  pending report only exists because it hasn't uploaded yet). Added a `createdAt` field (separate
  from the report's own `timestamp`) to make FIFO queue draining unambiguous. `PendingReportDao`
  exposes `getAll()` as a `Flow` for reactive queue-badge UI, plus a one-shot `getAllOnce()` for
  the WorkManager/connectivity-callback flush routine, and both `delete(entity)` and
  `deleteById(id)` for convenience.
- **Hilt**: `MobAppAssignment` (`@HiltAndroidApp`) registered as `android:name` in the manifest.
  `DatabaseModule` provides a singleton `AppDatabase` (Room, `fallbackToDestructiveMigration()`
  since this is coursework with no shipped user data to preserve across schema changes) and the
  `PendingReportDao`. `FirebaseModule` provides singleton `FirebaseAuth`, `FirebaseFirestore`,
  `FirebaseStorage` instances via `getInstance()`.
- **Firestore schema**: `data/remote/FirestoreSchema.kt` defines `FirestoreConstants` — collection
  name constants (`users`, `reports`), field-name constants grouped under `UserFields`/
  `ReportFields` nested objects (avoids magic strings scattered across repository code), a
  `RoleValues` object matching the `UserRole` enum names, and a `reportPhotoStoragePath(userId,
  reportId)` helper so the Storage path convention (`report_photos/{userId}/{reportId}.jpg`) is
  defined once and shared between the repository and `storage.rules`.
- **Firestore/Storage security rules**: `firestore.rules` — a user can create/read/update only
  their own `users/{uid}` doc (and cannot self-promote to admin: `update` requires
  `request.resource.data.role == resource.data.role`); `reports/{reportId}` can only be created by
  its stated owner (`request.resource.data.userId == request.auth.uid`), and can be read/updated by
  the owner or by an admin (role looked up via `get()` on the requesting user's own doc). No
  deletes allowed anywhere (reports form an audit trail; status changes are updates, not deletes).
  `storage.rules` restricts `report_photos/{userId}/{fileName}` writes to an authenticated user
  writing to their own uid-prefixed path, caps upload size at 15MB, and requires an `image/*`
  content type; reads require auth (any authenticated user, since report photos are meant to be
  visible in the shared Reports/Map view, not just to the owner).
- **Manifest**: added `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`,
  `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` permissions; a `<uses-feature android:name=
  "android.hardware.camera" android:required="false"/>` so the app still installs on
  camera-less devices/emulators; registered `MobAppAssignment` as `android:name` on
  `<application>`; registered a `FileProvider` (authority `${applicationId}.fileprovider`) backed
  by `res/xml/provider_paths.xml` (exposes `external-files-path` and `cache-path` under
  `report_photos/`) so the camera capture screen (a later phase) can hand the system camera app a
  writable `content://` URI instead of a raw file path.
- No `MainActivity` was created in this phase — that belongs to the navigation/UI phase; this
  phase is data/DI/manifest foundation only, per the task scope.

## 2026-07-28 — Auth & Permissions phase

- **AuthRepository** (`data.repository.AuthRepository` / `AuthRepositoryImpl`): wraps
  `FirebaseAuth` + the `users/{uid}` Firestore doc. `register()` creates the Firebase Auth user,
  sets the Firebase profile `displayName`, then writes a `User` document with
  `role = UserRole.CITIZEN` by default (server-side rules already prevent self-promotion to admin,
  per the Foundation phase's `firestore.rules`). `login()` signs in then reads back the Firestore
  user doc (falls back to a basic `User` built from `FirebaseUser` if the doc read fails, so a
  transient Firestore hiccup doesn't block a valid login). `currentUser: Flow<User?>` is a
  `callbackFlow` wrapping `FirebaseAuth.AuthStateListener`, emitting a quick "basic" user first for
  responsiveness then the richer Firestore-backed `User` (with real role) once that document read
  completes — avoids a role-dependent UI (e.g. Admin tab visibility) flashing incorrectly while the
  Firestore round trip is in flight, at the cost of one extra emission. Bound to the interface via
  a new `di/RepositoryModule.kt` (`@Binds`).
- **Token storage decision (explicit per task spec)**: `SecurePrefsManager` (`util` package) uses
  `androidx.security.crypto.EncryptedSharedPreferences` (Keystore-backed `MasterKey`,
  AES256_SIV/AES256_GCM schemes) to store only `isRememberMeEnabled` (Boolean, defaults true) and
  `lastUsedEmail` (String?, prefills the login email field). It deliberately does **not** store any
  Firebase ID token or refresh token — the Firebase Android SDK already persists and auto-refreshes
  its own session (backed by its own secure on-device storage), so writing our own copy would be
  redundant and would introduce a second, harder-to-invalidate copy of sensitive credential
  material. This reasoning is intentionally documented here per the task's explicit instruction.
- **PermissionManager** (`util` package): stateless helper (not a class needing DI) wrapping
  `ContextCompat.checkSelfPermission` for `AppPermission.CAMERA`, `LOCATION`
  (`ACCESS_FINE_LOCATION`), and `NOTIFICATIONS` (`POST_NOTIFICATIONS`, API 33+; treated as always
  granted below API 33 since the permission doesn't exist pre-Tiramisu). Also exposes
  `isPermanentlyDenied(activity, permission)` (checks `shouldShowRequestPermissionRationale`) and
  `appSettingsIntent(context)` (`ACTION_APPLICATION_DETAILS_SETTINGS` deep link) for the "permanently
  denied -> Open Settings" branch required by the spec. No explicit runtime permission entry was
  added for the accelerometer/magnetometer (motion/compass sensors): on API 28+ these are
  normal-protection-level sensors (not `BODY_SENSORS`, which is for biometric/health sensors like
  step-counter/heart-rate), so no runtime request applies to them — documented inline in the file
  so a later phase doesn't mistakenly add a request for something that will never appear in the
  system permission dialog.
- **PermissionExplainerScreen** (`ui.permissions`): generic reusable composable
  (icon/title/body/button-label/onContinue) — the shake-capture and location-tagging phases can
  reuse this directly ahead of each first-time system permission prompt rather than each screen
  hand-rolling its own explainer layout.
- **PermissionDashboardScreen + PermissionDashboardViewModel** (`ui.permissions`): lists
  Camera/Location/Notifications with a status icon (granted/not granted) and, per denied row, either
  a "Grant" button (launches `ActivityResultContracts.RequestPermission()` via
  `rememberLauncherForActivityResult`, then calls `viewModel.refresh()`) or an "Open Settings" button
  when `PermissionManager.isPermanentlyDenied` is true. `PermissionDashboardViewModel` is a plain
  `AndroidViewModel` (not `@HiltViewModel`) since it only needs `Application` context for the
  permission checks and has no repository dependency — kept deliberately simple rather than forcing
  Hilt DI where it adds no value.
- **LoginScreen/LoginViewModel, RegisterScreen/RegisterViewModel** (`ui.auth`): Material 3 forms
  with inline field validation (email regex via `Patterns.EMAIL_ADDRESS`, password length >= 6 on
  register, confirm-password match) and `StateFlow<UiState>` exposed to Compose, matching the
  project's ViewModel convention. On successful login/register, `lastUsedEmail` is written to
  `SecurePrefsManager` and the login screen prefills it from there on next launch. Both screens
  surface `AuthRepository` `Result` failures as an inline error `Text` rather than throwing/crashing
  the UI.
- Did **not** wire `MainActivity`/navigation graph/auth-gated nav in this phase — that's the
  navigation phase's job per the phase boundaries already established (Foundation phase likewise
  left `MainActivity` for later). Auth/permission screens are self-contained composables ready to be
  slotted into a `NavHost`.
- No new placeholders/stubs were introduced this phase (auth wraps real `FirebaseAuth`/Firestore
  calls; the existing placeholder `google-services.json` from the Foundation phase remains the only
  reason these calls won't reach a real backend yet, already tracked in
  `PLACEHOLDERS_TODO.csv`).

---

## 2026-07-28 — Sensors & Capture phase

- **ShakeDetector** (`sensor`): wraps `SensorManager` + `TYPE_ACCELEROMETER`, computes the
  gravity-normalized acceleration delta each sample. Pocket-guard strategy implemented as: (1) a raw
  magnitude threshold (adjustable, read from `ShakePreferences`/DataStore, default 15 m/s²), (2) a
  requirement of at least 2 sign-reversals of the delta within a rolling 1s window (a genuine shake
  is back-and-forth; a single jostle or a phone sliding in a pocket rarely reverses direction twice
  quickly), and (3) if a proximity sensor is present, the trigger is suppressed while it reads
  "covered" (pocket/hand over screen). Debounced with a 1.5s minimum gap between fires, per spec.
  Exposed as `Flow<Unit>` via `callbackFlow` so it composes naturally with `LaunchedEffect` at the
  Home-screen level (screen wiring left to the navigation/home phase).
- **ShakePreferences** (`util`, DataStore Preferences, key `shake_sensitivity`): stores the
  adjustable threshold (double, 8.0-25.0 range, default 15.0) so the not-yet-built Settings screen
  can read/write it; `ShakeDetector` reads the latest value each time its flow is collected and also
  exposes `updateThreshold` for a live slider preview.
- **CompassProvider** (`sensor`): wraps accelerometer + magnetometer via
  `SensorManager.getRotationMatrix`/`getOrientation` to produce a heading-degrees `Flow<Float>`, plus
  a stateless `degreesToCardinal` helper for the 8-point compass string, used both live (compass UI)
  and once as a snapshot value at photo-capture time.
- **LocationProvider** (`data.location`, new package — chosen over cramming into `data.remote` since
  it wraps Play Services location/Geocoder, not Firestore/Storage): `getCurrentLocation()` is a
  suspend fn using `FusedLocationProviderClient.getCurrentLocation(...).await()` from
  kotlinx-coroutines-play-services; `reverseGeocode(lat, lng)` uses the async
  `Geocoder.getFromLocation(..., listener)` overload on API 33+ and falls back to the deprecated
  synchronous overload below that, returning null gracefully if `Geocoder.isPresent()` is false or
  the call throws.
- **ExifStripper** (`util`): strips GPS-* tags and device-identifying tags (make/model/software/
  artist/owner/serial numbers) in place via `androidx.exifinterface.media.ExifInterface`, called on
  the captured photo file right after confirm, before any of it is used for upload. GPS/heading/
  timestamp continue to live only as explicit Firestore fields (already modeled on `Report` from the
  Foundation phase), never duplicated into EXIF. Added the `androidx.exifinterface:exifinterface:1.3.7`
  dependency (was not yet present) to both `libs.versions.toml` and `app/build.gradle.kts`.
- **MlKitCategoryClassifier** (`sensor`): suspend `classify(uri)` running the default on-device ML
  Kit `ImageLabeling` client, keyword-matches each returned label's text (case-insensitive, first
  match wins by category priority order Pothole/Streetlight/Garbage/WaterLeak) against the exact
  keyword lists specified in the task, using that label's own confidence score; if nothing matches,
  returns `OTHER` with the top label confidence capped below the 0.5 auto-select threshold so the
  form always falls back to a manual pick in that case, per spec.
- **NoteSanitizer** (`util`): `sanitizeNote` strips any `<...>` tag-shaped substring via regex (covers
  both HTML and `<script>` tags without needing a full HTML parser dependency), trims, then truncates
  to 500 chars. Unit tested in `app/src/test/.../util/NoteSanitizerTest.kt` (trim, tag-stripping,
  truncation, all-whitespace-to-empty cases).
- **CaptureViewModel/CaptureScreen** (`ui.capture`): single-screen state machine
  (`IDLE -> PREVIEW -> ANALYZING -> FORM -> SUBMITTING -> SUBMITTED`) matching the "two-tap submit"
  spec (Confirm capture, then Submit form). Camera launched via
  `ActivityResultContracts.TakePicture()` against a `FileProvider` URI, reusing the Foundation
  phase's existing `provider_paths.xml`/manifest `<provider>` entry (both already present, no changes
  needed). On Confirm, location+geocode, compass-heading snapshot, and ML Kit classification run
  concurrently via three `async {}` coroutines then join, all inside a single `ANALYZING` loading
  state. Category is shown as `FilterChip`s (all 5 enum values always visible/selectable); the
  suggested category is only pre-selected when confidence >= 0.5, otherwise nothing is pre-selected
  and a low-confidence hint text is shown, matching the spec exactly. Note field sanitizes on every
  keystroke via `NoteSanitizer` and shows a live `n/500` counter.
  - **Submission target for this phase**: since the Reports/offline-queue repository layer does not
    exist yet (that's a later phase's scope per the task boundaries), `submit()` writes directly into
    the already-existing Room `PendingReportEntity` table via `PendingReportDao.insert(...)` rather
    than calling Firestore directly or inventing a `ReportRepository` ahead of schedule. This means
    right now *every* submission goes through the offline queue table regardless of connectivity;
    the future offline-queue/network-callback phase is expected to add the "if online, write straight
    to Firestore/Storage, else queue" branching and the flush-on-reconnect worker on top of this same
    table, so no rework of this phase's schema/DAO usage should be needed.
  - Duplicate-check dialog (item 9) and the actual Firestore/Storage upload + EXIF-stripped file
    upload are intentionally **not** implemented in this phase — they depend on the Reports
    repository this phase does not own. Left as follow-up for the Reports/offline-queue phase.
- No manifest changes were needed: `CAMERA`/`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` permissions
  and the `FileProvider` were already declared by the Foundation phase. Motion/magnetic-field sensors
  require no runtime or manifest permission on API 28+, consistent with the note already left in
  `PermissionManager.kt` by the previous phase.
- Added dependency: `androidx.exifinterface:exifinterface:1.3.7` (new `libs.versions.toml` entry
  `exifinterface`/`androidx-exifinterface`, added to `app/build.gradle.kts`). No other dependencies
  were added — `play-services-location`, `mlkit-image-labeling`, `kotlinx-coroutines-play-services`,
  `androidx-datastore-preferences` were all already present from the Foundation phase's Gradle setup.

---

## 2026-07-28 — Screens phase (ReportRepository, Home/Reports/Map/Detail/MyReports/Admin/Profile/Settings)

- **`data.repository.ReportRepository` / `ReportRepositoryImpl`**: wraps Firestore reads via
  `callbackFlow` + `addSnapshotListener` (so `getAllReports()`, `getReportsForUser(uid)`, and
  `getReportById(id)` are all live-updating `Flow`s, matching "Repositories expose suspend
  functions/Flow, never raw Task objects" rule; this doubles as the mechanism behind item 17's
  local-notification listener in a later phase, since the Flow already diffs on every server push).
  `updateReportStatus` is a plain suspend function (admin-only enforcement lives in the Foundation
  phase's already-written `firestore.rules`, not re-checked client-side here). `findNearbyRecentReports`
  does the ~100m/10min duplicate check: queries Firestore by category + a `timestamp >=` bound (a
  composite index on `category`+`timestamp` will be needed once a real Firestore project exists —
  Firestore will surface a console link to auto-create it on first real query, not something to
  hand-write now), then filters candidates further by a haversine distance calculation client-side
  (Firestore doesn't do native geo-radius queries without GeoFire/geohashing, which is out of scope).
  `submitReport(report, photoFile)` is the intentionally generic "upload photo then write doc"
  method the task asked for, so a later offline-queue phase can wrap/replace it with connectivity-
  aware branching without changing its signature; `createReport(report)` is a photo-less convenience
  overload for callers that already have a `photoUrl` (not currently used by any screen, kept for
  the offline-queue phase to call once it has already uploaded a queued photo itself).
- **CaptureViewModel integration**: `submit()` now actually calls `reportRepository
  .findNearbyRecentReports(...)` first (item 9's duplicate check) and, if a match is found, moves
  to a new `CaptureStage.DUPLICATE_CHECK` stage that shows an `AlertDialog` with "Submit anyway" /
  "View existing" (which calls back to `onViewExistingReport(reportId)`, wired by whatever nav host
  a later phase builds). Otherwise it calls `doSubmit()`, which now calls `reportRepository
  .submitReport(report, photoFile)` directly — i.e. this phase resolves the "PLACEHOLDERS_TODO.csv:
  submit() only ever queues to Room" row from the previous phase for the *online* path. If
  `submitReport` fails (thrown exception, e.g. no network — Firestore's offline persistence may
  itself succeed silently, so this really only catches non-network failures) it falls back to the
  existing `PendingReportDao.insert(...)` Room path exactly as before, so the queue table still
  exists as a safety net pending the dedicated offline-queue phase's `ConnectivityManager`/
  `WorkManager` wiring (documented as still-open in `PLACEHOLDERS_TODO.csv`).
- **Reusable list building blocks** (`ui.reports.ReportListComponents.kt`): `ReportFilterState` +
  `applyFilter()` (a plain `List<Report>` extension, not tied to any ViewModel) plus two shared
  composables — `ReportFilterBar` (status chips + category chips + a sort-order toggle chip) and
  `ReportListItem` (a `Card` with an optional `trailingContent` slot). `ReportsListScreen`,
  `AdminReportsScreen`, and `HomeScreen`'s recent-activity list all reuse `ReportListItem`;
  `ReportsListScreen` and `AdminReportsScreen` both reuse `ReportFilterBar`. The `trailingContent`
  slot is what lets `AdminReportsScreen` bolt on a `StatusDropdown` per row without a separate
  admin-specific list item composable.
- **`ReportsListViewModel`** takes a `ReportsScope` (`ALL` / `MINE` / `ADMIN_ALL`) set via
  `setScope()` from a `LaunchedEffect(scope)` in `ReportsListScreen`, and `flatMapLatest`s between
  `getAllReports()`/`getReportsForUser(uid)` accordingly. `MyReportsScreen` is a 3-line wrapper that
  calls `ReportsListScreen(scope = ReportsScope.MINE, ...)` rather than duplicating list/filter UI,
  per the "keep it DRY" instruction. `AdminReportsScreen` deliberately does **not** reuse
  `ReportsListViewModel` — it has its own `AdminReportsViewModel` because admin status-change
  (`updateStatus`) doesn't belong on the citizen-facing list ViewModel's public API — but it does
  reuse the `ReportFilterBar`/`ReportListItem` composables and the same `ReportFilterState`/
  `applyFilter()` filtering logic, so only the ViewModel differs, not the UI building blocks.
- **`MapPlaceholderScreen`**: no Google Maps API key exists for this project, so per the task's
  explicit instruction this is a placeholder — a `LazyColumn` of report cards showing raw
  lat/long text, plus a `Canvas`-drawn dot-grid above it that normalizes each report's lat/long
  into a 0..1 box (min/max bounds of the current report set) and draws a dot per report, giving a
  rough "relative position" visual without any real map tiles/projection. Logged as a placeholder
  needing a real Maps API key in `PLACEHOLDERS_TODO.csv`.
- **`ReportDetailScreen`/`ReportDetailViewModel`**: `reportId` comes from `SavedStateHandle`
  (assumes a later nav-graph phase passes it as a `"reportId"` nav argument). Shows full photo via
  Coil `AsyncImage(model = report.photoUrl ?: report.localPhotoPath)` — falls back to the local file
  path for reports still sitting in the offline queue that haven't been assigned a Storage URL yet.
  Admin-only status dropdown is gated on `authRepository.currentUser.map { it?.role == ADMIN }`,
  not a separate role check duplicated from `AuthRepository`.
- **`HomeScreen`/`HomeViewModel`**: big `Button` (not `FloatingActionButton`, to match "always show
  manual button" prominently) plus a static shake hint `Text`, plus last-3 reports for the current
  user via `getReportsForUser(uid).map { it.take(3) }`. Does not itself launch `CaptureScreen`; that
  wiring (`onReportIssueClick`) is left to whatever nav host phase wires the bottom navigation/drawer,
  matching how `CaptureScreen` itself doesn't know about navigation.
- **`ProfileScreen`/`ProfileViewModel`**: thin wrapper over `AuthRepository.currentUser`/`logout()`;
  navigates out to `PermissionDashboardScreen` (built by the Auth phase) and the new `SettingsScreen`
  via caller-supplied lambdas rather than importing a `NavController` directly, keeping this screen
  nav-graph-agnostic.
- **`SettingsScreen`/`SettingsViewModel`**: thin wrapper over the already-existing `ShakePreferences`
  DataStore class from the sensor phase — no new persistence code was needed, just a `Slider` bound
  to `thresholdFlow`/`setThreshold()` using the existing `ShakePrefsKeys.MIN_THRESHOLD/MAX_THRESHOLD`.
- **Not built this phase (explicitly out of scope per task)**: `NotificationHelper`/Firestore status-
  change listener (item 17) and the `ConnectivityManager`/`WorkManager` offline-queue flush job
  (item 16) — both left for their dedicated later phases. No bottom navigation/NavHost was added
  either, since no `MainActivity`/nav-graph file exists yet in the codebase for this phase to hook
  into; all new screens take plain lambda callbacks (`onReportClick`, `onLoggedOut`, etc.) so a
  future nav-graph phase can wire them without needing to touch this phase's screen code.
- **Build verification note**: this environment's Gradle wrapper currently fails at plugin-apply
  time (`Cannot add extension with name 'kotlin', as there is an extension already registered with
  that name`) before compiling any module source, on a clean checkout with no changes from this
  phase — pre-existing local Gradle/Android Studio toolchain conflict, not something introduced by
  this phase's edits. Code was reviewed manually line-by-line against existing types
  (`Report`/`ReportCategory`/`ReportStatus`/`User`/`UserRole`, `FirestoreConstants`) instead of a
  full `gradlew compileDebugKotlin` run; flag this Gradle environment issue for a future phase/
  local dev machine to resolve so CI/local builds succeed.

---

## 2026-07-28 — Offline & Sync phase

- **`ConnectivityObserver`** (`util/ConnectivityObserver.kt`): wraps
  `ConnectivityManager.NetworkCallback` via `callbackFlow`, exposing `isOnline(): Flow<Boolean>`
  (distinct-until-changed, emits current state immediately on collection) plus a synchronous
  `isOnlineNow()` used for one-shot checks. `isOnlineNow()` is intentionally the primitive used at
  submit time (per task spec: "check ConnectivityObserver's current value once, not a long-lived
  collection, for the submit-time decision") rather than collecting the Flow.
- **`ReportRepositoryImpl.submitReport`**: now checks `connectivityObserver.isOnlineNow()` first.
  If offline, it inserts a `PendingReportEntity` (photo file's local path, all report fields) into
  Room and returns `Result.success(report)` immediately — no Storage/Firestore call is attempted.
  If online, it proceeds exactly as before (Storage upload of the stripped-EXIF photo, then
  Firestore doc write). `CaptureViewModel.doSubmit()`'s existing "insert into
  `PendingReportEntity` if `submitReport` throws" fallback is left in place as a second safety net
  for transient failures that occur mid-upload while nominally online (e.g. connection drops
  during `putFile`) — the two are not mutually exclusive and do not double-queue since the
  ViewModel only reaches its own fallback when `Result.isFailure`, which the repository's offline
  branch never returns.
- **`ReportRepository.syncPendingReports()`**: new interface method. Reads
  `PendingReportDao.getAllOnce()`, and for each row reconstructs a `Report` + resolves its local
  photo `File` (guarding for the file having been deleted), calls the same private `createReport`
  helper used by the online path (Storage upload + Firestore write), and deletes the Room row only
  on success — a failed row is left in place for the next sync attempt, per spec.
- **Sync trigger**: rather than a `WorkManager` job, implemented as "a simple app-scoped
  CoroutineScope + `ConnectivityObserver` Flow collector started from the Application class" (the
  task's explicitly offered alternative). `MobAppAssignment.onCreate()` field-injects
  `ConnectivityObserver` + `ReportRepository` (Hilt supports field injection into
  `@HiltAndroidApp Application`), tracks the previous online/offline state, and calls
  `reportRepository.syncPendingReports()` only on an offline→online transition (not on every
  online emission, to avoid re-running the drain redundantly while already online). Uses a
  `CoroutineScope(SupervisorJob())`, not `GlobalScope`, satisfying the project's coroutine rule
  while still being appropriately process-lifetime-scoped (this is the one place in the app where
  that scope belongs, since it must outlive any single screen/ViewModel). Documented as a
  known trade-off in `PLACEHOLDERS_TODO.csv`: this only runs while the process is alive; a real
  production app might prefer `WorkManager` with a `NetworkType.CONNECTED` constraint for
  guaranteed background delivery even if the app was killed while offline.
- **Duplicate check**: found already fully wired from a prior phase (`CaptureViewModel.submit()`
  calls `reportRepository.findNearbyRecentReports(...)` before `doSubmit()`, and `CaptureScreen`
  already renders an `AlertDialog` for `CaptureStage.DUPLICATE_CHECK` with "Submit anyway" /
  "View existing" actions wired to `viewModel.submitAnyway()` / `onViewExistingReport`). No changes
  were needed for item 4 of this phase's task list; verified rather than re-implemented.
- **`NotificationHelper`** (`util/NotificationHelper.kt`): new class wrapping
  `NotificationManagerCompat`/`NotificationCompat.Builder`. Creates a single
  `report_status_updates` `NotificationChannel` (API 26+) in its constructor. `notifyStatusChanged`
  checks the `POST_NOTIFICATIONS` runtime permission via `ContextCompat.checkSelfPermission` on
  API 33+ before posting (silently no-ops if not granted, rather than crashing — posting without
  the permission granted would throw a `SecurityException` on API 33+). Notification ID is derived
  from `reportId.hashCode()` so repeated updates to the same report replace rather than stack.
- **Status-change listener**: implemented as new `ReportRepository.startStatusChangeListener(uid)`
  / `stopStatusChangeListener()` methods (not a separate service class) inside
  `ReportRepositoryImpl`, since the repository already owns the Firestore `FirebaseFirestore`
  instance and `reportsCollection` reference used by the rest of the class. Internally keeps an
  in-memory `MutableMap<reportId, lastKnownStatus>`; each `addSnapshotListener` callback on
  `whereEqualTo(userId, uid)` diffs the new status against the map and calls
  `notificationHelper.notifyStatusChanged(...)` only when a report's status has actually changed
  since the last snapshot for a report already seen before (a report's very first snapshot seeds
  the map without notifying, so a user isn't notified about the current status of every one of
  their reports the moment the listener starts).
- **Listener lifecycle wiring**: started from `HomeViewModel.init {}` — chosen because `HomeScreen`
  is the first screen shown after auth-gated navigation lets a logged-in user into the main app
  (per the existing screens phase's own notes, no `MainActivity`/nav-graph exists yet for this
  phase to hook a more central "on login" lifecycle event into), and stopped from
  `ProfileViewModel.logout()` right before calling `authRepository.logout()`, so the listener does
  not keep running (and does not leak a `uid` from a signed-out session) after logout.
- **`POST_NOTIFICATIONS` (Android 13+)**: no new plumbing needed — the Auth & Permissions phase
  already added `AppPermission.NOTIFICATIONS` to `PermissionManager`/`PermissionDashboardScreen`
  (both are already `AppPermission`-generic, iterating `entries`), already declared
  `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` in the manifest, and
  `PermissionExplainerScreen` is a generic reusable composable that takes an icon/title/body/
  onContinue — so wiring an explainer screen specifically before the first notification-permission
  request is a matter of the future nav-graph phase passing `AppPermission.NOTIFICATIONS`'s
  copy/icon into an existing `PermissionExplainerScreen` route, not new permission-handling code.
  `NotificationHelper.notifyStatusChanged` itself defensively re-checks the grant at call time
  regardless of whether that explainer flow ran, so a denied/never-asked permission just means no
  notification is posted rather than a crash.
- **Files created**: `util/ConnectivityObserver.kt`, `util/NotificationHelper.kt`.
- **Files modified**: `data/repository/ReportRepository.kt` (added `syncPendingReports`,
  `startStatusChangeListener`, `stopStatusChangeListener`), `data/repository/ReportRepositoryImpl.kt`
  (offline-aware `submitReport`, sync + listener implementations), `MobAppAssignment.kt`
  (app-scoped sync collector), `ui/home/HomeViewModel.kt` (starts status listener),
  `ui/profile/ProfileViewModel.kt` (stops status listener on logout).
- **Build verification**: same pre-existing Gradle plugin-apply failure noted by the prior phase
  reproduced again in this session (`./gradlew --stop` + cache clear did not resolve it), confirming
  it is an environment/toolchain issue unrelated to source changes in either phase. Reviewed all
  new/modified files manually against existing types instead.

---

## 2026-07-28 — Wiring / Final Integration phase

- **Navigation graph**: Added `MainActivity.kt` (single-Activity, `@AndroidEntryPoint`,
  `ComponentActivity`) hosting a Compose `NavHost` with two top-level nested graphs off
  `Destinations.AUTH_GRAPH` and `Destinations.MAIN_GRAPH` (two new route constants added to
  `ui/nav/Destinations.kt`): the auth graph (Login/Register) and the main graph (Home, Reports tab,
  My Reports, Profile, Settings, Permission Dashboard, Capture, Report Detail, and Admin Reports —
  Admin only reachable/rendered when `role == ADMIN`). Start destination is chosen by collecting
  `AuthRepository.currentUser` as state in `MainActivity` — `null` -> auth graph, non-null -> main
  graph — which combined with `popUpTo(...) { inclusive = true }` on login/register success and on
  logout gives the auth-gated navigation the scope requires (item 20).
- **Bottom navigation**: `MainScaffold` private composable in `MainActivity.kt` wraps
  Home/Reports/MyReports/Profile screens in a Material 3 `Scaffold` + `NavigationBar`, with a 5th
  `Admin` item appended to the item list only when `isAdmin` is true. Capture, Report Detail,
  Settings, and Permission Dashboard are pushed as full-screen routes without the bottom bar (they
  are "drill-in" destinations, not top-level tabs), matching how each screen was already built to
  take its own `TopAppBar`/back-style navigation.
- **Reports tab (list + map)**: added `ui/reports/ReportsTabScreen.kt`, a small new composable that
  hosts a Material 3 `TabRow` toggling between the existing `ReportsListScreen` (`ReportsScope.ALL`)
  and the existing `MapPlaceholderScreen` — this is pure composition of two already-built screens
  under one nav route (`Destinations.REPORTS`), not a new feature.
- **Report Detail route**: `ReportDetailViewModel` already reads `reportId` off
  `SavedStateHandle["reportId"]`, so `composable(Destinations.REPORT_DETAIL_ROUTE) { ReportDetailScreen() }`
  works unmodified — Navigation Compose populates the SavedStateHandle from the `{reportId}` path
  argument automatically, no extra arguments block was needed since the route only has one segment.
- **Permission Dashboard ViewModel**: `PermissionDashboardViewModel` was built in an earlier phase as
  a plain `AndroidViewModel` (not `@HiltViewModel`), so it can't use `hiltViewModel()`. Wired it with
  an inline `ViewModelProvider.Factory` inside the `PERMISSION_DASHBOARD` composable route
  (constructs it with `LocalContext.current.applicationContext as Application`) rather than
  retrofitting it to Hilt, to keep this phase's changes to wiring only, not redesigning an existing
  ViewModel's DI style.
- **Shake detection at Activity/lifecycle level**: `MainActivity` collects
  `shakeDetector.onShakeDetected()` inside `LifecycleStartEffect(Unit)` (androidx.lifecycle 2.8.7,
  `lifecycle-runtime-compose`), which (re)launches the collecting coroutine on `ON_START` and cancels
  it in `onStopOrDispose` on `ON_STOP`. `ShakeDetector.onShakeDetected()` is a `callbackFlow` that
  registers the accelerometer/proximity `SensorEventListener`s on flow-collection start and
  unregisters them in `awaitClose`, so tying collection to `ON_START`/`ON_STOP` means the
  `SensorManager` listeners are registered/unregistered in step with the Activity's visible
  lifecycle (the onResume/onPause-equivalent composable lifecycle states), preventing the sensor
  listener leak the assignment's non-functional requirements call out. A shake anywhere in the app
  navigates to `Destinations.CAPTURE` with `launchSingleTop = true` so repeated shakes while already
  on the capture screen don't stack duplicate destinations.
- **Compose theme**: no `ui/theme` package existed from prior phases (only the legacy XML
  `Theme.Mob_app_assignment` in `themes.xml` used as the Activity's window theme pre-Compose-draw).
  Added a minimal local `AppTheme` composable in `MainActivity.kt` using Material 3
  `lightColorScheme()`/`darkColorScheme()` defaults keyed off `isSystemInDarkTheme()`, rather than
  inventing a custom color palette, since no design/branding phase defined one and the scope is
  locked against adding new features.
- **AndroidManifest.xml**: added the missing `<activity android:name=".MainActivity">` declaration
  (exported, MAIN/LAUNCHER intent filter, uses the existing `Theme.Mob_app_assignment` XML theme as
  its window background theme prior to Compose taking over). No other manifest changes were needed —
  all permissions (`CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`,
  `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`) and the `FileProvider` were already declared by prior
  phases and did not need adjustment.
- **firestore.rules / storage.rules review**: read both against the final `Report`/`User` models.
  `role.name` (Kotlin enum, e.g. `"ADMIN"`) is what `AuthRepositoryImpl` writes to Firestore, matching
  the rules' `get(...).data.role == 'ADMIN'` check exactly (case-sensitive, uppercase on both sides).
  The Storage rule's `report_photos/{userId}/{fileName}` path matches
  `FirestoreConstants.reportPhotoStoragePath(userId, reportId)` used by `ReportRepositoryImpl`'s
  upload path. No rule changes were necessary — the data model produced by earlier phases already
  lines up with the rules written in the Foundation phase.
- **Cross-file reference check**: walked every screen's `@Composable fun` signature
  (`ui/**/*.kt`) against what `MainActivity`'s nav graph passes in — all matched prior phases'
  contracts as built (e.g. `MyReportsScreen(onReportClick)`, `AdminReportsScreen(onReportClick)`,
  `SettingsScreen(onOpenPermissionDashboard)`) with no signature mismatches found, so no ViewModel or
  screen code needed correction in this phase.
- **Known gap carried forward, not fixed in this phase**: `PermissionExplainerScreen` (item 14,
  consent-before-first-request) is a fully built, reusable composable but is not yet invoked from
  `CaptureScreen`/`CaptureViewModel`'s camera/location permission requests — that wiring belongs to
  the Capture screen's own permission-request flow (choosing when "first-time" triggers it) rather
  than the navigation graph, so redesigning `CaptureScreen` to call it was out of scope for a
  wiring-only pass; flagging here instead of silently leaving it unmentioned. See
  `PLACEHOLDERS_TODO.csv` for a tracked row.
- **Build verification**: attempted `./gradlew :app:compileDebugKotlin` after `--stop` + clearing
  `.gradle`/`app/build`/`build`/`.kotlin` caches; reproduced the same pre-existing failure documented
  by the previous phase (`Cannot add extension with name 'kotlin', as there is an extension already
  registered with that name` when applying `org.jetbrains.kotlin.android`) even on a bare `gradlew help`
  with no project code evaluated yet — i.e. it fails before any Kotlin source in this repo is
  compiled, confirming it is a Kotlin Gradle Plugin 2.1.0 vs. Gradle 9.4.1 toolchain incompatibility,
  not something introduced by this or any prior phase's source changes. Recommend either upgrading
  the Kotlin Gradle plugin version in `gradle/libs.versions.toml` to one certified for Gradle 9.4, or
  pinning the Gradle wrapper to a version in the Kotlin 2.1.0 compatibility matrix, before the next
  build attempt. All new/modified Kotlin files in this phase were instead reviewed manually
  line-by-line against the exact signatures of the composables/ViewModels/repositories they call.

---

## 2026-07-29 — Real Firebase project connected; Storage dropped by design

- Created a real Firebase project (`mobile-app-dev-12761`), registered the Android app under
  `com.king.mob_app_assignment`, and replaced the placeholder `google-services.json` with the real
  config. Firestore database provisioned and `firestore.rules` deployed.
- **Firebase Storage decision**: Firebase now requires the Blaze (pay-as-you-go) plan to enable
  Cloud Storage, even though usage would stay within the free tier at coursework scale. The user
  chose not to add billing to the Google account for this assignment.
- **Resulting architecture change**: `ReportRepositoryImpl.createReport` no longer uploads photos to
  Firebase Storage. Instead, the captured photo's local file path (`localPhotoPath`) is written
  directly into the Firestore report document, and `photoUrl` is always null. Report *data* (GPS,
  heading, category, note, status) still syncs fully to Firestore/multi-device; only the photo
  itself remains device-local. `ReportDetailScreen`'s `AsyncImage` already falls back to
  `localPhotoPath` when `photoUrl` is null (Coil resolves a local file path natively), so no UI
  change was needed.
- **Report framing (Section 5.7, Limitations)**: this should be described as a deliberate scope
  trade-off, not a bug — real backend auth/Firestore sync is demonstrated end-to-end; only object
  storage for media was descoped to avoid a billing dependency for a coursework submission. Future
  improvement: re-enable Storage upload (code pattern preserved in git history / prior Offline &
  Sync section above) if billing becomes acceptable, or substitute a free alternative
  (e.g. Cloudinary/Supabase Storage free tier) if cloud photo sync is required later.
- Remaining Firebase console steps still needed: enable Email/Password sign-in method under
  Authentication, and (optionally, only if Storage is reconsidered) enable Cloud Storage +
  deploy `storage.rules`.

---

## 2026-07-29 — Fixed crash: camera launched without a permission check

- **Bug found via device testing**: tapping "Report an Issue" crashed the app with
  `SecurityException: Permission Denial ... with revoked permission android.permission.CAMERA`.
  Root cause: `CaptureScreen`'s `LaunchedEffect(Unit)` fired the system camera intent
  unconditionally on screen entry, with no runtime permission check beforehand — the CAMERA
  permission had never actually been requested from the user, so Android denied the intent.
- This was the same gap already flagged in the placeholder log as "PermissionExplainerScreen built
  but never wired into Capture" — fixing the crash and closing that gap were the same change.
- **Fix**: added a `permissionStep` state machine to `CaptureScreen` gating camera launch behind:
  `CAMERA_EXPLAINER` (shows the existing `PermissionExplainerScreen`) → `CAMERA_REQUEST` (real
  Android runtime permission dialog) → `LOCATION_EXPLAINER` → `LOCATION_REQUEST` → `READY` (only
  then is the camera intent launched). Reuses the existing `PermissionManager`/`AppPermission`
  utilities from the Auth & Permissions phase rather than adding new permission-checking logic.
- This is a good example for the report's Section 5.4 (Security Implementation) /
  Section 5.6 (Testing): a real runtime crash caught by testing on-device, traced to a missing
  permission gate, fixed by enforcing the consent-then-request flow the assignment brief requires
  anyway — device testing directly surfaced a security/permissions gap that static review missed.
- Verified with `compileDebugKotlin` (BUILD SUCCESSFUL).

---

## UI/UX Design System

### Context
The app previously used bare Material 3 defaults (lightColorScheme()/darkColorScheme() with zero
customization, stock Typography(), default Shapes(), inline in a private AppTheme composable
inside MainActivity.kt). This phase replaces that with a dedicated, reusable design system under
ui/theme/ and ui/components/, following the locked "civic/trustworthy" direction. No other screens
were modified in this phase - only the theme foundation and shared components were built, ready
for the next phase to apply across all 14+ screens.

### Color system (ui/theme/Color.kt, ui/theme/Theme.kt)
Chosen using Material 3 tonal-palette conventions (10/20/30/40/80/90/95/99 tones) so every
ColorScheme role (primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, tertiary,
error, background, surface, surfaceVariant, outline, etc.) is filled in deliberately, not just a
couple of overridden values.

- Primary - Civic Blue (#0B4F6C light / #9FCBE0 dark): a deep, desaturated blue-teal. Evokes
  municipal/official trust (similar family to many city government and 311-style brand colors)
  without being cold or corporate. Used for navigation, primary buttons/links, app bar, and the
  launcher icon background so the brand reads consistently from home screen to in-app.
- Secondary/Accent - Civic Amber (#B2650A light / #FFB871 dark): reserved specifically for the
  urgent "report an issue" action - the shake/capture CTA (PrimaryActionButton). Kept out of
  routine navigation/secondary chrome so it retains visual urgency: when the user sees amber, it
  means "this is the action that matters right now."
- Tertiary - Civic Teal-green (#1C6E5D light / #83D5C0 dark): a supportive accent for
  positive/secondary emphasis, and reused as the base hue for the "Resolved" status color.
- Neutrals: standard Material neutral/neutral-variant ramps for backgrounds, surfaces, outlines -
  kept close to true neutral (not tinted) so content (photos, category chips, status badges) stays
  the visual focus rather than competing with a tinted background.
- Error: standard Material red ramp (#BA1A1A light / #FFB4AB dark) - kept semantically reserved
  for actual errors, distinct from the amber urgent-action accent so the two are never confused.
- Semantic report-status colors (independent constants, consumed by StatusBadge):
  - SUBMITTED -> civic blue (received, awaiting triage - calm, neutral)
  - IN_REVIEW -> amber (actively being worked - draws attention, matches the "urgent" amber family
    conceptually without reusing the exact CTA color/container pairing)
  - RESOLVED -> green (positive outcome, distinct hue so status is scannable at a glance in a list
    of reports)
  Each status has a light/dark container + content pair meeting WCAG-reasonable contrast (verified
  by construction against Material tonal contrast guidance: dark-on-light-container in light mode,
  light-on-dark-container in dark mode - never text directly on a saturated color).
- Accessibility/contrast: all "on*" roles pair a tone-10/20 (very dark) color with a tone-90/95
  (very light) container, or the tone-40 base with tone-100/white text, which is the standard
  Material 3 approach to guarantee >=4.5:1 contrast for body text and >=3:1 for large text/icons.

### Typography (ui/theme/Type.kt)
Full Typography() override using FontFamily.Default (system font) with deliberate weight/size/
letter-spacing choices per Material 3 type scale, rather than accepting the stock scale as-is:
- headlineLarge/headlineMedium use FontWeight.Bold/SemiBold with slightly negative letter spacing -
  reads as confident, professional screen titles (report list headers, screen titles).
- titleMedium/titleSmall use SemiBold/Medium - for card headers, section labels (e.g. report
  category in a list item).
- bodyLarge/bodyMedium stay Normal weight with slightly open letter-spacing for comfortable reading
  of report descriptions/addresses.
- labelLarge (buttons) is bumped to SemiBold with wider tracking so CTAs read as distinctly
  "actionable" compared to body text - important since PrimaryActionButton and StatusBadge both
  rely on labelLarge/labelMedium for their text.
No custom font assets were added - system font + weight variation is enough for a professional
hierarchy without adding APK size or licensing complexity.

### Shape system (ui/theme/Shape.kt)
One consistent, generous-rounded-corner language chosen (not the sharper alternative) to read as
approachable civic-tech rather than bureaucratic/harsh, while staying clean and geometric:
- extraSmall 8dp - chips, small controls
- small 12dp - buttons, text fields
- medium 16dp - cards, dialogs
- large 24dp - bottom sheets / prominent containers
- extraLarge 28dp - full-screen sheets
- A dedicated PillShape (RoundedCornerShape(percent = 50)) is used only for StatusBadge, so status
  pills are visually distinct from cards/buttons/chips (fully rounded vs. corner-radius rounded) -
  a small deliberate exception that still fits the "rounded" language rather than mixing in a
  sharp-cornered element.
This single scale is applied via MaterialTheme.shapes in Theme.kt and referenced explicitly in
component styling (CategoryChip, PrimaryActionButton) so future screens inherit it automatically
through MaterialTheme.shapes.* rather than hardcoding radii.

### Reusable components (ui/components/)
Built specifically because 14+ screens each had ad hoc TopAppBar, CircularProgressIndicator,
FilterChip, and text-only status rendering - consolidating these is what makes the redesign
consistent app-wide rather than a one-screen reskin:
- AppTopBar.kt - CenterAlignedTopAppBar wrapper, consistent title style/back button/actions,
  themed via MaterialTheme.colorScheme.surface.
- StatusBadge.kt - pill badge for ReportStatus, using the semantic status colors above.
- CategoryChip.kt - standardized FilterChip wrapper (found existing ad hoc FilterChip/AssistChip
  usage in ui/reports/ReportListComponents.kt and ui/capture/CaptureScreen.kt; this component is
  the future refactor target for both rather than a third divergent style).
- PrimaryActionButton.kt - full-width, 56dp-tall, amber-accent CTA for "Report an Issue", "Submit",
  "Register"-class actions, with a built-in loading state.
- EmptyState.kt - icon + title + message empty-list placeholder. Prior to this, screens (Home's
  recent activity, Reports list, My Reports, Admin list) either showed nothing or a single plain
  Text; this is a new, additive polish item for lists with no data.
- LoadingIndicator.kt - centered CircularProgressIndicator wrapper, consolidating inconsistent
  inline usages across AdminReportsScreen, MapPlaceholderScreen, ReportDetailScreen,
  ReportsListScreen, LoginScreen, RegisterScreen, CaptureScreen.

### Native Android theme + launcher icon
- res/values/themes.xml / res/values-night/themes.xml: replaced default Theme.MaterialComponents
  purple/teal template colors with the civic blue/amber palette (and a matching windowBackground/
  statusBarColor) so the pre-Compose splash/window background matches the in-app theme - no
  flash-of-wrong-color on launch.
- res/values/colors.xml: added named civic_* color resources kept in sync with the hex values in
  ui/theme/Color.kt; legacy purple_*/teal_* template colors were left in place (unused by the
  theme now) to avoid breaking any other lingering references, per minimal-blast-radius intent.
- res/drawable/ic_launcher_background.xml: recolored the adaptive icon background from the default
  Android Studio template green (#3DDC84) to civic blue (#0B4F6C); vector shape/adaptive-icon
  structure (grid lines, viewport, foreground robot mark) left untouched per instructions - only
  the fill color changed, so the home-screen icon now reads as branded rather than
  generic-template.

### Principles applied
- Consistency: one color system, one type scale, one shape scale, consumed through MaterialTheme.*
  everywhere rather than hardcoded values - future screens automatically match.
- Visual hierarchy: bold/tight headlines vs. open/regular body text vs. semibold wide-tracked
  labels create clear scannability; amber is deliberately scarce (reserved for the single most
  important action per screen) so it always draws the eye correctly.
- Material 3 best practices: full tonal ColorScheme (not just primary/secondary), Shapes() and
  Typography() objects wired through MaterialTheme, CenterAlignedTopAppBar with proper
  TopAppBarDefaults color roles.
- Accessibility/contrast: every text-on-color pairing follows the tone-10/20-on-tone-90/95 (or
  tone-100-on-tone-40) Material convention to keep contrast ratios in an accessible range in both
  light and dark themes; status badges never place text directly on a saturated/base-tone color.
- Verified with compileDebugKotlin (BUILD SUCCESSFUL) after adding the theme package, components
  package, and MainActivity.kt theme swap.

---

## 2026-07-29 — Auth screens restyle (LoginScreen, RegisterScreen)

- **New shared composable**: `ui/auth/AuthBrandHeader.kt` - an icon badge (Icons.Filled.Campaign in
  a primaryContainer-colored MaterialTheme.shapes.medium box) + "Shake to Report" wordmark in the
  primary color + a screen-specific headline (typography.headlineMedium) + subtitle
  (typography.bodyMedium, onSurfaceVariant). Used by both LoginScreen ("Welcome back") and
  RegisterScreen ("Create account") so first-time users see consistent branding before any form
  field, rather than the previous zero-branding plain-text titles. Factored into one file since
  both screens are structurally identical here (icon + wordmark + headline + subtitle stack).
- **LoginScreen.kt / RegisterScreen.kt**: replaced raw `Button` + inline `CircularProgressIndicator`
  submit buttons with `PrimaryActionButton` (amber accent CTA, built-in `isLoading` state) so the
  submit action visually matches the same "urgent primary action" styling used by the
  shake/capture CTA elsewhere in the app. Added `shape = MaterialTheme.shapes.small` to every
  `OutlinedTextField` so fields pick up the app's rounded-corner language explicitly (Compose
  defaults text field shape to `MaterialTheme.shapes.extraSmall`, which no longer matches the
  12dp/small radius used for buttons/fields elsewhere in the design system). Error text now uses
  `MaterialTheme.typography.bodyMedium` instead of default (unstyled) Text so it's consistent size
  with the rest of the form.
- No hardcoded colors were found in either screen (all colors already went through
  `MaterialTheme.colorScheme.error` / default Text color) - nothing to strip there.
- Kept all existing callback signatures unchanged: `LoginScreen(onLoginSuccess, onNavigateToRegister,
  viewModel)`, `RegisterScreen(onRegisterSuccess, onNavigateToLogin, viewModel)`. No ViewModel
  changes. No raw Material3 TopAppBar was present on either screen (they use bare `Scaffold` with no
  top bar), so `AppTopBar` was not applicable here — left as-is since adding a back-navigable top bar
  to first-screen auth flows would be a UX change beyond a pure visual restyle.
- No new placeholders/stubs introduced.

---

## 2026-07-29 — Restyle pass: Home + Permission screens

Applied the design system to `ui/home/HomeScreen.kt`, `ui/permissions/PermissionExplainerScreen.kt`,
and `ui/permissions/PermissionDashboardScreen.kt`. Pure UI restyle - no ViewModel/business logic
changes, all existing composable parameter names/signatures preserved.

- **HomeScreen**: replaced raw `TopAppBar` with `AppTopBar`, raw `Button` with `PrimaryActionButton`
  (amber CTA) for "Report an Issue" - this is the app's core action so it now gets the reserved
  accent color per the locked design direction. Shake-hint text now uses `onSurfaceVariant` instead
  of default text color for correct visual hierarchy (secondary/tip copy vs. primary content).
  Recent-activity list now branches on loading/empty/populated state using the shared
  `LoadingIndicator` and `EmptyState` components (previously: a single inline plain-Text empty
  message, no loading state at all). Each `ReportListItem` row now renders its status via
  `StatusBadge` (semantic status color) passed through the existing `trailingContent` slot, instead
  of relying only on the plain colored `Text` already inside `ReportListItem` itself - required no
  change to `ReportListItem`/`ReportListComponents.kt`.
- **PermissionExplainerScreen**: was already theme-clean (no hardcoded colors, already reading
  `MaterialTheme.colorScheme`/`typography`) - confirmed via read-through, no color logic changed.
  Polished only: icon bumped 72dp -> 88dp for more visual presence on a screen that's otherwise
  mostly whitespace, added explicit `onSurface`/`onSurfaceVariant` colors to title/body text so they
  don't silently rely on Text's implicit LocalContentColor default, and swapped the raw `Button` for
  `PrimaryActionButton` so the continue action matches the app's CTA styling everywhere else.
- **PermissionDashboardScreen**: replaced raw `TopAppBar` with `AppTopBar`. Replaced the plain
  "Granted"/"Not granted" `Text` with a new small private `GrantedStatusChip` composable (pill
  shape, reusing `PillShape` and the same visual pattern as `StatusBadge` - green container/text for
  granted via the existing `StatusResolvedLight*` status colors, `errorContainer`/
  `onErrorContainer` for not-granted) so permission state reads as a scannable chip rather than
  plain text, consistent with how report status reads elsewhere in the app. Card and Grant/Open
  Settings button now explicitly use `MaterialTheme.shapes.medium`/`.small` respectively rather than
  Material3 defaults, matching the app's single rounded-corner shape language. Kept
  `GrantedStatusChip` local/private to this file rather than promoting it to `ui/components` since
  it is a granted/denied-specific two-state chip, not a general-purpose component like `StatusBadge`
  (which is keyed off the 3-value `ReportStatus` enum) - no other screen in this group needed a
  third reusable chip variant.
- No new placeholders/stubs introduced. Files changed: `ui/home/HomeScreen.kt`,
  `ui/permissions/PermissionExplainerScreen.kt`, `ui/permissions/PermissionDashboardScreen.kt`. Not
  run through `compileDebugKotlin` in this session (same pre-existing Gradle/Kotlin plugin
  toolchain conflict documented in earlier phases); reviewed manually against the exact
  `AppTopBar`/`StatusBadge`/`PrimaryActionButton`/`EmptyState`/`LoadingIndicator` signatures read
  directly from `ui/components/*.kt` before use.

---

## 2026-07-29 — Capture flow restyle (ui/capture/CaptureScreen.kt)

Pure visual restyle of the app's signature shake -> camera -> form -> submit flow, using the
design-system components built in the prior phase. No changes to CaptureViewModel, the
permissionStep state machine, or any callback signatures/names.

- Replaced the raw `TopAppBar` with `AppTopBar(title = "Report an issue")`.
- **CategoryChip**: the ad hoc `FilterChip` category row in `FormStep` now uses the shared
  `CategoryChip(label, selected, onClick)` component instead of a hand-rolled `FilterChip`, so it
  matches the civic palette/shape used everywhere else chips appear (e.g.
  `ui/reports/ReportListComponents.kt`).
- **AnalyzingStep**: replaced the bare `CircularProgressIndicator` + single line of text with the
  shared `LoadingIndicator` component (sized to 64dp) plus a two-line copy treatment (bold title +
  supporting detail line) and distinct copy for the "Analyzing photo" vs. "Submitting report"
  states, so the two loading moments read as intentional rather than an identical generic spinner.
- **PreviewStep**: added a short "Review your photo" title + helper line above the actions for
  clearer intent, clipped the photo preview to `MaterialTheme.shapes.medium`, and fixed the
  Retake/Confirm button hierarchy per spec - Retake is now a full-height `OutlinedButton` (secondary,
  outline-only) and Confirm is the shared `PrimaryActionButton` (amber, primary), both in a
  `weight(1f)` row so they read as a clear "safe fallback vs. main action" pair instead of two
  visually equal buttons.
- **Metadata display**: replaced the four stacked plain `Text` lines (address/coords/heading/
  timestamp) with a new private `CaptureMetadataCard` - a `Card` (surfaceVariant background,
  `shapes.medium`) containing icon+label+value rows built from a small private `MetadataRow`
  helper, using `Icons.Filled.LocationOn` (address+coordinates), `Icons.Filled.Explore` (heading),
  and `Icons.Filled.Schedule` (captured timestamp) - satisfies the "card-based layout and icons
  instead of plain stacked Text" requirement without touching any ViewModel field.
- **Submit button**: the final submit `Button` in `FormStep` is now `PrimaryActionButton("Submit",
  onClick = viewModel::submit)` - amber accent, since submitting a report is the app's core urgent
  action, matching `PrimaryActionButton`'s documented intent.
- Also clipped the photo preview in `FormStep` to `MaterialTheme.shapes.medium` and gave the
  `OutlinedTextField`/note field the shared `shapes.small` corner radius, so every rounded surface
  on the screen (photo, card, chips, buttons, text field) draws from the same one shape scale.
- No new data was needed from the ViewModel - icon selection is a fixed icon-per-metadata-row
  mapping local to the metadata card, not a per-category/per-status enum extension, so no private
  helper extension function was required for this screen.
- Not touched: `CaptureViewModel.kt` (business logic, method signatures untouched),
  `permissionStep` state machine logic (only wrapped in the same `AppTopBar`/`Scaffold`, control
  flow unchanged), `AlertDialog` duplicate-check dialog (left as stock Material3 `AlertDialog` -
  no themed dialog wrapper exists yet in the design system to swap in).
- No new placeholders/stubs introduced.

## 2026-07-29 — Design System applied: Profile, Settings, Admin Reports group

Restyled `ui/profile/ProfileScreen.kt`, `ui/settings/SettingsScreen.kt`, and
`ui/admin/AdminReportsScreen.kt` to use the design system built in the prior UI/UX phase. Pure
visual restyle only — no ViewModel/business logic touched, all existing composable parameter names
(`onLoggedOut`, `onOpenPermissionDashboard`, `onOpenSettings`) unchanged.

- **New shared component**: `ui/components/NavigationRow.kt` — a themed tappable list row (leading
  icon in primary tint, label, trailing chevron) factored out because both ProfileScreen ("Permission
  Dashboard", "Settings" links) and SettingsScreen ("Permission Dashboard" link) needed the exact same
  visual pattern; avoids duplicating the row layout in two files.
- **ProfileScreen**: added a profile header card (`surfaceVariant` container, `MaterialTheme.shapes.medium`)
  with a circular primary-color avatar placeholder (`Icons.Filled.Person` in a `CircleShape` box tinted
  `colorScheme.primary`), display name, email, and a pill-shaped role badge (reusing the same
  `PillShape` StatusBadge uses, colored with `primaryContainer`/`onPrimaryContainer`). Navigation links
  now use `NavigationRow` inside a card instead of plain `OutlinedButton`s. Logout restyled as an
  `OutlinedButton` with `colorScheme.error` content/border (secondary/destructive-leaning, visually
  distinct from the amber primary-action accent) plus a `Logout` icon. Replaced raw `TopAppBar` with
  `AppTopBar`.
- **SettingsScreen**: shake-sensitivity section now lives in a `Card` with a clear title, helper text,
  themed `Slider` (primary-colored thumb/active track), and a labelLarge "Current threshold" readout in
  the primary color. Permission Dashboard link converted from `OutlinedButton` to `NavigationRow` inside
  its own card, matching Profile's link styling. Replaced raw `TopAppBar` with `AppTopBar`.
- **AdminReportsScreen**: replaced raw `TopAppBar`/inline `CircularProgressIndicator`/plain "No reports
  found" `Text` with `AppTopBar`/`LoadingIndicator`/`EmptyState` respectively — now matches
  `ReportsListScreen`'s chrome exactly. Continues to reuse the same `ReportListItem` +
  `ReportFilterBar` + `StatusDropdown` composables from `ui/reports/` as before (no divergent second
  list style was created), only the surrounding screen chrome changed.
- Left `ReportsListScreen.kt` itself untouched in this pass (it was not in this group's task scope,
  though its own `TopAppBar`/inline loading/empty text remain a candidate for a future pass to
  match `AdminReportsScreen`'s new consistency — flagged here, not a scope creep fix).

## 2026-07-29 — Design System applied: Reports/Map/StatusDropdown group

Restyled `ui/reports/ReportListComponents.kt`, `ui/reports/ReportsListScreen.kt`,
`ui/reports/MyReportsScreen.kt`, `ui/reports/ReportDetailScreen.kt`,
`ui/reports/ReportsTabScreen.kt`, `ui/reports/MapPlaceholderScreen.kt`, and
`ui/reports/StatusDropdown.kt`. Pure visual restyle — no ViewModel/business logic touched, all
existing composable parameter names (`onReportClick`, `title`, `scope`, `currentStatus`,
`onStatusSelected`) unchanged.

- **New private helpers in `ReportListComponents.kt`**: `ReportCategory.icon()` (extension function
  returning an `ImageVector` per category — Construction/Lightbulb/Delete/WaterDrop/Help) and
  `ReportCategory.displayLabel()`/`ReportStatus.label()` (human-readable strings instead of raw enum
  `.name`), added as private-to-screen helpers per the task rule rather than touching the domain
  enums or any ViewModel.
- **`ReportListItem`**: now a proper `Card` with `CardDefaults.cardElevation`, category icon +
  `displayLabel()` instead of `report.category.name`, and `StatusBadge` (from `ui/components`)
  replacing the old plain colored status `Text`.
- **`ReportFilterBar`**: status/category `FilterChip`s replaced with the shared `CategoryChip`
  component (already existed from the design-system phase) so filter chips match the same shape/
  color language as the category selector on the Capture screen; the sort-order toggle kept as an
  `AssistChip` but themed to `shapes.extraSmall` + `surfaceVariant` colors with a `SwapVert` icon.
- **`ReportsListScreen`**: raw `TopAppBar`/inline `CircularProgressIndicator`/plain "No reports
  found" `Text` replaced with `AppTopBar`/`LoadingIndicator`/`EmptyState` respectively — matches
  `AdminReportsScreen`'s chrome from the prior phase, closing the gap flagged at the end of that
  phase's note.
- **`MyReportsScreen`**: unchanged (already a 3-line wrapper delegating to `ReportsListScreen`, which
  now inherits all of the above automatically).
- **`ReportDetailScreen`**: replaced raw `TopAppBar` with `AppTopBar`; replaced the plain status
  `Text`/`StatusDropdown` row with `StatusBadge` for non-admins (admins still get `StatusDropdown`);
  category header now shows an icon (`ReportCategory.icon()`) beside `displayLabel()`; the previously
  flat stack of address/coordinates/heading/timestamp/note `Text` composables was restructured into a
  single metadata `Card` (`surfaceVariant` container, `shapes.medium`) with icon-led `DetailRow`s
  (Location/Heading/Captured/Note, using `LocationOn`/`Explore`/`Schedule`/`Notes` icons) for clearer
  visual hierarchy; the photo now gets `clip(MaterialTheme.shapes.medium)` + `ContentScale.Crop`
  instead of an unclipped raw `AsyncImage`.
- **`MapPlaceholderScreen`**: replaced raw `TopAppBar`/inline loading with `AppTopBar`/
  `LoadingIndicator`; empty list now shows `EmptyState` instead of just an empty `LazyColumn`; the
  per-report cards below the dot-grid now show a category icon + `StatusBadge` and are clickable
  (`onReportClick`) instead of being static text-only rows; the dot-grid canvas was upgraded with a
  theme-colored background grid (`outlineVariant` gridlines), a soft halo + stroked ring per dot
  (instead of a single flat filled circle) and dots colored `secondary` for resolved reports vs.
  `primary` for others, so the placeholder reads as an intentional, polished visualization rather than
  a bare debug canvas; the outer container is clipped to `shapes.medium`.
- **`StatusDropdown`**: replaced the bare `TextButton` with an `OutlinedButton` (primary content
  color, `shapes.small`) plus an `ArrowDropDown` icon, and both the button label and menu items now
  use the same `label()` human-readable strings as elsewhere instead of raw enum `.name`.
- **`ReportsTabScreen`**: `TabRow` explicitly themed (`containerColor = surface`,
  `contentColor`/`selectedContentColor = primary`, `unselectedContentColor = onSurfaceVariant`)
  instead of relying on default TabRow colors.
- No new placeholders/stubs introduced; `PLACEHOLDERS_TODO.csv` not touched.

---

## 2026-07-30 — Real app logo + working Remember Me on Login

- **App logo**: created `res/drawable/ic_app_logo.xml`, an original vector mark (not a stock icon)
  combining a shield (civic/official trust), a GPS pin at its core (the app's location-tagging
  identity), and small amber "shake burst" accents on either side — a compact visual summary of
  what the app actually does. Uses the existing civic blue/amber palette. `AuthBrandHeader` (shown
  on both Login and Register) now renders this logo via `Image(painterResource(...))` instead of a
  generic Material `Campaign` icon, centered, at 88dp.
- **Remember Me**: `SecurePrefsManager` already stored `isRememberMeEnabled`/`lastUsedEmail` in
  Keystore-backed `EncryptedSharedPreferences` from the Auth & Permissions phase, but no UI ever
  exposed the toggle — the last-used email was always remembered unconditionally. Added a real
  `Checkbox` + "Remember me" label to `LoginScreen`, wired through `LoginViewModel`
  (`LoginUiState.rememberMe`, defaulted from `securePrefsManager.isRememberMeEnabled`,
  `onRememberMeChange`). On successful login, `lastUsedEmail` is now only persisted if the checkbox
  is checked; if unchecked, any previously remembered email is cleared. This is a small but genuine
  UX + privacy improvement worth mentioning in Section 5.4 (Security Implementation) — the user now
  has explicit, visible control over whether their email is retained on-device, rather than it being
  an invisible default.
- Verified with `compileDebugKotlin` (BUILD SUCCESSFUL).
