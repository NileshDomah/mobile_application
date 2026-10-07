# Shake to Report: A Civic Infrastructure Reporting and Management Platform

**Technical Report — Mobile Application Development Coursework**

---

## 5.1 Introduction

### Purpose of the application

Shake to Report is a native Android application that lets a citizen report a civic
infrastructure issue — a pothole, a broken streetlight, an overflowing bin, a water
leak — in the time it takes to shake a phone and tap twice. The core insight driving
the design is that people notice civic problems constantly but rarely report them,
because the reporting process itself (a phone call, a web form, a generic 311-style
app with many fields) is too slow and too much friction relative to how minor any
single problem feels in the moment. By collapsing "notice a problem → file a report"
into a physical gesture plus a two-tap confirmation, the app removes the friction that
normally kills the intention to report before it becomes an actual report.

Beyond the citizen-facing capture flow, the application also closes the loop on the
other side: an admin/municipal-reviewer role can triage, prioritise, assign, and
resolve reports, and the platform automatically groups related reports into a single
tracked **Incident** when several citizens report the same underlying problem, so a
single pothole is not counted and dispatched as ten unrelated tickets. A later phase
of the project added a rule-based **predictive risk scoring** system (which
areas/categories are trending towards becoming a bigger problem, based on real
historical report data) and a five-layer digital map (reports, incidents,
infrastructure, risk, maintenance).

### Problem statement

Existing municipal reporting channels (phone hotlines, generic web forms, legacy
311-style apps) share three problems: (1) they require many taps/fields before a
report is even started, (2) they treat every report as an isolated ticket with no
sense of "this is the same pothole three other people already reported," and (3)
there is little to no transparency back to the citizen about what happens to a report
after submission. This project addresses all three: a near-instant capture flow, an
incident-clustering layer that recognises duplicate/related reports, and a full
status/resolution feedback loop (including an admin's resolution note and, optionally,
a photo of the fix) visible back to the citizen who filed the report.

### Target users

- **Primary**: members of the public who encounter a civic infrastructure issue in
  daily life and want to report it with minimal effort, and who want to know what
  happened to a report they filed.
- **Secondary**: a municipal admin/reviewer role responsible for triaging incoming
  reports, assigning them to the right department, tracking resolution work (via a
  work-order model), and understanding where infrastructure risk is concentrated
  across the area they manage.

---

## 5.2 System Design

### Architecture

The application follows an **MVVM + Repository** architecture on top of **Jetpack
Compose** for the UI layer, with **Hilt** providing dependency injection throughout.

