import com.lagradost.cloudstream3.gradle.CloudstreamExtension 
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies 
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.6.0")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream.gradle:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        namespace = "com.prutprit"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    listOf(
                        "-Xno-call-assertions",
                        "-Xno-param-assertions",
                        "-Xno-receiver-assertions"
                    )
                )
            }
        }
    }

    dependencies {
        val apkTasks = listOf("deployWithAdb", "build", "make")
        val useApk = gradle.startParameter.taskNames.any { taskName ->
            apkTasks.any { apkTask ->
                taskName.contains(apkTask, ignoreCase = true)
            }
        }

        val implementation by configurations
        val apk by configurations

        // If the task is specifically to compile the app then use the stubs, otherwise us the library.
        if (useApk) {
            // Stubs for all Cloudstream classes
            apk("com.lagradost:cloudstream3:pre-release")
        } else {
            // For running locally
            implementation("com.github.Blatzar:CloudstreamApi:0.1.6")
        }

        // these dependencies can include any of those which are added by the app,
        // but you dont need to include any of them if you dont need them
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle
        implementation(kotlin("stdlib")) // adds standard kotlin features
        implementation("com.github.Blatzar:NiceHttp:0.4.4") // http library
        implementation("org.jsoup:jsoup:1.16.2") // html parser
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")


    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
