"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  TOK,
  loadRealBG,
  loadClientModule,
  makeMockBridge,
  makeStubBG,
} = require("./helpers/loadBundle");

function buildClient(bg, bridge, extra) {
  const mod = loadClientModule();
  return mod.makeClient(
    Object.assign(
      {
        BG: bg,
        bridge,
        globalObj: {},
        evalInterpreter() {
          /* never run real VM code in Node */
        },
      },
      extra || {},
    ),
  );
}

/** Asserts a promise settles (no hang) within a deadline. */
async function noHang(p, ms) {
  let timer;
  const sentinel = Symbol("timeout");
  const race = await Promise.race([
    p.then(() => "ok"),
    new Promise((r) => {
      timer = setTimeout(() => r(sentinel), ms || 2000);
    }),
  ]);
  clearTimeout(timer);
  assert.notEqual(race, sentinel, "operation must not hang");
}

test("success: onPoTokenResult called once with EXACTLY 3 args incl. defined arg2", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG(), bridge);
  await noHang(client.solveChallenge("{}", "VIDEO_ID"));
  assert.equal(bridge.count("onChallengeSolved"), 1);
  await noHang(client.mintTokens("INTEGRITY", "VIDEO_ID", "VISITOR_DATA"));
  const res = bridge.lastResult();
  assert.ok(res, "onPoTokenResult called");
  assert.equal(res.args.length, 3, "exactly 3 args");
  assert.notEqual(res.args[2], undefined, "arg2 must be defined (null, not undefined)");
  assert.equal(typeof res.args[0], "string");
  assert.equal(typeof res.args[1], "string");
});

test("binding: player<-videoId, streaming<-visitorData; snapshot contentBinding=videoId", async () => {
  const opts = {};
  const bridge = makeMockBridge();
  const stub = makeStubBG(opts);
  const client = buildClient(stub, bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await client.mintTokens("IT", "VIDEO_ID", "VISITOR_DATA");
  const [player, streaming, visitor] = bridge.lastResult().args;
  assert.equal(player, TOK("VIDEO_ID", "IT"), "player bound to videoId");
  assert.equal(streaming, TOK("VISITOR_DATA", "IT"), "streaming bound to visitorData");
  assert.equal(visitor, "VISITOR_DATA");
  assert.equal(opts._capturedContentBinding, "VIDEO_ID", "snapshot contentBinding is videoId");
});

test("null visitorData: streaming falls back to videoId; arg2 is literal null", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG(), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await client.mintTokens("IT", "VIDEO_ID", null);
  const args = bridge.lastResult().args;
  assert.equal(args.length, 3);
  assert.equal(args[1], TOK("VIDEO_ID", "IT"), "streaming falls back to videoId");
  assert.strictEqual(args[2], null, "arg2 is literal null, not undefined");
});

test("webPoSignalOutput[0] survives snapshot->mint as a FUNCTION", async () => {
  const opts = {};
  const bridge = makeMockBridge();
  const stub = makeStubBG(opts);
  const client = buildClient(stub, bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await client.mintTokens("IT", "VIDEO_ID", "VD");
  assert.ok(Array.isArray(opts._capturedSignal), "signal array passed to WebPoMinter.create");
  assert.equal(typeof opts._capturedSignal[0], "function", "signal[0] is the minter callback fn");
  assert.equal(bridge.count("onPoTokenResult"), 1);
});

test("mint before solve -> onPoTokenError, no result, no mint from empty signal", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG(), bridge);
  await noHang(client.mintTokens("IT", "VIDEO_ID", "VD"));
  assert.equal(bridge.count("onPoTokenError"), 1);
  assert.equal(bridge.count("onPoTokenResult"), 0);
});

test("BG missing -> onPoTokenUnavailable, no result", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(undefined, bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenUnavailable"), 1);
  assert.equal(bridge.count("onPoTokenResult"), 0);
});

test("WebPoMinter surface missing -> onPoTokenUnavailable on mint", async () => {
  const bridge = makeMockBridge();
  const bg = makeStubBG();
  const client = buildClient(bg, bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  delete bg.WebPoMinter; // surface disappears before mint
  await client.mintTokens("IT", "VIDEO_ID", "VD");
  assert.equal(bridge.count("onPoTokenUnavailable"), 1);
  assert.equal(bridge.count("onPoTokenResult"), 0);
});

test("malformed challenge -> onPoTokenError, no onChallengeSolved", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG({ malformedChallenge: true }), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenError"), 1);
  assert.equal(bridge.count("onChallengeSolved"), 0);
});

