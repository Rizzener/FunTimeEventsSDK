"""Generate target tables and exact dependency examples from the build registry."""
import json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def main():
    c=json.loads((ROOT/'targets.json').read_text());v=c['sdk_version'];group=c['maven_group']
    standalone=ROOT/'examples/consumer'
    (standalone/'targets.json').write_text(json.dumps(c,indent=2)+'\n')
    for name in ['gradlew','gradlew.bat','gradle.properties','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties']:
        target=standalone/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/name,target)
    table='| Minecraft | Java | Fabric API | Maven artifact |\n|---|---:|---|---|\n'+''.join(f"| {t['minecraft']} | {t['java']} | `{t['fabric_api']}` | `mc{t['minecraft']}` |\n" for t in c['targets'])
    example=(ROOT/'examples/consumer/src/main/java/example/ExampleClient.java').read_text()
    readme=f'''# FunTimeEventsSDK

Клиентский Fabric SDK для FunTimeEvents API: игровые трекеры, REST, WebSocket и SSE.

**Ветка release candidate, версия `{v}`. Публичный выпуск ещё не выполнен.**
Таблица ниже задаёт целевые артефакты. Фактическая повторная проверка интегрированной
версии, hosted CI и оставшиеся ограничения фиксируются в [PHASE6_RESULTS.md](PHASE6_RESULTS.md).
Принятые результаты этапа 5 остаются в [историческом отчёте](poc/multiversion/PHASE5_RESULTS.md).

## Цели

{table}

Fabric Loader: **{c['loader']}** или совместимая более новая версия; проверки фиксируют
именно {c['loader']}. Fabric API устанавливает consumer-мод. Один SDK JAR предназначен
строго для одной строки таблицы. 26.x требует Java 25; 1.21.x — Java 21.

## Подключение

Текущий канал — JitPack. Координаты для будущего выпуска:

```groovy
repositories {{ maven {{ url = 'https://jitpack.io' }} }}
dependencies {{
    modImplementation "{group}:mc1.21.4:{v}"
    include "{group}:mc1.21.4:{v}"
}}
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
{example.rstrip()}
```

Передайте настоящий ключ в `fte.apiKey` либо получите его из собственной конфигурации.
Не помещайте ключ в исходники или публичные логи. Builder вызывается на клиентском
потоке. Прежние пакеты `net.funtimeevents.api.*` и типы результатов сохранены.

Подробности: [DOC.md](DOC.md), [команды разработки](AGENTS.md),
[решения перед выпуском](RELEASE_DECISIONS.md).

## Ограничения

Один глобальный runtime. Повторный Builder принимает только полностью совпадающую
конфигурацию; конфликт явно отклоняется. `shutdown()` пока глобален: не вызывайте его
при выходе с сервера и не используйте независимо в нескольких модах без координации.
Доставка payload — bounded best effort, без backend ACK и exactly-once.
Известный Windows native startup crash 26.3 остаётся **OPEN**.

Лицензия: [MIT License with Backend Restriction](LICENSE), а не стандартная MIT.
'''
    doc=f'''# FunTimeEventsSDK — руководство разработчика

## Состояние выпуска

Кандидат `{v}`, не опубликован. Проверка новых артефактов — PHASE6_RESULTS.md.
Исторический локальный PASS этапа 5 не подменяет новые hosted результаты.

## Версии и зависимости

{table}

Gradle {c['gradle']}, Loom {c['loom']}, Fabric Loader {c['loader']}.
Для разработки нужны оба JDK (21/25); запускайте wrapper на JDK 25. Для запуска
consumer нужен JDK из строки Minecraft. Mappings старых версий — официальные Mojang;
release namespace старых JAR — intermediary после remapJar. Современные JAR используют
необфусцированные имена и не проходят remap. Не remap-те modern JAR вручную.

### Dependency для каждого target

Все строки используют один group и одну версию релиза. Выберите ровно одну:

'''+''.join(f"- Minecraft {t['minecraft']}: `{('modImplementation' if t['java']==21 else 'implementation')} \"{group}:mc{t['minecraft']}:{v}\"`\n" for t in c['targets'])+f'''

Для распространения добавьте `include` с той же координатой. Для предоставляемого
отдельно SDK-мода include не нужен, но тогда именно пользователь обязан установить
подходящий JAR. Не распаковывайте SDK классы в consumer JAR, не relocate-те их и не
вкладывайте `core` отдельно. Fabric mod ID SDK — `fte-api`; metadata содержит точный
Minecraft target, Java constraint, Loader и Fabric API dependencies.

### Независимый минимальный consumer

В examples/consumer нет producer project dependencies или Maven Local. Для RC:
Папку можно скопировать отдельно: в ней собственные wrapper, sources и каталог
версий. Родительский checkout SDK и его классы для сборки не нужны.

```sh
sh ./gradlew sdkStage -Ptarget_versions=1.21.4
sh examples/consumer/gradlew -p examples/consumer build -Pminecraft_version=1.21.4 -Psdk_repository=/absolute/path/to/build/staging-maven
```

Для 26.3 поменяйте только minecraft_version и используйте соответствующий staging
target. Зависимость по умолчанию указывает на JitPack, применяйте её после публикации.
Точный ExampleClient.java приведён в README; ключ приходит из consumer-конфигурации.

## Builder и режимы

`userAgent(...)` обязателен. `apiKey(...)` обязателен в online режиме.
`baseUrl(...)` по умолчанию https://api.funtimeevents.su/v1/.
`logLevel(FteConfig.LogLevel.DEBUG)` меняет уровень модульного логирования.
Полная конфигурация сравнивается при повторном Builder: URL, ключ, userAgent,
режим, compression, флаги трекеров, интервал, logLevel. Несовпадение вызывает
IllegalStateException без раскрытия ключа. Чужой API-ключ незаметно не используется.

| Режим | Отправка трекеров | Получение/кеши | Captcha |
|---|---|---|---|
| По умолчанию | WSS relay, plain TEXT JSON | REST GET + WebSocket snapshots | REST |
| disableWebSocket() | REST POST | REST GET + SSE | REST |
| offlineMode() | без отправки | пустые caches, completed-null futures | без запроса |

`disableCompression()` отключает gzip HTTP POST. WSS всегда использует текстовый
JSON. Auth envelope, endpoint paths, JSON и прежние типы DTO не менялись при переносе.
Offline не является локальным хранилищем данных: payload отбрасывает no-op sender.

### Трекеры и интервалы

Все включены по умолчанию. Отключение: disableScanTabPlayers(), disableBansTracker(),
disableScanDungeon(), disableScanHellMap(), disableScanMine(),
disableEventCoordinatesTracker(). Dungeon включает Copper Dungeon и Warden City.
`tickIntervalSeconds(...)`: общий период 10 секунд по умолчанию, минимум 1 секунда.
Сохраняются прежние дополнительные интервалы HellMap и алгоритмы каждого трекера.
SDK реагирует на JOIN/DISCONNECT и сообщения; consumer не регистрирует свои callbacks.

### Публичные методы

Typed caches: getEvents(), getUserEvents(), getMines(), getCopperDungeons(),
getWardenCities(); getSystemInfo() доступен в WSS режиме.
Async GET: fetchEvents(params), fetchUserEvents(params), fetchMines(params),
fetchPlayers(params), fetchBans(params), fetchCopperDungeon(), fetchWardenCity().
Captcha: solveCaptcha(base64), всегда REST, даже при WSS.
Не блокируйте клиентский поток вызовом join/get на сетевых futures. Результат
обработайте асинхронно; изменение Minecraft UI верните на клиентский поток.

## Lifetime и shutdown

Игровая сессия и SDK runtime имеют разные сроки жизни. DISCONNECT останавливает
трекеры, очищает игровой context/donate caches и делает queued payload старой
сессии недействительными. Backend caches принадлежат runtime. Reconnect не создаёт
новые Fabric подписки. Не вызывайте shutdown при DISCONNECT.

Legacy shutdown() останавливает глобальный runtime. В текущем кандидате один
consumer может остановить SDK другого: это явный продуктовый риск, решение
lease/owner изложено в RELEASE_DECISIONS.md. До принятия решения централизуйте
владение runtime в одном host-моде. CLIENT_STOPPING очищает runtime автоматически.
Build/shutdown выполняются на клиентском потоке. Инициализация с ошибкой очищает
созданные ресурсы, сохраняя ранее установленный чужой runtime.

## Гарантии доставки

Snapshots: TAB, игроки шахт, состояние данжей, HellMap. Очередь до 64 ключей
type/server, latest wins, TTL 15 секунд; старое состояние заменяется новым.
Важные события: bans и coordinates, FIFO до 256, TTL 30 секунд, overflow отклоняет
новое событие. При shutdown очередь очищается; смена сессии инвалидирует payload.
Перед отправкой JSON/gzip выполняются вне client thread. Gzip failure отменяет
HTTP запрос: plain bytes не отправляются с Content-Encoding: gzip.

Это best effort. Нет durable spool, backend ACK, гарантии at-least-once или
exactly-once. HTTP/WS неоднозначный исход не повторяется слепо. При reconnect бан
может потеряться; приложению нельзя считать очередь журналом банов. Усиление
доставки требует отдельного решения по ACK/idempotency/retention backend.

## Несколько модов и Loader

Одинаковый `fte-api` одной версии в двух jar-in-jar должен разрешаться в один мод.
Используйте совместимые явные version constraints. Exact-конфликт SDK версий
должен завершаться диагностикой Loader. При диапазонах Loader может выбрать одну
совместимую версию; это не означает, что два runtime будут изолированы. Разные MC
targets никогда не должны загружаться в один клиент. Детали фактических негативных
проверок и бинарной совместимости находятся в PHASE6_RESULTS.md.

## Миграция с прежнего SDK

Старые tags не перепубликуются. Проверка v1.0.5 обнаружила HTTP 404 для
`com.github.Rizzener.FunTimeEventsSDK:fte-api:v1.0.5`, при этом доступен POM
`com.github.Rizzener:FunTimeEventsSDK:v1.0.5`. Подробности и raw ответы —
RELEASE_DECISIONS.md. Для нового релиза используйте multi-module group и artifactId
`mc<точная-версия>`, одновременно обновите include и metadata зависимости на
`fte-api`. Не используйте Maven relocation с fte-api на произвольный MC target:
Maven не выбирает target из вашей версии Minecraft. Не используйте агрегат repo,
который притянет несколько несовместимых targets.

Предлагается новый tag без префикса v, чтобы Fabric semantic version совпадала с
Maven version. Builder, пакеты facade/config/models и сетевые публичные дескрипторы проверяются
по старому JAR. Внутренние tracker/bootstrap constructors не являются обещанием
бинарной совместимости. Precompiled legacy probe запускается на новом SDK;
это не подтверждает совместимость всех неизвестных consumer-модов.

## Разработка и публикация

AGENTS.md содержит команды. Root Gradle — единственная producer сборка. Python
контролирует production процессы и доказательства; обычная сборка/IDE используют
Gradle напрямую. Public channel остаётся JitPack. RC не опубликован. Изолированное
Maven staging и GitHub artifact uploads не являются Maven release deployment.
Порядок публикации и сравнение каналов — RELEASE_DECISIONS.md.

Лицензия — MIT License with Backend Restriction; сохранить точный LICENSE.
Известная native проблема Windows 26.3 остаётся OPEN независимо от Linux PASS.
'''
    (ROOT/'README.md').write_text(readme,encoding='utf-8',newline='\n')
    (ROOT/'DOC.md').write_text(doc,encoding='utf-8',newline='\n')
    print('DOCS_GENERATED',len(c['targets']),'targets',v)

if __name__=='__main__':main()
