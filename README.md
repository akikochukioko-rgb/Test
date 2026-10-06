# Radio Player (Android)
Нативное приложение (Kotlin + Jetpack Compose + Media3) для онлайн-радио. Список станций изначально пуст.
- minSdk 29 (Android 10), targetSdk/compileSdk 36 (Android 16)
- Добавление/редактирование станции: ссылка на поток, название, иконка (URL или файл)
- При старте запрашиваются: доступ к фото, затем доступ ко всем файлам
- Фоновое воспроизведение с уведомлением

## Сборка
Локально: `gradle assembleDebug` (Gradle 8.12, JDK 17, Android SDK 36)
GitHub Actions: `.github/workflows/android.yml` — APK в Artifacts.
