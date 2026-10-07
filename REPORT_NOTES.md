# Shake to Report — Report Notes

Running log of design decisions, features, and rationale for the technical report (2500-5000 words).
Organized to mirror the required report sections (numbered 1-7 here; the coursework brief itself
labels these as 5.1-5.7 - see the .docx for the submission-ready numbering).

---

## 1. Introduction

Shake to Report exists because most people notice civic problems constantly - a pothole they
drive past every day, a streetlight that's been out for weeks - but almost never actually report
them. Not because they don't care, but because the reporting process is more hassle than the
problem feels worth: a phone call during business hours, a clunky web form, a generic 311-style
app with a dozen fields to fill in. The whole idea here is to cut that down to almost nothing -
shake the phone, take a photo, tap twice, done.

Existing municipal reporting channels share that same friction problem. This app tries to fix it
by collapsing "notice something -> file a report" into one physical gesture plus a couple of taps.

Primary users are ordinary citizens who spot an issue in daily life and want to flag it without
much effort. There's also a secondary admin/reviewer role for whoever on the municipal side
actually triages and resolves what comes in.

---

## 2. System Design

Before any admin-facing triage UI existed, the underlying data model for it was laid down first -
`Report` gained fields for department, priority, a resolution note, an optional "after" photo, and
(at the time) upvote tracking, all following the same typed-enum conventions already established
elsewhere in the domain model rather than introducing raw strings. This was deliberately built as
a data-model-only phase with no UI attached yet, so the rest of the app kept compiling and running
exactly as before while the admin workflow and citizen feedback screens described later in this
report (Sections 3.19 and 3.31) were built on top of it in subsequent phases. Upvoting was
ultimately removed from the app entirely later on over a security concern - see Section 3.41.

The app follows MVVM with a Repository layer on top, built in Jetpack Compose with Hilt handling
dependency injection. This is fairly standard for modern Android work, and it also happens to
line up well with the "modular and reusable code" requirement in Section 4 - each layer can be
tested and swapped independently.

Firebase (Auth, Firestore, Storage) was chosen over a local-only Room database mainly because a
coursework project that only stores data on-device doesn't really demonstrate real sync, real
token-based auth, or a real offline story. Firestore gives all three more or less for free, which
also happens to feed directly into the functionality and security marking criteria.

Room still has a job to do though - it sits underneath Firestore as an offline cache/queue, so a
report created without network gets staged locally and pushed up once connectivity returns.
Navigation is handled with Jetpack Navigation Compose in a single-Activity setup.

## 3. Features Description (full list, with hardware/API mapping)

### Core reporting flow
The reporting flow is the heart of the app, so most of the hardware integration lives here.
- Shake detection runs off the accelerometer via SensorManager - debounced and foreground-only, so
  it won't fire from a jostle in a pocket or while the app is backgrounded.
- On top of that there's a pocket-guard filter (checking multiple axes and ambient light) to cut
  down on false triggers.
- A manual "report an issue" button sits alongside shake as a fallback - shake detection is fun
  but not something you want to bet a live demo on, so there's always a guaranteed way in.
- A valid shake auto-launches the camera, captures, then gives a retake/confirm step.
- Location is tagged automatically via FusedLocationProviderClient, with reverse geocoding to turn
  coordinates into a street address.
- Heading/compass direction is captured at the same moment using the magnetometer and
  accelerometer together (SensorManager's rotation-matrix/orientation calls), plus an
  auto-timestamp.
- ML Kit's on-device Image Labeling API takes a pass at suggesting a category (pothole, streetlight,
  garbage, water leak, other) with a confidence score - below a certain confidence it just leaves
  the field for the user to pick manually rather than guessing. This is the AI-integration piece of
  the brief; going with an existing on-device model rather than training a custom one was a
  deliberate scoping call for a two-person project.
- Notes are optional, sanitized and length-capped before submission, which covers the data
  validation/sanitisation requirement.
- Submission itself is two taps: confirm the photo, then submit.

### Reports & tracking
- Reports show up both as pins on a map and as a filterable/sortable list (status, category, date).
- Tapping into a report shows the photo, its map pin, address, and current status.
- Status moves through submitted -> in review -> resolved, and there's a "My Reports" screen for a
  citizen's own history.
- Duplicate handling is intentionally simple: if a similar-category report already exists nearby
  and recent, the user gets shown that report instead of filing a new one. A full geo-clustering
  backend would be more robust but felt like overkill for this scope - flagged as a future
  improvement rather than something worth building out now.
- Same logic for offline handling - reports made without a connection sit in a local Room queue and
  get pushed once the device is back online. It's one-way (queue then flush), not full
  bidirectional sync with conflict resolution, which again felt like the right amount of complexity
  for what this needs to prove.

### Auth & roles
- Firebase Auth handles email/password sign-in with token-based sessions.
- Tokens are stored via Keystore-backed EncryptedSharedPreferences rather than plain
  SharedPreferences, which covers the secure-login and sensitive-data-protection requirements.
- There's a profile screen and logout, and a permission dashboard that shows the live state of
  camera/location/motion permissions with a re-request flow if something's been denied.
- Rather than building a separate admin web dashboard, admin is just a role on the same app -
  admins see every report and can update status. A second product surface for two students wasn't
  realistic in the time available, and folding it into the same app still closes the loop (someone
  has to actually resolve what gets reported).

### Security & privacy
- Every sensitive permission (camera, location, motion) gets a short explainer screen before the
  system prompt appears, rather than surprising the user with a dialog out of nowhere.
- Photos have their EXIF data stripped before upload, so device make/model and any GPS baked into
  the file metadata never leak beyond what the app deliberately attaches to the report.
- Free-text fields are sanitized on the way in.

### Extras
- Local notifications fire when a report's status changes.
- Shake sensitivity is adjustable in settings, since not everyone can (or wants to) shake a phone
  hard enough to hit a fixed threshold.

### Explicitly cut from scope
A few things were considered and deliberately left out, worth naming honestly in the Limitations
section rather than pretending they weren't thought about:
- Server-side rate limiting per user/device - would need Cloud Functions, more effort than felt
  justified for this scope.
- A separate admin/municipal web dashboard - the in-app role-based view does the job without
  maintaining a second product.
- A custom-trained classification model - ML Kit's pretrained labeling still satisfies the AI
  integration requirement with a real on-device inference call, and training a model from scratch
  wouldn't have meaningfully improved on that for the time it would cost.

## 4. Security Implementation

Permission handling follows a consistent path throughout the app: show an explainer, make the
runtime request, and handle denial gracefully - if a permission has been permanently denied, the
app links straight into system Settings rather than leaving the user stuck.

Authentication runs through Firebase Auth (email/password), and it's the resulting ID token that
gates access to Firestore reads/writes - the security rules restrict report writes to whoever
owns the report, with admins getting broader read/update access. Importantly, this enforcement
happens server-side in Firestore's own rules, not just as a client-side check that a modified
client could bypass.

On the data-protection side: tokens are Keystore-encrypted at rest, photos get their EXIF stripped
before they go anywhere, and free-text input is sanitized before it's stored.

## 5. Development Tools & Technologies

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

## 6. Testing (to fill in as build progresses)

Testing so far has mostly been manual: walking through shake -> capture -> submit -> status
change end to end, and doing it on a physical device rather than trusting the emulator, since
sensors, camera, and GPS are all flaky or outright fake in the emulator. Compose UI tests for the
key screens are still on the to-do list.

## 7. Conclusion (draft points to expand later)

What's actually working end to end: the full reporting flow (shake or manual trigger, camera,
GPS/compass tagging, AI category suggestion, submit, then tracking status through to
resolution), plus auth, role-based views, the offline queue, and the security measures described
above.

The honest limitations: no server-side rate limiting, no real geo-clustering for duplicate
detection, no custom-trained ML model. As of this write-up, notifications were still local-only -
that's since been closed (see the FCM entry below), so this should be updated before final
submission.

If there were more time, the natural next steps would be moving rate limiting and duplicate
clustering server-side via Cloud Functions, adding multi-language support, and building out full
bidirectional offline sync instead of the current one-way queue.

---

### 2.1 Why this architecture and this stack

A few foundational calls shaped everything that came after, so it's worth spelling out the
reasoning rather than just listing the stack.

"Shake to Report" as a concept was chosen over the other suggested project themes mainly because
it naturally demanded genuine hardware integration - accelerometer for the shake gesture, camera
for the photo, GPS for location, compass for heading - all working together in one coherent flow
rather than four features bolted on separately to hit a checklist.

Early on, the full feature list was checked against the assignment brief to find gaps and
right-size the scope for two students. Offline handling, duplicate detection, AI classification,
notifications, consent screens, and EXIF stripping all needed some form of implementation; a few
things were deliberately cut or simplified where the effort clearly outweighed the benefit for a
project this size - full server-side rate limiting and a separate admin dashboard were dropped,
while offline handling and duplicate detection were kept but simplified to client-side/one-way
versions rather than fully robust distributed systems.

Firebase won out over a local-only Room database because a coursework submission that never talks
to a real backend doesn't demonstrate real authentication, sync, or security - which was worth
more than the added complexity of wiring up a cloud backend. Jetpack Compose was chosen over the
older XML View system simply because it's the current, less boilerplate-heavy way to build
Android UI. And MVVM with a Repository layer and Hilt for dependency injection won out over
manual DI because it's testable and modular in a way that lines up directly with the "modular and
reusable code" requirement.

### 2.2 Foundation: domain models, Room queue, security rules

The first real implementation phase laid down the data layer everything else builds on.

The domain models - `User`, `Report`, `ReportCategory` (pothole, streetlight, garbage, water
leak, other), `ReportStatus` (submitted, in review, resolved), and `UserRole` (citizen, admin) -
all live under `domain.model`. Each enum exposes a `fromFirestoreValue` helper that falls back
safely to a sensible default if Firestore ever holds an unexpected or legacy string, rather than
crashing on a raw `valueOf` call.

`Report` carries both a `photoUrl` (the remote Firebase Storage download link, nullable until the
photo actually uploads) and a `localPhotoPath` (used while the photo only exists on-device,
pending upload). Heading is deliberately split into `headingDegrees` as a float and
`headingCardinal` as a string like "NE" - computed once at capture time rather than derived later
on display, since it's a point-in-time sensor reading that belongs permanently on the record.

The offline queue sits in Room as `PendingReportEntity`, which mirrors `Report`'s fields but
flattens the enums down to plain strings - Room doesn't need custom TypeConverters that way, and
it matches how the value gets re-serialized back to Firestore once it flushes. Every pending
report always carries a non-null `localPhotoPath`, since the only reason it exists in this table
at all is that it hasn't uploaded yet. A separate `createdAt` field (distinct from the report's
own `timestamp`) keeps FIFO draining of the queue unambiguous. `PendingReportDao` exposes
`getAll()` as a Flow for a reactive queue-badge in the UI, plus a one-shot `getAllOnce()` for the
connectivity-triggered flush routine.

Hilt wiring starts at `MobAppAssignment` (`@HiltAndroidApp`), registered as the application class
in the manifest. `DatabaseModule` provides a singleton Room `AppDatabase` - using
`fallbackToDestructiveMigration()` since this is coursework with no real user data that needs
preserving across schema changes - along with the `PendingReportDao`. `FirebaseModule` provides
singleton instances of `FirebaseAuth`, `FirebaseFirestore`, and `FirebaseStorage`.

