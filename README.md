# ساعة ومنبّه Android

تطبيق Android أصلي مبني باستخدام Kotlin وJetpack Compose وMaterial 3.

## البناء والاختبارات

يتطلب JDK 17 وAndroid SDK 36. شغّل الاختبارات وابنِ نسخة Debug باستخدام:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

ينتج ملف التثبيت في `app/build/outputs/apk/debug/app-debug.apk`.

يعمل سير GitHub Actions عند تحديث الفرع `main`، وطلبات السحب، أو تشغيله يدويًا
من تبويب Actions. يرفع نسخة Debug باسم `Android-Clock-Debug-APK` ولا ينشر
إصدارًا إنتاجيًا.