```
┌─────────────────────────────────────────────────────────────┐
│                        Compose UI Layer                      │
│  Screens (Composables) — one per feature, e.g. CaptureScreen, │
│  ReportDetailScreen, AdminDashboardScreen, ChatScreen          │
└───────────────────────────┬─────────────────────────────────┘
                            │ collectAsState(StateFlow<UiState>)
┌───────────────────────────▼─────────────────────────────────┐
│                    ViewModel Layer (@HiltViewModel)           │
│  One ViewModel per screen; owns UI state, calls repositories, │
│  never touches Android framework APIs directly                │
└───────────────────────────┬─────────────────────────────────┘
                            │ suspend fun / Flow<T>
┌───────────────────────────▼─────────────────────────────────┐
│                      Repository Layer                         │
│  Interface + *Impl pair per domain area (Report, Auth,        │
│  Incident, WorkOrder, RiskScore, Support, AuditLog)            │
└──────┬───────────────┬───────────────┬───────────────┬───────┘
       │               │               │               │
┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
│  Firebase   │ │  Room DB    │ │ WorkManager │ │  Device     │
│  Auth /     │ │  (offline   │ │ (background │ │  Hardware   │
│  Firestore  │ │   queue)    │ │  AI/sync)   │ │  (camera,   │
│             │ │             │ │             │ │  GPS,       │
│             │ │             │ │             │ │  sensors)   │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

This layering satisfies the non-functional "modular and reusable code" requirement
directly: a ViewModel never imports a Firebase or Android Location class itself, so
the persistence/hardware layer underneath any repository could be swapped without
touching UI code, and each repository is independently testable behind its interface.

### Backend

**Firebase** (Authentication + Firestore) is the backend of record. Firestore was
chosen over a local-only database because it gives the app three things a coursework
CRUD app would not otherwise demonstrate: real multi-device data sync, real
token-based authentication, and a real built-in offline-read cache — which pairs with
an app-level **Room** database used specifically as a *write*-side offline queue (a
report captured with no connectivity is staged locally and flushed once connectivity
returns, rather than being lost or blocking the UI).

Firebase Storage (for uploading photos to the cloud) was evaluated but intentionally
**not** enabled, because Firebase Storage requires the paid Blaze billing plan and
enabling billing was judged out of scope for a coursework project with no production
budget. This is a deliberate, documented architectural decision, not an oversight: a
`storage.rules` file securing the intended upload path still exists in the repository,
and photos/videos are instead kept as local file paths written into the corresponding
Firestore document field (`localPhotoPath` / `localVideoPath`), with the
Storage-download-URL field always left null. The consequence — a photo captured on one
device is not visible on another device or after a reinstall — is called out explicitly
in the Limitations section (5.7) rather than hidden.

### Navigation

A single-Activity architecture with **Jetpack Navigation Compose** drives all
screen-to-screen navigation. Three top-level navigation graphs exist: an onboarding
graph (first-run walkthrough), an auth graph (login/register/forgot-credentials), and
a main graph (everything behind sign-in, including a role-gated admin section). A
bottom navigation bar switches between Home, My Reports, Profile, and — for admin
accounts only — Admin Reports, Admin Dashboard, and Admin Support, with deeper
admin-only screens (Incident Detail, Risk Area Detail, Work Orders) reached via
drill-down navigation from the dashboard rather than occupying additional bottom-nav
slots.

### Navigation flow (high level)

```
Splash → Onboarding (first run only) → Login/Register
                                            │
                                            ▼
                    ┌───────────────────────────────────────┐
                    │              Main App                  │
                    │  Home ─┬─ Capture (shake or manual)     │
                    │        └─ My Reports ─ Report Detail     │
                    │  Profile ─ Settings / Permission         │
                    │            Dashboard / Contact Support   │
                    │                                          │
                    │  [Admin only]                            │
                    │  Admin Reports / Admin Dashboard          │
                    │        └─ Incident Detail ─ Work Orders   │
                    │        └─ Risk Area Detail                │
                    │  Admin Support Messages                   │
                    └───────────────────────────────────────┘
```

### UI wireframes

Representative screens (see attached screenshots): the Home screen with a hero
illustration, a "Shake Now" affordance, and a "New Report" button into the form-first
capture flow; the Capture flow (either shake's single-photo camera → preview with
freehand pen annotation → form, or the form-first flow's empty form with an
"Add Photos" grid for attaching one or several photos before filling in category and
metadata); the Report Detail screen with status timeline and citizen-visible
resolution feedback; the admin Dashboard with active-incident and high-risk-area
drill-down cards; and the citizen-facing Map screen with issue-type-colored pins
(Pothole/Streetlight/Garbage/Water Leak/Other).

---

## 5.3 Features Description

### Core reporting flow

- **Shake-to-report**: `ShakeDetector` listens to `TYPE_ACCELEROMETER` and requires a
  minimum number of direction reversals within a rolling time window before firing —
  a genuine shake reverses direction repeatedly; a single jolt or a phone sliding in a
  pocket rarely does. An adjustable sensitivity threshold is exposed in Settings for
  accessibility (not every user can shake a phone equally hard). A proximity-sensor
  pocket check additionally suppresses a detected shake while the phone is held close
  against something (e.g. in a pocket) — gated on the sensor's most recent reading
  being fresh (updated within the last few seconds), so a device whose proximity
  sensor reports a stale or default value is treated as "unknown" rather than having
  shake detection silently blocked forever. A `ShakeBackgroundService` foreground
  service keeps shake detection active even while the app is not in the foreground. A
  manual "New Report" button on the Home screen exists alongside shake as a guaranteed
  fallback trigger, so the flow never depends on a gesture landing correctly; it opens
  a form-first capture flow distinct from shake's own camera-first one (see the
  Camera capture point below).
- **Camera capture**: photo or video capture via the system camera app
  (`ActivityResultContracts.TakePicture`/`CaptureVideo`), or picking an existing
  photo/video from the gallery. A `FileProvider` hands the system camera a writable
  `content://` URI rather than a raw file path. Shake-to-report always captures
  exactly one photo (or one video) before moving to the form. The Home screen's
  "New Report" button instead opens a form-first flow that lands on an empty form
  immediately, with an "Add Photos" tile that can be tapped repeatedly to attach
  several photos to the same report before submitting.
