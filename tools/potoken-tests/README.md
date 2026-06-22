# PoToken JS tests

Node unit tests for [`app/src/main/assets/potoken/potoken-client.js`](../../app/src/main/assets/potoken/potoken-client.js)
(the PoToken solve orchestrator) plus contract tests against the vendored
`bgutils.bundle.js` that catch re-vendor drift.

These live **outside** the Gradle build (`settings.gradle.kts` includes only
`:app`), so they never affect `assembleDebug` or `ktlintCheck`. They use Node's
built-in `node:test` runner — **zero `npm install`** (Node 18+; verified on v24).

## Run

```sh
cd tools/potoken-tests
node --check ../../app/src/main/assets/potoken/potoken-client.js   # JS syntax gate
node --test potoken-client.test.js                                 # the suite
```

Or simply `npm test` from `tools/potoken-tests` (runs both of the above).

The orchestrator is dependency-injected (`BG`, `bridge`, `globalObj`,
`evalInterpreter`), so the suite exercises the full solve/mint flow with a stub
`BG` faithful to bgutils 3.2.0 contracts — no network, no real WebView.
Device-only behaviour (real BotGuard mint, the Kotlin `@JavascriptInterface`
3-arg dispatch boundary) is covered by instrumented tests, not here.
