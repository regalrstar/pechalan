plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
	id("org.jetbrains.kotlin.plugin.serialization")
}

android {
	namespace = "dev.triage.app"
	compileSdk = 35

	defaultConfig {
		applicationId = "dev.triage.app"
		minSdk = 29
		targetSdk = 35
		versionCode = 1
		versionName = "0.1.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		vectorDrawables.useSupportLibrary = true
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = signingConfigs.getByName("debug")
		}
		debug {
			applicationIdSuffix = ".debug"
			versionNameSuffix = "-debug"
		}
		// Real-device test build: release speed and packaging, but debuggable so
		// `run-as` and logcat keep working. No R8, to remove a variable.
		create("deviceTest") {
			initWith(getByName("release"))
			applicationIdSuffix = ".devicetest"
			versionNameSuffix = "-devicetest"
			isMinifyEnabled = false
			isDebuggable = true
			signingConfig = signingConfigs.getByName("debug")
			matchingFallbacks += listOf("release")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
	buildFeatures {
		compose = true
	}
	packaging {
		resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
	}
}

dependencies {
	val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
	implementation(composeBom)
	androidTestImplementation(composeBom)

	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.activity:activity-compose:1.9.2")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-extended")
	implementation("androidx.datastore:datastore-preferences:1.1.1")
	implementation("androidx.work:work-runtime-ktx:2.9.1")
	implementation("androidx.security:security-crypto:1.1.0-alpha06")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

	debugImplementation("androidx.compose.ui:ui-tooling")

	testImplementation("junit:junit:4.13.2")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
