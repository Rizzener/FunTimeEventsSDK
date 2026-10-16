# Исправления проверочных fixtures этапа 5

## Диагностика доставки сцены

В 1.21.11 Windows REST/WSS/flags и некоторых других прогонах DungeonPayload содержал пустой список контейнеров. Это остаётся FAIL, golden не изменён. В diagnostic WSS attempt-02 сервер подтверждает `setBlockAndUpdate=true`, правильные блоки и minY=-64; клиент видит `void_air` и только 16 соседних TextDisplay с текстом `Not a timer`, без таймеров над контейнерами. Следующий REST attempt-02 дополнительно показывает `class_2812`: фактический mappings.tiny определяет его как `EmptyLevelChunk`. Его реальный bytecode всегда возвращает VOID_AIR. `world.hasChunkAt()` здесь не служит доказательством получения настоящего чанка.

Сохранены фактические javap в `results/phase5/local-environment/chunk-diagnostics`. Fixture теперь журналирует тип чанка, bounded историю реальных Fabric CHUNK_LOAD/UNLOAD и server chunkSender pending. Сборка диагностики обнаружила отдельные ошибки самого fixture: `performPrefixedCommand` возвращает void; новый ChunkPos больше не имеет старого constructor(BlockPos)/toLong. Они исправлены без изменения SDK; failed compile/runtime attempts сохраняются. Для диагностического long key используется прежняя vanilla упаковка двух signed int, без reflection и новых платформенных интерфейсов.

Контроль `1.21.11/runtime-rest/attempt-03` с обычной серверной командой `forceload` до подключения клиента прошёл все 21 сессию: настоящий LevelChunk, все 6 timer TextDisplay (включая дубликат и негативные случаи), прежние payload. SDK JAR и golden прежние. Это подтверждает недостаточную подготовку сцены в fixture; точную внутреннюю причину задержки vanilla chunk sending данный контроль не устанавливает. Предварительная загрузка включена для fixtures этапа 5. Для воспроизведения старого контроля можно установить `FTE_PHASE5_PRELOAD=0`. Алгоритмы трекеров, таймеры SDK, сообщения, golden и Maven SDK JAR остаются прежними. Другие версии и режимы требуют собственных повторных результатов.

## Холодная подготовка assets

Первый native WSL production client 1.21.5 превысил 600 секунд без подтверждённого запуска Minecraft в сохранённом логе. Raw client log останавливается после Gradle tasks; сохранённый thread dump показывает сериализацию файловых snapshots Gradle execution history. `downloadAssets` теперь включён в отдельную consumer preparation stage, перед ограниченным runtime-прогоном. Лимит runtime и игровые assertions не увеличены. После освобождения пользователем места на C: runner сохраняет повторно используемые входные JAR при наличии более 5 GiB, чтобы не провоцировать новую загрузку Fabric Installer при каждом диагностическом повторе. Worlds и все raw evidence обрабатываются отдельно; SDK артефакты и crash dumps не удаляются.

Gradle может буферизовать stdout дочернего production JVM: отсутствие строк в `client.txt` само по себе не доказывает отсутствие запущенной игры. Для 26.3 это проверено непосредственно по процессу KnotClient и настоящему `logs/latest.log`. Runner дополнительно сохраняет game logs и наблюдённый JSON даже при timeout; они не превращают ненормальное завершение wrapper в PASS. Существующая ротация первых Linux запусков сохранена в `wsl/native/recovered-game-logs`.

Для устранения медленного доступа через `/mnt/c` официальные objects проверяются по SHA-1 имени и SHA-256 manifest и копируются на native Linux FS. Исходная Windows ссылка сохраняется отдельно; переключение выполняется без активных production процессов. Первый prepare отклонён из-за старого prefixed index `1.21.8-26.json`: его SHA-1 не совпал с Mojang metadata. Такой индекс не копируется; свежий Linux Loom должен получить его по pinned metadata. Ошибка и rejected indexes сохраняются, исходный кэш не изменяется.

## Linux TIME_WAIT и возобновление матрицы

После штатной остановки первого сервера 26.3 последующие режимы не достигали запуска: предварительный bind без SO_REUSEADDR получал errno 98. Проверка `phase5_port_check.py` воспроизводит отказ на собственном закрытом TCP соединении и подтверждает, что SO_REUSEADDR допускает TIME_WAIT, но по-прежнему отклоняет действующий listener. Для Linux исправлен только probe; backend и Minecraft protocol не менялись. Исходные backend attempts остаются FAIL.

