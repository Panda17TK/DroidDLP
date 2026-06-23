# DroidDLP — プロジェクトガイド (CLAUDE.md)

> このファイルがプロジェクトの正典(source of truth)です。仕様・方針・バックログは
> ここを基準にし、変更したら同じ PR/コミットで本ファイルも更新します。

---

## §1 概要

DroidDLP は Android 製のメディアダウンローダです。最初の難関は、YouTube の
**PoToken (Proof-of-Origin Token)** を端末上で生成することで、これは
[`bgutils-js`](https://github.com/LuanRT/BgUtils)(BotGuard チャレンジ応答 +
PoToken 生成ライブラリ)を WebView 上で実行して実現します。これがバックログ
(§6)の **P0** です。

- ターゲット: Android 11+ (minSdk 30)
- 形態: 単一 `:app` モジュールから開始。PoToken/ダウンロード基盤が育ったら
  モジュール分割(`:core-potoken` 等)へリファクタ。
- ライセンス: **GPLv3**(NewPipeExtractor を組み込むため。repo ルートの `LICENSE` 参照)。

---

## §2 技術スタック

| 項目 | バージョン / 採用 |
|---|---|
| 言語 | Kotlin 2.4.0 |
| ビルド | Gradle 8.13 (wrapper) / AGP 8.13.2 |
| JDK | 17+(実行は Temurin 21 を確認済み。`jvmTarget = 17`) |
| UI | Jetpack Compose (BOM 2026.05.01) + Material 3 |
| 非同期 | kotlinx-coroutines 1.11.0 |
| SDK | compileSdk 36 / targetSdk 36 / minSdk 30 |
| Lint/Format | ktlint (Gradle plugin 12.1.1) |
| YouTube 抽出 | NewPipeExtractor v0.26.3(**GPLv3**, JitPack) |
| バージョン管理 | Gradle version catalog (`gradle/libs.versions.toml`) |

PoToken 配線で追加予定: WebView(framework)+ `bgutils-js` バンドル(assets)。
ダウンロード基盤の抽出方式(NewPipeExtractor 等)は §6 P1 で確定する。

---

## §3 プロジェクト構成

```
DroidDLP/
├── CLAUDE.md                 # このファイル（正典）
├── README.md
├── settings.gradle.kts       # include(":app")
├── build.gradle.kts          # ルート: plugin を apply false で集約
├── gradle.properties
├── gradle/libs.versions.toml # version catalog
├── gradlew / gradlew.bat / gradle/wrapper/...
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/droiddlp/app/
        │   │   ├── MainActivity.kt
        │   │   ├── potoken/          # PoToken 抽象・WebView solver・キャッシュ・factory（§6 P0）
        │   │   ├── download/         # DownloadEngine・StreamExtractor(NewPipe/Direct)・MediaStore（§6 P1）
        │   │   └── ui/               # PoTokenScreen / DownloadScreen / ViewModels + theme/
        │   └── res/values/           # strings, themes
        └── test/java/com/droiddlp/app/   # JVM ユニットテスト
```

- パッケージ: `com.droiddlp.app` / applicationId: `com.droiddlp`

---

## §4 ビルド & 実行

```bash
./gradlew assembleDebug      # デバッグ APK（開発ループの検証コマンド）
./gradlew test               # JVM ユニットテスト
./gradlew ktlintCheck        # フォーマット検査
./gradlew installDebug       # 実機/エミュレータへインストール
```

PoToken の JS solve は Gradle 外で Node テスト(`tools/potoken-tests`、`node:test`、npm install 不要):

```bash
cd tools/potoken-tests
node --check ../../app/src/main/assets/potoken/potoken-client.js   # JS 構文ゲート
node --test potoken-client.test.js                                 # solve スイート
```

前提: `ANDROID_HOME` が SDK を指し、`platforms;android-36` が導入済みであること。
`local.properties` はコミットしない(§9)。

---

## §5 アーキテクチャ方針 / コーディング規約

- **KISS / YAGNI**: 必要になるまでモジュール分割や抽象を増やさない。単一モジュールで始める。
- **境界に抽象を置く**: 外部依存(WebView/JS, ネットワーク, ストレージ)は interface
  の背後に隠し、ビジネスロジックは実装詳細に依存しない(Repository / Provider パターン)。
