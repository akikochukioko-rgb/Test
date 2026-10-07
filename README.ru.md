# Rplayer

[English](README.md) | **Русский**

Нативное Android-приложение для онлайн-радио на **Kotlin**, **Jetpack Compose** и **Media3**.

**Версия:** 2.2 (`versionCode` 22)

<p align="center">
  <img src="screenshots/Screenshot_20261007-214458~2.jpg" alt="Список радиостанций" width="360" />
</p>

## Возможности

- Список станций изначально пуст — добавляйте свои потоки
- Добавление / редактирование: URL потока, название, иконка (URL или файл через SAF)
- Перетаскивание для смены порядка (долгое нажатие на станцию)
- Свайп для удаления с подтверждением
- Массовый **импорт / экспорт** станций в JSON (меню ⋮)
- Фоновое воспроизведение с уведомлением
- Метаданные потока (ICY) в бегущей строке «Играет: …»
- HTTP и HTTPS; для HTTPS с просроченным/невалидным SSL соединение допускается
- Подсказки для MIUI / HyperOS (автозапуск, батарея)
- Лог ошибок в app-specific каталоге (`log.txt`)
- При старте (API 33+): запрашивается только `POST_NOTIFICATIONS`

## Требования

| | |
|--|--|
| minSdk | 29 (Android 10) |
| targetSdk / compileSdk | 36 (Android 16) |
| Package | `io.github.tytebyte_dev.rplayer` |

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

Секреты GitHub Actions:

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
