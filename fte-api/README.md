# FunTimeEvents API (FTE SDK)

Клиентская Java-библиотека для Minecraft Fabric (1.21.4). Одна строка инициализации — SDK сам отслеживает чат, игроков в мире и TAB-листе.

## Возможности

- Перехват **всех** сообщений чата (CHAT + GAME)
- Отслеживание игроков в мире (дельта: join/leave)
- Отслеживание игроков из TAB-листа (дельта: join/leave)
- Публичный API: `getEvents()`, `onEvent()`
- Автоматический жизненный цикл (старт при входе в мир, стоп при выходе)
- HTTP-отправка событий на бэкенд (с ретраями и backoff)
- Оффлайн-режим: все события доступны локально без сети

## Требования

- Java 21
- Minecraft 1.21.4
- Fabric Loader 0.16+
- Fabric API

## Подключение

### 1. Добавь зависимость

**`build.gradle`**

```groovy
repositories {
    maven { url = uri("https://your-maven-repo.example.com/releases") }
}

dependencies {
    modImplementation "com.funtimeevents:fte-api:0.1.0"
    include "com.funtimeevents:fte-api:0.1.0"
}
```

### 2. Вызови инициализацию

```java
import com.funtimeevents.sdk.api.FunTimeEventsAPI;
import com.funtimeevents.sdk.event.*;

public class MyMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Оффлайн-режим (события только локально):
        FunTimeEventsAPI.init();

        // Онлайн-режим (события шлются на бэкенд):
        FunTimeEventsAPI.init("https://api.example.com", "your-api-key");

        // Явное указание режима:
        FunTimeEventsAPI.init("https://api.example.com", "your-api-key", false);

        // Подписка на события:
        FunTimeEventsAPI.onEvent(event -> {
            if (event instanceof ChatMessageEvent msg) {
                System.out.println(msg.sender() + ": " + msg.text());
            }
        });
    }
}
```

## Использование API

### Polling (забираем события вручную)

```java
// Где угодно, например в END_CLIENT_TICK:
List<FteEvent> events = FunTimeEventsAPI.getEvents();
for (FteEvent event : events) {
    switch (event) {
        case ChatMessageEvent msg ->
            handleChat(msg.sender(), msg.text());
        case PlayerJoinEvent join ->
            handleJoin(join.name(), join.uuid(), join.source());
        case PlayerLeaveEvent leave ->
            handleLeave(leave.name(), leave.uuid(), leave.source());
    }
}
```

### Push (подписка через callback)

```java
FunTimeEventsAPI.onEvent(event -> {
    if (event instanceof PlayerJoinEvent join && join.source() == Source.TAB) {
        System.out.println("New TAB player: " + join.name());
    }
});
```

## Публикация в Maven-репозиторий

Сборка и публикация SDK в ваш репозиторий:

```bash
./gradlew :fte-api:publish -Pmaven_publish_url=https://your-repo.example.com/releases \
                           -Pmaven_publish_user=your-user \
                           -Pmaven_publish_password=your-password
```

Или через переменные окружения:

```bash
export FTE_MAVEN_URL=https://your-repo.example.com/releases
export FTE_MAVEN_USER=your-user
export FTE_MAVEN_PASSWORD=your-password
./gradlew :fte-api:publish
```

## События

| Событие | Поля |
|---------|------|
| `ChatMessageEvent` | `sender`, `text`, `timestamp` |
| `PlayerJoinEvent` | `name`, `uuid`, `source` (TAB/WORLD), `timestamp` |
| `PlayerLeaveEvent` | `name`, `uuid`, `source` (TAB/WORLD), `timestamp` |

## Лицензия

MIT