- **不変・明示的エラー処理**: データクラスは copy で更新。失敗は握り潰さず型/例外で表現。
- **ファイルは小さく**: 1 ファイル 200〜400 行目安、最大 800。関数は < 50 行目安。
- **命名**: クラス/型は PascalCase、関数/変数は camelCase、定数は UPPER_SNAKE_CASE。
- **ktlint** に従う(`kotlin.code.style=official`)。

---

## §6 バックログ

優先度: **P0 = 最優先(今のスプリント)** / P1 / P2。完了したらチェックを入れる。

### P0 — bgutils-js の実配線(PoToken 生成)
YouTube の bot 判定を回避する PoToken を端末内で生成する。`bgutils-js` は DOM 風の
JS 実行環境を要するため、WebView 内で BotGuard VM + bgutils-js を動かし、JS ブリッジ
経由で `{ poToken, visitorData }` を取り出す方式を採る(§7 参照)。

- [x] **P0-1**: `PoTokenProvider` 抽象(interface)+ stub 実装を追加し、呼び出し口を確定する。
- [~] **P0-2 (c)**: WebView ホスト `WebPoTokenProvider` の骨組みを実装(JS↔Kotlin ブリッジ契約・
      スレッド/ライフサイクル・`withTimeoutOrNull`、非表示 WebView でローカル `assets` を読込、緑・stub で `null`)。
- [x] **P0-2 (a)**: `bgutils-js` 実バンドル(v3.2.0, MIT)を esbuild で IIFE 化し
      `app/src/main/assets/potoken/bgutils.bundle.js` へ vendor。ホスト HTML で `<script>` 有効化、
      `BG_BUNDLE_PRESENT` を `window.BG` 検出で導出(Kotlin 変更なし)。出所は `third_party/bgutils-js/`。
- [x] **P0-3**: 実 solve を実装。Kotlin がネイティブで JNN を2回 POST(Create/GenerateIT)し、
      WebView 内 bgutils-js が BotGuard を解いて `integrityToken`→PoToken を生成、**3引数**で
      `onPoTokenResult` を返す。seam を `getPoToken(videoId, visitorData?)` に拡張。NewPipe 方式
      (アセットをインライン化し `loadDataWithBaseURL("https://www.youtube.com", …)`、`blockNetworkLoads=true`
      + fail-closed `shouldInterceptRequest`、ネイティブ HTTP は境界バウンド/no-redirect)。
      テスト: Node 20本(スタブ + 実バンドル契約)+ Kotlin 6本、ktlint/assembleDebug 緑。
- [~] **P0-3 (E2E)**: instrumented テスト `WebPoTokenProviderInstrumentedTest` を実装(コンパイル済み)。
      実機/エミュレータで `./gradlew connectedDebugAndroidTest` 実行(実トークン生成・YouTube 受理・Kotlin
      ブリッジ3引数 dispatch・仮想 youtube.com オリジン妥当性を検証)。※本環境では実行不可(device-only)。
      最初の実機失敗候補は JNN の 403(UA/key/header で緩和済み)。アプリ起動時の `PoTokenScreen` でも手動確認可。
- [~] **P0-4**: プロバイダ選択 `PoTokenProviders.default()`(WebView solver + TTL キャッシュ)+
      `PoTokenResult` を untrusted 扱いする消費レイヤ `PoTokenViewModel` + 実 solve を叩く検証 UI
      `PoTokenScreen`(MainActivity に結線)を実装。ダウンロード/プレイヤーリクエストへの実結線は
      P1(抽出基盤)に依存するため未了。
- [x] **P0-5**: `CachingPoTokenProvider`(decorator)で PoToken を `(videoId, visitorData)` 単位に
      TTL キャッシュ + 失敗時の bounded リトライ。注入クロックで JVM テスト可能(5本緑)。

### P1 — ダウンロード基盤と最小 UI
- [x] **P1-1**: ダウンロード基盤コア — `DownloadEngine`(進捗 Flow + キャンセル/失敗で部分破棄)、
      `ByteSource`/`DownloadSink` 抽象、`HttpByteSource`、`MediaStoreDownloadSink`(Scoped Storage)、
      `StreamExtractor` seam(抽出 backend は差し替え可能)。エンジンは依存注入で JVM 4本テスト緑。
