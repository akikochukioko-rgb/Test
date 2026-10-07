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

## Сборка и подпись

Debug и release подписываются **одним и тем же** release-keystore (без Android debug key).

GitHub Actions — repository secrets:

| Secret | Описание |
|--------|----------|
| `KEYSTORE_BASE64` | Keystore в Base64 (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Пароль хранилища |
| `KEY_ALIAS` | Alias ключа |
| `KEY_PASSWORD` | Пароль ключа |

Локально (те же переменные окружения):

```bash
export KEYSTORE_BASE64="$(base64 -w0 release.keystore)"
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
gradle assembleDebug   # или assembleRelease
```

## Лицензия

[PolyForm Noncommercial License 1.0.0](LICENSE) — можно использовать, изменять и распространять **только в некоммерческих целях** (личное, хобби, учёба, исследования, НКО и т.п.). Коммерческое использование запрещено.
