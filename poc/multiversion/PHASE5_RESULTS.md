# Phase 5 — результаты

Обновлено: 2026-10-15T19:15:25.827659+00:00

**Статус Windows + локальный Linux: FUNCTIONAL_PASS_NATIVE_OPEN**. Native startup 26.3: **OPEN**. Production release не публиковался.

Только изолированный `poc/multiversion`; экспериментальная версия `0.1.0-phase5.1`, group `net.funtimeevents.poc`.

## Матрица Windows

| Minecraft | Java | Fabric API | Compile | Maven | Consumer | Packaging | API | Transport | Runtime |
|---|---|---|---|---|---|---|---|---|---|
| 1.21.4 | 21 | 0.113.0+1.21.4 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.5 | 21 | 0.128.2+1.21.5 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.6 | 21 | 0.128.2+1.21.6 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.7 | 21 | 0.129.0+1.21.7 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.8 | 21 | 0.136.1+1.21.8 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.9 | 21 | 0.134.1+1.21.9 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.10 | 21 | 0.138.4+1.21.10 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.11 | 21 | 0.141.6+1.21.11 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1 | 25 | 0.145.1+26.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.1 | 25 | 0.145.4+26.1.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.2 | 25 | 0.155.3+26.1.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.2 | 25 | 0.161.0+26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.3 | 25 | 0.163.0+26.3 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

## Матрица native WSL / Linux

| Minecraft | Worker | Compile | Maven | Consumer | Packaging | API | Transport | Runtime |
|---|---|---|---|---|---|---|---|---|
| 1.21.4 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.5 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.6 | native-b | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.7 | native-b | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.8 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.9 | native-b | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.10 | native-b | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.11 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.1 | native-b | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.2 | native-b | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.2 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.3 | native | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

Каждый Runtime PASS требует отдельно offline, REST, WSS и disable flags. Отсутствующая проверка получает NOT VERIFIED. Компиляция сама по себе поддержку не подтверждает. Подробные assertions, session payload и нормальные exit codes: `results/phase5/<MC>/runtime.json`.

## Производительность

Числа ниже — измеренные SDK/Fabric callback spans в тестовом клиентском потоке; соседние локальные проверки создают смешанную нагрузку. Это не FPS benchmark. Raw counters сохраняют count/sum/max nanos и allocated bytes; JSON runtime.json содержит нормализованные mean/max и затраты каждого трекера.

