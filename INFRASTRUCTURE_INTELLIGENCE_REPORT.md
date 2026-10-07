# Infrastructure Intelligence Platform — Final Deliverable Report

Covers the four-system upgrade of Shake to Report from a simple "report an issue" app into an
infrastructure intelligence and management platform: Incident Intelligence, Resolution Lifecycle,
Predictive Intelligence, and the Digital Map. Written after all four phases were built, compiled,
and reviewed. See `REPORT_NOTES.md` for the full narrative/rationale log (this document is the
structured summary the brief asked for) and `PLACEHOLDERS_TODO.csv` for every acknowledged
limitation with its row-level detail.

> **2026-08-31 update — System 4 (Digital Map) has since been removed.** The citizen-facing map
> was rebuilt on the Google Maps SDK (`ui/map/MapScreen.kt`), with pins colored by report category
> instead of status, and the 5-layer admin Digital Map this section describes
> (`ui/reports/MapLayer.kt`, `ReportsMapScreen.kt`, `ReportsMapViewModel.kt`) was deleted entirely
> in the same pass — none of those three files exist in the codebase anymore. Systems 1–3
> (Incident Intelligence, Resolution Lifecycle, Predictive Intelligence) are unaffected and remain
> current. Section 1's "System 4" entry and Section 2's `MapLayer` row below describe a feature
> that no longer exists; kept as historical record of what was built in that phase rather than
> rewritten, per `PLACEHOLDERS_TODO.csv`'s Maps screen row for the up-to-date current state.

## 1. Files changed or added, by system

### Cross-cutting (touched by more than one system)
- `domain/model/Report.kt` — added `incidentId: String` (pointer field only; report's own
  status/priority/department fields are untouched).
- `data/repository/ReportRepository.kt` / `ReportRepositoryImpl.kt` — added
  `setReportIncidentId()`.
- `data/remote/FirestoreSchema.kt` — added `INCIDENTS_COLLECTION`, `WORK_ORDERS_COLLECTION`,
  `AUDIT_LOG_COLLECTION`, `RISK_SCORES_COLLECTION` and their field-name objects (`IncidentFields`,
  `IncidentMemberFields`, `IncidentHistoryEntryFields`, `PriorityFactorFields`, `WorkOrderFields`,
  `WorkOrderHistoryEntryFields`, `AuditLogFields`, `RiskScoreFields`, `RiskFactorFields`,
  `RiskHistoryPointFields`), plus `ReportFields.INCIDENT_ID`.
- `di/RepositoryModule.kt` — bindings for `IncidentRepository`, `AuditLogRepository`,
  `WorkOrderRepository`, `RiskScoreRepository`.
- `firestore.rules` — new `/incidents`, `/workOrders`, `/auditLog`, `/riskScores` match blocks;
  a new `incidentId`-only field-scoped branch added to `/reports`' existing update rule.
- `MobAppAssignment.kt` — enqueues the new periodic `RiskRefreshWorker`.
- `MainActivity.kt` — new routes wired for Incidents/Risk Areas list+detail; new
  `onIncidentClick`/`onViewAllIncidents`/`onViewRiskAreas`/`onRiskAreaClick` callbacks threaded
  into `AdminDashboardScreen` and `ReportsTabScreen`.
- `ui/nav/Destinations.kt` — `ADMIN_INCIDENTS`, `ADMIN_RISK_AREAS`, `INCIDENT_DETAIL_ROUTE`,
  `RISK_AREA_DETAIL_ROUTE`.
- `ui/admin/AdminDashboardScreen.kt` / `AdminDashboardViewModel.kt` — "Active incidents" and
  "High-risk areas" drill-down sections added to the existing command-center dashboard.

### System 1 — Infrastructure Incident Intelligence
- `domain/model/Incident.kt` (new)
- `data/repository/IncidentRepository.kt` / `IncidentRepositoryImpl.kt` (new)
- `ai/IncidentClusteringWorker.kt` (new)
- `ui/admin/AdminIncidentsScreen.kt` / `AdminIncidentsViewModel.kt` (new)
- `ui/admin/IncidentDetailScreen.kt` / `IncidentDetailViewModel.kt` (new)
- `ui/reports/ReportDetailScreen.kt` / `ReportDetailViewModel.kt` — citizen-facing
  `IncidentSummaryCard`, jargon-free.
- `ui/capture/CaptureViewModel.kt` — enqueues `IncidentClusteringWorker` after every successful
  report submission (photo and video).

### System 2 — Complete Resolution Lifecycle
- `domain/model/WorkOrder.kt`, `domain/model/AuditLogEntry.kt`, `domain/model/PriorityScorer.kt`
  (new)