To avoid magic strings scattered across repository code, `data/remote/FirestoreSchema.kt` defines
a `FirestoreConstants` object holding collection names, field-name constants grouped under
`UserFields`/`ReportFields`, a `RoleValues` object matching the `UserRole` enum, and a
`reportPhotoStoragePath(userId, reportId)` helper so the Storage path convention is defined once
and shared between the repository code and `storage.rules`.

Security rules were written alongside the data model rather than bolted on afterward. In
`firestore.rules`, a user can create, read, and update only their own `users/{uid}` document, and
critically cannot self-promote to admin - the update rule requires
`request.resource.data.role == resource.data.role`, so role can never change through a client
write. `reports/{reportId}` documents can only be created by their stated owner
(`request.resource.data.userId == request.auth.uid`), and can be read or updated by the owner or
by an admin, with admin status looked up via a `get()` call on the requester's own user document.
No deletes are allowed anywhere at this stage, since reports are meant to form an audit trail -
status changes are updates, never deletions. `storage.rules` restricts photo writes to an
authenticated user writing under their own uid-prefixed path, caps upload size at 15MB, and
requires an `image/*` content type; reads only require authentication, since report photos are
meant to be visible across the shared Reports/Map view, not locked to the owner.

The manifest picked up the permissions this phase needed - camera, fine and coarse location,
internet, network state, and post-notifications - plus a `<uses-feature>` declaration marking
camera as not required, so the app still installs on camera-less devices or emulators. A
`FileProvider` was registered too, backed by `provider_paths.xml`, so a later capture screen can
hand the system camera app a proper `content://` URI instead of a raw file path.

No `MainActivity` existed yet at this point - navigation and UI were left for the following
phase, keeping this one scoped purely to data, dependency injection, and manifest foundation.

### 2.3 Authentication and permission handling

With the data layer in place, the next phase built out authentication and the app's approach to
runtime permissions.

`AuthRepository`/`AuthRepositoryImpl` wraps `FirebaseAuth` together with the `users/{uid}`
Firestore document. Registration creates the Firebase Auth user, sets the profile display name,
then writes a `User` document defaulting to the citizen role - the server-side rules from the
previous phase already prevent any client from self-promoting to admin, so this is belt-and-braces
rather than the only line of defense. Login signs in and reads back the Firestore user document,
falling back to a basic `User` built straight from the Firebase user object if that document read
fails, so a transient Firestore hiccup doesn't block an otherwise-valid login. `currentUser` is
exposed as a `Flow<User?>` built from a `callbackFlow` wrapping Firebase's own auth-state
listener; it emits a quick "basic" user first for responsiveness, then the richer
Firestore-backed version with the real role once that document read completes. That two-step
emission costs one extra UI update but avoids a role-dependent element like the Admin tab
flashing incorrectly while the Firestore round trip is still in flight.

Token storage got specific thought rather than a default choice. `SecurePrefsManager` uses
Android's Keystore-backed `EncryptedSharedPreferences` to store exactly two things: whether
"remember me" is enabled, and the last-used email to prefill the login field. It deliberately
does not store any Firebase ID token or refresh token itself - the Firebase Android SDK already
persists and auto-refreshes its own session through its own secure storage, so keeping a second
copy would be redundant and would introduce a second, harder-to-invalidate copy of sensitive
credential material sitting around for no benefit.

`PermissionManager` is a stateless helper (no DI needed) wrapping `ContextCompat.checkSelfPermission`
for camera, location, and notifications - the last of which only exists as a runtime permission
from API 33 onward, and is treated as always granted below that. It also exposes a check for
whether a permission has been permanently denied, and an intent builder for deep-linking straight
into the app's system Settings page for the "permanently denied, go enable it manually" path.
Notably, no runtime permission request was added for the accelerometer or magnetometer used for
motion and compass sensing - on modern Android these are normal-protection-level sensors, not the
`BODY_SENSORS` permission class reserved for biometric sensors like step counters or heart-rate
monitors, so no system dialog would ever appear for them regardless.

`PermissionExplainerScreen` is a generic, reusable composable (icon, title, body text, button
label, continue callback) that later capture and location-tagging screens can reuse ahead of each
first-time system permission prompt, rather than every screen hand-rolling its own explainer
layout. `PermissionDashboardScreen` and its ViewModel list camera, location, and notifications
with a status icon each, offering either a "Grant" button that launches the system permission
request or an "Open Settings" button once a permission has been permanently denied. That
ViewModel is a plain `AndroidViewModel` rather than a Hilt one, since it only needs application
context for the permission checks and has no repository dependency worth injecting.

The login and register screens are Material 3 forms with inline validation - an email regex,
minimum password length on registration, confirm-password matching - exposing a `StateFlow<UiState>`
to Compose in line with the rest of the project's ViewModel convention. On a successful login or
registration, the last-used email gets written to `SecurePrefsManager` so it prefills next time.
Both screens
surface authentication failures as an inline error message rather than crashing the UI, and
neither wires up `MainActivity` or a navigation graph yet - that stays scoped to a later phase,
consistent with how the foundation work also left `MainActivity` for later. Auth and permission
screens at this point are self-contained composables ready to be slotted into a NavHost.

### 3.1 Sensors and the capture flow

This phase built out everything hardware-related for turning a shake into a submitted report.

`ShakeDetector` wraps `SensorManager`'s accelerometer, computing a gravity-normalized
acceleration delta on each sample. Getting the pocket-guard logic right took a few passes: it
combines a raw magnitude threshold (adjustable, defaulting to 15 m/s²) with a requirement of at
least two sign-reversals of that delta within a rolling one-second window - a genuine shake is
back-and-forth, so a single jostle or a phone sliding around in a pocket rarely reverses direction
twice quickly enough to qualify. Where a proximity sensor is present, the trigger is also
suppressed while it reads as covered. The whole thing is debounced with a 1.5-second minimum gap
between fires and exposed as a Flow so it composes naturally with a `LaunchedEffect` at the
Home-screen level. The adjustable threshold itself lives in `ShakePreferences`, a small DataStore
wrapper, so a settings screen can read and write it later and the detector always picks up the
latest value.

`CompassProvider` combines the accelerometer and magnetometer through Android's rotation-matrix
APIs to produce a live heading Flow, plus a helper that turns degrees into an eight-point compass
string - used both for a live compass display and as a one-off snapshot at the moment a photo is
captured. `LocationProvider` sits in its own package rather than alongside the Firestore/Storage
code, since it wraps Play Services location and geocoding instead - it exposes a suspend function
for getting the current location and another for reverse-geocoding coordinates into a readable
address, falling back gracefully to null if geocoding isn't available on that device.

`ExifStripper` removes GPS tags and device-identifying metadata (make, model, software, owner,
serial numbers) from a captured photo right after it's confirmed, before it's used anywhere else.
Location, heading, and timestamp continue to live only as explicit fields on the report itself,
never duplicated into the image's own metadata.

`MlKitCategoryClassifier` runs the on-device ML Kit image-labeling client and keyword-matches the
returned labels against the category list, using the label's own confidence score. If nothing
matches, it falls back to "other" with confidence deliberately capped below the auto-select
threshold, so the form always drops to a manual category pick in that case rather than guessing.
`NoteSanitizer` strips anything that looks like an HTML or script tag out of the note field via
regex, trims whitespace, and truncates to 500 characters - simple enough not to need a full HTML
parser, and unit tested for the trim/strip/truncate/all-whitespace cases.

`CaptureViewModel` and `CaptureScreen` implement the actual state machine behind the two-tap
submit flow: idle, preview, analyzing, form, submitting, submitted. The camera launches against a
`FileProvider` URI reusing the provider setup from the foundation phase. Once a photo is
confirmed, location lookup, the compass-heading snapshot, and ML Kit classification all run
concurrently as coroutines and join together under one loading state, rather than serially, which
keeps that step from feeling sluggish. Category selection is shown as filter chips covering every
category, with the suggested one only pre-selected when confidence clears 0.5 - otherwise nothing
is pre-selected and a low-confidence hint is shown instead of guessing on the user's behalf.

At this stage, submission itself still only wrote into the local Room queue rather than talking
to Firestore directly, since the actual reports repository didn't exist yet - that gap was closed
by the following phase, along with the duplicate-check dialog.

### 3.2 Reports repository and the main screens

With sensors and capture working, this phase built the actual reports repository and most of the
app's screens around it.

`ReportRepository`/`ReportRepositoryImpl` wraps Firestore reads as live-updating Flows via
`addSnapshotListener`, rather than one-shot Task objects - which turned out useful later, since
that same Flow-based approach is what makes the local status-change notification listener
possible without extra plumbing. `findNearbyRecentReports` implements the duplicate check: it
queries Firestore by category and a recent-timestamp bound, then narrows the candidates further
with a client-side haversine distance calculation, since Firestore has no native geo-radius query
without pulling in a geohashing library, which felt like unnecessary weight for this project.
`submitReport` is deliberately written as a generic "upload the photo, then write the document"
method so a later offline-aware version could wrap it without changing its signature - which is
exactly what happened once connectivity handling was added.

On the capture side, `submit()` now genuinely calls the duplicate check before submitting, and
shows an alert dialog offering to view the existing report or submit anyway if a match turns up.
Otherwise it calls through to the repository's real submit path - closing the gap from the
previous phase where every submission, online or not, went straight into the local queue.

A handful of reusable list components (`ReportFilterState`, `ReportFilterBar`, `ReportListItem`)
get shared across the reports list, the admin reports screen, and the home screen's recent
activity section, so filtering and rendering logic isn't duplicated three times. The admin screen
keeps its own ViewModel rather than sharing the citizen-facing one, since status-changing actions
don't belong on the public list ViewModel's API, but it still reuses the same filter bar and list
item composables underneath.

The map screen at this point was a genuine placeholder, since no Maps API key existed yet for the
project - a list of report cards showing raw coordinates, plus a simple canvas-drawn dot grid
giving a rough sense of relative position without real map tiles. That was tracked as a known gap
and replaced later with a proper interactive map once a real API key was available (see
Section 3.9).

The report detail screen shows the full photo (falling back to the local file path for anything
still sitting in the offline queue without a Storage URL yet), with an admin-only status dropdown
gated on the current user's role. The home screen carries a prominent manual report button - a
regular button rather than a floating action button, to keep it visually obvious - alongside the
last few reports for the signed-in user. Profile and settings screens are thin wrappers around
existing state (auth/logout, and the shake-sensitivity DataStore preference respectively) rather
than introducing new persistence layers of their own.

Notification handling and the offline-queue flush job were both deliberately left for their own
dedicated phases rather than folded in here, and no navigation host existed yet at this point, so
every new screen took plain lambda callbacks that a later navigation phase could wire up without
touching this screen code.

### 3.3 Making the offline queue actually work

The previous phases had already built the pieces - a Room queue table, a capture flow, a reports
repository - but nothing yet decided when a report should go to Firestore directly versus sit in
that queue, or automatically retried once the device came back online. This phase closed that
gap.

`ConnectivityObserver` wraps Android's `ConnectivityManager.NetworkCallback` as a Flow, exposing
both a live `isOnline()` stream and a synchronous one-shot check for the moment of submission
itself - deliberately using the one-shot check at submit time rather than a long-lived
subscription, since the decision only needs to reflect the network state at that instant.
`ReportRepositoryImpl.submitReport` now checks that one-shot state first: offline, it queues
straight into Room and returns success immediately without attempting any network call; online,
it proceeds with the normal Storage-upload-then-Firestore-write path as before. The existing
fallback in the ViewModel (queue locally if the online path throws) stays in place as a second
safety net for the case where a connection drops mid-upload despite nominally being online at the
moment of the check - the two paths don't conflict, since the repository's offline branch never
returns a failure that would trigger the ViewModel's own fallback.

