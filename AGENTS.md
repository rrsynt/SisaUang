# AGENTS.md — DompetKu

> Read this file before every task. These rules are mandatory and override
> any conflicting instruction in a prompt. If a request conflicts with the
> compliance rules below, STOP and ask the user before proceeding.

## 1. Project Overview

DompetKu is a **native Android** personal-finance app for a **single user** in
Indonesia. It tracks cash flow, assets, and investments in one place. The core
goal is friction-free input (target: under 5 seconds per entry), automated
capture of transactions, and an at-a-glance view of financial health (net
worth, surplus/deficit, asset composition).

- Platform: Android native (Kotlin)
- Currency: IDR (Rupiah)
- Storage: offline-first, multi-device capable, cloud backup, optional sync to
  Google Sheets via a user-provided spreadsheet ID

## 2. Project Specs (read before coding)

You MUST read and follow these before writing or changing code:

- Business context: `docs/BRD.md`
- Product requirements & features: `docs/PRD.md`
- Technical spec & compliance: `docs/SRS.md`

Do NOT implement features that are not described in `docs/PRD.md`. If you think a
feature is missing, propose it to the user first; do not add it on your own.

## 3. Language Rules

- Code identifiers (class/function/variable names), commit messages, branch
  names, and technical docs (this file, SRS): **English**.
- **Code comments: Bahasa Indonesia** on important/non-obvious parts — this is a
  deliberate project decision. Write clean, modular code with Indonesian comments
  explaining the "why" of tricky logic.
- ALL user-facing content — UI labels, button text, screen titles, dialogs,
  error messages, notifications, onboarding copy: **Bahasa Indonesia**.
- Currency display format: `Rp 1.500.000` (dot as thousands separator, no decimals
  for whole Rupiah; use comma for cents only when needed).
- Dates shown to the user: Indonesian locale (e.g. `31 Mei 2026`).

## 4. Play Store Compliance — NON-NEGOTIABLE

These constraints exist because violating them can get the app rejected or
removed from Google Play. Treat them as hard limits.

- **NEVER** declare or request `READ_SMS` / `RECEIVE_SMS`. Do not add them to the
  manifest, not even as commented-out placeholders. Google Play prohibits
  finance/budgeting apps from using SMS permissions unless the app is the default
  SMS handler, which is not realistic here.
- **NEVER** use `QUERY_ALL_PACKAGES` to detect installed apps.
- **NEVER** misuse `AccessibilityService` to scrape financial data from other
  apps (e.g. reading balances from Ajaib, Bibit, Seabank). Reading another app's
  internal data is impossible under Android sandboxing and is a policy violation
  if attempted via Accessibility.
- **NEVER** send notification content or any financial data to third-party
  servers. Data stays on-device or syncs only to the user's own Google
  Sheets/Drive.
- `NotificationListenerService` is allowed ONLY with a prominent disclosure &
  consent screen shown BEFORE activation, explaining what is read, why, and that
  processing is on-device. Provide a global on/off toggle and per-source-app
  toggles, and allow revoking access at any time. Declare this honestly in the
  Play Data Safety form.

Permitted input/automation methods ONLY: manual entry, notification parsing
(with explicit consent), CSV/Excel import, OCR (receipts), and text/voice
parsing. All automated capture produces **draft entries that the user confirms**
— never auto-commit financial data silently.

## 5. Anti-Hallucination Rules

- Do NOT guess library, SDK, or API versions. Check the existing
  `build.gradle` / version catalog first. Verify from official sources rather
  than relying on training data.
- Before using a class, function, or API, confirm it exists in the codebase or
  in official Android/Kotlin documentation. Do not invent method names.
- If you are unsure about a requirement or an implementation detail, say so and
  ask — do NOT fabricate an answer or silently pick an approach.
- Ground every implementation in the specs in `docs/`. If the spec is silent on
  something, ask rather than assume.
- When researching the codebase, search the actual files first; do not assume
  structure from memory.

## 6. Architecture & Conventions

- Language: Kotlin. UI: Jetpack Compose preferred unless the spec says otherwise.
- Architecture: clean separation — UI / domain / data layers. Repository pattern
  for data access.
- Local storage: Room (SQLite), **encrypted at rest**. Offline-first; the app
  must be fully usable with no network.
- Background work: WorkManager for scheduled/background sync.
- App lock: PIN/biometric required to open the app.
- Sync: **two-way** with Google Sheets/Drive using the user's account and a
  user-provided spreadsheet ID. Strategy: **last-write-wins + soft-delete** so
  multiple devices sharing the same spreadsheet ID converge. Local data is the
  per-device source of truth; the sheet is the shared reconciliation layer.
- Drive backup must be fully restorable.
- No hardcoded secrets, API keys, or credentials anywhere in the repo.
- One feature per module/file where reasonable; keep files focused and small.
- Write tests for parsing logic (notification rules, CSV import, text parser)
  since these are the most error-prone and correctness-critical parts.

## 7. Design

- Clean, readable, accessible. Prioritize information density and fast entry over
  flashiness.
- Respect system light/dark theme.
- Indonesian copy must sound natural, not machine-translated.
