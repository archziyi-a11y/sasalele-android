import java.util.Properties
import java.net.URI
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

val salesServiceUrl = providers.gradleProperty("salesServiceUrl").orElse("").get().trimEnd('/')
val salesSigningFile = providers.gradleProperty("salesSigningProperties").orElse("").get()
val salesSigning = Properties().apply { if(salesSigningFile.isNotBlank()) file(salesSigningFile).inputStream().use {load(it)} }
val salesVersionCode = providers.gradleProperty("salesVersionCode").orElse("36").get().toInt()

android {
    namespace = "com.example.sasalele"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sasalele"
        minSdk = 24
        targetSdk = 37
        versionCode = salesVersionCode
        versionName = "1.12-knowledge-trial"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if(salesSigningFile.isNotBlank()) {
        signingConfigs.create("salesRelease") {
            storeFile = file(salesSigning.getProperty("storeFile") ?: error("签名文件缺少storeFile"))
            storePassword = salesSigning.getProperty("storePassword")
            keyAlias = salesSigning.getProperty("keyAlias")
            keyPassword = salesSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if(salesSigningFile.isNotBlank()) signingConfig=signingConfigs.getByName("salesRelease")
            // Partial library optimization caused IllegalAccessError during startup.
            // Keep release signing; re-enable optimization only after runtime validation.
            optimization {
                enable = false
            }
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
    defaultConfig { buildConfigField("String", "SALES_SERVICE_URL", "\"${salesServiceUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"") }
    // Private samples remain on the PC; real business data is queried at runtime.
}

dependencies {
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

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

abstract class VerifySalesRelease : DefaultTask() {
    @get:Input abstract val serviceUrl: Property<String>
    @get:Input abstract val signingReady: Property<Boolean>
    @get:Input abstract val releaseVersion: Property<Int>
    @TaskAction fun verify() {
        val valid=runCatching {
            val uri=URI(serviceUrl.get());val host=uri.host.orEmpty()
            uri.scheme=="https" && host.contains('.') && !host.matches(Regex("[0-9.]+")) && !host.endsWith(".invalid") && !host.endsWith(".example") && host !in setOf("example.com","example.org","example.net") && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.path.orEmpty() in listOf("","/") && uri.port in listOf(-1,443)
        }.getOrDefault(false)
        require(valid) { "正式构建必须配置已落实的HTTPS服务地址：-PsalesServiceUrl=https://正式域名" }
        require(signingReady.get()) { "正式构建必须提供完整的私有签名配置，不能发布debug签名或未签名包" }
        require(releaseVersion.get()>=12) { "正式版本号不得低于本轮交付版本12" }
    }
}
val signingResourceReady=salesSigningFile.isNotBlank() && listOf("storeFile","storePassword","keyAlias","keyPassword").all { !salesSigning.getProperty(it).isNullOrBlank() } && file(salesSigning.getProperty("storeFile")).isFile
val productionPreflight = tasks.register<VerifySalesRelease>("productionPreflight") {
    serviceUrl.set(salesServiceUrl)
    signingReady.set(signingResourceReady)
    releaseVersion.set(salesVersionCode)
}
tasks.matching { it.name in setOf("assembleRelease","bundleRelease","packageRelease","signReleaseBundle") }.configureEach { dependsOn(productionPreflight) }
