import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.FileInputStream
import java.util.Properties

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

  val netraVersionCode = providers.gradleProperty("netraVersionCode").orElse("1").get().toInt()
  val netraVersionName = providers.gradleProperty("netraVersionName").orElse("1.0.0").get()
  val stabilityReportUrl = providers.gradleProperty("netraStabilityReportUrl").orElse(providers.environmentVariable("NETRA_STABILITY_REPORT_URL")).orElse("")
  val stabilityReportUrlEscaped = stabilityReportUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")
  val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) FileInputStream(file).use { load(it) }
  }
  val signingPath = System.getenv("KEYSTORE_PATH") ?: signingProperties.getProperty("storeFile")
  val signingStorePassword = System.getenv("STORE_PASSWORD") ?: signingProperties.getProperty("storePassword")
  val signingKeyAlias = System.getenv("KEY_ALIAS") ?: signingProperties.getProperty("keyAlias")
  val signingKeyPassword = System.getenv("KEY_PASSWORD") ?: signingProperties.getProperty("keyPassword")

  defaultConfig {
    applicationId = "com.aistudio.batterysentinel.ntra"
    minSdk = 24
    targetSdk = 36
    versionCode = netraVersionCode
    versionName = netraVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "STABILITY_REPORT_URL", "\"" + stabilityReportUrlEscaped + "\"")
  }

  signingConfigs {
    create("release") {
      if (signingPath != null) storeFile = file(signingPath)
      storePassword = signingStorePassword
      keyAlias = signingKeyAlias
      keyPassword = signingKeyPassword
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debug") }
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
// Dependabot: force patched versions of build-tool transitives (Netty, Bouncy Castle) on every
// Gradle configuration in this module. Only versions in the same major line as the patched
// release are rewritten, so unrelated artifacts (e.g. netty-tcnative) are left alone.
configurations.configureEach {
  resolutionStrategy.eachDependency {
    val v = requested.version ?: return@eachDependency
    if (requested.group == "io.netty" && requested.name.startsWith("netty-") &&
      !requested.name.startsWith("netty-tcnative") && v.startsWith("4.1.")) {
      useVersion("4.1.138.Final")
      because("Dependabot: Netty alerts (patched in 4.1.137.Final or later)")
    }
    if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") && v.startsWith("1.")) {
      useVersion("1.86")
      because("Dependabot: Bouncy Castle alerts")
    }
    // Dependabot: httpclient, commons-lang3 and guava reach the build tooling through the Android
    // Gradle Plugin. Only versions older than the patched release are rewritten.
    if (requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" &&
      v.startsWith("4.5.") && (v.removePrefix("4.5.").toIntOrNull() ?: 99) < 13) {
      useVersion("4.5.13")
      because("Dependabot: Apache HttpClient XSS (patched in 4.5.13)")
    }
    if (requested.group == "org.apache.commons" && requested.name == "commons-lang3" &&
      v.startsWith("3.") && (v.removePrefix("3.").substringBefore('.').toIntOrNull() ?: 99) < 18) {
      useVersion("3.18.0")
      because("Dependabot: Commons Lang uncontrolled recursion (patched in 3.18.0)")
    }
    if (requested.group == "com.google.guava" && requested.name == "guava" &&
      v.endsWith("-android") && (v.substringBefore('.').toIntOrNull() ?: 99) < 32) {
      useVersion("33.4.8-android")
      because("Dependabot: Guava temp directory alerts (patched in 32.0.0-android)")
    }
  }
}

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
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
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
  implementation(libs.vico.compose)
  implementation(libs.vico.compose.m3)
  implementation(libs.vico.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito)
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