Матрица остановлена только внутри task-owned cgroup, на compile стадии 1.21.8, с проверкой отсутствия активного publish. Прерванная компиляция записана как FAIL без утверждения о её результате; опубликованные 1.21.5/26.3 артефакты сохранены. Новая матрица использует строгие результаты verifier при выборе пропущенных режимов: raw process PASS с неверным scheduler count не считается функциональным PASS. Первый offline 26.3 именно так отклонён (`scheduler_9`), несмотря на PASS отдельных session assertions. Assertions не ослаблены.

## Очистка временных worlds

Вспомогательный `phase5_watch.py` завершился с FileNotFoundError: watcher и основной runner одновременно очищали уже завершённый fixture world 26.1. Это произошло после проверки client/server exit и не меняет SDK verdict. Выбран простой обход: watcher выключен; очисткой владеет основной runner после завершения процесса. SDK JAR, raw logs и JSON не удалялись. Копия полного вывода tool session 13308 сохранена в `local-environment/watch-cleanup-failure.txt`. Не следует запускать watcher параллельно со встроенной очисткой runner.

## Межсистемная воспроизводимость POM (проверка)

Независимые Windows/Linux JAR, sources и Module Metadata 1.21.5/26.3 побайтно совпали. POM отличается только CRLF/LF, что подтверждено точным сравнением bytes после единственной замены CRLF на LF. Первоначальная слишком строгая проверка сохранена как `cross-os-bytecheck-initial.json`; исходные POM и разные SHA-256 не меняются. Verifier требует byte equality для JAR/sources/module; для POM явно фиксирует byte_equal=false и допускает только это конкретное различие. Изменение version или XML content по-прежнему даёт FAIL.

## Счётчик pending sendText: гонка наблюдения

Исходный FAIL: `results/phase5/26.1.2/network/attempt-02/`, `WS_serial_futures_failure_no_replay_and_old_callbacks: concurrent text sends`.

В FakeSocketClient счётчик увеличивался при sendText, уменьшался в `future.whenComplete(...)`, а вызывающему возвращался **исходный** future. Его `get()` уже может завершиться, пока зависимый whenComplete ещё не выполнил decrement. Последующий корректно последовательный sendText тогда видел ложное значение 2.

RelayClient ожидает завершения возвращённого sendText future в единственном writer thread, включая auth. Его исходники и опубликованные SDK JAR не изменялись.

Исправление только fixture: возвращается зависимый future, публикующий завершение после decrement. Счётчик по-прежнему обнаруживает два одновременно незавершённых возвращённых future; сериализация или lock внутри fake не добавлялись. Assertion `maxPending == 1`, удержание первого send и проверки ошибки/отсутствия replay сохранены.

`SendFutureAccountingCheck` воспроизводит старую гонку детерминированно с latch: исходный future уже завершён и get возвращает результат, когда callback ещё блокируется. Отдельный negative control намеренно вызывает два незавершённых sendText и требует maxPending=2. Проверка включена в прежнюю группу hardening и может запускаться отдельно на Java 21/25. Исходные fixtures сохранены в `fixtures/history/phase5-pre-future-accounting/`.

Основание: [JDK CompletableFuture](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html) разделяет завершение Future и зависимые CompletionStage actions; их синхронное завершение перед возвратом другого потока из get не обещается.

До повторных успешных прогонов новый fixture не считается доказательством PASS. Первоначальная попытка остаётся FAIL и не удаляется.

## Остальные наблюдения

- Windows отклонила фиксированные loopback-порты до старта SDK; первая серия network attempts сохранена. Runner теперь проверяет пару доступных портов, не изменяя системные резервирования.
- 1.21.6 network attempt-02: timeout на обычном GET в hardening. 1.21.10 attempt-02: timeout в parity. Ресурсная конкуренция рассматривается как гипотеза; assertions не увеличивались, SDK не исправлялся без доказательств. Требуются повторные результаты.
- Первая WSL-сборка с cache на `/mnt/c` прервана оператором задачи после диагностики при нехватке RAM. Сохранены FAIL и daemon log. Новая native-filesystem WSL область имеет отдельный evidence namespace, поэтому не перезаписывает эту попытку.
- Portable WSL prepare attempt-02 сначала вернул exit=0 из pipeline `Xvfb -help | head`, несмотря на missing shared libraries. Это не Linux CI PASS. Зависимости добавлены, проверка заменена на явный ldd/error check. Системный dpkg не ремонтировался.

## Агрегация во время live-copy