`syncPendingReports()` reads every row out of the local queue, reconstructs each report along
with its local photo file, uploads it through the same path the online flow already uses, and
only removes the row from Room once that succeeds - a row that fails is simply left in place for
the next attempt rather than lost.

Rather than reaching for WorkManager, the sync trigger is a simple application-scoped coroutine
scope collecting the connectivity Flow from the Application class itself, firing
`syncPendingReports()` specifically on an offline-to-online transition rather than on every online
emission, to avoid redundantly re-running the drain while already connected. This only runs while
the app process is alive, which is a real limitation worth naming - a production app would more
likely use WorkManager with a network-connected constraint so a queued report still gets delivered
even if the app was killed while offline. That trade-off is documented in `PLACEHOLDERS_TODO.csv`
rather than silently accepted.

`NotificationHelper` was also introduced around this point, wrapping the standard Android
notification APIs behind a single notification channel and defensively re-checking the runtime
notification permission at the moment of posting rather than assuming it was already granted -
a denied or never-requested permission simply means no notification gets posted rather than a
crash. A status-change listener was added to the reports repository itself, keeping a small
in-memory record of each report's last-known status so it can tell a genuine change apart from
just re-observing the same status again - a report's very first snapshot seeds that record
silently rather than firing a notification, so a user isn't immediately notified about the current
state of every report they already have the moment the listener starts. It starts as soon as the
home screen loads after login and stops explicitly on logout, so it never keeps running - or
keeps a stale user id around - past the session it belongs to.

---

### 3.4 Wiring the app together

Once the individual screens existed, they still needed a real navigation graph tying them
together. `MainActivity` hosts a Compose NavHost with two nested graphs - an auth graph for
login/register and a main graph for everything else, with Admin only reachable when the signed-in
user actually has that role. The start destination is chosen by collecting the current user as
state: no user routes to the auth graph, a signed-in user routes to the main graph, and both
login/register success and logout clear the back stack appropriately so the auth boundary is
genuinely enforced by navigation, not just by hiding a button.

Bottom navigation wraps Home, Reports, My Reports, and Profile in a standard Material 3 scaffold,
with a fifth Admin tab appended only when the signed-in user is an admin. Capture, report detail,
settings, and the permission dashboard are pushed as full-screen routes without the bottom bar,
since they're drill-in destinations rather than top-level tabs. The reports tab itself is a small
new screen hosting a tab row that toggles between the existing report list and the map, which is
really just composing two already-built screens under one route rather than new functionality.

Shake detection moved up to the Activity/lifecycle level here too - `MainActivity` collects the
shake-detection Flow inside a lifecycle-aware effect that (re)starts the collection on `ON_START`
and cancels it on `ON_STOP`, which keeps the underlying sensor listeners registered only while the
app is actually visible. That matters for battery and correctness both - without it, sensor
listeners would leak and keep firing in the background. A shake anywhere in the app now navigates
to the capture screen with `launchSingleTop` set, so repeated shakes while already there don't
stack duplicate destinations.

No dedicated theme package existed yet at this point, so a minimal Material 3 theme keyed off the
system's light/dark setting was added directly in `MainActivity` - a proper design system came
later (see the next entry). The manifest picked up the missing Activity declaration with its
launcher intent filter, and a pass through the security rules confirmed the data model already
matched what had been written back in the foundation phase, so no rule changes were needed.

One gap was consciously carried forward rather than fixed here: the permission-explainer screen
built earlier was fully functional but not yet actually invoked from the capture flow's own
permission requests. That wiring belonged inside `CaptureScreen`'s own logic rather than the
navigation graph, so it was left for the screen's owning phase and tracked in
`PLACEHOLDERS_TODO.csv` rather than silently left unmentioned.

### 3.5 Connecting a real Firebase project, and the Storage trade-off

With the app functionally wired together, a real Firebase project replaced the placeholder
configuration - Firestore was provisioned and the security rules deployed.

One real constraint showed up here: Firebase now requires the paid Blaze plan to enable Cloud
Storage at all, even though usage would comfortably sit inside the free tier at coursework scale.
Rather than add billing to the account for this assignment, the decision was to drop Storage
entirely and keep photos device-local - the captured photo's local file path gets written
straight into the Firestore report document, and the remote photo URL field stays null. Report
data itself (location, heading, category, note, status) still syncs fully across devices through
Firestore; only the image file stays put on the device that captured it. The report detail
screen already fell back to the local path when there's no remote URL, so no UI change was needed
to accommodate this.

Worth stating plainly rather than glossing over: this is a deliberate scope trade-off, not a bug.
Real backend authentication and Firestore sync are demonstrated end-to-end; only object storage
for media was descoped specifically to avoid a billing dependency. If billing becomes acceptable
later, the Storage upload code pattern still exists in git history and could be reinstated, or a
free alternative like Cloudinary could be substituted if cloud photo sync becomes a requirement.

### 3.6 A permission crash found by testing on a real device

Testing on an actual phone rather than just reading code caught a real bug: tapping "Report an
Issue" crashed the app outright with a security exception over a revoked camera permission. The
cause was that the capture screen fired the system camera intent unconditionally the moment the
screen loaded, with no runtime permission check beforehand - the permission had simply never been
requested from the user yet, so Android refused the intent and the app crashed rather than
degrading gracefully.

This turned out to be the same gap already flagged earlier - a permission-explainer screen had
been built but never actually wired into the capture flow. Fixing the crash and closing that gap
were the same change: a small state machine now gates the camera launch behind explainer, then
request, for both camera and location, and only proceeds once both are actually granted. It
reuses the existing permission utilities rather than introducing new checking logic. This is a
good illustration of testing catching something a code review alone hadn't - the permission gate
existed in the codebase, it just wasn't being called from the one place that mattered.

### 3.7 Building a proper design system

Up to this point the app used bare, unstyled Material 3 defaults - stock colors, stock type
scale, stock shapes, defined inline inside `MainActivity`. This phase replaced that with a real,
reusable design system, aiming for a "civic and trustworthy" visual direction rather than a
generic Material template look.

The color system follows Material 3's tonal-palette conventions properly, filling in every role
(primary, secondary, tertiary, error, surface, outline, and their "on-color" pairs) deliberately
rather than overriding just one or two values. Primary is a deep, desaturated blue - the kind of
color municipal and government-adjacent brands often use, chosen to read as official and
trustworthy without feeling cold. An amber accent is reserved specifically for the single most
urgent action on any given screen - the shake/report call to action - and kept out of routine
navigation chrome entirely, so seeing amber always means "this is the thing that matters right
now." A teal-green tertiary color serves as a supportive accent and doubles as the base hue for
the "resolved" status color. Report status itself gets its own semantic color mapping - blue for
submitted, amber for in review, green for resolved - each with light/dark container pairs chosen
to keep contrast within Material's accessibility guidance rather than placing text directly on a
saturated color.

Typography keeps the system font rather than adding a custom typeface, but with deliberate weight
and spacing choices layered on top of the default scale - bold, tightly-tracked headlines for
screen titles, semibold labels with wider tracking so buttons read as distinctly actionable
against body text, and slightly open spacing on body copy for comfortable reading of longer report
descriptions. Shape follows one consistent, generously rounded language throughout - a small
extra-rounded "pill" shape is reserved specifically for status badges so they read as visually
distinct from cards, buttons, and chips, while everything else shares one graduated corner-radius
scale from small controls up to full-screen sheets.

A set of shared components came out of this pass too, mainly because more than a dozen screens had
each been building their own ad hoc version of a top bar, a loading spinner, a status pill, or an
empty-state message. Consolidating those into `AppTopBar`, `StatusBadge`, `CategoryChip`,
`PrimaryActionButton`, `EmptyState`, and `LoadingIndicator` is what actually makes the redesign
consistent across the whole app rather than a one-screen reskin - every future screen inherits the
same look automatically by using these components rather than hand-rolling its own.

The native Android theme (used for the pre-Compose window background and splash) and the launcher
icon's background color were both updated to match, so there's no flash of the wrong color on
launch and the home-screen icon reads as branded rather than a generic template.

### 3.8 Applying the design system across the app

With the design system built, the following passes went screen by screen applying it. Login and
register both picked up a shared branded header - an icon badge, wordmark, and screen-specific
headline - so first-time users see consistent branding before they even reach a form field, where
previously there had been plain unstyled titles. Submit buttons across both screens switched to
the new primary action button component, and text fields picked up the app's rounded-corner shape
explicitly, since Compose's own default text field shape didn't match the radius used everywhere
else in the new system.

The same pattern of pass continued across the rest of the app - home and the permission
dashboard, the capture flow, profile and settings, the admin reports group, and the
reports/map/status-dropdown group all had their ad hoc styling replaced with the shared components
and the new color/type/shape system, screen by screen, without changing any screen's underlying
behavior or callback signatures.

### 3.9 Finishing the design-system rollout

The same restyling pattern continued across the remaining screens - home, the permission
explainer and dashboard, the capture flow, profile, settings, admin reports, and the whole
reports/map/status-dropdown group - swapping raw top bars, spinners, and plain text for the
shared components, and standardizing chips, status pills, and card shapes throughout. A few
specific additions came out of this: report status now renders as a small colored chip on the
permission dashboard's granted/not-granted rows too, matching how status reads everywhere else in
the app; a new shared `NavigationRow` component gave profile and settings identical-looking
tappable links instead of two slightly different implementations; the report detail screen's
scattered address/heading/timestamp text turned into a proper metadata card with icons per row;
and the map placeholder's dot-grid visualization got a real visual pass - a themed grid, halo
rings around each dot, and color-coding for resolved versus open reports - so it read as an
intentional lightweight visualization rather than a bare debug canvas. None of this touched any
ViewModel or changed a single screen's behavior; it was a pure visual pass end to end.

A proper logo followed shortly after - an original vector mark combining a shield, a GPS pin at
its center, and small accent bursts either side, meant to read as a compact visual summary of
what the app actually does rather than reaching for a generic stock icon. Around the same time,
the login screen's "remember me" checkbox actually got wired up for real - the secure-storage
layer had supported it since the very first auth phase, but no UI had ever exposed the toggle, so
the last-used email was always being remembered whether the user wanted that or not. Now it's an
explicit, visible choice, which is a small but genuine privacy improvement worth noting in the
security section: the user actually controls whether their email persists on-device.

A round of hands-on navigation testing then caught a real usability gap that a code review alone
hadn't: every sub-screen pushed on top of the bottom-nav tabs - settings, the permission
dashboard, report detail, capture - showed no back arrow at all, even though the shared top bar
component had always supported one. Users were left relying on the invisible system back gesture,
which reads as "this isn't interactive." Wiring up the back button on each of those screens fixed
it in a few lines. Bottom-nav tab roots deliberately still have no back button, matching how most
Android apps behave at a tab root - there's no meaningful destination to go back to from there.

### 3.10 A real vision model for category suggestions

ML Kit's generic pretrained labels gave a working category suggestion, but a genuinely more
capable option was available: a local vision-capable LLM (Gemma), served from a development
machine over its own OpenAI-compatible API. The goal was to let the app use a real LLM for
classification while still working as a standalone product that doesn't depend on any particular
PC being switched on.

