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
2. Соберите или скачайте APK из [Releases](https://github.com/Marfa/VPN_switcher/releases).
3. В приложении: **Настроить Shizuku** → **VPN-разрешение**.
4. Виджет Happ на рабочий стол. Подключите ChatVPN и Happ вручную по одному разу.
5. Включите режим переключения (см. ниже).

## Режимы

| Переключатель | Поведение |
|---------------|-----------|
| Переключать VPN при недоступности | Wi-Fi off → 10 с → проверка mobile → Happ |
| Переключать VPN всегда | Wi-Fi off → сразу Happ |
| Отправка пушей | Только когда нужно ваше действие |

Пуши о статусе («подключён», «ждём») не отправляются — только foreground-сервис в шторке.

## Первый запуск vs обновление

**Первый запуск:** Shizuku, VPN-разрешение, виджет Happ, ручное подключение обоих VPN, затем включить переключатель.

**Обновление поверх старой версии:** настройки сохраняются, повторно подключать VPN не нужно. Проверьте Shizuku и один цикл Wi-Fi off/on.

## Ограничения

- Только **ChatVPN** (`net.chatvpn.app.wg.android`) + **Happ** (`com.happproxy`).
- Happ включается broadcast виджета; ChatVPN «замораживается» через `pm disable-user` на время Happ.
- Sideload APK: Play Protect может показать предупреждение → **«Все равно установить»**.

## Автообновление

Приложение проверяет [GitHub Releases](https://github.com/Marfa/VPN_switcher/releases). Нужен релиз с прикреплённым `.apk`.

## Зависимости и лицензии

### [Anubis](https://github.com/sogonov/anubis) (MIT)

Часть VPN-логики **адаптирована по идеям Anubis** (лицензия MIT, copyright sogonov). Прямого копирования исходников Anubis нет — паттерны переиспользованы и переписаны под VPN Switcher:

| Паттерн Anubis | Где в VPN Switcher |
|----------------|-------------------|
| StealthVpnService — dummy VPN revoke | `DummyVpnService.kt` |
| Заморозка приложений `pm disable-user` | `AppController.kt` |
| Happ widget broadcast с `-p` (MIUI) | `VpnAppConnector.kt` |

Полный текст лицензии Anubis — в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

### Прочее

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API для shell без root
- Проверка сети — TCP probe (внутренняя логика, без упоминания сервисов в UI)

## Сборка

```bash
# Windows (локальный SDK в .android-sdk)
$env:TEMP\gradle-8.11.1\bin\gradle.bat assembleDebug
```

`minSdk 29`, `targetSdk 35`. Текущая версия: **0.4.0** (versionCode 14).

---

Код подготовлен с помощью Cursor.

**Поддержка:** [DonationAlerts](https://www.donationalerts.com/r/themarfa) · [Крипта](https://nowpayments.io/donation/themarfa)
