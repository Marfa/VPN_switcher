# ChatVPN на Wi-Fi, Happ на mobile — без root.

**VPN Switcher** сам переключает VPN при смене сети. Только пара **ChatVPN + Happ**, через Shizuku.

```bash
gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

| Что | Как |
|-----|-----|
| Wi-Fi | ChatVPN |
| Mobile, сеть недоступна | Happ (виджет) |
| Mobile → Wi-Fi | ChatVPN снова |
| Управление | Shizuku shell, без root |

## Quick Start

1. Установите [Shizuku](https://shizuku.rikka.app/) и запустите (ADB или root).
2. Скачайте APK из [Releases](https://github.com/Marfa/VPN_switcher/releases).
3. **Настроить Shizuku** → **VPN-разрешение** → виджет Happ на экран.
4. Подключите ChatVPN и Happ вручную по одному разу.
5. Включите режим переключения.

## Режимы

| Переключатель | Поведение |
|---------------|-----------|
| Переключать VPN при недоступности | Wi-Fi off → 10 с → проверка mobile → Happ |
| Переключать VPN всегда | Wi-Fi off → сразу Happ |
| Отправка пушей | Только когда нужно ваше действие (не при автопереключении) |

## Лицензии

| Документ | Описание |
|----------|----------|
| [LICENSE](LICENSE) | MIT — VPN Switcher |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | MIT — [Anubis](https://github.com/sogonov/anubis) (адаптированные паттерны) |

Часть VPN-логики адаптирована по идеям Anubis (MIT, sogonov): dummy VPN revoke, `pm disable-user`, Happ widget broadcast. Подробности — в THIRD_PARTY_NOTICES.

## Автообновление

Проверка [GitHub Releases](https://github.com/Marfa/VPN_switcher/releases). Нужен прикреплённый `.apk`.

## Сборка

```bash
$env:TEMP\gradle-8.11.1\bin\gradle.bat assembleDebug
```

`minSdk 29`, `targetSdk 35`. Версия: **0.4.1** (15).

## Cursor rules

В `.cursor/rules/` — правила из [awesome-cursorrules](https://github.com/PatrickJS/awesome-cursorrules) (Android Kotlin + project defaults).

---

Код подготовлен с помощью Cursor.

**Поддержка:** [DonationAlerts](https://www.donationalerts.com/r/themarfa) · [Крипта](https://nowpayments.io/donation/themarfa)
