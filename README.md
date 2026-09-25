# Cents

A personal budget manager for Android, built as a learning project to explore modern Android development with Kotlin. It keeps track of your accounts and of every euro that moves between them, entirely on your phone: no account to create, no server, and the app does not even ask for the internet permission.

The user interface is in French.

## Features

### Accounts
- Three kinds of account: checking, savings and cash, each with an optional description.
- Open an account with an optional opening balance (recorded as its first transaction, which can be corrected later).
- Edit an account's name, type and description. Names must be unique among active accounts, ignoring case.
- **Archive** an account instead of deleting it: it leaves the lists and the totals, cannot receive new transactions, keeps its history, and can be unarchived if its name is still free.
- **Delete** an account, with a confirmation that says how many transactions go with it (and archiving offered as the gentler choice).
- Arrange the accounts by hand with **drag and drop**; the order is saved.
- Swipe a row right to archive or unarchive it, left to reveal edit and delete.
- A consolidated balance over the active accounts, with total income and expenses, and a button to hide the amounts.

### Transactions
- Record an **expense**, an **income** or a **transfer** between two accounts (a transfer is stored as two movements, one out and one in, so both balances stay right; the two are not edited separately).
- Give a transaction a title, a date (today, yesterday or any day), a subcategory and a description.
- Edit or delete an income or an expense, and even move it to another account. Balances are always computed from the transactions, so they follow.
- History grouped by day, with a search box and filters by period (7 days, 30 days, all time, custom range), by account and by subcategory, plus the expenses / income / net of what is shown.
- Each account has its own page with its balance and its history.

### Subcategories
- The app comes with 21 common subcategories, each with an emoji (14 for expenses such as food, housing, transport or health, and 7 for income such as salary or refunds).
- Add, rename or delete your own from the settings screen, with an emoji picker that only offers what your phone can draw. Names are unique within a kind, ignoring case and accents.
- Deleting a subcategory never loses a transaction: it just becomes uncategorised.
- A new subcategory can also be created on the spot, from the transaction form.

### Data and privacy
- Everything is stored in a local database on the device and kept from one launch to the next. The app never sends anything over the network.
- Amounts are integers in cents (never floating point), in euros.
- Light and dark themes, following the system.

## Not built yet

These are planned, and are not in the app today:
- Category budgets and alerts before overspending.
- Foreign currencies with live exchange rates (the [Frankfurter](https://frankfurter.dev) API is the intended source). Accounts are in euros for now.

## Architecture

The project follows a **hexagonal architecture** (ports and adapters), split into Gradle modules so that the compiler enforces the dependency rule:

```
:app  ───────────────►  :application  ───►  :domain   (pure Kotlin, no Android)
  │                                            ▲
  └────────►  :infrastructure  ────────────────┘
```

| Module | Role |
|---|---|
| `:domain` | The business model and rules (`Account`, `Transaction`, `Subcategory`, `Money`…) and the **ports**: the use-case interfaces it offers and the repository interfaces it needs. Pure Kotlin. |
| `:application` | The use-case services (open an account, record a transfer, reorder accounts…), written against the ports only. |
| `:infrastructure` | The adapters of the output ports: the **Room** database (entities, DAOs, mappers, repositories, unit of work) and the id generators. |
| `:app` | The Android shell: Jetpack Compose screens, view models, and the manual wiring in `AppContainer`. |

A few design choices worth knowing:
- **Invariants live in the domain.** For example an opening deposit or a transfer leg can only be created by its own factory, so an impossible transaction cannot exist.
- **Value classes** (`Money`, `AccountName`, ids…) carry validation, and `Money` is a `Long` of cents on purpose.
- **Writes are `suspend`, reads are `Flow`.** Screens observe the database and update by themselves; there is no manual refresh anywhere.
- **A `UnitOfWork` port** makes writes across several repositories all-or-nothing (opening an account and its opening deposit, deleting an account with its transactions…). The Room adapter uses a real database transaction.
- **The database enforces the rules a second time**: foreign keys stop a transaction from pointing at a missing account, and deleting a subcategory leaves its transactions uncategorised.

## Tech stack

Kotlin 2.4 · Jetpack Compose (Material 3) · Kotlin coroutines and Flow · **Room 3** with KSP · Android Gradle Plugin 9.4 · JUnit 5 and AssertJ.

Minimum Android version: 11 (API 30). Built against API 37 with JDK 17.

## Testing

The project is developed test-first, with close to a thousand unit tests, all running on the JVM (no device needed):
- Domain and application tests use small in-memory fakes of the ports instead of a mocking library.
- **Contract tests** describe what every repository and the unit of work must do, and run against the real Room adapter on a real SQLite (Room 3 can build a database without an Android `Context`).
- Screen logic that does not need Compose (form validation, filters, drag-and-drop arithmetic, view models) is extracted and tested as plain Kotlin.

## Database and schema evolution

The database schema is versioned and exported to `infrastructure/schemas/`, and those files are checked in. Version 1 is frozen: a test fails if an entity changes without a new version, or if a new version has no migration. A destructive fallback is deliberately not configured, since it would erase the user's accounts. To change the schema, raise the version in `CentsDatabase` and add a `Migration` to `CentsMigrations`.

## Getting started

Open the project in a recent Android Studio, or use the Gradle wrapper:

```bash
# Build and install the debug app on a connected device or emulator
./gradlew :app:installDebug

# Run all the unit tests
./gradlew :domain:test :application:test :infrastructure:testDebugUnitTest :app:testDebugUnitTest
```

The app starts with no account and the common subcategories in place.

## License

Apache License 2.0. See [LICENSE](LICENSE).
