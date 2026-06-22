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
        │   │   ├── potoken/          # PoToken 抽象・実装（§6 P0）
        │   │   └── ui/theme/         # Compose テーマ
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

- [ ] **P0-1**: `PoTokenProvider` 抽象(interface)+ stub 実装を追加し、呼び出し口を確定する。
- [ ] **P0-2**: `bgutils-js` のバンドルを `app/src/main/assets/` に取り込み、WebView ホスト
      (`WebPoTokenGenerator`)で読み込む。
- [ ] **P0-3**: BotGuard challenge 取得 → bgutils-js で `integrityToken` → 動画/visitorData
      に紐づく PoToken を JS→Kotlin ブリッジで受け取る。
- [ ] **P0-4**: 取得した PoToken をダウンロード/プレイヤーリクエスト経路へ結線する。
- [ ] **P0-5**: PoToken のキャッシュ/失効(TTL)とリトライ。

### P1 — ダウンロード基盤と最小 UI
- [ ] ストリーム抽出方式の決定(NewPipeExtractor 連携 等)と PoToken の受け渡し I/F。
- [ ] URL 入力 → 解析 → フォーマット選択 → 保存 の最小フロー(Compose)。
- [ ] ダウンロードの進捗/通知、保存先(Scoped Storage)。

### P2 — 仕上げ
- [ ] ランチャーアイコン(現状は端末既定アイコン)。
- [ ] 設定画面 / 権限フロー。
- [ ] release ビルドの R8 / リソース縮小チューニング。
- [ ] CI(assembleDebug + ktlint + test)。

> 履歴: 2026-06-22 プロジェクトを bootstrap(最小 Compose 雛形 + 本バックログ)。

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
