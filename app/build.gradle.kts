import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.beamdrop.kqyxvw"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// ----------------------------------------------------------------------------
// Dedicated Windows MSI Installer Packaging Tasks (Distinct from Android APK)
// Publisher: Sajjad Hossain (sajjadhossain1508@gmail.com)
// ----------------------------------------------------------------------------
tasks.register<Zip>("packageWindowsInstaller") {
  group = "distribution"
  description = "Packages the standalone signed Windows MSI installer, Authenticode certificates, and desktop client for BeamDrop Continuity (Publisher: Sajjad Hossain), distinct from the Android APK."

  archiveBaseName.set("BeamDrop-Windows-Installer")
  archiveVersion.set("2.4.0.0")
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))

  val winDir = layout.projectDirectory.dir("../windows")
  val assetsDir = layout.projectDirectory.dir("src/main/assets")
  val distDir = layout.buildDirectory.dir("distributions")

  from(winDir) {
    exclude("**/.git*", "**/bin/**", "**/obj/**")
  }

  doFirst {
    println("============================================================================")
    println("  Packaging Standalone Windows MSI Installer (BeamDrop Continuity)")
    println("  Official Publisher: Sajjad Hossain (sajjadhossain1508@gmail.com)")
    println("  Product Version:    2.4.0.0")
    println("  Windows Package:    BeamDropInstaller.msi / Package-BeamDropMsi.bat")
    println("  Certificates:       SajjadHossain_CodeSigning.cer / .pfx")
    println("============================================================================")

    val winFolder = winDir.asFile
    val assetsFolder = assetsDir.asFile
    assetsFolder.mkdirs()

    listOf(
      "SajjadHossain_CodeSigning.cer",
      "SajjadHossain_CodeSigning.pfx",
      "SajjadH0ssain_CodeSigning.cer",
      "SajjadH0ssain_CodeSigning.pfx"
    ).forEach { name ->
      val f = winFolder.resolve(name)
      if (f.exists()) {
        f.copyTo(assetsFolder.resolve(name), overwrite = true)
      }
    }

    listOf(
      "Package-BeamDropMsi.bat",
      "Package_BeamDropmsi.bat",
      "Install-Certificate.bat",
      "Build-BeamDropMsi.ps1",
      "BeamDropWindows.cs",
      "BeamDrop.ico",
      "BeamDrop.exe.manifest",
      "Run-BeamDrop.bat"
    ).forEach { name ->
      val src = winFolder.resolve(name)
      if (src.exists()) {
        src.copyTo(assetsFolder.resolve(name), overwrite = true)
      }
    }
  }

  doLast {
    val zipFile = distDir.get().file("BeamDrop-Windows-Installer-2.4.0.0.zip").asFile
    println("[SUCCESS] Windows Standalone Installer Package generated:")
    println("  -> Archive: ${zipFile.absolutePath} (${if (zipFile.exists()) zipFile.length() else 0} bytes)")
    println("  -> Separate & distinct from Android APK (no Android compile dependencies)")
  }
}

tasks.register("packageWindowsMsi") {
  group = "distribution"
  description = "Alias for packageWindowsInstaller to generate the signed Windows MSI installer."
  dependsOn("packageWindowsInstaller")
}