- `data/repository/WorkOrderRepository.kt` / `WorkOrderRepositoryImpl.kt` (new)
- `data/repository/AuditLogRepository.kt` / `AuditLogRepositoryImpl.kt` (new)
- `ui/admin/WorkOrderComponents.kt`, `ui/admin/AuditHistoryComponents.kt` (new)
- `ui/admin/IncidentDetailScreen.kt` / `IncidentDetailViewModel.kt` — extended with validation
  actions, transparent priority-score card, work order section, audit history section.
- `Incident.kt` — extended with `validationState`, `priorityScore`, `priorityFactors`.

### System 3 — Predictive Infrastructure Intelligence
- `domain/model/RiskScore.kt`, `domain/model/RiskScorer.kt` (new)
- `data/repository/RiskScoreRepository.kt` / `RiskScoreRepositoryImpl.kt` (new)
- `ai/RiskRefreshWorker.kt` (new)
- `ui/admin/AdminRiskAreasScreen.kt` / `AdminRiskAreasViewModel.kt` (new)
- `ui/admin/RiskAreaDetailScreen.kt` / `RiskAreaDetailViewModel.kt` (new)

### System 4 — Infrastructure Digital Map
- `ui/reports/MapLayer.kt` (new)
- `ui/reports/ReportsMapViewModel.kt` (rewritten)
- `ui/reports/ReportsMapScreen.kt` (rewritten)
- `ui/reports/ReportsTabScreen.kt` (modified — threads incident/risk click-through)

## 2. New domain models/entities

| Model | Purpose |
|---|---|
| `Incident`, `IncidentMember`, `IncidentHistoryEntry`, `IncidentStatus`, `IncidentValidationState`, `IncidentMembershipSource` | Aggregate layer above individual reports |
| `PriorityFactor`, `PriorityScorer`, `DepartmentSuggester` | Transparent priority scoring + department suggestion |
| `WorkOrder`, `WorkOrderHistoryEntry`, `WorkOrderStatus` | Dispatch/repair tracking |
| `AuditLogEntry`, `AuditEntityType` | Cross-entity change history |
| `RiskScore`, `RiskFactor`, `RiskHistoryPoint`, `RiskLevel`, `RiskScorer` | Predictive area/category risk |
| `MapLayer` | Digital map's 5 selectable layers |

`Report` gained one new field: `incidentId` (pointer only, non-breaking).

## 3. New Firestore collections and fields

| Collection | Key fields | Notes |
|---|---|---|
| `incidents` | title, category, status, severity, department, centerLatitude/Longitude, address, reportCount, totalUpvotes, members[], history[], validationState, validationNote, priorityScore, priorityFactors[] | Client-writable by any signed-in user for the auto-clustering fields only (members/reportCount/totalUpvotes/updatedAt/history); everything else admin-only |
| `workOrders` | incidentId, department, assignedTeam, priority, status, createdAt, dueDate, inspectionNotes, repairNotes, materialsUsed, resolutionPhotoPaths[], completedAt, history[] | Admin-only end to end |
| `auditLog` | entityType, entityId, action, performedByUid, timestamp, previousValue, newValue, note | Create: any signed-in user acting as themselves; read: admin-only |
| `riskScores` | areaKey, areaLabel, category, centerLatitude/Longitude, score, level, factors[], insufficientData, contributingReportIds[], contributingIncidentCount, unresolvedCount, averageResolutionHours, isRecurring, recommendation, history[] | Any signed-in user (periodic worker has no server identity) |

`reports` gained one field: `incidentId` (empty string when unclustered).

## 4. Security rule changes

- New match blocks for `incidents`, `workOrders`, `auditLog`, `riskScores` (see `firestore.rules`
  for full conditions, summarized in the table above).