- [x] **P1-2**: 抽出 backend = `NewPipeStreamExtractor`(**NewPipeExtractor v0.26.3 / GPLv3**)を seam に統合。
      `NewPipeDownloader`(HttpURLConnection)、`CompositeStreamExtractor`(NewPipe→`DirectUrlStreamExtractor`
      フォールバック)、P0 の PoToken を `YoutubeStreamExtractor.setPoTokenProvider` でブリッジ。
      web PoToken の `visitorData` は `YoutubeVisitorDataProvider`(YouTube homepage の ytcfg から1回 GET で取得)で
      生成し、ブリッジ内で取得→キャッシュ→`getPoToken(videoId, visitorData)` に渡して streaming を束縛。
      ※実ストリーム解決・実トークン受理はネット/実機での検証が必要(device-verified)。
      **アプリ全体が GPLv3**(repo ルートに `LICENSE` 追加)。
- [x] **P1-3**: URL 入力 → ダウンロード(進捗バー/サイズ/%)→ 保存(Downloads/DroidDLP)の最小 UI。
      ボトムナビで Download / PoToken を切替。複数画質のフォーマット選択 UI は YouTube backend と同時に拡張。
- [x] **P1-4**: フォアグラウンド `DownloadService`(`foregroundServiceType=dataSync`)+ 進捗通知 + Cancel アクション。
      `DownloadProgressBus` で UI と同期、`DownloadViewModel` はサービス起動+購読に変更。POST_NOTIFICATIONS をリクエスト。

### P2 — 仕上げ
- [x] ランチャーアイコン(アダプティブ + monochrome、ダウンロードグリフ、`mipmap-anydpi-v26`)。
- [ ] 設定画面 / 権限フロー。
- [ ] release ビルドの R8 / リソース縮小チューニング。
- [ ] CI(assembleDebug + ktlint + test)。

> 履歴:
> - 2026-06-22 プロジェクトを bootstrap(最小 Compose 雛形 + 本バックログ)。
> - 2026-06-22 P0-1: `PoTokenProvider` 抽象 + `StubPoTokenProvider` + ユニットテストを追加。
> - 2026-06-23 P0-2(c): `WebPoTokenProvider` の WebView 骨組み + JS ホスト(`assets/potoken/potoken.html`)+ ブリッジ契約を追加(緑・`null`、実バンドル投入は P0-2(a))。
> - 2026-06-23 P0-2(a): `bgutils-js@3.2.0`(MIT)を IIFE 化して `assets/potoken/bgutils.bundle.js` に vendor、ホスト HTML 有効化(`window.BG` 公開、解の実装は P0-3)。
> - 2026-06-23 P0-3: 実 BotGuard solve を実装(Kotlin ネイティブ JNN ×2 + WebView 内 bgutils-js、seam を `getPoToken(videoId, visitorData?)` へ拡張、Node 20本/Kotlin 6本テスト緑)。実機 E2E は P0-3(E2E) へ分離。
> - 2026-06-23 P0-5: `CachingPoTokenProvider`(TTL キャッシュ + bounded リトライ、注入クロック、JVM 5本)を追加。
> - 2026-06-23 P0-4(部分): `PoTokenProviders` factory + `PoTokenViewModel`(untrusted 扱い)+ 検証 UI `PoTokenScreen` を MainActivity に結線。ダウンロード結線は P1 依存で未了。
> - 2026-06-23 P0-3(E2E)+P2: instrumented E2E テスト(device-only、`connectedDebugAndroidTest`)とアダプティブ・ランチャーアイコンを追加。
> - 2026-06-23 P1-1: ダウンロード基盤コア(`DownloadEngine` + `ByteSource`/`DownloadSink` + `HttpByteSource` + `MediaStoreDownloadSink` + `StreamExtractor` seam、JVM 4本)を追加。
> - 2026-06-23 P1-2/3: `DirectUrlStreamExtractor`(JVM 5本)+ ダウンロード UI(進捗)+ ボトムナビ(Download/PoToken)。直リンクを Scoped Storage へ保存する end-to-end。
> - 2026-06-23 P1-2(YouTube backend): NewPipeExtractor v0.26.3(**GPLv3**)を seam に統合(`NewPipeDownloader` + `CompositeStreamExtractor` + PoToken ブリッジ)。アプリ全体が GPLv3 化、`LICENSE` 追加。
> - 2026-06-23 P1-2(visitorData)+P1-4: `YoutubeVisitorDataProvider` で web PoToken 用 visitorData を生成しブリッジに結線。フォアグラウンド `DownloadService` + 進捗通知 + `DownloadProgressBus`(UI 同期)+ POST_NOTIFICATIONS。