- **Freehand annotation**: on the photo-preview step, the user can draw directly over
  the photo with a finger (a simple red pen, with undo-last-stroke and clear-all) to
  circle or mark exactly where the issue is — useful when the issue in a photo is not
  otherwise obvious (a hairline crack, a small leak). The strokes are baked into the
  actual photo file before submission.
- **GPS location tagging**: `LocationProvider` requests a high-accuracy, fine-granularity
  fix via `FusedLocationProviderClient`, explicitly rejects any fix whose reported
  accuracy is worse than 50 metres (treating an imprecise network/Wi-Fi-derived
  estimate the same as no fix at all, rather than silently storing it as if it were
  exact), and times out after 15 seconds rather than hanging indefinitely if GPS
  cannot lock indoors. The coordinate is reverse-geocoded to a street address via
  `Geocoder`.
- **Compass heading**: `CompassProvider` fuses the accelerometer and magnetometer
  (`SensorManager.getRotationMatrix`/`getOrientation`) to record which direction the
  phone was facing at capture time, shown both as a live heading and an 8-point
  cardinal label (e.g. "NE").
- **AI-assisted category suggestion**: the photo is classified in the background
  *after* submission (not before, so the user is never blocked waiting on a model
  call) by `AiReviewCoordinator`. Classification tries a vision-capable LLM
  (Gemma, served via an OpenAI-compatible chat-completions endpoint) first, and falls
  back automatically to an on-device ML Kit Image Labelling call if that server is
  unreachable, so the app remains fully functional standalone. If the AI disagrees
  with — or cannot confidently determine — the citizen's chosen category, the report
  is flagged for review and the citizen is asked to confirm or correct the category
  from an in-app banner.
- **Category selection is optional at submission time**: a report can be submitted
  as `UNCATEGORIZED` and classified entirely in the background, or the citizen can
  pick from five fixed categories (Pothole, Streetlight, Garbage, Water Leak, Other)
  or a free-text **Custom** category for issues that do not fit the fixed list.
- **Input sanitisation**: the free-text note field is sanitised on every keystroke
  (`NoteSanitizer` strips HTML/script-tag-shaped substrings, trims, and caps at 500
  characters) before it is ever written to Firestore.