Один промежуточный запуск `phase5_collected_runtime.py` совпал с полной `copytree` выгрузкой Native B: 1.21.7 и ранее проверенная 1.21.9 получили NOT VERIFIED. Последующее чтение показало raw PASS для всех четырёх modes; сохранённый производный файл 1.21.7 временно не содержал offline run, а производный 1.21.9 уже снова имел PASS. Снимки и первичный stdout сохранены в `local-environment/aggregation-live-copy/attempt-01`.

После завершения копирования повторный строгий verifier дал PASS обеим версиям, без повторного запуска игры, изменения SDK, payload или assertions. Предположение — несогласованный вид файлов во время перезаписи общей копии; точный Windows/DrvFS механизм этим наблюдением не установлен. Обход: финальные hashes, runtime verification и manifest выполняются после завершения обоих workers и их collection. Промежуточный NOT VERIFIED не выдаётся за функциональный FAIL SDK или за окончательный PASS. Hosted jobs передают завершённые artifacts между стадиями и не проверялись запуском на GitHub.

## Windows long paths при очистке завершённых кэшей

При уменьшении свободного места C: сначала удалены только регенерируемые входные JAR завершённых Windows fixtures. Затем проверены 15 локальных PoC `.gradle` каталогов. Первая попытка `shutil.rmtree` остановилась с WinError 3 на длинном пути remapped Fabric API sources JAR; сохранён SHA manifest и FAIL. Повтор с Windows extended-length prefix после проверки каждого абсолютного пути успешно очистил эти каталоги. Source, build/libs, Maven artifacts, raw logs, dumps и работающие native WSL workspaces сохранены. Cache `.log` при наличии копировались отдельно. Evidence: `local-environment/completed-Windows-cache-inventory.json`, `completed-Windows-cache-reclaim/first-attempt-result.json`, `manifest.json`, `retry-manifest.json`, `result.json`. Это очистка тестового окружения; SDK JAR не менялся.

## Контроль длинных WSL callback spans

После полной функциональной матрицы замечен wall-clock max 266.642 ms у 26.2 REST при лимите 2 CPU. Вместо изменения SDK выполнены ещё два полных production REST fixtures: 2 и 4 CPU, с ThreadMXBean CPU timing только в consumer. SDK JAR не перепубликовывался; SDK/consumer/peer/server JAR между контролями совпали побайтно. Все прежние golden, lifecycle, network и shutdown assertions прошли.

Повтор 2 CPU: active mean 51.629 µs, max 6.898 ms, CPU этого span 6.845 ms. Повтор 4 CPU: mean 39.864 µs, max 5.025 ms, CPU 4.933 ms. Первоначальная пауза не повторилась уже при 2 CPU. Throttling cgroup зарегистрирован, OOM counters нулевые; накопительные counters и delta второго контроля сохранены. Эти результаты не устанавливают причину старого outlier и не доказывают FPS или worst-case latency. Устойчивая регрессия 26.2 не подтверждена; причина исходной паузы остаётся открытым вопросом измерений, а не исправленным SDK-дефектом.

Source ZIP старых запусков сохранены. Новые fixtures измеряют только spans от 1 ms, с прежними allocation/wall counters; CPU instrumentation включается отдельным test property. Основная матрица остаётся с 2 CPU, дополнительный 4 CPU случай помечен отдельно в performance table/CSV. `phase5_perf_control.py` воспроизводит сравнение из `local-environment/performance-cpu-control/analysis.json`, команды запусков находятся в `runner.json`.

## Подготовка настоящего vanilla server input

1.21.7 (offline/REST/WSS) и 1.21.11 (offline/REST) достигли лимита запуска во время повторной загрузки vanilla server JAR Fabric Installer, до запуска игры и SDK. В первом случае сохранён `.fabric/server/1.21.7-server.jar.tmp` размером 51 331 072 байта. Эти попытки остаются FAIL.

Проверены исходники ServerLauncher и bytecode фактического `fabric-installer-1.1.2-server.jar`: launcher берёт `.fabric/server/<MC>-server.jar`, если файл уже существует. Исполняемая тестовая задача теперь подготавливает **тот же официальный vanilla binary** из Loom raw cache и проверяет его SHA-1 по `mojang_minecraft_info.json` перед каждым запуском. При несовпадении запуск завершается ошибкой; повреждённый файл не маскируется. Лимит 240 секунд и все игровые assertions сохранены. JVM flags, игровой код и SDK JAR не изменялись.

Результаты: `results/phase5/local-environment/runtime-inputs/`; raw server logs содержат `[PHASE5-INPUT]` с проверенным Mojang SHA-1. Это подготовка среды, а не доказательство runtime PASS — требуется полный последующий fixture run.

## Loopback readiness