- `reports.update` rule gained one additional field-scoped OR-branch: a signed-in user may update
  `incidentId` alone (the clustering worker's write path), never any other report field through
  that branch.
- No changes to existing `users`/`usernames`/`supportMessages` rules from this work.

## 5. New Firestore indexes required

**None.** Every new query is either a single-field `orderBy` (no composite index needed) or a
`whereEqualTo` + client-side sort, deliberately chosen throughout all four phases specifically to
avoid provisioning new composite indexes (a real, previously-painful issue earlier in this
project). Confirmed by inspecting every `.orderBy(`/`.whereEqualTo(` call added in this work.

## 6. New screens

- Admin: Incidents list, Incident detail (status/severity/department/validation/priority-score/
  work-orders/audit-history), Risk Areas list, Risk Area detail (factor breakdown/trend/
  contributing reports).
- Citizen-visible additions: an `IncidentSummaryCard` on the existing Report Detail screen
  (jargon-free); a 5-layer switcher, filter row, and search field added to the existing Reports
  Map screen.
- Dashboard: "Active incidents" and "High-risk areas" drill-down sections added to the existing
  Admin Dashboard (no new bottom-nav tabs — reached via drill-down links, matching the app's
  existing tab-budget constraint).

## 7. New services/repositories

- `IncidentRepository`/`Impl`, `WorkOrderRepository`/`Impl`, `AuditLogRepository`/`Impl`,
  `RiskScoreRepository`/`Impl`.
- `IncidentClusteringWorker` (per-report, enqueued after submission), `RiskRefreshWorker`
  (periodic, 6-hour interval, network-constrained) — both `@HiltWorker CoroutineWorker`s mirroring
  the pre-existing `ClassificationWorker` pattern exactly.
- `PriorityScorer`, `DepartmentSuggester`, `RiskScorer` — pure, unit-testable scoring functions
  with no Firestore dependency.

## 8. Tests performed

All verification in this project is compile/build verification plus manual code review (no
device/emulator was used in this session, and no existing test suite exists in the project to
extend — see PLACEHOLDERS_TODO.csv for that as a pre-existing, unrelated gap).

- **Compile verification**: `:app:compileDebugKotlin` run after every phase (1 through 4) and
  after the post-review bugfix — BUILD SUCCESSFUL each time, zero new errors, only pre-existing
  deprecation warnings (MapLibre annotation API, `Icons.Filled.*` non-AutoMirrored variants —
  present before this work began).
- **Full build verification**: `:app:assembleDebug` run after Phase 1, Phase 2, Phase 3, Phase 4,
  and the post-review bugfix — BUILD SUCCESSFUL every time (dex/resource merge/manifest merge all
  succeed, confirming no resource or manifest-level breakage from the new screens/routes).
- **Independent code review**: a dedicated review pass across all four systems' new/modified
  files (domain models, repositories, workers, ViewModels, Composables, DI bindings,
  `firestore.rules`) specifically checking for null-pointer risks, Firestore field-mapping
  mismatches between write-side and read-side, security-rule gaps, and Flow-combining bugs.
  - **One genuine bug found and fixed**: `IncidentRepositoryImpl.evaluateAndClusterReport()`
    returned on the *first* nearby incident crossing either confidence threshold rather than the
    *best-scoring* one, which could attach a report to the wrong incident (or strand it as a
    low-confidence candidate) when multiple existing incidents were nearby. Fixed to score every
    candidate first, then pick the maximum. Recompiled/reassembled clean after the fix (see
    REPORT_NOTES.md's "Post-implementation review fix" entry).
  - No other genuine defects found: Firestore field names verified to match between every
    repository's write-side `toFirestoreMap()` and read-side `toXOrNull()`; every `firestore.rules`
    match block's writable-field whitelist checked against the actual write shapes issued by the
    corresponding repository; every division/average operation checked for a zero-denominator
    guard; no unclosed `callbackFlow` listeners.
- **Manual reasoning checks performed against the brief's testing checklist** (not executed on a
  device, reasoned through against the actual code paths):
  - *Report → incident association*: `IncidentClusteringWorker` is enqueued for every successful
    submission (photo and video) in `CaptureViewModel`; `evaluateAndClusterReport()` is a no-op if
    `report.incidentId` is already set, guarding against double-processing on WorkManager retry.
  - *Duplicate prevention*: existing-incident search always runs before any new-incident proposal
    path is considered; a new incident is only proposed once `MIN_REPORTS_TO_PROPOSE_NEW_INCIDENT`
    (3) unclustered nearby reports exist.
  - *Manual merge/unmerge*: `IncidentDetailViewModel.mergeReport()`/`unmergeReport()` call
    dedicated `IncidentRepository` methods distinct from the automatic path, each recording an
    audit-log entry.
  - *Incident lifecycle*: all 8 `IncidentStatus` values (`DETECTED`→...→`CLOSED`, plus
    `DISMISSED`) are selectable via `IncidentDetailScreen`'s status chip row; a status change
    triggers `recomputePriorityScore()` and an audit-log entry.
  - *Work order creation/assignment/status transitions*: `WorkOrderRepositoryImpl.createWorkOrder()`
    and all mutating methods (`updateStatus`, `updateAssignment`, `setDueDate`,
    `addInspectionNotes`, `addRepairNotes`, `completeWithEvidence`) each call
    `AuditLogRepository.record()`; all 10 `WorkOrderStatus` values are represented in the model.
  - *Audit trail*: verified present on every incident/work-order mutating path (see file list
    above); `AuditLogRepositoryImpl.record()` deliberately never throws back to its caller, so an
    audit-write failure cannot roll back the real action it's describing (traced through the code,
    not device-tested).
  - *Risk score calculation*: `RiskScorer.score()` only runs when
    `bucketReports.size >= MIN_REPORTS_FOR_SCORE` (3); below that, `RiskRefreshWorker` writes an
    explicit `insufficientData = true` placeholder instead, and `RiskAreaDetailScreen` branches on
    that flag rather than ever showing a fabricated numeric score.
  - *Map layers*: each of the 5 `MapLayer` values maps to a distinct, independently-filterable
    marker set in `ReportsMapScreen`; the Infrastructure layer explicitly renders an
    "no data source connected" empty state rather than any markers.
  - *Offline behavior*: no new online-only assumption was introduced — Firestore's own offline
    persistence/snapshot-listener cache (already relied upon by the pre-existing report list
    screens) is the only mechanism any new screen depends on; `RiskRefreshWorker` has an explicit
    `NetworkType.CONNECTED` constraint so it simply doesn't run rather than failing when offline.
  - *Firestore rules*: every new collection's rule reviewed against the actual write shape issued
    by its repository (see "Independent code review" above); confirmed field-scoped `update`
    rules on `incidents` (auto-cluster path) and `reports` (incidentId-only branch) can't be used
    to smuggle in a write to a field outside their intended whitelist.
  - *Anonymous reports*: incident clustering/risk scoring never read or expose `userId` — an
    incident stores only `reportId`s in `members[]`, and a risk score stores only
    `contributingReportIds[]`; an anonymous report's privacy is unaffected by either aggregate.
  - *Admin vs non-admin access*: every new admin screen route is wrapped in the existing
    `AdminRouteGuard`; citizen-facing incident/risk marker taps on the map are gated by `isAdmin`
    at the `MainActivity` call site so navigation to admin-only detail screens never fires for a
    non-admin, even though the underlying map data (aggregate incident/risk info) is itself
    readable by any signed-in user per `firestore.rules`.
  - *Large datasets / performance*: no new screen loads more than the app's existing
    `getAllReports()`/`getAllIncidents()` live listeners already load; the map only ever renders
    the currently-selected layer's markers, not all 5 layers' entities simultaneously.
  - *Failure/retry*: both new WorkManager workers (`IncidentClusteringWorker`, `RiskRefreshWorker`)
    follow the pre-existing `ClassificationWorker` retry pattern — up to 3 attempts, then
    `Result.failure()` rather than retrying forever.