---

## §7 PoToken / bgutils-js 設計メモ

- **なぜ WebView か**: `bgutils-js` が依存する BotGuard VM はブラウザ相当の
  実行環境(DOM/グローバル)を前提にするため、純粋な JS エンジン単体では動かしにくい。
  Android では非表示 WebView に bgutils-js バンドル + VM をロードして実行するのが現実解。
- **想定フロー**:
  1. challenge を取得(BotGuard の `bg_challenge`)。
  2. WebView 内で bgutils-js が VM を解いて `integrityToken` を生成。
  3. `visitorData`(または videoId)を content binding として PoToken を生成。
  4. Kotlin 側へ `{ poToken, visitorData }` を返却し、ストリーム要求に付与。
- **抽象 (P0-1)**: `interface PoTokenProvider`(suspend で `PoTokenResult` を返す)。
  WebView 実装が出来るまでは `StubPoTokenProvider` が `null`/未対応を返す。
- **バンドル**: `bgutils-js@3.2.0`(MIT)を esbuild で IIFE 化し `assets/potoken/bgutils.bundle.js` に vendor。
  実行時 `window.BG`(`BotGuardClient`/`Challenge`/`PoToken`/`WebPoMinter`)を公開。BotGuard VM 自体は同梱されず
  実行時に取得する(ゆえにバンドルは ~7KB と小さい)。出所・再現手順・ライセンスは `third_party/bgutils-js/`。
- **P0-3 実装**: ネットワークは Kotlin がネイティブ実行(JNN Create/GenerateIT を2回 POST、no-redirect・
  境界バウンド読み・JNN 用ヘッダ/公開 web キー)。WebView は無ネット(`blockNetworkLoads` + fail-closed
  `shouldInterceptRequest` + CSP `connect-src 'none'`)。solve オーケストレーションは
  `assets/potoken/potoken-client.js` に依存注入で分離し、Node でユニットテスト可能。
  注意: BotGuard VM 実行は設計上の RCE。無ネット/アクション無ブリッジ/毎回破棄/トークン非ログで封じ込め、
  下流(P0-4)は `PoTokenResult` を untrusted として扱うこと。
- 参考: LuanRT/BgUtils, youtubei.js, NewPipe の PoToken provider 実装。

---

## §8 開発ループ(必ずこの順で)

1 セッションにつき **1 機能(意味のある最小単位)** を回す:

1. **実装** — バックログ(§6)の 1 項目だけ着手する。範囲を広げない。
2. **ビルド確認** — `./gradlew assembleDebug` がグリーンであることを確認。
   必要に応じ `./gradlew test` / `ktlintCheck`。
3. **コミット** — 意味単位で 1 コミット。Conventional Commits 形式:
   `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:` …
4. **プッシュ** — `git push`(初回は `-u origin main`)。
5. 関連する仕様変更があれば §6 等の本ファイルも同じコミットで更新する。

ビルドが赤いまま commit しない。秘密情報を含めない(§9)。

---

## §9 秘密情報の扱い(コミット禁止)

以下は**絶対にコミットしない**。`.gitignore` で機械的にも防ぐ。

- API キー / トークン / パスワード / OAuth クライアントシークレット。
- 署名鍵(`*.keystore` / `*.jks`、`debug.keystore` を除く)と署名情報。
- `local.properties`(SDK パス等のローカル設定)。
- `.env` 系、個人を特定する情報、実機固有のトークン。

運用ルール:
- 秘密は環境変数 / ローカル非追跡ファイル / Android Keystore で扱う。コードに直書きしない。
- ユーザの認証情報・PoToken・Cookie 等の機微データは端末内に安全に保持し、ログに出さない。
- 既に露出した秘密は無効化・ローテーションする。
