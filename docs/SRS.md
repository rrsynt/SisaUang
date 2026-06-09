# SRS — DompetKu

**Software Requirements Specification** · Technical spec. Written in English for
precision. Agent must follow this when implementing. User-facing strings remain
in Bahasa Indonesia (see AGENTS.md).

## 1. Scope

Native Android personal-finance app, single user, Indonesian market, IDR.
Offline-first with optional sync to the user's own Google Sheets/Drive.

## 2. Technical Stack

- Language: Kotlin
- UI: Jetpack Compose
- Local DB: Room (SQLite)
- Async: Coroutines + Flow
- Background: WorkManager (scheduled/background Google sync)
- DI: Hilt (or constructor injection if Hilt is overkill)
- Min SDK: confirm against the project's `build.gradle` — do NOT guess.
- Verify all dependency versions from the version catalog / official sources.

## 3. Functional Requirements

### FR-1 Transactions
- Create/read/update/delete income & expense entries.
- Fields: amount (IDR, integer minor units or rounded Rupiah), type (income/expense),
  category, account, date, note, source (manual / notification / csv / ocr / text).
- Fast-entry path must reach "saved" in minimal taps.

### FR-2 Accounts & Wallets
- Manage accounts: cash, bank, e-wallet. Each has a name, type, and current balance.

### FR-3 Assets & Net Worth
- Asset types: savings, deposit, stocks, mutual funds, bonds, gold, crypto.
- Manual valuation updates with timestamp.
- Net worth = total assets − total liabilities.

### FR-4 Notification Parsing
- `NotificationListenerService`.
- Prominent disclosure + consent screen REQUIRED before activation.
- Global on/off toggle and per-source-app toggles; revocable anytime.
- Rule engine: per-app patterns (regex) extracting amount, debit/credit, merchant,
  account. Ship built-in templates for common Indonesian banks/e-wallets and allow
  user-defined patterns with a "Test Pattern" feature against sample text.
- Output → draft queue with deduplication. Never auto-commit.

### FR-5 CSV/Excel Import
- Accept official exports from brokers/banks. Column mapping UI + preview before
  commit. Output → drafts. Must not duplicate already-imported rows.

### FR-6 OCR
- Extract amount and date from receipt images → editable drafts.

### FR-7 Text/Voice Parser
- Parse free text (e.g. "kopi 25rb gopay") into a draft transaction.

### FR-8 Reports
- Monthly cash flow, by-category breakdown, trends, asset composition.

### FR-9 Sync & Backup
- **Two-way sync** with a Google Sheet identified by a user-provided spreadsheet
  ID, using the user's own Google account (OAuth). Push local changes and pull
  remote changes so multiple devices sharing the same spreadsheet ID show the
  same data after sync.
- Backup to the user's Google Drive; backups must be fully restorable.
- Conflict handling: **last-write-wins + soft-delete** (no hard deletes that
  break convergence). Local data is the per-device source of truth; the sheet is
  the shared reconciliation layer. Provide a visible sync log; never silently
  discard local data.

## 4. Non-Functional Requirements

- **Offline-first**: every feature except sync works with no network.
- **Performance**: app cold start and fast-entry flow must feel instant.
- **Privacy**: all financial data processed on-device; nothing sent to any
  third-party server. Only destination beyond the device is the user's own
  Google account.
- **Security**: app lock via PIN/biometric is REQUIRED to open the app. Local
  data must be encrypted at rest. No secrets in the repo.
- **Localization**: all user-facing text in Bahasa Indonesia; IDR formatting
  `Rp 1.500.000`; Indonesian date locale.

## 5. Data Model (initial)

- `Account(id, name, type, balance, currency=IDR)`
- `Transaction(id, accountId, amount, type, categoryId, date, note, source)`
- `Category(id, name, type)`
- `Asset(id, type, name, value, valuedAt)`
- `Liability(id, name, amount)`
- `ParseRule(id, sourceApp, pattern, fieldMapping, enabled)`
- `DraftEntry(id, rawText, parsed fields, source, status)`

Refine as needed but keep names consistent across layers.

## 6. Compliance Constraints (HARD LIMITS)

These mirror AGENTS.md §4 and are binding:

- No `READ_SMS` / `RECEIVE_SMS` — not in manifest, not as placeholders.
- No `QUERY_ALL_PACKAGES`.
- No `AccessibilityService` for scraping financial data from other apps.
- No transmission of financial/notification data to third-party servers.
- `NotificationListenerService` only with prominent disclosure + consent +
  revocable toggles + honest Data Safety declaration.
- All automated capture produces user-confirmed drafts only.

Directly reading another app's internal data (Ajaib, Bibit, Seabank balances) is
impossible under Android sandboxing and must not be attempted. Permitted capture
methods: manual, notification parsing (consented), CSV import, OCR, text/voice.

## 7. Permissions Summary

Declare ONLY what is needed and justifiable:
- `BIND_NOTIFICATION_LISTENER_SERVICE` (notification parsing, consented)
- Internet (for Google sync only)
- Camera / storage read (OCR, CSV import) as needed, scoped appropriately

Explicitly excluded: SMS permissions, `QUERY_ALL_PACKAGES`, call log, contacts.

## 8. Acceptance Criteria (high level)

- Manual transaction can be saved in under 5 seconds.
- App fully functional offline.
- Notification parsing produces drafts, never auto-commits, and never activates
  without consent.
- CSV import maps columns correctly and does not duplicate data.
- OCR fills total & date as an editable draft.
- Two-way Google Sheets sync is consistent: a second device with the same
  spreadsheet ID shows the same data after sync.
- Drive backup can be restored intact.
- App is locked with PIN/biometric and stored data is encrypted.
- No prohibited permissions present anywhere in the build.