Rather than replacing ML Kit outright, a new `ai` package was added that composes with it. A
small config object holds the base URL, model id, and API key in one place, so switching from the
local development server to a real hosted API later is a one-file change. The actual classifier
service downsizes the captured photo before sending it, then calls the chat-completions endpoint
using the same request shape most hosted vision APIs already speak - a text instruction plus an
embedded image - specifically so a future migration to a real hosted provider wouldn't mean
rewriting the request logic, just changing where it points. A thin wrapper tries the vision model
first and falls back silently to the existing ML Kit classifier on any failure - a timeout, the
PC being off, a different Wi-Fi network - so the capture flow's dependency on a classifier didn't
need to change at all, just which implementation sits behind it.

Since the vision server runs over plain HTTP rather than HTTPS, and Android blocks cleartext
traffic by default, a scoped network security exception was added that permits cleartext
specifically to that one development machine's LAN address, with cleartext still refused
everywhere else by default. That's worth calling out specifically in the security section - it's
a real, deliberate trade-off between development convenience and security, but scoped as
narrowly as it could be rather than disabled globally for convenience. This whole setup is
explicitly a development/testing arrangement, not how the app should ship - before any real
submission or demo where the development machine won't be present, the config needs to point at
a properly hosted API instead, and that's tracked as a blocking item until it's done. In the
meantime the ML Kit fallback keeps the app fully functional and demoable even without that server
reachable at all.

### 3.11 Impact stats on Home and Profile

A small but meaningful addition: a compact stat row showing each citizen how many reports they've
filed in total and how many have actually reached resolved status, shown on both the home screen
and the profile screen. Civic-engagement apps benefit a lot from this kind of visible positive
reinforcement - seeing "you've filed 12 reports, 5 resolved" makes the habit of reporting feel
worthwhile and closes the loop between filing something and knowing it led somewhere.

It reuses the existing per-user reports stream entirely - no new repository method or Firestore
query was needed, since deriving two counts from an already-fetched list client-side was
straightforward. Both counts come from one small pure function shared between the home and
profile ViewModels, so the numbers can never drift apart from each other or need two separate
bug fixes if the counting logic ever changes. A fresh account with zero reports just renders as
two "0" tiles naturally, without needing any special-cased empty state.

### 3.12 Real multi-language support, photo annotation, and search

Multi-language support was built as a genuine working feature rather than a cosmetic toggle,
picking up the assignment brief's bonus callout for localization. Rather than mechanically
extracting every string in the app - low marginal value for the effort, and a real risk of missed
call sites - a curated, high-visibility subset was chosen so switching languages produces an
immediately obvious change across the app's most-used flows: bottom navigation labels, the whole
auth flow, the home screen's main content, and the settings screen. Spanish was the first
language added, with real hand-written translations rather than machine-garbled placeholders,
switched at runtime through AndroidX's per-app-language API and persisted the same way the
existing shake-sensitivity preference already was.

Around the same time, a photo-annotation feature let a citizen draw directly on a freshly captured
photo - circling or pointing at the specific defect - before it gets analyzed and submitted. A
plain photo of "a street" is often ambiguous about exactly which problem is being reported,
especially in a wider shot with more than one candidate issue in frame; annotation closes that gap
without needing a text description, and the markup gets baked permanently into the actual image
file rather than existing only as a disposable on-screen sketch. The drawing itself uses a fixed
bright red, deliberately distinct from the app's own blue/amber palette so it reads clearly
against almost any photo background. If nothing gets drawn, none of this extra processing runs at
all - the photo goes through exactly the same path as before, with no quality loss for the common
case of an unannotated photo.

A search field was added across all three report-list surfaces - the main reports tab, my reports,
and the admin view - all of which already shared the same underlying filter logic, so the same
change reused cleanly across all three rather than needing three separate implementations. It's a
plain text field rather than Material's dedicated search bar component, chosen specifically to
stay visually consistent with the rest of the app's form fields. Search composes with the existing
status and category filters rather than replacing them, matching case-insensitively against a
report's address and note text - and since Firestore has no native full-text search, this
deliberately stays a client-side filter over whatever's already loaded rather than standing up a
real search backend, which would be well beyond what this project needs.

### 3.13 Crashlytics, a real splash screen, and interaction polish

Firebase Crashlytics was wired in around this point too, with breadcrumb logging added at the two
places most useful for diagnosing a real-world failure: right before and after the actual
Firestore write in the submit pipeline. Crash reports are correlated only by the Firebase user id,
deliberately never by email or display name, so a session can be traced without ever logging
anything personally identifying. This only adds observability - it doesn't change any control
flow, and the existing fallback-to-local-queue behavior on failure stays exactly as it was.

A proper Android 12+ splash screen replaced the default cold-start behavior, using the existing
app logo and civic blue background so there's no visual seam between the splash and the app's own
theme once it hands off. No artificial delay was added - it dismisses as soon as the first real
frame is ready.

A modest interaction-polish pass followed: haptic feedback at the two moments that actually
benefit from it - the instant a shake is detected (since there's no visual feedback until the
camera opens a beat later) and the moment a report successfully submits - rather than sprinkling
it across every button, which would read as noise rather than intentional feedback. A logout
confirmation dialog was also added, styled to match the app's existing duplicate-report dialog
pattern, so an accidental tap on logout doesn't immediately sign someone out. A couple of other
candidates for confirmation dialogs - clearing a photo annotation, discarding an in-progress
capture via back navigation - were reviewed and deliberately left without one, judged low enough
stakes not to need it, with the discard-on-back case noted as a reasonable future addition rather
than silently ignored.

### 3.14 Letting citizens edit or withdraw their own unreviewed reports

Before this point, once a report was submitted a citizen had no way to fix a mis-categorization or
walk back an accidental duplicate - they were stuck waiting for an admin to act on it regardless.
This phase closed that gap for reports still sitting in the submitted, not-yet-reviewed state.

Editing is scoped narrowly and deliberately: a report's owner can change its category and note
while it's still unreviewed, through an inline editable form on the same detail screen rather than
a separate edit screen. Photo, location, heading, and capture timestamp stay permanently
immutable - they're the evidentiary record of what was actually seen, where, and when, and letting
a citizen alter them after the fact would undermine trust in the data itself. Only the citizen's
own classification and free-text context are reasonably correctable before anyone's reviewed it.
Deletion follows the same owner-and-still-submitted gate, with a confirmation dialog first.

The Firestore rules needed real tightening to make this safe: previously, any signed-in owner
could rewrite any field of their own report regardless of status, and deletion was disabled
entirely. The updated rule now enforces, server-side, that a non-admin owner's edit can only touch
category and note, only while the report is still submitted, and can never itself change status -
enforced through Firestore's field-diffing so a modified client genuinely can't bypass it, not
just a client-side convention. Admins keep their separate, already-existing status-change path
untouched. Deletion is owner-only and submitted-only too, and deliberately does not extend to
admins - once something's in the system, an admin's job is to resolve or track it, not remove it.

A small badge on the "My Reports" bottom-nav tab rounds this phase out, showing at a glance how
many of a citizen's own reports are still awaiting review, without needing to open the tab to
check.

### 3.15 A first-launch onboarding walkthrough

A short, three-page swipeable walkthrough now runs once on first launch, before either the
authentication or main navigation graphs take over - introducing the app's core pitch, the shake
gesture, and status tracking, with a skip option and a persisted "has completed onboarding" flag
so it only ever shows once per install. It deliberately reuses the existing primary-action button,
theme colors, and logo rather than introducing any new visual styling, so it reads as part of the
same app rather than a bolted-on intro sequence, and all of its copy was added in both English and
Spanish alongside the app's existing localization.

### 3.16 Making AI classification run in the background

Category classification - the vision model with an ML Kit fallback - originally ran synchronously
during capture, between confirming the photo and reaching the category-selection form. That added
real, noticeable delay to every single submission, which directly undercuts the app's whole pitch:
speed is the entire reason this app exists, since slowness is what stops people from reporting
civic issues in the first place. This phase moved classification out of the critical path
entirely and built a proper review workflow for when the AI's opinion doesn't match what the
citizen picked.

Confirming a photo now only waits on location and heading - both fast local reads, no network or
model call involved. The category picker stays a required manual choice exactly as before, just
with updated copy explaining that the AI will double-check it after submission. The actual
classification now runs from a new coordinator, scheduled deliberately on an app-scoped coroutine
rather than one tied to the screen's own lifecycle, since it needs to keep running even after the
user has already navigated away from the capture screen. If the AI agrees with what the citizen
picked, nothing happens. If it disagrees, comes back low-confidence, or fails outright, the report
gets flagged for review with the AI's suggestion (if it had one) attached.

That review surfaces as a clearly marked card on the report's detail screen with a "review and
resubmit" action, reusing the exact same inline edit UI already built for ordinary owner edits
rather than building a second parallel screen for it. A dedicated filter chip on the citizen's own
report list isolates just the flagged reports too, so nothing requiring their attention gets lost
in a longer list. The security rules needed a small widening to let this background write happen
at all - there's no separate server-side identity for the app's own AI-flagging logic, it runs
through the same client SDK as any other user action, so the owner-update rule had to explicitly
permit these new review-related fields while still forbidding status changes through that same
path. One known gap: a report submitted while offline and only synced later doesn't currently get
AI-reviewed retroactively, tracked as a low-priority follow-up rather than fixed immediately,
since closing it properly would introduce an awkward dependency between packages that otherwise
don't need to know about each other.

### 3.17 Two real bugs found through actual use, not compilation

Two separate user-reported bugs surfaced in the same stretch, both worth detailing since neither
would have been caught by a clean compile.

The first: a submitted report sometimes simply never appeared anywhere in the app, with impact
stats stuck at zero. The root cause was that the capture flow showed the identical "submitted"
success screen regardless of whether the report actually reached Firestore or silently fell back
to the local-only offline queue - which happens on any Firestore write failure, not only genuine
offline mode, so a permission error or a missing index looked exactly like a successful
submission from the user's point of view. The fix makes that fallback visible for the first time:
the home screen now also watches the local queue and shows a clear "waiting to sync" card whenever
anything is sitting there, so a stuck report is never silently invisible again. If this exact
complaint ever comes back and that banner isn't showing, the cause is different - the report never
even reached local storage - and is worth checking through Firestore's own auth/permission state
directly rather than assuming the same root cause.

The second: shake detection worked inconsistently on a real physical phone, a symptom the
emulator's simulated sensor never reproduced at all. Three separate issues turned out to compound
each other: the reversal counter used to detect a genuine back-and-forth shake was tallied inside
a fixed one-second window that reset on an arbitrary clock tick rather than in response to actual
motion, so a real shake starting near the end of a window could lose credit and have to start
over; the magnitude threshold used to count a reversal didn't match the threshold used to actually
trigger the shake, an inconsistency that made the two conditions harder to satisfy together than
either alone suggested; and a proximity-sensor "pocket guard" could report a stale or simply wrong
reading on real hardware in a way the emulator's fake sensor never did, silently and permanently
blocking detection with no way for the user to tell why. The fix replaced the fixed window with a
rolling deadline that only expires once motion genuinely stops, unified the two mismatched
thresholds into one, and removed the proximity guard entirely rather than trying to make it more
forgiving - it's an inherently unreliable signal across different phone hardware, and the app
already has a guaranteed fallback in the manual report button for exactly this reason. This class
of bug can't be fully confirmed by a clean compile alone; it needed retesting on the actual device
that first showed the symptom, and remaining inconsistency afterward would point next at the
adjustable sensitivity slider in Settings.

Separately in the same stretch, category selection stopped being mandatory at submission - a
report can now go out uncategorized, with the background AI review picking up the slack: a
confident classification gets applied automatically, and anything it can't confidently classify
falls back to the existing manual-review flag so the citizen fills it in later from their own
report list.

---

