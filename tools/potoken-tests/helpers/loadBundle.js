"use strict";
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const ASSETS = path.resolve(__dirname, "../../../app/src/main/assets/potoken");

/** Stub token format, centralized so assertions don't hardcode it. */
const TOK = (id, it) => "TOK(" + id + "|" + it + ")";

/** Loads the REAL vendored bundle in a fresh VM context; returns its BG. */
function loadRealBG() {
  const code = fs.readFileSync(path.join(ASSETS, "bgutils.bundle.js"), "utf8");
  const sandbox = {};
  sandbox.globalThis = sandbox;
  sandbox.window = sandbox;
  sandbox.self = sandbox;
  // Minimal browser-ish globals the bundle may reference at load time.
  sandbox.navigator = { userAgent: "node-test" };
  sandbox.document = {};
  vm.createContext(sandbox);
  vm.runInContext(code, sandbox);
  return sandbox.BG;
}

/** require()s the orchestrator under test, fresh each call. */
function loadClientModule() {
  const p = path.join(ASSETS, "potoken-client.js");
  delete require.cache[require.resolve(p)];
  return require(p);
}

/** Records every bridge call with its arg list. */
function makeMockBridge() {
  const calls = [];
  const rec = (name) =>
    function () {
      calls.push({ name, args: Array.prototype.slice.call(arguments) });
    };
  return {
    calls,
    onBridgeReady: rec("onBridgeReady"),
    onChallengeSolved: rec("onChallengeSolved"),
    onPoTokenResult: rec("onPoTokenResult"),
    onPoTokenUnavailable: rec("onPoTokenUnavailable"),
    onPoTokenError: rec("onPoTokenError"),
    count(name) {
      return this.calls.filter((c) => c.name === name).length;
    },
    terminalCount() {
      return this.calls.filter((c) =>
        ["onPoTokenResult", "onPoTokenUnavailable", "onPoTokenError"].includes(c.name),
      ).length;
    },
    lastResult() {
      return this.calls.filter((c) => c.name === "onPoTokenResult").pop();
    },
  };
}

/**
 * Stub BG FAITHFUL to bgutils 3.2.0 contracts:
 *  - snapshot pushes a FUNCTION at webPoSignalOutput[0] (the minter callback),
 *    matching WebPoMinter.create(t,e){ r=e[0]; await r(...) }.
 *  - WebPoMinter.create reads e[0] as a function and throws if absent.
 */
function makeStubBG(opts) {
  const o = opts || {};
  return {
    Challenge: {
      parseChallengeData(raw) {
        if (o.parseThrows) throw new Error("parse boom");
        if (o.malformedChallenge) return { program: null, globalName: null };
        return {
          program: "PROG",
          globalName: "GN",
          interpreterJavascript: {
            privateDoNotAccessOrElseSafeScriptWrappedValue: "/*noop*/",
          },
        };
      },
    },
    BotGuardClient: {
      async create() {
        if (o.botguardCreateThrows) throw new Error("bgcreate boom");
        return {
          async snapshot(args) {
            // Faithful: push the minter callback FUNCTION at index 0.
            if (Array.isArray(args.webPoSignalOutput)) {
              args.webPoSignalOutput[0] = function minterCallback(itBytes) {
                return itBytes; // stand-in; real VM returns a mint function
              };
            }
            o._capturedContentBinding = args.contentBinding;
            if (o.snapshotThrows) throw new Error("snapshot boom");
            return "BOTGUARD_RESPONSE";
          },
        };
      },
    },
    WebPoMinter: {
      async create(itObj, signal) {
        if (o.minterCreateThrows) throw new Error("minter boom");
        // Faithful: require signal[0] to be a function (mirrors the bundle).
        if (!Array.isArray(signal) || typeof signal[0] !== "function") {
          throw new Error("PMD:Undefined");
        }
        o._capturedSignal = signal;
        const it = itObj.integrityToken;
        return {
          async mintAsWebsafeString(identifier) {
            if (o.mintThrows) throw new Error("mint boom");
            return TOK(identifier, it);
          },
        };
      },
    },
  };
}

module.exports = { ASSETS, TOK, loadRealBG, loadClientModule, makeMockBridge, makeStubBG };
