# DroidDLP

An Android media downloader. The first hard problem on the roadmap is generating a
YouTube **PoToken** (Proof-of-Origin Token) on-device by wiring up
[`bgutils-js`](https://github.com/LuanRT/BgUtils) — see the backlog in
[`CLAUDE.md`](CLAUDE.md) §6.

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 17+ and an Android SDK with `platforms;android-36` installed
(`ANDROID_HOME` must point at the SDK). No `local.properties` is committed.

## Status

Early bootstrap — minimal single-module Compose app skeleton. See `CLAUDE.md` for
architecture, conventions, the development loop, and the secrets policy.
