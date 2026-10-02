// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Build-tool (plugin classpath) transitive dependencies that Dependabot flags. They come in through AGP and
// other Gradle plugins, not from the app's runtime. Patch-level pins inside the same major line only.
buildscript {
  dependencies {
    constraints {
      classpath("io.netty:netty-common:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-buffer:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-transport:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-resolver:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-handler:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-handler-proxy:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-codec:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-codec-http:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-codec-http2:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-codec-socks:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("io.netty:netty-transport-native-unix-common:4.1.138.Final") { because("Dependabot: Netty alerts") }
      classpath("org.bouncycastle:bcprov-jdk18on:1.86") { because("Dependabot: Bouncy Castle critical/high alerts") }
      classpath("org.bouncycastle:bcpkix-jdk18on:1.86") { because("Dependabot: bcpkix alert") }
      classpath("org.jdom:jdom2:2.0.6.1") { because("Dependabot: JDOM XXE") }
      classpath("org.apache.httpcomponents:httpclient:4.5.14") { because("Dependabot: HttpClient XSS") }
      classpath("org.apache.commons:commons-lang3:3.21.0") { because("Dependabot: commons-lang3 alert") }
      classpath("com.google.guava:guava:33.7.2-jre") { because("Dependabot: Guava alerts") }
      classpath("org.bitbucket.b_c:jose4j:0.9.7") { because("Dependabot: jose4j JWE DoS") }
    }
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
