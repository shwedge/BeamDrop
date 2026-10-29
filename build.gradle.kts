// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

tasks.register("packageWindowsInstaller") {
  group = "distribution"
  description = "Packages the standalone signed Windows MSI installer (Publisher: Sajjad H0ssain), distinct from the Android APK."
  dependsOn(":app:packageWindowsInstaller")
}

tasks.register("packageWindowsMsi") {
  group = "distribution"
  description = "Alias for packageWindowsInstaller to generate the signed Windows MSI installer."
  dependsOn(":app:packageWindowsMsi")
}