| Среда / Minecraft / mode | Active mean/max µs | Idle mean µs | Active mean bytes | FTE threads после shutdown |
|---|---|---|---|---|
| Windows / 1.21.4 / rest | 45.940 / 9779.200 | 4.919 | 1481.0 | 0 |
| Windows / 1.21.4 / ws | 41.882 / 10428.200 | 4.656 | 1477.9 | 0 |
| Windows / 1.21.5 / rest | 42.172 / 9269.000 | 4.651 | 1476.6 | 0 |
| Windows / 1.21.5 / ws | 59.464 / 37164.500 | 3.747 | 1476.1 | 0 |
| Windows / 1.21.6 / rest | 46.248 / 11054.200 | 6.175 | 1482.1 | 0 |
| Windows / 1.21.6 / ws | 46.336 / 9551.200 | 3.940 | 1458.1 | 0 |
| Windows / 1.21.7 / rest | 42.278 / 9977.000 | 4.409 | 1454.5 | 0 |
| Windows / 1.21.7 / ws | 39.770 / 6681.500 | 5.545 | 1454.5 | 0 |
| Windows / 1.21.8 / rest | 46.876 / 8055.200 | 5.073 | 1479.2 | 0 |
| Windows / 1.21.8 / ws | 47.555 / 9413.900 | 5.265 | 1486.0 | 0 |
| Windows / 1.21.9 / rest | 46.332 / 11395.100 | 4.603 | 1470.0 | 0 |
| Windows / 1.21.9 / ws | 45.082 / 9765.100 | 5.116 | 1457.8 | 0 |
| Windows / 1.21.10 / rest | 41.723 / 8058.900 | 4.878 | 1440.0 | 0 |
| Windows / 1.21.10 / ws | 47.072 / 11190.900 | 5.116 | 1439.0 | 0 |
| Windows / 1.21.11 / rest | 45.798 / 10405.300 | 4.971 | 1365.5 | 0 |
| Windows / 1.21.11 / ws | 41.401 / 10118.700 | 4.301 | 1392.0 | 0 |
| Windows / 26.1 / rest | 43.111 / 8173.200 | 4.797 | 1283.0 | 0 |
| Windows / 26.1 / ws | 44.560 / 5247.000 | 4.664 | 1282.5 | 0 |
| Windows / 26.1.1 / rest | 43.881 / 7678.100 | 5.406 | 1307.7 | 0 |
| Windows / 26.1.1 / ws | 44.020 / 8758.200 | 4.626 | 1309.7 | 0 |
| Windows / 26.1.2 / rest | 42.976 / 10218.700 | 4.417 | 1309.9 | 0 |
| Windows / 26.1.2 / ws | 42.040 / 8368.200 | 4.683 | 1313.9 | 0 |
| Windows / 26.2 / rest | 40.494 / 7315.000 | 5.162 | 1315.9 | 0 |
| Windows / 26.2 / ws | 40.051 / 5758.400 | 4.673 | 1285.2 | 0 |
| Windows / 26.3 / rest | 41.463 / 12995.100 | 4.912 | 1317.8 | 0 |
| Windows / 26.3 / ws | 39.997 / 8282.800 | 4.522 | 1298.6 | 0 |
| native / 1.21.4 / rest | 103.932 / 101612.353 | 4.915 | 1425.8 | 0 |
| native / 1.21.4 / ws | 112.722 / 67374.292 | 5.456 | 1417.5 | 0 |
| native / 1.21.5 / rest | 52.107 / 6003.768 | 3.871 | 1421.9 | 0 |
| native / 1.21.5 / ws | 110.307 / 62606.500 | 3.822 | 1423.9 | 0 |
| native / 1.21.8 / rest | 84.744 / 87510.495 | 4.902 | 1431.6 | 0 |
| native / 1.21.8 / ws | 75.141 / 64681.109 | 4.025 | 1435.5 | 0 |
| native / 1.21.11 / rest | 98.165 / 88554.732 | 3.776 | 1341.9 | 0 |
| native / 1.21.11 / ws | 43.474 / 8156.057 | 3.674 | 1372.3 | 0 |
| native / 26.1 / rest | 140.290 / 105828.145 | 3.786 | 1263.1 | 0 |
| native / 26.1 / ws | 105.837 / 69289.918 | 3.178 | 1268.9 | 0 |
| native CPU4 diagnostic / 26.2 / rest | 39.864 / 5025.200 | 5.821 | 1342.4 | 0 |
| native / 26.2 / ws | 50.456 / 9150.956 | 4.246 | 1257.4 | 0 |
| native / 26.3 / rest | 153.153 / 75629.737 | 3.986 | 1310.8 | 0 |
| native / 26.3 / ws | 48.461 / 8478.163 | 3.749 | 1316.5 | 0 |
| native-b / 1.21.6 / rest | 43.381 / 6377.092 | 4.441 | 1434.6 | 0 |
| native-b / 1.21.6 / ws | 47.189 / 7378.015 | 4.088 | 1427.2 | 0 |
| native-b / 1.21.7 / rest | 46.905 / 7747.979 | 5.008 | 1433.5 | 0 |
| native-b / 1.21.7 / ws | 43.415 / 8926.444 | 4.485 | 1431.6 | 0 |
| native-b / 1.21.9 / rest | 50.629 / 9021.485 | 4.850 | 1416.2 | 0 |
| native-b / 1.21.9 / ws | 47.599 / 9740.927 | 5.558 | 1417.0 | 0 |
| native-b / 1.21.10 / rest | 50.626 / 11682.683 | 5.236 | 1414.8 | 0 |
| native-b / 1.21.10 / ws | 47.598 / 10825.917 | 4.522 | 1415.4 | 0 |
| native-b / 26.1.1 / rest | 44.154 / 6909.796 | 5.252 | 1283.5 | 0 |
| native-b / 26.1.1 / ws | 42.627 / 8649.987 | 3.983 | 1254.7 | 0 |
| native-b / 26.1.2 / rest | 42.982 / 7971.722 | 3.271 | 1282.2 | 0 |
| native-b / 26.1.2 / ws | 41.756 / 8061.836 | 3.418 | 1281.1 | 0 |