- **Anonymous submission**: a toggle lets a citizen submit a report with no owner
  recorded at all (not even the app's own database), at the cost of being unable to
  edit or delete it afterward, since there is no owner to authorise that.
- **Offline resilience**: if the online Firestore write fails (no connectivity, or a
  transient error), the report is written into a local Room queue
  (`PendingReportEntity`) instead of being lost, and a home-screen banner explicitly
  shows "N reports waiting to sync" so a queued report is never silently invisible. A
  `ConnectivityObserver` triggers an automatic flush of the queue once the device
  reconnects.

### Reports, tracking, and the incident/resolution layer

- Personal ("My Reports") history split into **Not Solved** / **Solved** tabs, plus a
  full report-detail screen showing the photo/video (as a tappable thumbnail that
  opens full-screen on demand, to keep the page compact), status timeline, and, once
  an admin resolves it, a citizen-visible "Update from the city" card with the
  resolution note and optional after-photo.
- **Incident clustering**: when a new report is submitted, a background WorkManager
  job (`IncidentClusteringWorker`) evaluates nearby recent reports of the same
  category and computes a transparent, rule-based confidence score (weighing
  geographic distance, category match, and time proximity — a community-upvote
  signal was part of this formula in an earlier phase but was removed along with the
  rest of the upvoting feature; its weight was folded into the category/distance and
  report-count terms instead). A high-confidence match auto-attaches the new report to
  an existing **Incident**; a lower-confidence match is left as a candidate for an
  admin to manually merge; enough unclustered nearby reports (3 or more) can trigger a
  brand new Incident to be proposed automatically. The original citizen reports are
  always preserved — an Incident is an aggregate view layered on top of reports, never
  a replacement for them.
- **Resolution lifecycle for admins**: an Incident carries its own 8-stage status
  (Detected → Validating → Confirmed → Assigned → In Progress → Resolved → Verified →
  Closed), a transparent 0–100 **priority score** (report count, AI-assessed severity,
  incident age, recurrence, and emergency-keyword detection — community upvotes were
  also removed from this formula, same as above — each shown individually rather than
  as an opaque number), an admin validation step
  (confirm or reject the incident as genuine, with a note), and a **Work Order** model
  (department, assigned team, a 10-stage status, inspection/repair notes, and optional
  resolution photos) for tracking the physical dispatch-and-repair process. Every
  administratively significant change on an Incident or Work Order is recorded in a
  shared **audit log** (who, when, previous value, new value, optional note) so no
  important administrative action happens without traceability.
- **Predictive risk scoring**: a periodic background job (`RiskRefreshWorker`)
  computes a transparent 0–100 risk score per (area, category) pair from real
  historical report data — report volume, unresolved backlog, average declared
  severity, confirmed-incident count, a recent-increase trend check, and a
  slow-resolution-history penalty — classified Low/Moderate/High/Critical. Areas with
  too few reports to say anything meaningful are explicitly marked "Insufficient
  data" rather than shown a fabricated low-risk score.
- **Map**: a citizen-facing Google Maps view (`ui/map/MapScreen.kt`) plotting every
  report as a pin colored by issue category (Pothole, Streetlight, Garbage, Water
  Leak, Other/Custom), with a legend and a "locate me" control. An earlier five-layer
  MapLibre/Geoapify "Digital Map" (Reports/Incidents/Infrastructure/Risk/Maintenance
  layers, admin-facing) was built in an earlier phase and has since been removed
  entirely as the citizen experience was simplified — there is no surviving
  multi-layer or infrastructure-asset map view.
- **Support inbox**: a lightweight citizen-to-admin message channel independent of
  the report-status workflow, for questions that are not really about a specific
  report.

### Authentication and roles

- Firebase Authentication (email/password), with an added **username** option — a
  citizen can register a username alongside their email, and log in with either. This
  is implemented via a `/usernames/{username} → uid` reservation document, written
  inside the same Firestore transaction as the user profile document, closing the race
  window between a live-typing "is this username taken?" check and the actual write.
- **Biometric login**: once enabled, the device's fingerprint/face unlock (via
  `BiometricPrompt`, `BIOMETRIC_STRONG`) can decrypt a Keystore-protected copy of the
  password and silently re-run the normal login flow — the biometric layer protects
  the credential *at rest*, it does not change what Firebase's own sign-in API
  requires over the wire (still TLS-protected, same as a manual login).
- **Forgot username / forgot password**: a dedicated recovery screen resolves an
  email to its username (via a parallel `/emails/{email} → uid` reservation document,
  mirroring the username lookup in reverse) and can trigger Firebase's own built-in
  password-reset email. The screen deliberately never confirms or denies whether a
  given email actually has an account, to avoid account enumeration.
- **Role-gated admin views**: a `role` field on the user document (`CITIZEN`/`ADMIN`)
  gates access to every admin screen, enforced both client-side (an `AdminRouteGuard`
  composable redirects a non-admin away) and server-side (Firestore security rules
  independently verify the caller's role before allowing an admin-only write — the
  client-side guard is a UX convenience, not the actual security boundary). Admin
  accounts are never self-service; the only way to create one is for an existing admin
  to edit a user's `role` field directly in the Firebase console, which the
  `firestore.rules` file's account-creation rule enforces server-side (a client cannot
  set its own role to `ADMIN` on registration, regardless of what the app's own UI
  would ever send).

### AI integration (bonus feature)

Two AI-backed features exist: the background photo-category classifier described
above, and an in-app **help chatbot** (`ChatScreen`) scoped by its system prompt to
only answer app/civic-issue-related questions, which can also summarise the signed-in
user's own recent reports as conversation context. Both talk to the same
OpenAI-compatible chat-completions endpoint, served in this project by a local LM
Studio instance running a Gemma model on the developer's machine — a deliberate,
clearly-documented dev/testing-only backend choice for a coursework project with no
budget for a hosted vision/LLM API, not something intended to be mistaken for a
production AI service. The photo classifier's ML Kit fallback ensures the app never
becomes non-functional if that server is unreachable; the chatbot has no equivalent
fallback and shows a plain "assistant unavailable" message in that case, since a
text-chat feature with no answer to give is a much smaller functional loss than a
report that could not be classified at all.

### Multi-language support (bonus feature)