### 3.18 Fixing the language bug for real, and a real interactive map

The earlier locale fix - forcing an Activity recreate after a language change - turned out to be
treating the symptom rather than the actual cause, and got properly fixed once the root issue was
found: the per-app-language API only genuinely integrates with `AppCompatActivity`, not the plain
Activity base class this app's main screen used. Once `MainActivity` was switched to extend
`AppCompatActivity`, all the manual recreate plumbing could be removed entirely - the framework
now handles it correctly on its own, preserving navigation state properly in a way the manual
workaround never quite did. It's a good illustration of a first fix looking like it worked (it did
force a visible UI refresh) while not actually addressing why the locale wasn't taking effect in
the first place. A third language, French, was added around the same time, covering the same
curated string subset as the existing Spanish support.

The map tab's placeholder dot-grid was also finally replaced with a real interactive map, using
Geoapify's tile service through OSMdroid, the standard open-source Android map view library -
Google Maps wouldn't have worked here since Geoapify serves plain map tiles over a URL rather than
through Google's own SDK. Report pins are colored using the exact same status colors used
everywhere else in the app, and tapping one opens the same report detail flow the list view
already used. Wrapping a classic Android View-based map component inside Compose needed some care
around its lifecycle - the map owns background tile-loading threads that have to be explicitly
paused, resumed, and detached in step with the screen's own lifecycle, or they'd leak past it.

### 3.19 Full admin triage workflow and closing the loop back to citizens

Two connected phases built out a genuine admin workflow and its citizen-facing counterpart. On the
admin side, the previous experience was a bare status dropdown with no real triage information and
no record of what was actually done about a report. Admins can now assign a department, set a
priority level, and - when moving a report into review or marking it resolved - are required to
write an explanatory note, with an optional photo attached when resolving. The admin list itself
stayed deliberately compact, showing department and priority as a small label and colored dot next
to the existing status control rather than crowding the row with detail; the full editing controls
live on the report's own detail screen.

The other half of this closes the loop back to the citizen who filed the report - and this is
genuinely one of the more meaningful features in the app: when a report is marked resolved, the
citizen now sees an "update from the city" card showing the admin's actual note, and a photo of
the fix if one was attached. Previously a status badge just flipped to "resolved" with no
explanation at all; now there's a real answer to "so what actually happened." A community upvoting
feature was added alongside this, letting any other citizen indicate an existing report also
affects them rather than filing a duplicate - though this was later removed entirely once its
security properties were reassessed (see the entry on that decision further on). Status-change
notifications also picked up a snippet of the resolution note in their body text, so a citizen can
see roughly why their report changed status without needing to open the app first.

### 3.20 A real free-text category for issues that don't fit the fixed list

