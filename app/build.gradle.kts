plugins {
    id("com.android.application")
    id("dagger.hilt.android.plugin")
    id("com.github.triplet.play") version "3.5.0"
    kotlin("android")
    kotlin("kapt")
    id("io.objectbox")
    id("com.hiya.jacoco-android")
}

val versionMajor = 2
val versionMinor = 4
val versionPatch = 3

val googleMapsAPIKey = extra.get("google_maps_api_key")?.toString() ?: "PLACEHOLDER_API_KEY"

jacoco {
    version = "0.8.7"
    toolVersion = "0.8.7"
}

android {
    compileSdkVersion(30)

    defaultConfig {
        applicationId = "org.owntracks.android"
        minSdkVersion(21)
        targetSdkVersion(30)

        versionCode = versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments(mapOf("eventBusIndex" to "org.owntracks.android.EventBusIndex"))
            }
        }
        val locales = listOf("en", "de", "fr", "es", "ru", "ca", "pl")
        buildConfigField(
            "String[]",
            "TRANSLATION_ARRAY",
            "new String[]{\"" + locales.joinToString("\",\"") + "\"}"
        )
        resConfigs(locales)
        testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
        testInstrumentationRunnerArguments.putAll(
            mapOf(
                "clearPackageData" to "false",
                "coverageFilePath" to "/storage/emulated/0/coverage"
            )
        )
    }

    signingConfigs {
        register("release") {
            keyAlias = "upload"
            keyPassword = System.getenv("KEYSTORE_PASSPHRASE")
            storeFile = file("../owntracks.release.keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSPHRASE")
        }
    }

    buildTypes {

        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles = mutableListOf(
                getDefaultProguardFile("proguard-android.txt"),
                file("proguard-rules.pro")
            )
            resValue("string", "GOOGLE_MAPS_API_KEY", googleMapsAPIKey)
            signingConfig = signingConfigs.findByName("release")
        }

        named("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles = mutableListOf(
                getDefaultProguardFile("proguard-android.txt"),
                file("proguard-rules.pro")
            )
            resValue("string", "GOOGLE_MAPS_API_KEY", googleMapsAPIKey)
            applicationIdSuffix = ".debug"
            isTestCoverageEnabled = true
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }

    packagingOptions {
        exclude("META-INF/DEPENDENCIES.txt")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/LICENSE")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/notice.txt")
        exclude("META-INF/license.txt")
        exclude("META-INF/dependencies.txt")
        exclude("META-INF/LGPL2.1")
        exclude("META-INF/proguard/androidx-annotations.pro")
        exclude("META-INF/metadata.kotlin_module")
        exclude("META-INF/metadata.jvm.kotlin_module")
        exclude("META-INF/gradle/incremental.annotation.processors")
        jniLibs.useLegacyPackaging = false
    }

    lintOptions {
        baselineFile = file("../../lint/lint-baseline.xml")
        isCheckAllWarnings = true
        isWarningsAsErrors = false
        isAbortOnError = false
        disable(
            "TypographyFractions",
            "TypographyQuotes",
            "Typos",
            "UnsafeExperimentalUsageError",
            "UnsafeExperimentalUsageWarning"
        )
    }
    testOptions {
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
            isIncludeAndroidResources = true
        }
    }

    tasks.withType<Test> {
        testLogging {
            events("passed", "skipped", "failed")
            setExceptionFormat("full")
        }
        reports.junitXml.isEnabled = true
        reports.html.isEnabled = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    flavorDimensions("locationProvider")
    productFlavors {
        create("gms") {
            dimension = "locationProvider"
            sourceSets {
                getByName("main").java.srcDirs("src/gms/java")
                getByName("main").res.srcDirs("src/gms/res")
            }
            dependencies {
                // Play Services libraries
                implementation("com.google.android.gms:play-services-maps:17.0.1")
                implementation("com.google.android.gms:play-services-location:18.0.0")
            }
        }
        create("oss") {
            dimension = "locationProvider"
        }
    }
}

kapt {
    correctErrorTypes = true
}