The bottom navigation, authentication screens, Home screen, Settings screen, and
onboarding walkthrough are localised into **English, Spanish, and French**
(`res/values/`, `res/values-es/`, `res/values-fr/strings.xml`), switchable from
Settings with an immediate UI refresh (not requiring an app restart). This is a
deliberately curated subset of the app's full string surface rather than an exhaustive
sweep, chosen to demonstrate the localisation mechanism convincingly on the screens a
user sees most, within the time available for a coursework project.

---

## 5.4 Security Implementation

### Permission handling strategy

Every sensitive runtime permission (Camera, Location, Notifications on Android 13+)
follows the same three-step pattern: an explainer screen states in plain language why
the permission is needed *before* the system dialog ever appears, then the actual
`ActivityResultContracts.RequestPermission()` request fires, and a denial is handled
gracefully rather than crashing — if the user has permanently denied a permission
(`shouldShowRequestPermissionRationale` returns false), the app offers a deep link
straight to the app's system settings page instead of repeatedly asking. A
**Permission Dashboard** screen lets a user review and fix the granted/denied state of
every permission at any time after onboarding, not just at first request.
Accelerometer/magnetometer readings require no runtime permission request at all on
modern Android (they are normal-protection-level sensors, unlike step-counter/health
sensors), which is documented in code so a later contributor does not mistakenly try
to add a request that will never appear.

### Authentication method

Firebase Authentication (email/password) provides the session; Firebase's own SDK
persists and auto-refreshes the underlying ID token, so the app deliberately does
**not** maintain a second, parallel copy of that token — writing one would be
redundant and would create a second, harder-to-invalidate copy of sensitive credential
material. What the app *does* store locally (via
`androidx.security.crypto.EncryptedSharedPreferences`, backed by the Android
Keystore) is limited to non-secret convenience data: whether "remember me" is
enabled and the last-used email, to prefill the login form. Biometric login stores an
AES-encrypted copy of the password behind a Keystore key that itself requires a
successful biometric authentication to unlock — if the user removes all enrolled
fingerprints/face data afterward, that stored credential becomes permanently
undecryptable, which is treated as expected behaviour (the biometric login button
simply stops appearing) rather than a bug needing a cleanup routine.

### Data protection measures

- **Firestore Security Rules** (`firestore.rules`) are the actual server-side
  enforcement boundary, not merely a client-side convenience — every collection has an
  explicit rule, and several use field-scoped diff checks
  (`request.resource.data.diff(resource.data).affectedKeys().hasOnly([...])`) so, for
  example, the automatic incident-clustering write path can only ever touch a report's
  `incidentId` field, never any other field of a report the writing client does not
  own. A citizen's own report can only be edited while it is still in the initial `SUBMITTED`
  state and only its category/note fields, since the photo/GPS/heading/timestamp are
  the evidentiary record of what was captured and are never editable after the fact.
  A new user document's `role` field is forced to `CITIZEN` server-side on creation,
  regardless of what a client might attempt to write, closing off self-service admin
  escalation entirely.
- **EXIF metadata stripping**: every captured photo has its GPS tags and
  device-identifying tags (make, model, software version, owner name, serial numbers)
  stripped via `ExifInterface` immediately after capture, before it is used anywhere
  else in the app — the report's location/heading/timestamp continue to exist only as
  explicit, intentional Firestore fields, never duplicated invisibly inside the image
  file itself.
- **Anonymous reports**: when a citizen chooses to submit anonymously, no user ID is
  recorded on the document at all — not merely hidden from other users, genuinely
  absent from the data model, so an anonymous report cannot be traced back to its
  submitter even by inspecting the raw database.
- **Input validation and sanitisation**: registration fields (display name, username,
  email, password, confirm-password) are validated client-side before any network
  call is made; the free-text note field is sanitised of tag-shaped substrings on
  every keystroke, not just at submit time.
- **Secrets management**: the Geoapify map-tile API key and the Google Maps SDK for
  Android key both live in `local.properties` (excluded from version control) and are
  injected at build time via generated `BuildConfig` fields — the Google Maps key is
  additionally wired through a Gradle `manifestPlaceholders` entry, since the Maps SDK
  reads its key from an `AndroidManifest.xml` meta-data tag at runtime, not from
  `BuildConfig` directly. Neither key is ever hardcoded as a literal in a source file
  that could end up in a public repository. Firebase configuration
  (`google-services.json`) follows Google's standard non-secret client-configuration
  model.

