import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cn.y10n.gotoapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.y10n.goto"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // 编译时注入构建日期，供主界面页脚展示，便于判断是否过旧
        val buildDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$buildDateStr\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 固定签名：本地读 android/local.properties 的 goto* 字段，CI 读 GOTO_* 环境变量。
    // 密钥文件绝不进 git，CI 通过 secrets 还原。
    signingConfigs {
        create("releaseSigned") {
            val ksProps = Properties().apply {
                val local = rootProject.file("local.properties")
                if (local.exists()) load(local.inputStream())
            }
            val ksFile = (ksProps.getProperty("gotoKeystoreFile")
                ?: System.getenv("GOTO_KEYSTORE_FILE"))?.let { rootProject.file(it) }
            val ksPass = ksProps.getProperty("gotoKeystorePassword")
                ?: System.getenv("GOTO_KEYSTORE_PASSWORD")
            val keyAlias = ksProps.getProperty("gotoKeyAlias")
                ?: System.getenv("GOTO_KEY_ALIAS") ?: "goto"
            val keyPass = ksProps.getProperty("gotoKeyPassword")
                ?: System.getenv("GOTO_KEY_PASSWORD")
            if (ksFile != null && ksPass != null && keyPass != null) {
                storeFile = ksFile
                storePassword = ksPass
                this.keyAlias = keyAlias
                this.keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseSigned")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 固定 Core 版本，避免传递依赖引入超出当前 compileSdk 的版本。
    configurations.configureEach {
        resolutionStrategy {
            force("androidx.core:core:1.15.0")
            force("androidx.core:core-ktx:1.15.0")
        }
    }

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // 内嵌局域网服务器使用原生 IPv4 ServerSocket。

    testImplementation("junit:junit:4.13.2")
    // org.json 为 Android 内置；JVM 单测需要单独引入
    testImplementation("org.json:json:20240303")
}
