# Shake to Report

A civic infrastructure-issue reporting app for Android. A citizen shakes their phone (or taps a
button) to report a problem - a pothole, a broken streetlight, a water leak - captures a photo
and its GPS location, and submits it. Reports are automatically classified by an AI vision model,
clustered into tracked incidents, and routed through an admin dashboard for triage, resolution,
and predictive risk scoring.

This is a coursework project (CI6330, Mobile App Development). See `TECHNICAL_REPORT.md` for the
full write-up (architecture, security, testing, limitations) and `REPORT_NOTES.md` for a
narrative log of how the app evolved feature by feature. `PLACEHOLDERS_TODO.csv` tracks every
known limitation and one-time setup step in detail.

## Requirements

- Android Studio (a recent stable release - the project uses AGP 9.x and Kotlin 2.3.10)
- An Android device or emulator running **API 28 (Android 9) or later**
- A Firebase project with Authentication, Firestore, and Cloud Functions enabled (see
  [Firebase setup](#firebase-setup) below)
- A Geoapify API key and a Google Maps SDK for Android key (see [API keys](#api-keys) below)

## Getting started

1. Clone the repository and open it in Android Studio.
2. Add your own `app/google-services.json` (see [Firebase setup](#firebase-setup)) - the one in
   this repo, if present, points at the original development project and won't work for a fresh
   clone without access to that Firebase project.
3. Create `local.properties` in the project root (Android Studio usually generates this file
   automatically on first sync, pointing at your local Android SDK) and add:
   ```properties
   GEOAPIFY_API_KEY=your-geoapify-key-here
   MAPS_API_KEY=your-google-maps-key-here
   ```
   Both keys are read via `BuildConfig` fields (see `map/GeoApiConfig.kt` and
   `AndroidManifest.xml`'s `com.google.android.geo.API_KEY` meta-data) - never hardcode either
   key directly in a source file. `local.properties` is already gitignored.
4. Sync Gradle, then build and run (`Run ▶` in Android Studio, or `./gradlew assembleDebug` from
   a terminal).

## Firebase setup

The app needs a Firebase project (Spark/free tier is enough for everything except server-triggered
push notifications, which need the Blaze plan - see `PLACEHOLDERS_TODO.csv` row 6):

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package name `com.king.mob_app_assignment`, download its
   `google-services.json`, and place it at `app/google-services.json`.
3. Enable **Authentication → Email/Password** sign-in.
4. Create a **Firestore database** (production mode), then deploy the security rules and
   composite indexes already in this repo:
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```
5. To bootstrap the very first admin account: register a normal account through the app, then
   manually edit that user's `role` field to `"ADMIN"` on their `/users/{uid}` document in the
   Firestore console. Every admin after that can be promoted in-app (Admin Dashboard → Promote to
   Admin) without touching the console again.
6. (Optional) Deploy the Cloud Functions in `functions/` if you want server-triggered push
   notifications - this requires the Blaze plan:
   ```bash
   cd functions && npm install && firebase deploy --only functions
   ```

## API keys

| Key | Used for | Where to get one |
|---|---|---|
| `GEOAPIFY_API_KEY` | MapLibre vector tile styles (used by `map/GeoApiConfig.kt`) | [myprojects.geoapify.com](https://myprojects.geoapify.com) - free tier |
| `MAPS_API_KEY` | Google Maps SDK for Android (the citizen-facing Map screen) | [Google Cloud Console](https://console.cloud.google.com) → enable "Maps SDK for Android", restrict the key to that API + this app's package name/SHA-1. Requires a billing account on the project, but stays within the free monthly credit at this app's usage scale. |

## AI classification backend

Report photos are classified by a vision-capable LLM (Gemma, served locally via
[LM Studio](https://lmstudio.ai)'s OpenAI-compatible API) with an on-device ML Kit fallback if
that server is unreachable - see `ai/VisionAiConfig.kt` and `PLACEHOLDERS_TODO.csv` row 23. The
app is fully functional without the LLM server running (ML Kit takes over automatically); it's
only needed for the more accurate primary classification path. To point the app at your own LM
Studio instance, update `VisionAiConfig.BASE_URL`.

## Architecture

- **UI**: Jetpack Compose throughout, single-Activity, Compose Navigation (`ui/nav/Destinations.kt`)
- **Pattern**: MVVM + Repository - one `*ViewModel.kt` per screen holding UI state as a
  `StateFlow`, delegating all data access to an interface-based repository layer
  (`data/repository/`) so ViewModels never talk to Firestore/Room directly
- **DI**: Hilt (`di/`) - repositories, Firebase instances, and the local database are all provided
  as singletons
- **Local persistence**: Room (`data/local/`) - used for the offline report-submission queue, not
  as the primary data store (Firestore is)
- **Background work**: WorkManager (`ai/ClassificationWorker.kt`,
  `ai/IncidentClusteringWorker.kt`, `ai/RiskRefreshWorker.kt`) for anything that shouldn't block
  the UI or a plain in-process coroutine that would die if the app process is killed
- **Backend**: Firebase Authentication + Cloud Firestore, with `firestore.rules` as the real
  server-side authorization boundary (not just client-side checks)

## Project layout

```
app/src/main/java/com/king/mob_app_assignment/
├── ai/            AI classification, chat, and background workers
├── data/
│   ├── local/     Room database (offline report queue)
│   ├── location/  GPS/reverse-geocoding
│   ├── remote/    Firestore schema constants
│   └── repository/ Interfaces + Firestore/Room-backed implementations
├── di/            Hilt modules
├── domain/model/  Plain data classes (Report, Incident, WorkOrder, User, ...)
├── map/           Geoapify/MapLibre configuration
├── sensor/        Shake detection, compass
├── ui/            One package per feature area (auth, capture, home, admin, support, ...)
└── util/          Cross-cutting helpers (notifications, permissions, sanitization, ...)
```

## Testing

Unit tests cover the app's pure business-logic classes (`app/src/test/`) - severity/priority
scoring, department suggestion, input sanitization, registration validation. Run with:

```bash
./gradlew testDebugUnitTest
```

## Building

```bash
./gradlew assembleDebug     # debug APK
./gradlew compileDebugKotlin  # quick compile check without a full build
```
