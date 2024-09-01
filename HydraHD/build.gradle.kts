repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("org.jsoup:jsoup:+")
    implementation("io.karn:khttp-android:+")
    implementation("com.github.Blatzar:NiceHttp:+")
}
// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = ""
    authors = listOf("prutprit")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 3

    tvTypes = listOf(
        "Movie",
        "Anime",
        "OVA"
    )

    requiresResources = true
    language = "en"

    iconUrl = "https://hydrahd.com/fav-192.png"
}

android {
    buildFeatures {
        viewBinding = true
    }
}