Все измеренные modes/categories, включая idle, сообщения, Dungeon/TAB/Mine/Hell и allocations: [performance.csv](results/phase5/performance.csv), [performance.json](results/phase5/performance.json). Отсутствующие измерения явно отмечены NOT MEASURED / NOT VERIFIED. Tracker spans измеряют время; allocations доступны для общего клиентского callback span, а не отдельно для каждого трекера.

Исследование выбросов: Windows WSS 1.21.5 active mean 59.464 µs при 2176 измерениях и max 37164.5 µs; среднее остальных 2175 равно 42.405 µs. Windows REST TAB 26.1.1/26.1.2/26.3 имеет только по 9 измерений, один max составляет более половины их суммы; средние остальных восьми 127.763/87.488/130.712 µs. Это арифметическое описание агрегатов, а не удаление наблюдений. Исходные mean/max сохранены. Причину отдельных пауз (планировщик/JIT/GC) эти счётчики не устанавливают; устойчивую регрессию конкретной версии по ним утверждать нельзя. Все случаи такого доминирования max: [performance-outliers.json](results/phase5/performance-outliers.json). Алгоритмы ради результатов измерений не изменялись.

Дополнительное исследование WSL 26.2 REST: исходный max 266.642 ms сохранён. Повторы с тем же опубликованным SDK при 2/4 CPU и Thread CPU instrumentation: [analysis.json](results/phase5/local-environment/performance-cpu-control/analysis.json).

| Контроль | Active mean µs | Active max ms | Thread CPU у самого длинного span, ms |
|---|---|---|---|
| primary_cpu2 | 202.742 | 266.642 | NOT MEASURED |
| diagnostic_cpu2 | 51.629 | 6.898 | 6.845 |
| diagnostic_cpu4 | 39.864 | 5.025 | 4.933 |

Одинаковый SDK SHA и прежние функциональные assertions проверены отдельно. Дополнительная instrumentation находится только в consumer; основная матрица и исходные source ZIP сохранены. Повторы не устанавливают первопричину старой паузы и не являются обещанием FPS. Cgroup counters и команды сохранены рядом. Семантика измерения и ограничение точности: [JDK ThreadMXBean](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadMXBean.html).

Исходные 266.642 ms не воспроизвелись даже при повторном лимите 2 CPU: max 6.898 ms (Thread CPU 6.845 ms); при 4 CPU max 5.025 ms (Thread CPU 4.933 ms). SDK, consumer, peer и server JAR между двумя контролями побайтно одинаковы. Throttling cgroup присутствует и учитывается накопительными counters с отдельной delta второго контроля; он сам по себе не устанавливает причину старого span. Устойчивую регрессию 26.2 эти данные не подтверждают. Точная причина первоначальной паузы остаётся неустановленной; гарантии worst-case latency и production FPS из fixtures не следуют. Алгоритмы SDK ради измерений не менялись.

Dungeon/TAB/Mine/Hell: `tracker_performance`; entity visits, TextDisplay reads и block reads: `reads` для каждой из 21 сессий. Нулевое число FTE threads — реальная проверка живых потоков после shutdown, а не число полей executor.

## Архитектура и различия API

Один multi-project, общий Java 21 core, общие трекеры/transport. Legacy использует Mojang mappings + `net.fabricmc.fabric-loom-remap`/remapJar, modern — `net.fabricmc.fabric-loom`/обычный unobfuscated JAR. Gradle 9.6.1, Loom 1.17.21, Loader 0.19.5, installer 1.1.2. Полная конфигурация каждого target: `targets.json`.

Реестр: [PHASE5_API_DIFFERENCES.md](PHASE5_API_DIFFERENCES.md). Четыре места несовместимости, 8 небольших leaf-файлов; общие трекеры не копировались. Фактические javap и authlib versions: `results/phase5/signatures`. Исправления только проверочных сценариев: [PHASE5_TEST_FIXES.md](PHASE5_TEST_FIXES.md).

Сравнение с замороженными исходниками 4C: `results/phase5/frozen-sources.json`. API: 36 прежних публичных классов, JVM descriptors и компиляция старого исходника; отдельно production consumer выполняет заранее собранный против старого SDK LegacyApiProbe с новым SDK.

## Упаковка и SHA-256

JAR, sources, POM, Module Metadata, единственная копия core, bytecode, namespace, fabric.mod.json, два accessor mixin, отсутствие внешних библиотек внутри SDK и JIJ consumers проверяются исполняемым `phase5_verify.py --stage artifacts`. Consumer использует только тестовый Maven repository при явном `phase5_repository`, без fallback на Maven Local. Нативная загрузка NESTED/development=false подтверждается отдельно runtime.