---

## 5.5 Development Tools & Technologies

| Category | Choice |
|---|---|
| Platform | Android (native), Kotlin 2.3.10 |
| UI toolkit | Jetpack Compose (Material 3), single-Activity architecture |
| Architecture | MVVM + Repository pattern, Hilt 2.60.1 for dependency injection |
| Navigation | Jetpack Navigation Compose 2.8.5 |
| Backend | Firebase Authentication + Cloud Firestore (BoM 33.7.0) |
| Local persistence | Room 2.8.4 (offline submission queue) |
| Background work | WorkManager 2.10.0 (AI classification, incident clustering, periodic risk-score refresh), integrated with Hilt via a custom `WorkerFactory` |
| Location | Google Play Services Location (`FusedLocationProviderClient`), `kotlinx-coroutines-play-services` for suspend-function interop |
| Sensors | Android `SensorManager` (accelerometer, magnetometer) |
| Camera | Android system camera intents via `ActivityResultContracts`, `FileProvider` |
| AI (photo classification) | ML Kit Image Labelling (on-device) with a vision-LLM (Gemma, via a local OpenAI-compatible server) as the primary path and ML Kit as its offline-safe fallback |
| AI (chatbot) | Same OpenAI-compatible chat-completions backend |
| Maps | Google Maps SDK for Android + Maps Compose (citizen-facing Map screen); MapLibre Native + Geoapify vector tile styles (retained, used elsewhere in the project) |
| Image loading | Coil 2.7.0 |
| Biometrics | AndroidX Biometric 1.1.0 |
| Secure storage | AndroidX Security-Crypto (`EncryptedSharedPreferences`, Keystore-backed) |
| Media metadata | AndroidX ExifInterface 1.3.7 |
| Preferences | Jetpack DataStore 1.1.1 |
| Crash reporting | Firebase Crashlytics |
| IDE / build | Android Studio, Gradle Kotlin DSL with version-catalog dependency management (`libs.versions.toml`) |

---

## 5.6 Testing

### Functional testing

The core citizen flow (shake or manual trigger → camera/gallery capture → optional
freehand annotation → GPS/compass tagging → optional manual category selection →
submit → background AI review → status tracking through to resolution) was exercised
repeatedly by hand throughout development, alongside the admin flow (triage →
department assignment → incident validation → work order creation → resolution). A
number of real defects were found and fixed this way over the course of the project —
for example, a shake-detection bug where the reversal counter was tallied against a
fixed wall-clock window rather than a rolling one, causing genuine shakes to
intermittently fail to register on a physical device (the emulator never exhibited the
symptom); a bug where a transient Firestore listener reconnect was indistinguishable
from "zero reports" and visibly flashed a user's report count to zero before
recovering; and a bug in the automatic incident-clustering logic where the very first
nearby incident scanned was accepted even if a later, better-matching incident existed
further down the candidate list, rather than always selecting the single best match.

### Device testing

Camera, GPS, and sensor-dependent flows were specifically tested on a physical Android
device rather than relying on the emulator, since Play Services location accuracy,
real camera hardware, and genuine accelerometer/magnetometer readings are not reliably
representative in an emulator. The AI backend's LAN-only reachability was also a
real, hands-on device-testing problem during development — the test device could not
reach the development PC directly (a firewall restriction with no available
administrator override), which was solved with a Cloudflare tunnel rather than by
weakening the network's own security posture, and is documented as a dev-environment
workaround rather than a production deployment path.

### UI testing

UI review during development was manual (Compose Previews plus on-device visual
inspection), catching and fixing real layout defects — for instance, a floating
"help" button that could sit on top of another screen's own primary action button
depending on which screen was open, resolved by restricting the floating button to
appear only on the Home screen and making it user-draggable rather than trying to
predict every screen's layout from one shared piece of code.

### Automated tests

One meaningful unit test suite exists (`NoteSanitizerTest`, covering whitespace
trimming, HTML/script-tag stripping, length truncation, and blank-input handling for
the note-sanitisation utility). The project's automated test coverage beyond this is
acknowledged as a limitation (see 5.7) rather than something claimed to be more
extensive than it is — the majority of verification for this submission was manual
functional/device testing plus continuous compilation and full-build verification
after every change, not a comprehensive unit/instrumented test suite.

