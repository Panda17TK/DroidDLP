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

Single-module Compose app with two screens (bottom nav): **Download** (paste a
direct media URL or a YouTube URL → download to `Downloads/DroidDLP` with progress)
and **PoToken** (on-device BotGuard solve harness). YouTube extraction uses
NewPipeExtractor with the in-app PoToken provider wired in. Real YouTube
extraction is device/network-verified. See [`CLAUDE.md`](CLAUDE.md) for the
architecture, backlog, development loop, and secrets policy.

## License

**GPLv3** — this app embeds
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPLv3), which
makes the whole app GPLv3. See [`LICENSE`](LICENSE). The vendored bgutils-js
bundle is MIT (see [`third_party/bgutils-js/`](third_party/bgutils-js/)).