The fixed five-category list - pothole, streetlight, garbage, water leak, other - inevitably left
gaps: a fallen tree, a burst pipe, a downed power line, and countless other genuine one-off issues
all landed in an undifferentiated "other" bucket with no further context, which served neither the
citizen (their specific issue wasn't represented) nor the admin triaging a pile of unlabeled
"other" reports later. A real, selectable custom category was added, paired with a short free-text
description field, wherever category gets chosen or displayed throughout the app - capture, the
citizen edit flow, and the admin/AI-review surfaces. The vision classifier's own prompt was
updated too, so it can propose a specific custom label itself when a photo clearly shows something
outside the fixed list rather than defaulting everything uncertain to "other."

### 3.21 A subtle Firestore listener bug, only reproducible on a real flaky connection

A user report of the impact-stats count briefly flashing to the correct number and then dropping
back to zero led to a genuinely interesting root cause. Every Firestore listener in the reports
repository discarded the error parameter on its callback and treated a null snapshot exactly the
same as "zero documents." It turns out Firestore actually delivers a null snapshot specifically on
a transient error - a listen-stream reconnect after a brief network blip - typically followed
almost immediately by a fresh, correct snapshot once the stream recovers. So an ordinary one-second
connectivity hiccup was being rendered, briefly but visibly, as "you have zero reports," before
silently correcting itself a moment later. The fix makes every listener check for a real error
first and simply hold its last known state rather than treating a transient hiccup as an empty
result - since Firestore always returns a valid, non-null snapshot for a genuinely empty result
set, this change only affects the actual error case, never a real zero-reports state. This is a
good example of a bug that only reproduces under real, imperfect network conditions on a physical
device - a stable-network manual test, let alone a compile check, would never have surfaced it.

### 3.22 Background shake detection, and an unresolved delete-permission mystery

Shake detection was extended to keep working even while the app isn't open, behind an explicit
opt-in toggle in Settings rather than listening silently and unconditionally forever. It runs as a
foreground service - reusing the exact same shake-detection logic the in-app version already uses,
so any future sensitivity fix automatically applies to both - with the persistent low-priority
notification Android requires for any foreground service, which also has the side benefit of
always making it obvious to the user that the app is actively listening. Triggering a shake while
the app is closed launches straight into the capture screen once the user's authenticated, rather
than dumping them on whatever screen the app last happened to be on.

Separately, a genuinely puzzling bug came up around this time and was not fully resolved: a user
got a permission-denied error trying to delete their own unreviewed report, despite the deployed
security rules, read literally, clearly permitting exactly that action. The rule logic itself was
re-verified line by line and found correct - no mismatch in field names, casing, or the underlying
condition. Rather than keep guessing, targeted diagnostic logging was added directly in the delete
path, capturing the signed-in user id, the document's actual stored owner and status, and whether
the document even exists, so the next occurrence produces hard facts instead of requiring a manual
console lookup. The leading suspects at the time were a mismatch between which Firebase project the
rules were actually published to versus which one the app's configuration pointed at, a stray
unpublished draft in the console, or ordinary rules-propagation delay - but this stayed the one
genuinely open, unresolved item at that point in the project.

### 3.23 A professionalization pass: reference numbers, a status timeline, and real error states

With the direction shifting toward "keep shake-to-report exactly as it is, now make the whole app
feel more like a professional reporting product," a cluster of trust-and-polish features landed
together.

Every report now gets a short, human-readable reference code derived deterministically from its
underlying document id - the kind of thing real service apps (couriers, ISPs, government portals)
always give you to quote back in a support conversation, rather than either a raw database id or
nothing at all. It's purely presentational, with no new stored field and no real collision risk
beyond what the underlying id already guarantees.

A proper status history and timeline followed - every status-changing action an admin takes now
appends an entry (status, timestamp, and note) to the report's history, using Firestore's atomic
array-union operation specifically so two admin actions happening close together can't clobber
each other's entries the way a naive read-modify-write of the whole array could. The report detail
screen renders this as a vertical timeline for both citizens and admins. Reports that existed
before this feature shipped gracefully fall back to a synthesized single-entry timeline built from
whatever status they're already at, rather than showing a blank section or crashing - deliberately
avoiding the need for any kind of backfill migration script, which wasn't really available anyway
without server-side compute.

Real error handling rounded out this pass. Previously, a genuinely stuck error - something like a
permission failure that will never self-resolve, as opposed to the transient blips the earlier
listener fix already handled gracefully - left a list screen looking indistinguishable from
perpetually loading or genuinely empty, with no way for the user to tell something had actually
gone wrong. A new error stream drives a dismissible snackbar with a retry action instead of
replacing the whole list with a full-screen error state - the screen keeps showing whatever it
last successfully loaded, which is friendlier than throwing away good cached data over what might
just be a passing hiccup. This not-every-error-should-nuke-the-screen pattern is standard in
mature apps (mail clients, banking apps) for exactly this reason.

### 3.24 A gallery picker, and moving AI classification onto WorkManager

A gap surfaced while re-confirming the capture flow's original spec: there was no way to pick an
existing photo, only to take a new one. A gallery picker was added, deliberately without video
capture at this point - that came later as its own explicitly scoped feature. If the user backs
out of the camera intent, they now land on a small chooser instead of being dumped out of the
report flow entirely, with a photo picker option beside the camera option. The picker uses
Android's modern Photo Picker API rather than a raw storage intent, which has a genuinely nice
security property: no broad media-read permission is needed at all, since the picker runs in its
own sandboxed system process and only ever hands the app a reference to the one photo the user
explicitly chose.

Separately, AI classification's background execution got a real reliability upgrade. It had been
running on an app-scoped coroutine, which kept working after the user navigated away from the
capture screen but died immediately if the whole process got killed - swiped away from recents,
or reclaimed by the OS under memory pressure. Moving it onto a proper WorkManager job is what
actually makes "classifies even if the app is fully closed" true, rather than merely "keeps
running if the app happens to still be alive somewhere in the background" - WorkManager persists
the job to its own durable store and the OS resumes it even after the app's process, or the whole
device, restarts. It only runs with a network connection available, and gets three retry attempts
before falling back to a notification telling the user manual input is needed - a genuinely rare
case requiring both the AI server and the on-device fallback to fail together.

Severity got wired into the same classification pass too - the vision model's prompt was extended
to also propose a severity level based on the visible scale of damage in the photo, applied
automatically alongside the category on the same "AI is confident and nothing contests it"
condition, never forced onto a report the AI wasn't confident about. And the manual category
picker on the submit form was de-emphasized rather than removed - collapsed by default with a
line explaining the AI will handle it automatically, with an easy "choose manually" option still
one tap away, so the interface visually communicates "optional" instead of "required" without
actually taking the override away.

### 3.25 Video reports, full anonymity, and a real admin dashboard

Three separate, sizeable features landed in the same stretch, each following an explicit scoping
choice.

Video reports were deliberately kept simple: no AI analysis of video at all, by choice - a video
report always requires manual category selection. Extending the vision model to sample frames
from a video was a real option that was considered and explicitly rejected in favor of simply
telling the human "AI can't do this one," which kept the whole feature far smaller than true video
classification would have been, without pretending the AI does something it doesn't.

Anonymous reporting went further than a typical "hide the name" toggle - a fully anonymous report
records no owner at all, not even visible to admins. That has real, acknowledged consequences: an
anonymous report can never be edited or deleted afterward by anyone, since there's no owner to
authorize it, and it can never trigger a personal status notification either. The submit screen's
toggle states this trade-off plainly so nobody discovers it as a surprise later. Making this work
needed a genuine, deliberate loosening of the security rules - the create rule now has two
distinct branches, one for a normal owned report and one specifically for an anonymous submission
with an empty owner field - while still requiring the submitter to be authenticated to use the app
at all. "Anonymous" here means no identity attached to the document, not no login required, which
is exactly what keeps the rule change safe rather than opening the collection to arbitrary
unauthenticated writes.

Admin access got a real second look too. A "sign in as admin" option on the login screen is
purely a UX affordance - the actual check happens after Firebase authentication succeeds, and if
the toggle was on but the account's real role isn't admin, the session is immediately signed back
out with a clear message rather than silently dropping someone into the ordinary citizen
experience, which would just look like a confusing bug. Investigating this also surfaced a real,
if narrow, security gap: the rule governing new user documents checked that the account belonged
to the right user but never actually constrained the role field itself, meaning a client could in
principle have set its own role to admin on first write - the only reason this never happened in
practice was that the registration code always hardcoded citizen client-side, which is trusting
well-behaved app code, not a server-side guarantee. That gap is now closed at the rule level.
Admin accounts remain exclusively provisioned by manually editing a user's document in the
console - there's deliberately no self-service path to becoming an admin anywhere in the app. A
proper route guard was added too, so an admin-only screen can't be reached at all by directly
deep-linking its route, not just kept out of the bottom navigation - genuine defense in depth on
top of the server-side rule, which was always the real boundary either way.

A new admin dashboard rounded this phase out - a landing overview distinct from the existing
detailed reports list, aggregating counts by status, category, and department, and surfacing a
"needs attention" queue of unassigned, urgent, or AI-flagged reports as a fast triage entry point.

### 3.26 Real biometric login

Fingerprint and face login were added using Android's standard system biometric prompt rather than
a custom in-app recognition model - this is how virtually every serious app handles it, and lets
the OS distinguish which biometric method the device actually has enrolled without the app needing
to care. It's explicitly framed as an alternative fast path into an account that still
fundamentally exists as a normal email/password account, not a separate authentication scheme of
its own.

The interesting part of this feature is what it took to make it genuinely secure rather than just
convenient. The app already had Keystore-encrypted storage for a remembered email, but that scheme
only protects data at rest - nothing stops the app itself from decrypting it the instant it's
read, since there's no biometric gate on the decryption step, only on the file being unreadable to
another app or an attacker without root. A real biometric-gated credential needs the encryption
key itself to require authentication before it can be used at all. The new credential store
generates a Keystore key with exactly that requirement set - the hardware itself refuses to unlock
that specific key for decryption until a fresh, successful biometric prompt has just occurred, which
is a real hardware-backed guarantee, not app-level logic that a rooted device could bypass by
reading storage directly. Once biometric authentication succeeds, the stored password is decrypted
and run through the exact same login path a manual entry would take, so every other check - admin
role verification, remember-me - applies identically with no separate, weaker code path for the
biometric case.

The offer to enable it appears right after a successful manual login, only on a device that
actually has biometric hardware enrolled, and is never forced or repeated after being dismissed
once. Disabling it from settings is straightforward; re-enabling deliberately requires logging in
with a password again, since settings itself never has access to the plaintext password needed to
re-encrypt a new credential. Worth being honest about the actual threat model here: the decrypted
password still exists briefly in process memory and is sent to Firebase Auth in plaintext over TLS
exactly as a manual login would be - the biometric gate protects the credential at rest on the
device, it doesn't change what the authentication API itself requires once a login attempt
actually happens.

### 3.27 Three real bugs, each found by working backward from an actual device error

A cluster of three separate issues surfaced in quick succession, each solved by tracing an actual
error back to its root rather than guessing, and each worth walking through since they illustrate
different debugging approaches.

The first was a Firestore error on "My Reports": a missing composite index. Firestore requires an
explicit index any time a query combines an equality filter on one field with sorting on a
different field - a requirement that doesn't exist in a typical relational database and is easy to
miss if you're not expecting it. The query itself had existed since early in the project; it only
became a visible error once the underlying index had never been created in this particular
Firebase project - a genuine infrastructure gap, not a code bug, and one that only became visible
to begin with because of the earlier work adding real error surfacing instead of silently logging
and showing an empty list. A second, identical latent issue was found nearby in the duplicate-check
query while investigating the first, fixed the same way. Both required indexes are now documented
in the repo itself rather than existing only as a manual console click-through with no record of
why they're needed.

The second was a permission-denied error on the "All Reports" community tab. The reports read rule
only allowed a citizen to read their own reports (plus anonymous ones, plus admins reading
everything) - which worked fine for "my reports," but was structurally incompatible with a
full-collection browse view, since Firestore denies an entire query result the moment even one
document in it would fail the read rule for the requesting user. As soon as the collection held
more than one citizen's non-anonymous reports, every citizen's "all reports" query started failing
outright. After confirming that browsing everyone's reports really is the intended design - the
whole point of the community and map views - the rule was simply widened to any authenticated
user, leaving every write rule untouched. This was found by working backward methodically: opening
the actual Firestore data to rule out data corruption, then asking exactly which screen the error
happened on, which was the concrete fact that pinpointed a full-collection query hitting a
narrower-than-intended rule rather than continuing to suspect the index or the account's role.

The third was the map screen rendering as a blank gray canvas with no error. Working through this
one meant ruling things out layer by layer rather than guessing: confirming the rules fix was
already live, noting the exact shade of gray shown wasn't any app background or empty-state text
(so the map component itself was being reached), testing the tile server URL directly outside the
app to confirm the API key was valid, and finally checking the logs around the map's rendering
setup and finding a total absence of any network request logging at all - which pointed at the
map's style never even being attempted to load, not a network or auth failure. The actual cause
was a missing lifecycle call: the map library's documented lifecycle needs an explicit `onCreate`
call before requesting the map, which the code had simply never made, alongside the resume/pause
calls that were already wired up. Without it, the underlying rendering surface still draws its own
idle background color - the exact gray observed - while never actually issuing the request that
would load real tiles. This was a previously-uncaught lifecycle bug dating back to an earlier
library migration in the project, not a new regression - it simply had never been exercised
end-to-end with real report data on an actual device before, since earlier map testing may well
have hit the "nothing to plot yet" empty state before ever reaching this code path. It's a good
concrete illustration of a whole class of Android bug where everything logs as healthy and nothing
crashes, and the only real clue is the total absence of something that should have been there -
reasoning from what's missing in a log, not just what's present.

### 3.28 An in-app help chatbot, deliberately scoped to the app's own domain

The requirement here was explicit: a real chatbot, but one strictly limited to questions about the
app and a citizen's own reports, not a general-purpose assistant. It reuses the exact same vision
model connection already built for photo classification, just without the image content and with
support for a proper multi-turn conversation rather than a single one-shot prompt - deliberately
not introducing a second AI provider, so the app's "AI backend" story stays singular and
consistent rather than fragmenting across two different endpoints. If the server's unreachable, it
fails the same graceful way the classifier does, showing a plain "assistant unavailable" message
rather than a raw error.

Scope enforcement happens through the system prompt alone rather than a separate
keyword-filtering or classification pass - simpler, no extra infrastructure, and it's genuinely
how most real in-app chat assistants scope themselves in practice. The prompt explicitly names
what the assistant can help with and instructs it to decline anything else briefly rather than
lecturing the user about why. Worth being honest about the limit here though: this is
prompt-based enforcement only, and a sufficiently determined adversarial prompt could still coax
the underlying model off-topic - a real production system would likely add a second guardrail
layer on top, which is out of scope for this project but worth naming plainly rather than implying
the scoping is airtight.

The assistant can also answer questions about a citizen's own reports specifically, not just
generic how-to questions - a summary of their most recent reports gets built into the system
prompt the first time they actually send a message, not eagerly when the chat screen opens, and
only once per session rather than being rebuilt on every message, so a status change mid-
conversation doesn't quietly shift the assistant's frame of reference under the user's feet. Worth
flagging plainly: this is real report data going into an LLM prompt sent to a development server,
which is fine for this project's LAN-only deployment but would need real handling - a properly
hosted and audited API, explicit consent copy - before anything resembling a genuine deployment.

### 3.29 A citizen-to-admin support inbox, and real username-based login

A lightweight support-contact feature let citizens send a message and get a single reply from an
admin, kept deliberately separate from the reports collection entirely, since a support question
is a fundamentally different kind of record with its own access pattern - a citizen can only read
their own messages plus any admin reply, never other citizens' correspondence, unlike the
open-to-all-signed-in read model reports use.

Username-based login was the more involved addition. Firebase Authentication itself only ever
signs in with email and password under the hood - there's no native username login - so this
needed a lookup layer built on top rather than a change to the underlying mechanism. Uniqueness is
enforced through a separate reservation collection mapping username to user id, since Firestore's
security rules alone have no way to check "does any other document already have this value."
Registration reserves the username and creates the user's profile document inside one Firestore
transaction, which closes the race window between an earlier live-availability check (used only
for friendlier inline feedback on the register form) and the real write - two people racing to
register the same username genuinely cannot both succeed, since Firestore aborts and retries a
transaction against any conflicting concurrent write.

The login screen accepts either an email or a username in one field, distinguishing which was
typed by checking for the presence of an "@" - a username can never contain one, enforced at
registration. A username resolves to its real email through a two-step lookup before the actual
Firebase sign-in call happens; every other part of the login flow, including admin-role
verification and the biometric-login offer, runs completely unchanged after that resolution step.

Making this possible needed a real, deliberate rules change, since resolving a username to an
email has to happen before the user is authenticated at all - that's the entire point of letting
someone type a username instead of typing their email in the first place. The username-to-id
lookup collection is readable by anyone, exposing only that mapping, never a password or email
directly - which does mean anyone can check whether a given username is already taken, a
low-severity information leak accepted for this project's scope rather than solved with a Cloud
Function. Reading a single user document by a known id was also opened up narrowly for the same
reason, while collection-wide queries against user documents stayed exactly as restricted as
before - the narrowest opening that still makes the feature work, documented plainly as an
accepted trade-off rather than an oversight, since Firestore rules simply have no way to expose
only one field of a document without opening the whole thing.

### 3.30 Evolving into an infrastructure intelligence platform: incidents

At this point the project's direction shifted from a simple citizen reporting tool toward
something closer to a real infrastructure management platform, built out across several
substantial phases - incidents, a full work order lifecycle, predictive risk scoring, and a
multi-layer digital map. This first phase introduced incidents.

A few architectural decisions were settled before writing any code. With no server-side compute
available in this project - no Cloud Functions, Firestore plus client plus rules only - clustering
runs entirely on-device, triggered by a background job after every report submission, using the
same durability reasoning already established for background AI classification: the job survives
process death and gets retried by the platform itself rather than by hand-rolled logic. Work
orders were planned as a genuinely separate collection from incidents rather than fields bolted on,
since an incident can reasonably need more than one work order over its lifetime - a re-inspection
after a failed repair, for instance. And a single shared audit-log collection, rather than
per-entity subcollections, was chosen so "everything that happened" across incidents, work orders,
and reports could be queried from one place.

An incident sits above individual reports without replacing anything about them - a report gains
one new field pointing at the incident it's part of, if any, while its own status, priority,
department, and history stay completely untouched and keep functioning exactly as they did before.
An incident's own lifecycle runs independently: detected, validating, confirmed, assigned, in
progress, resolved, verified, closed - plus a dismissed state for when an admin determines a
cluster wasn't actually the same underlying problem, which simply releases its member reports
without resolving anything.

Confidence scoring for whether reports should cluster together is a plain, inspectable rule-based
formula - category match, geographic proximity, time proximity, severity agreement, and existing
community confirmation, each weighted and combined into a single score - deliberately not
presented as machine learning or "AI-powered," since every factor and its weight is fully visible
and explainable rather than a black box. A genuine visual-similarity signal was considered but not
built, since it would need a real image-embedding model this project's classifier doesn't provide;
its intended weight was folded into the category and distance factors instead. Two thresholds
gate what happens with a score: high enough auto-attaches or auto-creates a cluster, low enough is
ignored outright, and anything in between surfaces as a low-confidence candidate for a human to
decide on rather than being silently merged - reports are never automatically clustered against a
citizen's back with real uncertainty involved. Before ever proposing a brand new incident, the
clustering logic also checks whether a suitable existing open incident is already nearby and tries
attaching there first, specifically to avoid spawning duplicate incidents for the same underlying
problem.

Admins can always manually merge or unmerge a report into an incident too, at full confidence
regardless of what any automatic score would say - a human decision is by definition not a
low-confidence guess - with every such action appended to the incident's own history so there's a
complete record of who changed what and when.

On the citizen side, an incident stays almost entirely invisible except for a small, plainly
worded "part of a wider tracked issue" card on the report detail screen when relevant - no
confidence scores, no member lists, none of the admin machinery leaking into the citizen
experience. Admins get a full incidents list and a detail screen with status, severity, and
department controls, a validation note field, the member reports with an unmerge option, and a
list of nearby reports not yet part of the incident that could be merged in.

A few limitations are worth naming plainly here: there's still no visual-similarity signal, no
low-confidence surfacing for the brand-new-incident path specifically (only for attaching to an
existing one), and aggregation stays entirely client-side rather than using any server-side
counting - all consistent with, and inheriting, the same scope trade-offs already made elsewhere
in the project.

### 3.31 Phase 2: a complete resolution lifecycle for incidents

Incidents had a lifecycle status from the previous phase but no real machinery behind actually
resolving one - no record of why a cluster was accepted or rejected as a genuine issue, no
transparent explanation of why one incident matters more than another, no tracking of the actual
dispatch-and-repair work once something gets actioned, and no trace of who changed what. This
phase built all of that.

An incident can now be explicitly validated or rejected by an admin, with a note explaining the
decision. A priority score, on a 0-100 scale, gets computed from report count, upvotes, severity,
how long the incident has been open, and a scan for emergency-related keywords in the underlying
reports - and crucially, every factor that contributed to the score is shown in full on the
incident's detail screen, not just a bare number, so the score is genuinely inspectable rather
than a mysterious output. It recomputes automatically whenever status or severity changes, so it
never goes stale. A department can also be automatically suggested when an incident is first
created, based on category and keywords, though an admin can always override it - the suggestion
is a starting point, never a lock-in.

Work orders became a real, separate entity at this point too - admin-only end to end, since a work
order can carry an assigned team member's name and internal notes that aren't meant for citizen
visibility, unlike the incident itself. A work order moves through a genuinely detailed ten-state
lifecycle from creation through assignment, various stages of physical work, completion, and
verification - branching into an "awaiting verification" state specifically when resolution photos
get attached, otherwise going straight to completed. And a single shared audit log now records
every meaningful change across incidents and work orders alike - who did what, when, and what the
value was before and after - deliberately built so that a failure to write an audit entry can
never block or roll back the real administrative action it's describing; observability should
never get to hold up the actual work.

Security-wise, work orders are admin-only end to end at the rules level too, with no citizen or
automatic-clustering write path at all - a work order is always an explicit administrative action,
never something the system creates on its own. The audit log allows any signed-in user to create
an entry recording their own action (so the automatic clustering path can still log what it did),
but reading the log is restricted to admins, since an entry can reference details - a rejected
incident's validation note, a work order's internal note - that aren't meant for citizen
visibility. It permits no updates or deletes at all, ever, since a retroactively-editable audit
trail wouldn't actually provide real traceability.

---

### 3.32 Phase 3: predictive risk scoring

The next phase built a transparent, explainable risk score for each area and category, derived
entirely from real historical report data - never a fabricated or hard-coded number, and
explicitly required to say plainly "insufficient data" rather than invent a plausible-looking
score when there simply isn't enough evidence yet to say anything meaningful. A risk score sits on
a 0-100 scale grouped into four bands, low through critical, and every score carries a genuine
insufficient-data flag that the UI has to check before treating a score as real - a bucket that
hasn't cleared the minimum report threshold literally never gets a numeric score computed for it
at all.

The scoring formula itself follows the same philosophy as the incident priority score - a plain,
inspectable combination of report volume, how much of the backlog remains unresolved, average
reported severity, how many confirmed incidents exist in the area, whether recent activity is
trending upward compared to the prior period, and a penalty for historically slow resolution
times - deliberately not presented as anything resembling machine learning, with the same door
left open for a real trained model to replace the scoring function later without touching
anything that calls it. Since this project has no real ward or district boundary data available,
"area" is necessarily a coarse geographic grid cell rather than a genuine administrative
boundary - a documented simplification, not a claim of precision the project doesn't have.

Scores refresh periodically through a background job running every few hours rather than on every
single report, reading the full reports collection once per run and either computing a real score
or writing an explicit insufficient-data placeholder for each area-and-category bucket - on-device
again, for the same reason incident clustering runs on-device: there's no server-side compute
available in this project. Admins get a dedicated risk areas screen sorted with elevated risk
surfaced first, and a detail view for each area showing the full factor breakdown, a trend section
once enough history exists to plot one, and a drill-down into the actual reports behind the score -
satisfying the same "risk area leads to the reports behind it" navigation model incidents already
follow.

### 3.33 Phase 4: a real multi-layer digital map

The original map only ever plotted individual reports. This phase turned it into a genuine
multi-layer view - reports, incidents, infrastructure, risk, and maintenance work, each
independently selectable. The infrastructure layer is deliberately an honest, documented stub
rather than fabricated content: this project has no real road, streetlight, or drainage asset
dataset to draw from, and rather than invent fake pins to fill the space, selecting that layer
shows a plain explanation that no real data source is connected yet. The layer structure and
rendering path already exist, so a genuine asset feed could be plugged in later without touching
any of the other four layers.

Switching between layers never triggers a new database query - every layer's underlying data is
already a live listener the app holds anyway for its own screens (the report list, the incident
list, and so on), so changing which layer is active only changes which already-loaded data gets
turned into markers on screen, which is the map's actual answer to keeping the whole thing
performant without loading the entire dataset into memory at once. A location search field matches
against whatever's already loaded rather than pretending to be a real geocoding lookup, since no
geocoding API is configured in this project - an honest "search what's on screen" rather than a
fabricated address search. On the citizen side, tapping an incident or risk marker still shows its
basic popup information, but doesn't navigate into an admin-only detail screen - consistent with
keeping incident and risk concepts out of the ordinary citizen experience.

A few limitations carry through from these two phases worth stating plainly: the infrastructure
layer has no real backing dataset, risk areas are grid cells rather than genuine administrative
boundaries, none of the background compute (clustering, risk refresh) runs server-side, and none
of the scoring anywhere in the app is real machine learning - all deliberate, all documented, and
all architected so a more sophisticated real version could slot in later without a rewrite.

### 3.34 A clustering bug found on review: best match, not first match

A review of the incident-clustering logic caught a real bug: when scoring which existing incident
a new report might belong to, the code returned as soon as it found any incident scoring within
either the auto-merge or low-confidence range - not necessarily the best-scoring one. A report
sitting near two different existing incidents, one a weak match encountered first and one a
genuinely strong match encountered later in iteration order, could get stuck as a low-confidence
candidate against the weak match and never actually reach the strong one it should have
auto-attached to. The fix scores every nearby open incident first and only then picks the single
highest-confidence result before deciding what to do - no behavior change for the common case of
only one nearby incident, but a real correctness fix for the case where more than one exists.

### 3.35 Simplifying the citizen experience back down, and demanding a real GPS fix

After the platform had grown substantially more sophisticated on the admin side, a deliberate
simplification pass pulled the citizen-facing experience back to something more focused: citizens
were scoped down to seeing only their own reports, with no community-wide browsing feed at all,
split into a simple solved/not-solved view rather than a filterable general list. The whole
five-layer digital map, along with the general reports browsing tab it lived behind, was removed
from the citizen-facing app entirely - it had never been reachable from anywhere else in the
citizen UI, and the admin side kept its own separate dashboard, incident, and risk-area screens
completely untouched.

Location capture also got tightened at the same time: the location request now explicitly demands
a real GPS-chip fix rather than accepting a coarser network or Wi-Fi-derived estimate, rejects any
result whose reported accuracy is worse than 50 meters, and times out after 15 seconds rather than
hanging indefinitely if a GPS lock can't be acquired at all - indoors, for instance.

### 3.36 A real forgot-username and forgot-password flow

A genuine gap surfaced once username login had been in place for a while: there was no way to
recover a forgotten username at all, since the lookup only ever worked in the username-to-email
direction, and no password reset path existed either. Both got built properly - a new reservation
document mirrors the existing username-reservation pattern but keyed by email instead, letting a
forgotten-username lookup work the same pre-authentication-safe way the login flow's own username
lookup already does, and password reset is a thin wrapper over Firebase Authentication's own
built-in reset-email mechanism, which handles the actual email delivery entirely on Firebase's
side. The recovery screen deliberately never confirms or denies whether a given email actually has
an account behind it - a failed username lookup and "this email has no account" look completely
identical to the user, which is standard practice for not letting a recovery flow become a way to
enumerate valid accounts.

### 3.37 Usability fixes, a smaller footprint on Profile and Capture, and shake tuning

A handful of real usability issues got fixed together. The AI-review banner's low-confidence
phrasing - hedging with "might be X instead of Y" - read as confusing rather than simply honest
about uncertainty, so it was simplified to plainly say the AI couldn't categorize the photo and
ask the citizen to choose. The floating chat-help button, fixed in the same bottom-right corner on
every screen, ended up covering other bottom-right content on some screens since it had no
awareness of what else was on screen below it - fixed by restricting it to only appear on the home
screen and making it freely draggable, which solves the underlying problem (a globally-hoisted
overlay with no per-screen layout awareness) more generally than picking one more fixed position
would have.

Profile and the capture form both got a real space-efficiency pass too. Profile's spacing and
sizing were tightened throughout so its already-non-scrolling layout comfortably fits more device
sizes without clipping anything. The capture form's full-size photo preview, which had been the
main reason the form needed to scroll at all, shrank down to a small tappable thumbnail with a
full-screen view available on tap for anyone who wants to inspect the photo more closely - the
same pattern was then applied to the report detail screen's own photo and the admin's resolution
photo shortly after, though that screen kept its scroll behavior, since unlike a fixed-length form
it's a detail page whose content genuinely varies in length depending on the report's state.

Shake detection got a further tuning pass on both reliability and efficiency: a low-pass filter
now smooths raw accelerometer readings before they feed into the reversal-detection logic, since
high-frequency jitter - walking, a bumpy pocket, a phone resting somewhere vibrating - could
register as spurious motion reversals on its own. And the background listening service now
samples the sensor at a meaningfully lower rate than the foreground in-app listener, since
reacting to a deliberate shake in the background doesn't need the same responsiveness a live
foreground interaction does, trading a small amount of latency for real battery savings.

### 3.38 A real automated test suite, and a decision to keep photo storage local

Two things were settled together late in the project. Photo and video storage stayed
deliberately local-only - Firebase Storage requires the paid Blaze plan, and the decision was made
explicitly to keep the existing device-local architecture rather than enable billing this close to
submission, documented as a real architectural choice rather than left looking like an oversight.

A genuine, meaningful unit test suite was also built out beyond the single test that had existed
before - covering the priority-scoring formula's weighting and edge cases, department suggestion's
keyword matching, the risk-scoring formula's bucketing and band boundaries, and registration field
validation. Building these tests actually caught a real bug along the way: department suggestion
was matching keywords as plain substrings rather than whole words, which meant a report describing
a burst pipe "flooding the street" got mis-routed to roads instead of water, purely because
"street" happens to contain the substring "tree." That's exactly the kind of defect a real test
suite exists to catch before it reaches a live report being routed to the wrong department -
fixed by tokenizing into whole words and checking membership properly instead of a naive substring
check.

### 3.39 Better local notifications: deep-linking, a real icon, and an admin-facing type

Rather than build real server-triggered push at this point (which would have meant enabling
billing, the same trade-off already declined for photo storage), the existing local-notification
system got three concrete improvements instead. Tapping a notification previously just reopened
the app to whatever screen it last happened to show, rather than the actual report the
notification was about - fixed by carrying a target route through the notification's intent and
consuming it once the app opens, including the case where the app is already running in the
background and receives the tap through a different Android callback than a fresh launch would.
The notification icon switched from a generic stock system icon to a real flat silhouette
matching the app's own branding, since a full-color icon isn't usable in the status bar at all -
the system always re-tints notification icons to a single color regardless of what's supplied.

A genuinely new notification type was added too, specifically for admins: a local notification
fires when a new support message arrives while an admin is signed in with the app running,
filtered so signing in doesn't replay the entire existing inbox as a flood of notifications all at
once. Worth being clear about the honest limit here - like every other notification in the project
at this point, this only fires while that specific admin's own device has the app alive and signed
in, not a killed app, and not any other admin's device on a different phone. That gap is exactly
what the later FCM work (see the entry below) went on to close for citizen-facing status
notifications specifically.

### 3.40 Achievement badges, added and then removed

A lightweight achievement-badge feature was added to Profile in response to feedback that the app
felt too basic - computed entirely client-side from data the app already had on hand, so it added
no new Firestore schema or queries. It didn't last long: after trying it, the feedback was that it
wasn't wanted, and it was removed just as cleanly as it had been added, with no trace left in
either the UI or the underlying ViewModel layer. Worth including here mainly as an honest record
that not every feature that gets built survives - which is a normal part of an iterative process,
not something to hide from a technical report.

### 3.41 Removing upvoting over a security concern

A security review surfaced a real integrity gap in the upvoting feature: the count and the array
tracking who had upvoted were updated by a plain client read-then-write rather than an atomic
server-side operation, meaning a malicious client could in principle inflate its own upvote
bookkeeping. Properly fixing that would need a Cloud Function running a real transaction, which
means the same billing trade-off already declined elsewhere in the project. Rather than accept the
risk or change that billing decision for one feature, upvoting was removed entirely - from the
report data model, the repository, the UI, and every place its aggregate count fed into the
incident-priority and clustering-confidence formulas, with those formulas' weights properly
rebalanced afterward rather than just leaving a hole where the upvote factor used to be. This is
the same pattern as the badges decision above: a feature that introduced more risk than it was
worth for this project's scope, removed deliberately rather than shipped with a known,
unaddressed integrity gap.

### 3.42 Narrowing a login-lookup privacy exposure

A further security review flagged that the rule governing single-document reads of a user's
profile was broader than the app's own documentation claimed - it let anyone who already had or
could guess a user's id read that entire profile, including role and display name, before even
being signed in. Properly closing this fully would need a Cloud Function performing the lookup
server-side rather than any direct client read of per-user data pre-authentication - declined,
consistent with staying off the paid billing tier throughout this project. What was actually done
meaningfully narrows the exposure without fully closing it: a new, deliberately minimal lookup
document now holds only email and username, nothing else, and the login-resolution logic reads
from that narrow document instead of the full profile. The broad single-document read of the full
profile was closed off entirely and folded back into the same owner-or-admin-only rule the rest of
the collection already uses.

Worth being honest about what's left: a pre-authentication caller who already has or guesses a
user id can still read that narrow email-and-username pair - meaningfully less than before, since
role and display name are no longer exposed that way, but not zero, since Firestore's rules simply
have no way to verify a caller genuinely arrived through the intended username-lookup path first.
Full closure of that residual gap needs the same server-side Cloud Function approach noted
throughout this section of the project - documented plainly as a known, accepted trade-off rather
than something silently left broken.

---

### 3.43 Real push notifications, triggered server-side by a Cloud Function

The app's notifications had all been local up to this point - a citizen only ever saw a
status-change notification if their own device's app process happened to already be alive and
running the live Firestore listener that watches for it. That's a real gap for anything resembling
a genuine civic-reporting deployment, where the whole point is being told about a status change
even with the app fully closed. A periodic background sync could have approximated "even when
closed," but wouldn't have been instant - and instant, real push was the explicit requirement here,
so a background-polling compromise wasn't an acceptable substitute.

Every genuine push-delivery mechanism needs a credential somewhere, and there are really only two
places for it to live: server-side, behind a Cloud Function - which requires the paid Blaze plan
regardless of actual usage volume, since Cloud Functions simply has no free-tier option at all -
or embedded directly in the client app, a real and hard-to-fully-mitigate secret-exposure risk
once that credential ships inside the installed APK. The decision made here was to enable Blaze
with a budget cap as a safeguard, after confirming that Blaze itself carries no subscription fee
and shares the same free quotas as the free tier - the realistic expected cost for an app at this
scale is genuinely $0 a month, with the budget cap simply bounding the worst case. This is the
standard, secure architecture for real push notifications: the credential never leaves Google's
own servers, and never ships inside the app itself.

On the Android side, each device's current push-registration token gets captured and kept in sync
on the signed-in user's own profile document whenever it's issued or refreshes, through a small
Firebase-messaging service subclass and a narrowly field-scoped repository write. On the server
side, a genuinely new piece of infrastructure was added to the project: a real Cloud Function,
written in TypeScript and deployed independently of the Android app, that triggers whenever a
report document updates, checks specifically for an actual status change (ignoring every other
kind of report update, like an AI-review flag or an incident attachment), looks up the report
owner's current token, and sends the push directly through Firebase's own messaging API. A
delivery failure - a stale or unregistered token, for instance - is logged rather than thrown, so
the platform's automatic retry behavior doesn't keep hammering a permanently dead token forever.

Getting this actually deployed surfaced two genuine one-time infrastructure hurdles worth
recording, both of which manifested as billing-flavored errors from the deployment API even though
the Firebase console's own plan page already showed the paid plan as active: a real billing
account hadn't actually been linked to the underlying cloud project yet, since enabling the plan
in Firebase's own interface doesn't automatically attach one, and the project also needed an App
Engine application explicitly created before Cloud Functions could provision the storage bucket it
depends on internally. Once both were resolved, the deploy succeeded and the function was
confirmed live and receiving real Firestore trigger events.

Worth stating plainly what's still a real, documented limitation here: only one push token is
stored per user rather than one per device, so a second device signing into the same account
silently takes over where notifications get delivered, and a confirmed delivery failure doesn't
yet clear the stale token from the user's profile automatically. Both are reasonable, named
trade-offs for a coursework-scale deployment rather than oversights.

Verified with a full compile, the unit test suite, and a debug build, all passing cleanly, and
confirmed the deployed function was genuinely live and receiving real trigger events rather than
just deploying without error.

### 3.44 A visual restyle, a rebuilt Home screen, and a real Map screen

A separate pass restyled the app to match a reference design direction, rather than adding new
functionality. The color palette moved from a muted municipal blue-teal to a brighter, more
saturated civic blue, and the app's own primary call-to-action button - used on nearly every
screen for "Report an Issue"/"Submit"/"Register"/"Promote" and more - switched from its previous
amber gradient to that same blue, so the one most-repeated component in the app no longer read as
an unrelated accent fighting the rest of the interface. A gradient that used to blend blue into
teal across hero surfaces (the auth header, Home's header, Profile's header) was flattened to a
single solid color, since the blend read as a cyan drift rather than a clean, consistent blue.
Status badges were relabeled to a plainer Pending/In Progress/Resolved vocabulary and recolored to
match a status-color convention (orange/blue/green) used consistently between report list items
and the new Map screen described below.

The Home screen was rebuilt to match a reference mockup: the stats row and recent-activity report
list were dropped from Home entirely (both remain reachable exactly as before - the stats live on
Profile's own "Your Impact" card, and the full report list lives on My Reports), replaced with a
hero illustration and two entry points into reporting. The illustration is the user's own supplied
artwork, rendered with its rectangular edges faded to transparent via a gradient alpha mask so it
reads as part of the page rather than a pasted-in picture, shown in light theme only since its own
pale background would clash against the dark theme's near-black background. Of the two buttons,
"Shake Now" is deliberately NOT a tappable button despite looking like one, on explicit request -
shaking the phone is the only way to trigger that path, and a same-looking tap target for it would
have been misleading. "New Report" opens a new form-first capture flow, described next.

### 3.45 Multi-photo capture, split from the shake flow

Shake-to-report has always captured exactly one photo (or one video) before landing on the report
form - a deliberate design, since the entire point of shaking is a fast, one-motion capture. The
new "New Report" button reaches a different flow instead: it lands directly on an empty form with
an "Add Photos" tile, camera permission requested only the first time that tile is actually tapped
rather than eagerly on screen entry, and the tile can be tapped repeatedly to attach several photos
to one report. The two flows share the same underlying `CaptureViewModel`/`CaptureUiState`, with
the first photo added (via either path) always treated as the "primary" one - the one shown in
shake's own preview/annotation step, the one AI-classified, the one written to the report's
original `localPhotoPath` field for compatibility with every report submitted before this feature
existed - and every photo after that carried in a new, purely additive `additionalLocalPhotoPaths`
field that older report documents simply don't have (read back as an empty list, never null/error).
The same device-local-only architecture applies to every photo, primary or additional - see the
existing Firebase Storage decision above, now updated to note it also covers these extra photos.
The offline queue (the local Room database used when a report can't reach Firestore immediately)
gained a matching column, using a simple pipe-delimited string rather than introducing a Room type
converter or a second table for what is, in practice, always a short list of file paths.

### 3.46 A pocket check, tried once, removed, and tried again more carefully

An earlier phase (see the shake-reliability fix above) removed a proximity-sensor-based "is this
phone in a pocket" check because it turned out to be a genuinely unreliable signal on some real
devices - a sensor that reports a stale or default "covered" reading and never updates again could
permanently block shake detection on that specific phone, which is a worse outcome than not having
a pocket check at all. On an explicit request to bring it back anyway, accepting that same known
risk, it was re-added with a specific defense against the exact failure mode that got it removed
the first time: a detected shake is only suppressed when the proximity sensor's most recent reading
is BOTH "near" AND fresh (updated within the last few seconds). A stale reading, or a device that
never delivers one at all, is treated as "unknown" rather than as an implicit permanent veto - so a
phone with a broken or absent proximity sensor behaves exactly as if this feature didn't exist,
rather than silently losing shake detection altogether.

### 3.47 A real Map screen, and a Profile/Notifications redesign

The citizen-facing map was rebuilt on the Google Maps SDK, replacing the project's own
MapLibre/Geoapify-based map screen (which is still present in the project and still used
elsewhere, just no longer for this particular screen). Every report with a real captured location
now shows as a colored pin, one distinct color per issue category (pothole/streetlight/garbage/
water leak/other) rather than by status, with a legend matching. The earlier five-layer
admin-facing "Digital Map" from the Infrastructure Intelligence phase was removed entirely in the
same pass, with no surviving multi-layer or infrastructure-asset map view of any kind - see
`INFRASTRUCTURE_INTELLIGENCE_REPORT.md`'s own updated note on this.

Profile was redesigned to match a reference mockup: its stats row became a dark "Your Impact" card
showing report count and resolved count - a third "points" stat and a "view badges" button from the
mockup were explicitly declined, since no points/badges system exists anywhere in this app to back
them (badges were tried once before and removed, per an earlier section above). A new Notifications
row opens a real settings screen with two independent toggles - report status updates, and whether
the background AI could classify a submitted photo - each backed by its own stored preference that
the existing notification-sending code now checks before posting. The AI-classification outcome
notification itself gained a genuine "success" counterpart alongside the existing "we couldn't
classify this" one, so a citizen can now tell either way whether their report's photo was
automatically categorized, delivered the same way as every other notification in this app - even
with the app fully closed. The "AI review needed" card on a report's detail screen was also changed
to never show to an admin viewing the same report, since its wording and action only ever made
sense addressed to the citizen who owns the report.

Verified with a full compile and a debug build passing cleanly after each change in this pass.