---

## 5.7 Conclusion

### What was achieved

The submitted application is a complete, working system, not a UI prototype: a
citizen can genuinely shake (or manually trigger) their phone, capture and optionally
annotate one or more photos or a video, have it automatically GPS/compass-tagged, choose or defer a
category, and submit — after which a background AI process classifies it, related
reports automatically cluster into a tracked Incident, and the citizen sees real
status/resolution feedback (including the admin's note and any after-photo) once the
issue is addressed. The admin side is equally complete: triage, department assignment,
a transparent priority-scoring system, a full work-order lifecycle for tracking
physical repair work, an audit trail of every significant administrative action, and a
predictive risk-scoring dashboard built entirely from real historical report data
(never a fabricated statistic — an area with too little data explicitly says so rather
than showing a misleadingly confident number). Three of the assignment's four listed
hardware-integration categories are used meaningfully and are load-bearing to the core
concept, not bolted on afterward: camera, GPS, and accelerometer-based motion sensing
(the accelerometer's shake gesture is additionally supported by a proximity-sensor
pocket check, gated on a fresh sensor reading so an unreliable/stale one is treated as
inconclusive rather than blocking shake detection outright).
Security requirements are met with real server-side enforcement (Firestore rules, not
just client-side checks), genuine runtime permission handling with graceful denial
paths, EXIF stripping, and validated/sanitised user input throughout. Two bonus
features from the assignment brief were also implemented: AI integration (a
background photo classifier plus a scoped help chatbot) and multi-language support
(English/Spanish/French).

### Limitations

The most significant limitation is that **uploaded media never leaves the device**:
Firebase Storage was not enabled because it requires a paid billing plan, so photos
and videos are stored as local file paths rather than being uploaded to the cloud —
a photo is visible on the device that captured it, but not on another device or after
a reinstall. This was a deliberate scope decision for a coursework project with no
production budget, not an oversight, and the security rules that *would* govern real
uploads (`storage.rules`) remain in the repository ready to be enabled if billing
becomes available. Server-triggered push notifications (via Cloud Functions +
Firebase Cloud Messaging) were implemented in a later phase — a citizen is notified
the instant a report's status changes, or whether the background AI could
successfully classify their photo, even with the app fully closed; both notification
categories can be turned off independently from a Notifications settings screen. The
AI backend (both the photo classifier's primary path and the
chatbot) is a local development server exposed for testing via a temporary tunnel
rather than a real hosted cloud API — clearly documented in code and this report, and
never presented as more production-ready than it is; the ML Kit fallback means the
core reporting flow is never actually dependent on that server being reachable. The
incident-clustering and risk-scoring computations are transparent, rule-based
weighted formulas rather than a trained machine-learning model — an explicit design
choice, since a coursework project has no labelled training data to justify a claim of
real ML sophistication, but each is architected so that a genuine ML model could later
replace the scoring function without any caller needing to change. Automated test
coverage is limited to one utility-class unit test suite; the rest of this
submission's verification came from continuous build/compile checks and manual
functional and device testing, not a comprehensive automated suite. Multi-language
support covers the most-used screens rather than every string in the app.

### Possible future improvements

- Enable Firebase Storage (or an equivalent object-storage service) so media
  genuinely syncs across devices, and extend the existing EXIF-stripping/validation
  pipeline to the upload path.
- Move the incident-clustering compute and the periodic risk-score refresh from
  on-device WorkManager jobs to a real server-side Cloud Function, removing the
  current trust placed in client-computed aggregate writes.
- Replace the local development AI server with a real hosted vision/LLM API for
  category classification and the chatbot, removing the current LAN/tunnel
  dependency entirely.
- Introduce a genuine visual-similarity signal (image embeddings) into incident
  clustering, and — if a multi-layer infrastructure-asset map is wanted again —
  integrate real municipal GIS/infrastructure data into a rebuilt version of the
  admin-facing multi-layer map that was removed when the citizen map was migrated to
  Google Maps.
- Support tapping a pin on the citizen Map screen to jump straight into that report's
  detail screen, and add filtering (e.g. by category or by "near me") to the Map.
- Expand automated test coverage with ViewModel-level unit tests (using fake
  repository implementations, which the existing interface-based repository layer
  already supports cleanly) and Compose UI tests for the core capture and admin
  triage flows.