| Minecraft | Проверенный SDK JAR SHA-256 |
|---|---|
| 1.21.4 | `0434f67cfda683fb2e1b20b80dcafe0d4b8230d6ef0d95eaf5817ef4752726f8` |
| 1.21.5 | `8bf04d8e1a407fd51e5c9d2042c3fa3b89cd2aeea74ea576734659f9cf5fb6ba` |
| 1.21.6 | `aa21241de594dfbfba5a0282b7060284a787913b651fa2a644d7239929492335` |
| 1.21.7 | `1f1ac4abff1e84310e3e82ec6b5459f9545b91032aa2d1be8e25f790544851ee` |
| 1.21.8 | `9c74bcf8c5a3de0e3a1494ff2c85b0349b04bd91cca5660c30805e221658d144` |
| 1.21.9 | `1d17df8efe2e81f40b107cc9a95f643a745c278988f1f4e1fe83bbebc6f9ac67` |
| 1.21.10 | `7e7c9626f35df97ac2ebac0560f3a8c361a37f900ca9a5711174ac60b83213a8` |
| 1.21.11 | `010fa426784f20097d85ddfa165215086fc154a9101bdc83c94f93596ca8996c` |
| 26.1 | `6b0ffb38fae6e07d4ff858e19194d8936cafc826b55974865d4fda6b27a3f95c` |
| 26.1.1 | `5afdd78727ada97d996de66eaa04d1df32dd7aa3d84f57c6b0dfd0a2d8200bc3` |
| 26.1.2 | `70c221003cdb5e9dc1d4907816ed04fca015a1e0540c6bd007743e8c51bad51e` |
| 26.2 | `ae8db7c7a5d7ff16641a979ab686dbfe0a55145bad3e25bac8d34752ccf940fc` |
| 26.3 | `0c678e9163c3ac8692b1499e0114b9ff19423bc60c9e8a5276b955c4c6d61c0c` |

Все четыре опубликованных файла каждого target сохранены в `results/phase5/artifacts/`; соответствующие publish/result.json содержат их SHA. Прежние экспериментальные версии не перезаписывались. [Integrity](results/phase5/integrity.json) проверяет 76 замороженных SDK исходников, принятые Phase 4 артефакты, прежние Maven Local версии и неизменность tracked release SDK.

Полный SHA-256 список сохранённых evidence и исполняемых исходников: [evidence-manifest.json](results/phase5/evidence-manifest.json). Результат его отдельной проверки: [manifest-verification.json](results/phase5/manifest-verification.json). Manifest создаётся после остановки workers; исключает себя и собственный verification receipt.

Межсистемные SHA: [cross-os.json](results/phase5/cross-os.json). JAR/sources/module сравниваются побайтно. POM сохраняет разные исходные SHA и byte_equal=false при различии только CRLF/LF; допускается исключительно замена CRLF на LF, XML content должен совпадать.


## Дополнительные startup-проверки 26.3

| Среда | Сохранено попыток | Строгий functional PASS | Нормальные client/server exits | Native access violation |
|---|---|---|---|---|
| Windows | 5 | 4 | 4 | 1 |
| native-WSL | 13 | 6 | 13 | 0 |

План: четыре обязательных Linux режима и ещё два полных свежих production REST-запуска. Шесть подтверждённых Linux запусков: PASS. Старые Wayland/process-only результаты остаются в числе попыток, но не в строгом headless PASS. Полные пути и exit verdicts: [native-startup.json](results/phase5/native-startup.json). Native crash status остаётся OPEN независимо от числа успешных повторов.

## Linux / CI

По прямому решению пользователя проверки выполняются только локально в Windows/WSL. Передача SDK на внешний SSH-хост отклонена и не выполнялась. Исторический `phase5_remote.py` запрещает remote actions. До этого сохранялась только подготовка публичного Docker image без кода SDK.

GitHub Actions template: `ci/phase5/matrix.yml`. Отдельные matrix jobs compile/unit → test Maven publication → consumer/packaging/API → network/production runtime → summary. Все 13 целей читаются из targets.json. Actions закреплены SHA, JDK — версиями и SHA256. Отдельный namespace github.run_id исключает использование исторических PASS из checkout. Workflow пока изолирован от release `.github/workflows`; hosted GitHub Actions запуск **NOT RUN**.