tasks.withType<Test> {
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

val okHttpVersion = "4.9.1"
val jacksonVersion = "2.12.2"
val materialDialogsVersion = "0.9.6.0"
val espressoVersion = "3.4.0"
val androidxTestVersion = "1.4.0"
val kotlinCoroutinesVersion = "1.4.1"
val jaxbVersion = "3.0.1"

dependencies {
    implementation("androidx.preference:preference:1.1.1")
    implementation("com.takisoft.preferencex:preferencex:1.1.0")
    implementation("com.google.android.material:material:1.3.0")

    implementation("androidx.work:work-runtime:2.5.0")
    implementation("androidx.fragment:fragment-ktx:1.3.5")
    implementation("androidx.core:core-ktx:1.5.0")
    implementation("androidx.test.espresso:espresso-idling-resource:${espressoVersion}")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.3.1")

    // Explicit dependency on conscrypt to give up-to-date TLS support on all devices
    implementation("org.conscrypt:conscrypt-android:2.5.2")

    // Mapping
    implementation("org.osmdroid:osmdroid-android:6.1.10")

    // Utility libraries
    implementation("com.google.dagger:hilt-compiler:${rootProject.extra["dagger-version"]}")
    implementation("com.google.dagger:hilt-android:${rootProject.extra["dagger-version"]}")

    implementation("org.greenrobot:eventbus:3.2.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    implementation("com.squareup.okhttp3:okhttp:${okHttpVersion}")
    implementation("com.squareup.okhttp3:logging-interceptor:${okHttpVersion}")

    implementation("com.fasterxml.jackson.core:jackson-core:${jacksonVersion}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${jacksonVersion}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${kotlinCoroutinesVersion}")

    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("com.github.joshjdevl.libsodiumjni:libsodium-jni-aar:2.0.2")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.1.1")
    implementation("commons-codec:commons-codec:1.15")

    implementation("org.threeten:threetenbp:1.5.1")
    implementation("com.github.joschi.jackson:jackson-datatype-threetenbp:$jacksonVersion")

    // Widget libraries
    implementation("com.rengwuxian.materialedittext:library:2.1.4")
    implementation("com.mikepenz:materialdrawer:6.1.2@aar") { isTransitive = true }
    implementation("com.mikepenz:materialize:1.2.1@aar")

    implementation("com.jakewharton:process-phoenix:2.1.1")
    implementation("com.squareup.tape2:tape:2.0.0-beta1")

    // These Java EE libs are no longer included in JDKs, so we include explicitly
    kapt("javax.xml.bind:jaxb-api:2.3.1")
    kapt("com.sun.xml.bind:jaxb-core:$jaxbVersion")
    kapt("com.sun.xml.bind:jaxb-impl:$jaxbVersion")

    // Preprocessors
    kapt("org.greenrobot:eventbus-annotation-processor:3.2.0")
    kapt("com.google.dagger:hilt-compiler:${rootProject.extra["dagger-version"]}")

    kaptTest("com.google.dagger:hilt-compiler:${rootProject.extra["dagger-version"]}")

    testImplementation("androidx.test:core:${androidxTestVersion}")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("android.arch.core:core-testing:1.1.1")


    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test:core-ktx:${androidxTestVersion}")
    androidTestImplementation("com.adevinta.android:barista:4.2.0") {
        exclude("org.jetbrains.kotlin")
    }
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:${okHttpVersion}")

    androidTestImplementation("androidx.test:rules:${androidxTestVersion}")
    androidTestImplementation("androidx.test:runner:${androidxTestVersion}")
}


// Publishing
val serviceAccountCreds = file("owntracks-android-gcloud-creds.json")

play {
    if (serviceAccountCreds.exists()) {
        enabled.set(true)
        serviceAccountCredentials.set(serviceAccountCreds)
    } else {
        enabled.set(false)
    }
    track.set("internal")

    resolutionStrategy.set(com.github.triplet.gradle.androidpublisher.ResolutionStrategy.AUTO)
}

// Espresso test screenshot gathering
val reportsDirectoryPath = "$buildDir/reports/androidTests/connected/flavors/%s"
val screenshotsDeviceFolder = "/sdcard/Download/testscreenshots"

android.productFlavors.all { productFlavor ->
    tasks.register<Exec>("create${productFlavor.name.capitalize()}ScreenshotDirectory") {
        group = "reporting"
        description =
            "Creates ${productFlavor.name.capitalize()} screenshot directory on connected device"
        executable = "${android.adbExecutable}"
        args(mutableListOf("shell", "mkdir", "-p", screenshotsDeviceFolder))
    }
    tasks.register<Exec>("clear${productFlavor.name.capitalize()}Screenshots") {
        group = "reporting"
        description = "Removes ${productFlavor.name.capitalize()} screenshots from connected device"
        executable = "${android.adbExecutable}"
        args("shell", "rm", "-rf", screenshotsDeviceFolder)
    }
    tasks.register<Exec>("fetch${productFlavor.name.capitalize()}Screenshots") {
        group = "reporting"
        description =
            "Fetches ${productFlavor.name.capitalize()} espresso screenshots from the device"
        executable = "${android.adbExecutable}"
        args("pull", screenshotsDeviceFolder, reportsDirectoryPath.format(productFlavor.name))
        dependsOn("create${productFlavor.name.capitalize()}ScreenshotDirectory")
        doFirst {
            File(reportsDirectoryPath.format(productFlavor.name)).mkdirs()
        }
    }
    tasks.register("embed${productFlavor.name.capitalize()}Screenshots") {
        group = "reporting"
        description = "Embeds the ${productFlavor.name.capitalize()} screenshots in the test report"
        dependsOn("fetch${productFlavor.name.capitalize()}Screenshots")
        finalizedBy("clear${productFlavor.name.capitalize()}Screenshots")
        doFirst {
            val reportsPath = reportsDirectoryPath.format(productFlavor.name)
            val screenshotsDirectory = File(reportsPath, "testscreenshots/")
            if (!screenshotsDirectory.exists()) {
                println("Could not find screenshots. Skipping...")
                return@doFirst
            }
            screenshotsDirectory
                .listFiles()!!
                .forEach { testClassDirectory ->
                    val testClassName = testClassDirectory.name
                    testClassDirectory.listFiles()?.forEach failedFile@{
                        val testName = it.name
                        val testNameWithoutExtension = it.nameWithoutExtension
                        val testClassJunitReportFile = File(reportsPath, "${testClassName}.html")
                        if (!testClassJunitReportFile.exists()) {
                            println("Could not find JUnit report file for test class '${testClassJunitReportFile}'")
                            return@failedFile
                        }
                        val testJunitReportContent = testClassJunitReportFile.readText()

                        val failedHeaderPatternToFind =
                            "<h3 class=\"failures\">${testNameWithoutExtension}</h3>"

                        val failedPatternToReplace =
                            "$failedHeaderPatternToFind <img src=\"testscreenshots/${testClassName}/${testName}\" width =\"360\" />"
                        val successRecordPatternToFind = "<td>${testNameWithoutExtension}</td>"
                        val successPatternToReplace =
                            "<td>${testNameWithoutExtension} <a href=\"testscreenshots/${testClassName}/${testName}\">(screenshot)</a></td>"

                        testClassJunitReportFile.writeText(
                            testJunitReportContent
                                .replace(failedHeaderPatternToFind, failedPatternToReplace)
                                .replace(successRecordPatternToFind, successPatternToReplace)
                        )
                    }
                }
        }
    }
    true
}

tasks.whenTaskAdded {
    if (name == "connectedGmsDebugAndroidTest") {
        finalizedBy("embedGmsScreenshots")
    }
    if (name == "connectedOssDebugAndroidTest") {
        finalizedBy("embedOssScreenshots")
    }
}


// From  https://github.com/Triple-T/gradle-play-publisher/issues/974
// We need a way to get the *current* release code on GP, so that we can make an APK with the same
// code for the GH release. GPP has a way of getting the *next* release, so we can get that and
// subtract one

abstract class GetLatestVersionCodeMinusOne : DefaultTask() {
    @get:InputFile
    abstract val codes: RegularFileProperty

    @get:OutputFile
    abstract val outCode: RegularFileProperty

    @TaskAction
    fun read() {
        val code = codes.get().asFile.readLines().first().toInt() - 1
        outCode.get().asFile.writeText(code.toString())
    }
}

val codesTask = tasks.register<GetLatestVersionCodeMinusOne>("getLatestVersionCodeMinusOne") {
    dependsOn("processGmsReleaseVersionCodes")

    codes.set(file("build/intermediates/gpp/gmsRelease/available-version-codes.txt"))
    outCode.set(file("build/intermediates/version-code-minus-one.txt"))
}

androidComponents {
    onVariants { variant ->
        val overrideVerCode = System.getenv("VERSION_CODE_OVERRIDE")
        overrideVerCode?.toIntOrNull()?.apply {
            for (output in variant.outputs) {
                output.versionCode.set(this)
            }
        } ?:run {
            val minusOne = System.getenv("MAKE_APK_SAME_VERSION_CODE_AS_GOOGLE_PLAY")
            if (!minusOne.isNullOrEmpty()) {
                for (output in variant.outputs) {
                    output.versionCode.set(codesTask.flatMap { it.outCode }
                        .map { it.asFile.readText().toInt() })
                }
            }
        }
    }
}