# Vendored: bgutils-js

`app/src/main/assets/potoken/bgutils.bundle.js` is a vendored, bundled build of
[`bgutils-js`](https://github.com/LuanRT/BgUtils), used by the in-app WebView to
generate YouTube PoTokens (see `CLAUDE.md` §6 P0-2 and §7).

- **Package:** `bgutils-js`
- **Version:** 3.2.0
- **License:** MIT — see [`LICENSE`](LICENSE) (Copyright (c) 2024 LuanRT)
- **Exposes:** a `window.BG` global (`BotGuardClient`, `Challenge`, `PoToken`, `WebPoMinter`)

## Reproducing the bundle

bgutils-js ships only ESM/CJS, so we bundle it into a classic-script IIFE that
assigns `window.BG`:

```sh
mkdir bgutils-build && cd bgutils-build
npm install bgutils-js@3.2.0 esbuild

# entry.js:
#   import { BG } from "bgutils-js";
#   globalThis.BG = BG;

npx esbuild entry.js --bundle --format=iife --platform=browser \
  --target=es2020 --minify --legal-comments=eof \
  --outfile=bgutils.bundle.js
```

Then copy `bgutils.bundle.js` to `app/src/main/assets/potoken/`.

> Only the built artifact and this LICENSE are committed; `node_modules` is not.
> The bundle is a LOCAL asset and must never be replaced by a remote URL.
