import java.util.Properties
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Servidor próprio do SI-MET (proxy/cache da Open-Meteo e dos avisos do INMET).
// Ordem de leitura: -PSIMET_API_BASE_URL=... / gradle.properties  →  variável de ambiente  →  arquivo .env  →  "" (vazio).
// Vazio = o app continua chamando Open-Meteo e INMET diretamente (comportamento antigo).
fun simetSetting(name: String): String {
  providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }?.let { return it.trim() }
  providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }?.let { return it.trim() }
  val envFile = rootProject.file(".env")
  if (envFile.exists()) {
    envFile.readLines().map { it.trim() }
      .firstOrNull { it.startsWith("$name=") }
      ?.substringAfter("=")?.trim()?.trim('"')
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }
  }
  return ""
}
val simetApiBaseUrl = simetSetting("SIMET_API_BASE_URL")
val simetApiKey = simetSetting("SIMET_API_KEY")
// Chave do Gemini: SÓ para builds de debug locais. O release NUNCA embute GEMINI_API_KEY nem SIMET_API_KEY
// (qualquer valor em BuildConfig pode ser extraído do APK/AAB). TODO: mover as chamadas do Gemini para o
// servidor próprio (simet-server) e autenticar o app lá, sem segredo no cliente.
val geminiApiKeyDebugOnly = simetSetting("GEMINI_API_KEY")
fun String.asBuildConfigString() = "\"${replace("\\", "").replace("\"", "")}\""

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.ipmetradar.spwthr"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "5.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "SIMET_API_BASE_URL", "\"${simetApiBaseUrl.replace("\"", "")}\"")
  }

  signingConfigs {
    create("release") {
      // Chave de UPLOAD do Google Play. Ordem: keystore.properties (raiz, fora do git) → variáveis de ambiente
      // (KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) → sem assinatura.
      // O release NUNCA é assinado com a chave de debug (APK assinado com debug é rejeitado pela Play Console).
      val propsFile = rootProject.file("keystore.properties")
      if (propsFile.exists()) {
        val props = Properties().apply { propsFile.inputStream().use { stream -> load(stream) } }
        storeFile = file(props.getProperty("storeFile"))
        storePassword = props.getProperty("storePassword")
        keyAlias = props.getProperty("keyAlias", "upload")
        keyPassword = props.getProperty("keyPassword")
      } else if (System.getenv("KEYSTORE_PATH") != null && file(System.getenv("KEYSTORE_PATH")).exists()) {
        storeFile = file(System.getenv("KEYSTORE_PATH"))
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        logger.warn("AVISO: keystore.properties/KEYSTORE_PATH não encontrado. O build de release vai falhar até a chave de upload ser configurada.")
      }
    }
    if (file("${rootDir}/debug.keystore").exists()) {
      create("debugConfig") {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isDebuggable = false
      // R8: encolhe/ofusca o código e remove recursos não usados (regras em proguard-rules.pro).
      // O mapping.txt vai dentro do AAB (a Play Console usa para desofuscar os relatórios de falha).
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile?.exists() == true }
      // Segredos fora do release
      buildConfigField("String", "SIMET_API_KEY", "\"\"")
      buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    }
    // Sem debug.keystore na raiz, usa a chave de debug padrão do Android Gradle Plugin (~/.android/debug.keystore)
    debug {
      signingConfigs.findByName("debugConfig")?.let { signingConfig = it }
      buildConfigField("String", "SIMET_API_KEY", simetApiKey.asBuildConfigString())
      buildConfigField("String", "GEMINI_API_KEY", geminiApiKeyDebugOnly.asBuildConfigString())
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
  // lidos manualmente em simetSetting() (aceitam também -P e variável de ambiente)
  ignoreList.add("SIMET_API_BASE_URL")
  ignoreList.add("SIMET_API_KEY")
  // GEMINI_API_KEY é definida por build type acima (vazia no release); o plugin não deve gerá-la
  ignoreList.add("GEMINI_API_KEY")
}

// Release sem assinatura agora FALHA o build (antes só um aviso no log). Não lê nem imprime nenhum segredo:
// só verifica se a configuração de assinatura aponta para um keystore existente.
gradle.taskGraph.whenReady {
  val wantsRelease = allTasks.any { t ->
    t.project == project && Regex("^(bundle|assemble|package|publish)Release$|^(sign)ReleaseBundle$").matches(t.name)
  }
  if (wantsRelease) {
    val rel = android.signingConfigs.getByName("release")
    val ok = rel.storeFile?.exists() == true && !rel.storePassword.isNullOrEmpty() &&
      !rel.keyAlias.isNullOrEmpty() && !rel.keyPassword.isNullOrEmpty()
    if (!ok) throw GradleException(
      "Release sem assinatura: crie keystore.properties na raiz (storeFile, storePassword, keyAlias, keyPassword) " +
      "ou defina KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD. O build de release foi interrompido."
    )
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

configurations.all {
  resolutionStrategy {
    force("androidx.fragment:fragment:1.8.6")
    force("androidx.fragment:fragment-ktx:1.8.6")
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Firebase removido do build de release: nenhum código do app usa Firebase e não há google-services.json.
  // Reative junto com o google-services.json se for usar Firebase AI Logic / App Check (ver README).
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.fragment.ktx)
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
  implementation(libs.androidx.datastore.preferences) // chaves de API cifradas (Keystore AES/GCM)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx) // v5.1: verificação periódica de alertas (opcional)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // debugImplementation(libs.firebase.appcheck.debug) // provedor de debug do App Check só no build debug
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
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

// Esquema do Room exportado para versionar migrações (app/schemas/)
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
