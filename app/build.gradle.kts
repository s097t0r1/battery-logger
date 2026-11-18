plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.batterylogger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.batterylogger"
        // minSdk 29: тип FGS connectedDevice и ServiceInfo.FOREGROUND_SERVICE_TYPE_* появились в API 29.
        minSdk = 29
        // targetSdk фиксирован для ВСЕХ устройств эксперимента: от него зависят правила
        // FGS, точных будильников и значения-заглушки BatteryManager. Записывается в паспорт.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-exp"
    }

    buildTypes {
        release {
            // Для эксперимента достаточно debug-сборки; debuggable также даёт run-as как запасной путь к файлам.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Зависимостей нет намеренно: только платформенный API, никаких библиотек, которые
// могут сами планировать работу (WorkManager и т.п.) или тянуть фоновые инициализаторы.
dependencies {}