test("JSON.parse throws on bad challenge string -> onPoTokenError", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG(), bridge);
  await client.solveChallenge("{not json", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenError"), 1);
  assert.equal(bridge.count("onChallengeSolved"), 0);
});

test("parseChallengeData throws -> onPoTokenError", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG({ parseThrows: true }), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenError"), 1);
});

test("BotGuardClient.create throws -> onPoTokenError", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG({ botguardCreateThrows: true }), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenError"), 1);
});

test("evalInterpreter throws -> onPoTokenError, no onChallengeSolved", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG(), bridge, {
    evalInterpreter() {
      throw new Error("interp boom");
    },
  });
  await client.solveChallenge("{}", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenError"), 1);
  assert.equal(bridge.count("onChallengeSolved"), 0);
});

test("snapshot throws -> onPoTokenError", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG({ snapshotThrows: true }), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  assert.equal(bridge.count("onPoTokenError"), 1);
});

test("WebPoMinter.create throws -> onPoTokenError, no result", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG({ minterCreateThrows: true }), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await client.mintTokens("IT", "VIDEO_ID", "VD");
  assert.equal(bridge.count("onPoTokenError"), 1);
  assert.equal(bridge.count("onPoTokenResult"), 0);
});

test("mintAsWebsafeString throws -> onPoTokenError, no result", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG({ mintThrows: true }), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await client.mintTokens("IT", "VIDEO_ID", "VD");
  assert.equal(bridge.count("onPoTokenError"), 1);
  assert.equal(bridge.count("onPoTokenResult"), 0);
});

test("result call is OUTSIDE try: a throwing onPoTokenResult does NOT emit a 2nd terminal", async () => {
  // Bridge whose onPoTokenResult throws after recording. If the result call were
  // inside try, the catch would emit onPoTokenError -> double terminal.
  const bridge = makeMockBridge();
  const realResult = bridge.onPoTokenResult;
  bridge.onPoTokenResult = function () {
    realResult.apply(bridge, arguments);
    throw new Error("bridge marshaling boom");
  };
  const client = buildClient(makeStubBG(), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await assert.rejects(client.mintTokens("IT", "VIDEO_ID", "VD")); // throw propagates, uncaught
  assert.equal(bridge.count("onPoTokenResult"), 1, "result recorded once");
  assert.equal(bridge.count("onPoTokenError"), 0, "no second terminal from catch");
});

test("never double terminal on success path", async () => {
  const bridge = makeMockBridge();
  const client = buildClient(makeStubBG(), bridge);
  await client.solveChallenge("{}", "VIDEO_ID");
  await client.mintTokens("IT", "VIDEO_ID", "VD");
  assert.equal(bridge.terminalCount(), 1);
});

// ---- Real-bundle contract tests (catch re-vendor drift) ----

test("real-bundle: BG surface exposes all 5 entry functions", () => {
  const BG = loadRealBG();
  assert.equal(typeof BG.Challenge.create, "function");
  assert.equal(typeof BG.Challenge.parseChallengeData, "function");
  assert.equal(typeof BG.PoToken.generate, "function");
  assert.equal(typeof BG.BotGuardClient.create, "function");
  assert.equal(typeof BG.WebPoMinter.create, "function");
});

test("real-bundle: API arities match orchestrator assumptions (pins re-vendor drift)", () => {
  const BG = loadRealBG();
  assert.equal(BG.Challenge.parseChallengeData.length, 1, "parseChallengeData(raw)");
  assert.equal(BG.BotGuardClient.create.length, 1, "create(options)");
  assert.equal(BG.WebPoMinter.create.length, 2, "create(integrityTokenData, webPoSignalOutput)");
});

test("real-bundle: WebPoMinter.create rejects when signal[0] is not a function", async () => {
  // Pins the load-bearing contract: e[0] must be a function. If a re-vendor changes
  // this, the orchestrator's signal plumbing assumption breaks loudly here.
  const BG = loadRealBG();
  await assert.rejects(
    BG.WebPoMinter.create({ integrityToken: "x" }, []),
    /PMD:Undefined|Undefined/,
  );
});
