# DroidDLP R8 / ProGuard rules (release minify). CLAUDE.md §6 P2.

# --- WebView JS bridge: JS calls @JavascriptInterface methods reflectively ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- NewPipeExtractor (GPLv3) + its runtime dependencies ---
# NewPipeExtractor reflects across its service/extractor classes.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Mozilla Rhino — bundled by NewPipe to run YouTube's player JS; heavy reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

# jsoup / nanojson (NewPipe transitive parsers).
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

# Optional/desktop classes the above reference but we never ship.
-dontwarn javax.annotation.**
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.slf4j.**

# Our PoTokenResult crosses the NewPipe PoToken bridge boundary (defensive).
-keep class com.droiddlp.app.potoken.PoTokenResult { *; }
