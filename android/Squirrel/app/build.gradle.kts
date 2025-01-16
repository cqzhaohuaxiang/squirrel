plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    // Kotlinx 序列化插件
//    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.android.squirrel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.android.squirrel"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
//            isMinifyEnabled = false
            isMinifyEnabled = true           // 启用代码压缩

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true //视图绑定
    }


}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.animation.core.android)
    implementation(libs.cronet.embedded)
    implementation(libs.volley)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)



    //MPAndroidChart 图表库
    implementation (libs.philjay.mpandroidchart)

    //实现顶部滑动选项
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)

    //3D图形显示
    implementation(libs.rajawali)
    //适用于媒体用例的Jetpack Media3支持库,包括 ExoPlayer（适用于Android的可扩展媒体播放器）
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
//    implementation("androidx.media3:media3-ui:1.5.1")  //官方提供的界面管理


    //xml 序列化
//    implementation("org.simpleframework:simple-xml:2.7.1")


}


