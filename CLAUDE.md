# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build                 # compile, detekt, ktlint, Android lint and all unit tests (what CI runs)
./gradlew assembleDebug
./gradlew :app:installDebug
./gradlew ktlintFormat          # auto-fix formatting
./gradlew :core:domain:test     # one module
./gradlew :core:data:testDebugUnitTest --tests "com.greenfodor.ppremotece.core.data.SomeTest"  # one class
```

CI (`.github/workflows/build.yml`) runs `./gradlew build` on pushes and PRs to `master`.

## Comments

Comments describe **what** the code does, not **why** a decision was made. Do not record
rationale, trade-offs, alternatives considered, or debugging history in comments, and never
paraphrase a chat conversation into a comment. If the code is self-explanatory, add no comment.
KDoc (`/** */`) documenting a type/member's behavior is fine.

## Architecture

Multi-module Android app (minSdk 29, target/compileSdk 37, Java 25), Koin for DI, Navigation 3,
MVI presentation (State / Action / Event, `XxxRoot` / `XxxScreen`, `UiText`).

### Modules

| Module               | Plugin                               | Contents                                                                         |
|----------------------|--------------------------------------|----------------------------------------------------------------------------------|
| `:app`               | `ppremotece.android.application`     | `MainActivity`, `PPRemoteApplication` (Koin start), the `NavDisplay`             |
| `:core:domain`       | `ppremotece.jvm.library`             | Pure Kotlin: models, arrangement expansion, status-stream frame parser           |
| `:core:data`         | `ppremotece.android.library`         | Ktor client, status stream, `ProPresenterSession`, NSD, DataStore, DTOs, Koin    |
| `:core:designsystem` | `ppremotece.android.library.compose` | Dark-only `PPRemoteTheme`, `GroupColors`, icons, `UiText`, `ObserveAsEvents`     |
| `:feature:connect`   | `ppremotece.android.feature`         | Discovery / manual host, connect screen, `ConnectRoute`                          |
| `:feature:playlist`  | `ppremotece.android.feature`         | Playlist tree + slide grid                                                       |
| `:feature:remote`    | `ppremotece.android.feature`         | Remote tab: live and next boxes, item steps, Prev/Next                           |
| `:feature:clear`     | `ppremotece.android.feature`         | Clear FAB, rail item and sheet: layer clears and clear groups                    |
| `:feature:settings`  | `ppremotece.android.feature`         | More list and Settings screen                                                    |
| `:feature:timers`    | `ppremotece.android.feature`         | Timers tab: one card per timer with Start/Stop and Reset                         |
| `:feature:macros`    | `ppremotece.android.feature`         | Macros tab: one section per collection, a tile per macro that triggers it        |
| `:feature:looks`     | `ppremotece.android.feature`         | Looks tab: a radio card per look; a tap makes it the live look                   |
| `:feature:props`     | `ppremotece.android.feature`         | Props tab: one section per collection, a thumbnail tile per prop; a tap toggles  |

**Dependency rules:** `domain` depends on nothing; `data` → `domain`; `designsystem` → `domain`;
features → `domain` + `designsystem`, never `data` and never each other; `:app` → everything and
wires Koin modules and navigation. Features own their `@Serializable` `NavKey`s; cross-feature
navigation goes through callbacks wired in `:app`.

`:core:domain` compiles against the JDK 17 API (`-Xjdk-release=17`, javac `--release 17`), and
animal-sniffer (`animalsnifferMain`, part of `check`) fails the build on any JDK API missing from
Android API 29 (gummy-bears signatures). Android modules compile against `android.jar`. Convention plugins live in `build-logic/convention`. `ppremotece.lint` (detekt + ktlint) is
applied by every other plugin; JUnit Jupiter (`useJUnitPlatform()`) is configured in every module.
Versions come only from `gradle/libs.versions.toml`.

## ProPresenter network rule (P-10)

The app talks to ProPresenter's HTTP `/v1` API and sends only:
- `GET` reads (including `GET /v1/clear/group/{id}/icon`, the clear group's icon, `GET /v1/macro/{uuid}/icon`,
  the macro's icon, `GET /v1/libraries`,
  `GET /v1/library/{uuid}` and `GET /v1/playlist/active`, read with the slide index on each slide change),
  the item-cue trigger `GET /v1/playlist/{pl}/{item}/{cue}/trigger`, the item
  trigger `GET /v1/playlist/{pl}/{item}/trigger`, `GET /v1/trigger/next`,
  `GET /v1/trigger/previous` (from the playlist grid, the Remote, and the library grid while its
  presentation is live outside a playlist), the clear calls and the timer operations
  `GET /v1/timer/{uuid}/start`, `GET /v1/timer/{uuid}/stop` and `GET /v1/timer/{uuid}/reset`, and the
  macro trigger `GET /v1/macro/{uuid}/trigger`, the look trigger `GET /v1/look/{uuid}/trigger`, the prop
  trigger and clear `GET /v1/prop/{uuid}/trigger` and `GET /v1/prop/{uuid}/clear`, and the prop thumbnail
  `GET /v1/prop/{uuid}/thumbnail?quality=200|400|600` (kept in memory only);
- the presentation-cue trigger `GET /v1/presentation/{uuid}/{cue}/trigger`, only from library mode and
  when a presentation is live outside a playlist (or remembered from one after a clear);
- one `POST /v1/status/updates` stream whose URL array is exactly `["status/slide", "timer/system_time",
  "playlist/active", "status/layers", "timers", "timers/current", "macro_collections", "looks",
  "look/current", "prop_collections"]` (one unknown URL
  ends the whole stream); after an error frame `URL: x. Error: …` the reopened stream sends the same list
  without `x` for the rest of that connection.

Every other method (any `DELETE`, any `PUT`/`POST` that edits stored content, such as a timer edit) is out of
bounds, as are `timer/{id}/increment/…`, `timers/{op}` and any `/focus` route.
Inside a playlist, trigger by `(playlist uuid, item index, cue index)`, or by
`(playlist uuid, item index)` for the item trigger, only;
`/v1/presentation/active/{n}/trigger`, `/v1/presentation/{uuid}/{n}/trigger` and
`/v1/presentation/active/next|previous/trigger` switch to the presentation's own arrangement.

## Test fixtures

Fixtures under `core/data/src/test/resources/fixtures/` are generated by
`tools/sanitize_fixtures.py` from captures kept outside the repo; commit only its output. The
script replaces lyrics, titles, playlist/arrangement names, file paths and the host IP, and ends
with a leak check that must pass before fixtures are committed. The repo is public: real
presentation or playlist names, lyrics, personal paths and real IPs stay out of code, tests and
commit messages.

## Static analysis

detekt (plugin `dev.detekt`, config `config/detekt/detekt.yml`, with compose-rules) and ktlint
(disabled rules set in `LintConventionPlugin`) fail the build on any finding.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