**Not performed / explicitly out of scope for this session**: on-device or emulator manual QA,
automated unit/instrumented tests (none exist elsewhere in the project to extend), load testing
against a real large Firestore dataset, and Firestore Rules simulator/emulator-suite testing of
the new rule blocks.

## 9. Limitations and future ML/GIS integration areas

All of the following are also recorded per-row in `PLACEHOLDERS_TODO.csv`:

- **No server-side compute**: incident clustering and risk refresh both run on-device via
  WorkManager, not a Cloud Function — a deliberate tradeoff for a coursework project with no
  server budget. `firestore.rules` necessarily trusts client-computed aggregates as a consequence
  (documented, scoped to only the specific fields each path needs to write).
- **No real machine learning**: clustering confidence, priority scoring, and risk scoring are all
  transparent, rule-based weighted formulas — explicitly not presented as ML, per the brief's own
  instruction. Each is architected (`PriorityScorer.score()`, `RiskScorer.score()`,
  `IncidentRepositoryImpl.confidenceForAttachingTo()`) so a real trained model could later replace
  the scoring function's body without any caller changing.
- **No real GIS/infrastructure asset data**: the Digital Map's Infrastructure layer is an honest
  placeholder (no fabricated pins) pending a real road/streetlight/drainage/water asset dataset.
  Risk scoring's "area" is a coarse ~550m lat/lng grid cell, not a real ward/zone/district
  boundary, for the same reason — no such gazetteer data exists in this project.
- **No visual similarity in clustering**: the brief lists it as an optional factor; this project's
  LAN-only vision classifier has no embedding/similarity capability to support it, so its weight
  is folded into the category+distance factors instead.
- **Low-confidence surfacing gap**: exists for the attach-to-existing-incident path but not the
  propose-new-incident path — an acknowledged Phase 1 gap, not addressed in this session.
- **Location search**: client-side substring match against already-loaded text, not a real
  geocoding API (none is configured in this project).
- **No automated tests**: this session added no unit/instrumented tests, matching the rest of the
  pre-existing project, which also has none — verification here was compile/build success plus
  manual code review, not executed test assertions.
