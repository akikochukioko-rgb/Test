# Radio Player (Android)
Нативное приложение (Kotlin + Jetpack Compose + Media3) для онлайн-радио. Список станций изначально пуст.

- minSdk 29 (Android 10), targetSdk/compileSdk 36 (Android 16)
- Package: `io.github.tytebyte_dev.rplayer`
- Добавление/редактирование станции: ссылка на поток, название, иконка (URL или файл через SAF)
- **Массовый импорт / экспорт** станций из JSON (меню ⋮)
- При старте (API 33+): только `POST_NOTIFICATIONS` для фона/уведомления
- Лог ошибок: app-specific `getExternalFilesDir()/log.txt` (без широких storage-разрешений)
- Фоновое воспроизведение с уведомлением
- HTTP и HTTPS-потоки; для HTTPS с просроченным/невалидным SSL соединение допускается
- Бегущая строка «Играет: …» с метаданными трека (ICY)

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
Локально: `gradle assembleDebug` (Gradle 8.12, JDK 17, Android SDK 36) — debug-подпись по умолчанию.

GitHub Actions (`.github/workflows/android.yml`, `android-release.yml`) подписывает APK через **Repository secrets**:

| Secret | Описание |
|--------|----------|
| `KEYSTORE_BASE64` | Keystore в Base64 (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Пароль хранилища |
| `KEY_ALIAS` | Alias ключа |
| `KEY_PASSWORD` | Пароль ключа |

## Лицензия

[PolyForm Noncommercial License 1.0.0](LICENSE) — можно использовать, изменять и распространять **только в некоммерческих целях** (личное, хобби, учёба, исследования, НКО и т.п.). Коммерческое использование запрещено.
