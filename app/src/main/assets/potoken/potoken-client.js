/*
 * DroidDLP PoToken orchestration (P0-3).
 *
 * Dependency-injected: unit-testable under Node (node:test) with mocked BG/bridge,
 * AND inlined into potoken.html for the WebView. Performs NO network — the two JNN
 * POSTs (Create, GenerateIT) are done natively in Kotlin and injected via
 * evaluateJavascript. This module:
 *   - solveChallenge: parse Create challenge, eval interpreter, snapshot
 *     (-> botguardResponse to Kotlin). The VM fills webPoSignalOutput[0] with the
 *     minter callback FUNCTION (verified against bgutils 3.2.0 WebPoMinter.create).
 *   - mintTokens: given the integrity token, mint player+streaming and call the
 *     bridge with EXACTLY 3 args.
 *
 * Binding (DroidDLP PoTokenResult order):
 *   player    <- videoId      (mint-time identifier; also snapshot contentBinding)
 *   streaming <- visitorData  (falls back to videoId when visitorData is null)
 */
(function (root) {
  "use strict";

  function makeClient(deps) {
    var BG = deps.BG;
    var bridge = deps.bridge;
    var globalObj = deps.globalObj;
    var evalInterpreter =
      deps.evalInterpreter ||
      function (interpreterJs) {
        // eslint-disable-next-line no-new-func
        new Function(interpreterJs)();
      };

    // Per-solve state shared between solveChallenge() and mintTokens().
    // Filled by snapshot(); element 0 becomes the minter callback FUNCTION.
    var client = {
      solveChallenge: solveChallenge,
      mintTokens: mintTokens,
      _webPoSignalOutput: null,
    };

    function errMsg(e) {
      return String(e && e.message ? e.message : e);
    }

    async function solveChallenge(challengeJson, videoId) {
      // Phase A: produce the botguardResponse, hand it to Kotlin (non-terminal).
      var botguardResponse;
      try {
        if (!BG || !BG.Challenge || typeof BG.Challenge.parseChallengeData !== "function") {
          bridge.onPoTokenUnavailable("bgutils BG surface missing");
          return;
        }
        var raw =
          typeof challengeJson === "string" ? JSON.parse(challengeJson) : challengeJson;
        var challenge = BG.Challenge.parseChallengeData(raw);
        if (!challenge || !challenge.program || !challenge.globalName) {
          bridge.onPoTokenError("malformed challenge");
          return;
        }
        var interpreter =
          challenge.interpreterJavascript &&
          challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
        if (interpreter) {
          evalInterpreter(interpreter);
        }
        var botguardClient = await BG.BotGuardClient.create({
          globalObj: globalObj,
          globalName: challenge.globalName,
          program: challenge.program,
        });
        var signalOutput = [];
        botguardResponse = await botguardClient.snapshot({
          // contentBinding binds the snapshot to the video; webPoSignalOutput[0]
          // is filled by the VM with the minter callback function.
          contentBinding: videoId,
          webPoSignalOutput: signalOutput,
        });
        client._webPoSignalOutput = signalOutput;
      } catch (e) {
        bridge.onPoTokenError(errMsg(e));
        return;
      }
      // Outside try: a throw inside onChallengeSolved marshaling must NOT re-enter
      // the catch and emit a second terminal callback.
      bridge.onChallengeSolved(String(botguardResponse));
    }

    async function mintTokens(integrityToken, videoId, visitorData) {
      var player;
      var streaming;
      try {
        if (!BG || !BG.WebPoMinter || typeof BG.WebPoMinter.create !== "function") {
          bridge.onPoTokenUnavailable("bgutils WebPoMinter missing");
          return;
        }
        var signal = client._webPoSignalOutput;
        // Hard guard: mint-before-solve must fail cleanly, not mint from empty signal.
        if (!Array.isArray(signal) || typeof signal[0] !== "function") {
          bridge.onPoTokenError("mint before solve");
          return;
        }
        var minter = await BG.WebPoMinter.create({ integrityToken: integrityToken }, signal);
        player = await minter.mintAsWebsafeString(videoId);
        var streamingId = visitorData == null ? videoId : visitorData;
        streaming = await minter.mintAsWebsafeString(streamingId);
      } catch (e) {
        bridge.onPoTokenError(errMsg(e));
        return;
      }
      // Outside try: same single-terminal discipline as above.
      bridge.onPoTokenResult(
        String(player),
        String(streaming),
        visitorData == null ? null : String(visitorData),
      );
    }

    return client;
  }

  var api = { makeClient: makeClient };

  // UMD-lite: Node require() AND classic inlined-script global.
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  } else {
    root.DroidDlpPoTokenClient = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
