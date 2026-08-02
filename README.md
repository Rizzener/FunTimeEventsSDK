[![Telegram](https://img.shields.io/badge/Telegram-FunTimeEventsAPI-blue?logo=telegram)](https://t.me/FunTimeEventsAPI)
[![Telegram](https://img.shields.io/badge/Telegram-FunTimeEventsBot-blue?logo=telegram)](https://t.me/FunTimeEventsBot)

# FunTimeEvents SDK

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4%20|%201.21.8%20|%201.21.11-green?logo=minecraft)
![Fabric](https://img.shields.io/badge/Fabric_Loader-0.16+-orange?logo=fabricmc)
![Java](https://img.shields.io/badge/Java-21+-blue?logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-white)

Библиотека создана для проекта [FunTimeEventsBot](https://t.me/FunTimeEventsBot).

**Клиентская библиотека для Minecraft Fabric** — автоматически отслеживает игровые события на серверах FunTime и отправляет их на бэкенд через WebSocket Relay или HTTP REST.

---

## Подключение

### Способ 1: JitPack (рекомендуется)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    modImplementation "com.github.Rizzener.FunTimeEventsSDK:fte-api:v1.0.4"
}
```

### Способ 2: Локальная сборка (mavenLocal)

```bash
git clone https://github.com/Rizzener/FunTimeEventsSDK.git
cd FunTimeEventsSDK
.\gradlew :fte-api:publishToMavenLocal -Pmod_version=1.0.4-SNAPSHOT
```

```groovy
repositories { mavenLocal() }

dependencies {
    modImplementation "net.funtimeevents:fte-api:1.0.4-SNAPSHOT"
}
```

### 3. Инициализация

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("sk-fte-...")
    .build();
```

Трекеры запускаются автоматически при входе в мир.

### Прокси (скрытие IP клиента)

[FunTimeEventsAPIProxy](https://github.com/Rizzener/FunTimeEventsAPIProxy) — прозрачный reverse proxy: форвардит запросы и ключ как есть, скрывая реальный IP клиента от бэкенда.

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("sk-fte-...")
    .baseUrl("http://proxy.example.com/v1/")
    .build();
```

Подробнее — в репозитории [FunTimeEventsAPIProxy](https://github.com/Rizzener/FunTimeEventsAPIProxy).

---

## Возможности

- Баны — парсинг hover-текста из чата
- TAB-лист — имена и донат-префиксы
- Данжи — Медный данж и Город Вардена (сундуки + экипировка)
- Адская резня — мобов в боссбаре через рефлексию
- Авто-шахта — игроки вокруг на спавне
- Координаты ивентов — парсинг чата по регуляркам
- Капча — всегда через REST
- GET-запросы к бэкенду — события, шахты, игроки, баны, данжи
- User-ивенты — аирдропы, алтари нежити, загадочные маяки (`getUserEvents()`)
- WSS (релеи, снапшоты каждую секунду) или REST (SSE-стримы)
- Любой трекер отключается в билдере

---

## Лицензия

[`LICENSE`](LICENSE) — MIT with Backend Restriction
