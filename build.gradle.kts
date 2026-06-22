// Root build script. Plugins are declared here (apply false) so every module shares
// a single version on the build classpath; each module applies the ones it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
}

// Uniform lint/format gate across every module.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