В 26.2 hardening attempt-01 HTTP listener уже стартовал, а TLS bind получил WinError 10013. Проба runner использовала обычный bind вместо Windows SO_EXCLUSIVEADDRUSE; готовность проверялась TCP connect ещё до объявления готовности TLS listener. Исправлены только runner probes: exclusive bind и ожидание FAKE_BACKEND_READY после открытия обоих listeners. Ретест hardening 26.2 прошёл; исходный FAIL сохранён. Конкретную причину системного отказа bind нельзя считать окончательно установленной по одному stack trace.

## Более строгая проверка координат

Phase 5 verifier сохраняет порядок чисел внутри coordinate vectors, продолжая сравнивать неупорядоченные коллекции игроков/сущностей независимо от порядка обхода. Negative assertion требует различать `[-123,64,456]` и `[-123,456,64]`. Уже завершённые production-прогоны 1.21.4, 1.21.5, 1.21.6 и 1.21.8 проходят эту дополнительную проверку. Golden fixtures и код SDK не изменены.
## Проверка headless X11 в локальном WSL

У Native B первые production-клиенты 26.1.2/26.1.1 завершались до JOIN: GLFW `0x1000E`, `X11: Failed to open display`. Проверка сохранённых `xvfb.log` обнаружила отсутствие `/usr/bin/xkbcomp` и ошибку компиляции XKB. У прежних успешных Linux-прогонов 1.21.5/26.3 и offline 1.21.8 тоже не было доказательства готовности Xvfb. Их игровые результаты и raw logs сохранены, но они не засчитываются как подтверждённый headless runtime. Все обязательные режимы повторяются.

Официальные Ubuntu `x11-xkb-utils`, `xkb-data`, `libxkbfile1`, `x11-utils`, `libxxf86dga1` распакованы только в каталог этапа. Первая подготовка обнаружила отсутствующую зависимость `libXxf86dga.so.1`; failed attempt сохранён, следующая прошла ldd. Системный незавершённый dpkg не ремонтировался.

Последующий просмотр коротких строк игровых logs подтвердил исторический Wayland: 1.21.5 offline attempt-02 сообщает `Wayland: The platform does not support setting the cursor position`, а 26.3 REST attempt-03 прямо пишет `Created window using SDL video driver: wayland`. Выборка с путями сохранена в `local-environment/historical-display-log-review.json`. Поэтому прежний успешный выход игры действительно нельзя считать доказательством Xvfb.

Новый 26.3 offline attempt-03 прямо сообщает `Created window using SDL video driver: x11`; verifier теперь требует эту строку от Linux SDL-клиента. Приватный XDG runtime исключает прежний Wayland socket. Для последующих запусков сценарий явно задаёт оба названия hint: [SDL2 SDL_VIDEODRIVER](https://wiki.libsdl.org/SDL2/SDL_HINT_VIDEODRIVER) и [SDL3 SDL_VIDEO_DRIVER](https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_DRIVER). У текущей уже запущенной матрицы переменная SDL3 ещё не задана, но фактический backend проверяется по игровому log, без утверждения о наследовании нового env задним числом.

Xserver формирует путь к компилятору через `XkbBinDirectory`; `-xkbdir` задаёт каталог данных. Это проверено по [ddxLoad.c](https://raw.githubusercontent.com/mirror/xserver/master/xkb/ddxLoad.c) и [xkbInit.c](https://raw.githubusercontent.com/mirror/xserver/master/xkb/xkbInit.c). Выбран приватный mount namespace: overlay `/usr/bin` добавляет настоящий task-local `xkbcomp`, отдельный bind mount даёт приватные X11 sockets. Mount propagation предварительно rprivate. Внешний `/usr/bin/xkbcomp` после probe остаётся отсутствующим. Изменений глобальных пакетов, настроек WSL или JVM нет.

Перед Minecraft обязателен настоящий `xdpyinfo`; receipt проверяет сокет, отдельный mount namespace, путь к реальному компилятору и отсутствие WSLg fallback. `WAYLAND_DISPLAY` удалён, XDG runtime приватный, SDL/XDG session установлены в X11. Оба display-probe прошли; receipts и исходное состояние внешнего namespace сохранены в `results/phase5/local-environment/x11-display-probes.json`. GLFW exception является Java-ошибкой тестового окружения и не закрывает отдельный native startup crash 26.3.

CI bootstrap также явно устанавливает XKB/xdpyinfo dependencies. Hosted workflow не запускался по ограничению пользователя. Текущий verifier требует display receipt от каждого Linux runtime; старый process PASS без receipt получает NOT VERIFIED при повторной проверке.