Локальная WSL подготовка: свежие source/home/Gradle state на нативной Linux FS. Manifests `local-environment/wsl-native-inputs.json` и при наличии `wsl-native-b-inputs.json` фиксируют хэшированные dependency/raw Minecraft inputs. При использовании native-b он имеет отдельные HOME/Maven/Gradle/project outputs, игровые порты и Xvfb display; immutable dependencies могут использовать hard links. Готовые классы/JAR нового SDK и сгенерированные Loom Minecraft outputs не переносились; legacy SDK JAR/probe намеренно сохранены как compatibility fixtures. Assets перенесены с /mnt/c на native Linux FS с проверкой SHA; неверный prefixed index исключён. Первая медленная попытка на /mnt/c сохранена отдельно. Каждый task cgroup: 6 GiB RAM, 1 GiB swap, 2 CPU, 2048 PIDs; глобальные настройки WSL не изменены. Полный Linux PASS target берётся из одного worker; неполные jobs не смешиваются.

WSL system dpkg оказался незавершённым; системная Java не ремонтировалась. Полные переносимые JDK с desktop JRE извлечены в `/var/tmp/fte-sdk-phase5-01a13db2/tools`. Логи содержат pins/SHA Ubuntu JDK 21.0.12.1/25.0.4.1, Xvfb и Python. Эти локальные JDK отличаются от Temurin pins hosted template; результаты сред не подменяются. Фактические Linux jobs: `results/phase5/wsl/`, а не публичная сборка Docker image.

Headless runtime дополнительно требует сохранённый успешный xdpyinfo receipt. Локальный WSL использует приватный mount namespace, официальный task-local xkbcomp и отдельный X11 socket; WSLg fallback отключён. Глобальные пакеты и JVM не изменены. Первые Java GLFW failures и прежние успешные Linux process runs без доказанного Xvfb сохранены; они не засчитываются как headless PASS. См. PHASE5_TEST_FIXES.md и local-environment/x11-display-probes.json.

## Неуспешные попытки

