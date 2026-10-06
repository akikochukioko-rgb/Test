# Radio Player (Android)
Нативное приложение (Kotlin + Jetpack Compose + Media3) для онлайн-радио. Список станций изначально пуст.

- minSdk 29 (Android 10), targetSdk/compileSdk 36 (Android 16)
- Добавление/редактирование станции: ссылка на поток, название, иконка (URL или файл)
- **Массовый импорт / экспорт** станций из JSON (меню ⋮)
- При старте запрашиваются: доступ к фото, затем доступ ко всем файлам
- Фоновое воспроизведение с уведомлением
- HTTP и HTTPS-потоки; для HTTPS с просроченным/невалидным SSL соединение допускается

## Формат импорта / экспорта

```json
{
  "radio_stations": [
    { "name": "Gangster Music", "url": "http://example.com/stream" },
    { "name": "ALT fm", "url": "https://example.com/stream" }
  ]
}
```

Дубликаты по URL при импорте пропускаются.

## Сборка
Локально: `gradle assembleDebug` (Gradle 8.12, JDK 17, Android SDK 36)
GitHub Actions: `.github/workflows/android.yml` — APK в Artifacts.
