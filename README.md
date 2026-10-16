# FunTimeEventsSDK

Клиентский Fabric SDK для FunTimeEvents API: игровые трекеры, REST, WebSocket и SSE.

**Ветка release candidate, версия `2.0.0-rc.2`. Публичный выпуск ещё не выполнен.**
Таблица ниже задаёт целевые артефакты. Фактическая повторная проверка интегрированной
версии, hosted CI и оставшиеся ограничения фиксируются в [RELEASE_HARDENING_RESULTS.md](RELEASE_HARDENING_RESULTS.md).
Принятые результаты этапа 5 остаются в [историческом отчёте](poc/multiversion/PHASE5_RESULTS.md).

## Цели

| Minecraft | Java | Fabric API | Maven artifact |
|---|---:|---|---|
| 1.21.4 | 21 | `0.113.0+1.21.4` | `mc1.21.4` |
| 1.21.5 | 21 | `0.128.2+1.21.5` | `mc1.21.5` |
| 1.21.6 | 21 | `0.128.2+1.21.6` | `mc1.21.6` |
| 1.21.7 | 21 | `0.129.0+1.21.7` | `mc1.21.7` |
| 1.21.8 | 21 | `0.136.1+1.21.8` | `mc1.21.8` |
| 1.21.9 | 21 | `0.134.1+1.21.9` | `mc1.21.9` |
| 1.21.10 | 21 | `0.138.4+1.21.10` | `mc1.21.10` |
| 1.21.11 | 21 | `0.141.6+1.21.11` | `mc1.21.11` |
| 26.1 | 25 | `0.145.1+26.1` | `mc26.1` |
| 26.1.1 | 25 | `0.145.4+26.1.1` | `mc26.1.1` |
| 26.1.2 | 25 | `0.155.3+26.1.2` | `mc26.1.2` |
| 26.2 | 25 | `0.161.0+26.2` | `mc26.2` |
| 26.3 | 25 | `0.163.0+26.3` | `mc26.3` |


Fabric Loader: **0.19.5** или совместимая более новая версия; проверки фиксируют
именно 0.19.5. Fabric API устанавливает consumer-мод. Один SDK JAR предназначен
строго для одной строки таблицы. 26.x требует Java 25; 1.21.x — Java 21.

## Подключение

Текущий канал — JitPack. Координаты для будущего выпуска:

```groovy
repositories { maven { url = 'https://jitpack.io' } }
dependencies {
    modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.4:2.0.0-rc.2"
    include "com.github.Rizzener.FunTimeEventsSDK:mc1.21.4:2.0.0-rc.2"
}
```

На 26.x с новым Loom используйте `implementation` вместо `modImplementation`.
`include` физически вкладывает SDK в конечный JAR. Одна только compile/runtime
dependency не доставляет SDK пользователю. Не используйте агрегат всех targets.
RC сейчас доступен только в тестовом filesystem staging. Не запрашивайте его в
JitPack до решения о публикации: запрос отсутствующей версии может запустить сборку.

## Инициализация

Точный исходник [минимального consumer](examples/consumer/src/main/java/example/ExampleClient.java)
компилируется вместе с независимыми тестовыми consumer-модами каждого target:

```java
package example;

import net.fabricmc.api.ClientModInitializer;
import net.funtimeevents.api.FunTimeEventsAPI;
import net.funtimeevents.api.RuntimeLease;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class ExampleClient implements ClientModInitializer {
    private RuntimeLease lease;
    @Override public void onInitializeClient() {
        lease=FunTimeEventsAPI.builder()
            .userAgent("ExampleMod/1.0")
            .apiKey(System.getProperty("fte.apiKey"))
            .buildLease();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> lease.close());
    }
}
```

Передайте настоящий ключ в `fte.apiKey` либо получите его из собственной конфигурации.
Не помещайте ключ в исходники или публичные логи. Builder вызывается на клиентском
потоке. Прежние пакеты `net.funtimeevents.api.*` и типы результатов сохранены.

Подробности: [DOC.md](DOC.md), [команды разработки](AGENTS.md),
[решения перед выпуском](RELEASE_DECISIONS.md).

## Ограничения

Один глобальный runtime. Повторный Builder принимает только полностью совпадающую
конфигурацию; конфликт явно отклоняется. Для независимых модов используйте `buildLease()`
и закрывайте свой `RuntimeLease`. Legacy `build()` удерживает runtime до `shutdown()`;
при активных leases `shutdown()` явно отказывает. Подробности — [Runtime ownership](docs/RUNTIME_OWNERSHIP.md).
Доставка payload — bounded best effort, без backend ACK и exactly-once.
Известный Windows native startup crash 26.3 остаётся **OPEN**.

Лицензия: [MIT License with Backend Restriction](LICENSE), а не стандартная MIT.