- `results/phase5/metadata/attempt-01/result.json`: KeyError: data at Minecraft 1.21.9: version.json changed pack_version to data_major/data_minor. Raw metadata retained; metadata reader corrected for attempt-02.
- `results/phase5/setup/attempt-01/result.json`: Target generator assumed per-consumer gradle.properties and wrapper directories, absent by design. Optional copying corrected. Partial 1.21.5/1.21.8 declarations were completed before compilation. A one-off PowerShell di… (полный error в JSON)
- `results/phase5/1.21.10/backend-flags/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/backend-offline/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/backend-rest/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/backend-ws/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/network/attempt-02/result.json`: FAIL
- `results/phase5/1.21.10/runtime-flags/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/runtime-offline/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/runtime-rest/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.10/runtime-ws/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.11/backend-flags/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/backend-offline/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.11/backend-rest/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.11/backend-rest/attempt-02/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/backend-ws/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/backend-ws/attempt-02/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/compile/attempt-01/result.json`: FAIL
- `results/phase5/1.21.11/compile/attempt-02/result.json`: FAIL
- `results/phase5/1.21.11/compile/attempt-04/result.json`: FAIL
- `results/phase5/1.21.11/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.11/runtime-flags/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/runtime-offline/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.11/runtime-rest/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.11/runtime-rest/attempt-02/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/runtime-ws/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.11/runtime-ws/attempt-02/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/1.21.4/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.5/compile/attempt-01/result.json`: FAIL
- `results/phase5/1.21.5/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.6/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.6/network/attempt-02/result.json`: FAIL
- `results/phase5/1.21.7/backend-offline/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.7/backend-rest/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.7/backend-ws/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.7/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.7/runtime-offline/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.7/runtime-rest/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.7/runtime-ws/attempt-01/result.json`: TimeoutError('Fixture server startup timeout')
- `results/phase5/1.21.8/compile/attempt-01/result.json`: FAIL
- `results/phase5/1.21.8/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/1.21.9/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1/backend-flags/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1/backend-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1/backend-ws/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1/compile/attempt-01/result.json`: FAIL
- `results/phase5/26.1/compile/attempt-02/result.json`: FAIL
- `results/phase5/26.1/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1/runtime-flags/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1/runtime-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1/runtime-ws/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1.1/backend-flags/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/backend-offline/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/backend-rest/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/backend-ws/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/runtime-flags/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/runtime-offline/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/runtime-rest/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.1/runtime-ws/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.2/backend-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1.2/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.1.2/network/attempt-02/result.json`: FAIL
- `results/phase5/26.1.2/runtime-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.1.2/runtime-rest/attempt-02/result.json`: SUPERVISOR_CANCELLED_RESOURCE_LIMIT: 95 percent host memory load; third Windows lane will wait for capacity. SDK outcome not claimed.
- `results/phase5/26.2/backend-flags/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.2/backend-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.2/backend-ws/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.2/hardening/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.2/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.2/runtime-flags/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.2/runtime-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "copper": actual={"tab":[{"server_id":401,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡VIP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/26.2/runtime-ws/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.3/backend-flags/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/26.3/network/attempt-01/result.json`: RuntimeError('Fixture server exited with 1')
- `results/phase5/26.3/runtime-flags/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/local-environment/x11-deps/attempt-01/result.json`: FAIL
- `results/phase5/wsl/1.21.5/compile/attempt-01/result.json`: FAIL
- `results/phase5/wsl/native/1.21.5/backend-flags/attempt-01/result.json`: OSError(98, 'Address already in use')
- `results/phase5/wsl/native/1.21.5/backend-offline/attempt-01/result.json`: TimeoutExpired(['/var/tmp/fte-sdk-phase5-01a13db2/native/repo/poc/multiversion/gradlew', '--no-daemon', '--console=plain', '--max-workers=2', '-p', 'consumers/mc1.21.5', 'prodClient', '-Pphase3', '-Pphase5_port=34041', '… (полный error в JSON)
- `results/phase5/wsl/native/1.21.5/backend-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "warden": actual={"tab":[{"server_id":403,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡MVP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/wsl/native/1.21.5/runtime-offline/attempt-01/result.json`: TimeoutExpired(['/var/tmp/fte-sdk-phase5-01a13db2/native/repo/poc/multiversion/gradlew', '--no-daemon', '--console=plain', '--max-workers=2', '-p', 'consumers/mc1.21.5', 'prodClient', '-Pphase3', '-Pphase5_port=34041', '… (полный error в JSON)
- `results/phase5/wsl/native/1.21.5/runtime-rest/attempt-01/result.json`: RuntimeError('java.lang.IllegalStateException: Payload mismatch "warden": actual={"tab":[{"server_id":403,"server_type":"anarchy","players_list":[{"player_name":"Good","donate":"⚡MVP","active":"<ISO8601>"},{"player_name"… (полный error в JSON)
- `results/phase5/wsl/native/1.21.8/backend-ws/attempt-01/result.json`: INTERRUPTED: supervisor restart for verified private Xvfb; SDK outcome not claimed
- `results/phase5/wsl/native/1.21.8/compile/attempt-01/result.json`: INTERRUPTED: local test supervisor restart for TIME_WAIT probe fix; compilation outcome not claimed
- `results/phase5/wsl/native/1.21.8/runtime-ws/attempt-01/result.json`: INTERRUPTED: supervisor restart for verified private Xvfb; SDK outcome not claimed
- `results/phase5/wsl/native/26.3/backend-flags/attempt-01/result.json`: OSError(98, 'Address already in use')
- `results/phase5/wsl/native/26.3/backend-rest/attempt-01/result.json`: OSError(98, 'Address already in use')
- `results/phase5/wsl/native/26.3/backend-rest/attempt-02/result.json`: OSError(98, 'Address already in use')
- `results/phase5/wsl/native/26.3/backend-rest/attempt-03/result.json`: OSError(98, 'Address already in use')
- `results/phase5/wsl/native/26.3/backend-ws/attempt-01/result.json`: OSError(98, 'Address already in use')
- `results/phase5/wsl/native-b/26.1.1/backend-flags/attempt-01/result.json`: INTERRUPTED: supervisor restart for verified private Xvfb; SDK outcome not claimed
- `results/phase5/wsl/native-b/26.1.1/backend-offline/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.1/backend-rest/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.1/backend-ws/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.1/runtime-flags/attempt-01/result.json`: INTERRUPTED: supervisor restart for verified private Xvfb; SDK outcome not claimed
- `results/phase5/wsl/native-b/26.1.1/runtime-offline/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.1/runtime-rest/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.1/runtime-ws/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/backend-flags/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/backend-offline/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/backend-rest/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/backend-ws/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/runtime-flags/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/runtime-offline/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/runtime-rest/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')
- `results/phase5/wsl/native-b/26.1.2/runtime-ws/attempt-01/result.json`: RuntimeError('client exit=1, fresh result=False')

Исходные gradle/client/server/backend logs и JSON находятся рядом с каждой попыткой. Нехватка RAM и прерванный WSL daemon зафиксированы отдельно в `results/phase5/local-environment/resource-budget.json`. Таймауты повторяются без ослабления assertions. Удаляются только регенерируемые игровые inputs/fixture worlds после завершения процессов, с manifest в `local-environment/reclaimed-inputs`; логи, SDK artifacts и crash diagnostics сохраняются.

## Сохранённые продуктовые ограничения

- Bounded best effort: snapshots 64 с coalescing/eviction, важные события 256 FIFO с reject overflow, TTL 15/30 секунд. Бан/координаты могут потеряться при переполнении, истечении TTL, shutdown, смене игровой сессии или неоднозначной ошибке отправки. Подтверждения backend и replay не добавлены.
- Один глобальный runtime: любой consumer может остановить его через shutdown(). Идентичная полная конфигурация разделяет runtime; конфликт ключа/настроек явно отклоняется. Будущие варианты владения: owner handle либо reference-counted leases, после отдельного решения API.
- Jar-in-jar deduplication подтверждена для двух consumer-модов с одной проверяемой версией SDK. Старый бинарный API-клиент выполняется с новым SDK, но это не отдельная проверка разрешения Fabric Loader двух разных SDK JAR версий. Не следует устанавливать экспериментальный poc и release SDK одновременно; упаковка и mod ID окончательного релиза относятся к следующему этапу интеграции.
- Первичный Builder и shutdown требуют клиентский поток. Вызывать Builder в клиентском Fabric entrypoint или через Minecraft.execute; API-ключ и конфигурация другого consumer незаметно не используются.
- SessionFence отменяет неотправленные игровые payload предыдущей сессии. Это соответствует свежести snapshots; для исторических банов/координат означает возможную потерю при reconnect. Пересмотр политики важных событий требует отдельного продуктового решения.
- Exactly-once без серверной идемпотентности не обещается.
- Native startup 26.3 остаётся OPEN, включая ранее подтверждённый сбой без SDK. Успешные функциональные запуски не закрывают расследование. JVM/driver обходы в SDK не добавлялись.
- На этапе 5 native crash повторился в Windows flags attempt-01: 0xC0000005, jvm.dll+0xA7D687. Report.wer и события сохранены, временный dump уже отсутствовал. См. NATIVE_CRASH_INVESTIGATION.md; последующий PASS не отменяет этот FAIL.
- Реальный FunTime backend/сервер не использовался; protocol проверяется локальным fake backend и фактическими Fabric production callbacks. Production-ready пока не заявляется.

## Воспроизведение

Из `poc/multiversion` (в этой среде shell-команды выполняются через `rtk proxy`):

```text
python phase5_build.py --targets all --kind compile
python phase5_build.py --targets all --kind publish
python phase5_build.py --targets all --kind consumer
python phase5_verify.py --targets all --stage artifacts
python phase5_api.py --targets all
python prepare_phase4_tls.py
python phase5_run.py --targets all --kind network
python phase5_verify.py --targets all --stage network
python phase5_run.py --targets all --kind runtime
python phase5_verify.py --targets all --stage runtime
python phase5_report.py
python phase5_integrity.py
python phase5_cross_os.py
python phase5_perf.py
python phase5_perf_control.py
python phase5_headless_check.py
python phase5_final_verify.py --workers-finished
python phase5_manifest.py
python phase5_manifest.py --verify
python phase5_runtime_resume.py --targets all --min-free-mib 3072
```

Повторный publish той же уже существующей Maven Local версии намеренно запрещён. Для воспроизведения использовать отдельный HOME/Maven repository или новую явно выбранную экспериментальную версию; существующие файлы не удалять. WSL: `phase5_wsl.py --action prepare`, затем `--action run --kind compile|publish|consumer|network|runtime --targets <MC>`. Linux bootstrap и отдельные CI stages: `ci/phase5/`.
