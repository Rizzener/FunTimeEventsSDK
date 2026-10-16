# Отдельная диагностика native startup failure 26.3

Это не изменение трекеров, REST/WSS/SSE или release SDK.
Дата: 15 октября 2026. Итог: **сбой воспроизведён без SDK; точная native
первопричина остаётся открытой**.

## Подтверждённое

- Phase 2 имел startup exit `0xC0000409`, до JOIN; Java hs_err тогда не найден.
- Phase 3, [26.3 attempt-03](results/phase3/26.3/attempt-03/result.json), снова завершился до JOIN, но с **0xC0000005**.
- Raw Gradle/client log указывает реальный executable `C:\jdk-25.0.4\bin\java.exe`.
- Windows Application Error **1000** и WER **1001** подтверждают PID **13324**,
  faulting module `jvm.dll` version 25.0.4.0, offset **0xA7D687**.
- JDK release: **Eclipse Adoptium Temurin 25.0.4+7-LTS**, HotSpot, Windows x86_64.
- Последняя стадия лога — resource/font loading, до первого игрового JOIN и scan.
  Это устанавливает стадию сбоя; по последней строке нельзя установить виновный код.
- Сервер этой попытки завершён штатно, client result отсутствует; попытка помечена
  FAIL и не включается в успешную матрицу payload.
- На том же Temurin native 0xC0000005 повторился в
  [attempt-05](results/phase3/26.3/attempt-05/result.json), offset **0x43C713**, и
  [attempt-06](results/phase3/26.3/attempt-06/result.json), offset **0xA7D687**.
- Отдельный диагностический выбор установленного Azul **25.0.1** дал полный
  функциональный PASS в attempt-07, но затем
  [native failure в attempt-09](results/phase3/26.3/attempt-09/result.json),
  `jvm.dll+0x449D93`. Смена поставщика **не является исправлением**.
- Default launcher проекта не заменён на Azul. Окончательные attempt-12/13/14
  прошли на исходном Temurin25 с полными fixtures и штатным выходом.

Разные offsets и разные коды Phase2/3 не доказывают общую первопричину.

## Контроль без SDK и захваченный dump

`run_native_diagnostics.py --baseline --attempts 3` запускает production Minecraft
26.3 + Fabric Loader + официальный Fabric API. **SDK JAR и consumer JAR исключены**
из `fabric.addMods`; их нет в фактическом Loader mod list. TrackerSliceAPI не
запускается. Приложение остаётся на экране startup/menu; это отдельный native
контроль, не функциональная проверка трекеров.

| Контроль | SDK / consumer в mod list | Результат |
| --- | --- | --- |
| [no-sdk-143944](results/phase3/native-investigation/no-sdk-20261015-143944/result.json), PID8648 | false / false | Дожил до окна 45 с, контролируемо остановлен; thread dump сохранён |
| [no-sdk-144035](results/phase3/native-investigation/no-sdk-20261015-144035/result.json), PID19044 | false / false | **Native 0xC0000005**, захвачен minidump |
| [no-sdk-144104](results/phase3/native-investigation/no-sdk-20261015-144104/result.json), PID5232 | false / false | Дожил до окна 45 с, контролируемо остановлен; thread dump сохранён |

В `no-sdk-144035` runner успел сохранить WER minidump до удаления Windows.
Сначала прочитан MDMP MiscInfo и проверен PID19044: посторонние dumps не копируются.
Результат независимого stdlib-разбора —
[no-sdk-minidump.json](results/phase3/native-investigation/no-sdk-minidump.json):

| Поле | Значение |
| --- | --- |
| Размер | 1 399 988 байт |
| SHA256 | ca46417292770a1f1e085f0cf5554e3ded97b5bd387840cf037df57fcfa03175 |
| Process ID | 19044 |
| Exception thread | 4776, **Worker-Main-4** |
| Exception code | **0xC0000005** |
| Instruction address | 0x7ff9fbd9d687 |
| Module / offset | **jvm.dll / 0xA7D687** |

Это совпадает с module+offset+code attempt-03/06 на том же Temurin.
**Для воспроизведения этой сигнатуры загрузка SDK не требуется.** Это наблюдаемое
отделение сбоя от SDK, а не доказательство конкретного бага JVM, Fabric или драйвера.
Полная native stack с символами ещё не получена. Поиск официального Adoptium
debugimage через API вернул HTTP403; экспортные символы DLL недостаточны для
определения внутренней функции по этому offset. Имя функции не угадывалось.

Raw dump оставлен локально:
`.evidence/native-dumps/no-sdk-20261015-144035/`
`WER.86553cce-6297-4896-969d-8d660e63c696.tmp.dmp`.
Каталог исключён из Git; он не входит в Maven JAR и не отправлялся наружу.
Логи, разобранные поля и SHA сохранены в `results/phase3/native-investigation`.
Два выживших baseline процесса **не считаются штатно завершившимися PASS**:
runner остановил только принадлежащие ему process trees после окна наблюдения.

## Дополнительные startup-контроли с ProcDump

Три startup smoke существующей consumer-ветки, где SDK загружен, но трекеры не
инициализируются, завершились штатно: `startup-20261015-141150`, `141211`, `141231`.
Сводка всех шести контролей —
[controls-summary.json](results/phase3/native-investigation/controls-summary.json).

Microsoft ProcDump запускался с `-e -mm <PID> <directory>`: unhandled exception,
mini dump. Подпись проверена: Microsoft Corporation, **Status=Valid (0)**;
[источник/SHA](results/phase3/native-investigation/procdump-origin.json) и
[Authenticode](results/phase3/native-investigation/procdump-signature.json) сохранены.
Присоединение — только к java.exe с уникальным диагностическим флагом этого runner.
Системный postmortem debugger/registry не настраивался. Изменение PSModulePath для
проверки Authenticode касалось только дочернего Windows PowerShell процесса.

В логах успешных debugger-контролей есть first-chance exceptions c0000005.
Они **не выданы за fatal crash**: процессы завершились нормально, unhandled dump
не возник. Debugger меняет timing, поэтому эти три успеха не входят в обычную
payload/performance-матрицу и не опровергают сбой baseline без debugger.

## Сохранённая диагностика

- [Raw client attempt-03](results/phase3/26.3/attempt-03/client.txt), [server](results/phase3/26.3/attempt-03/server.txt).
- [Application Error/WER events](results/phase3/26.3/attempt-03/windows-java-events.txt).
- [Оригинальный Report.wer attempt-03](results/phase3/native-investigation/Report.wer), UTF-16; отдельные WER reports также лежат в attempt-05/06.
- [JDK release / java -version](results/phase3/native-investigation/jdk-release.txt), [SHA бинарных JVM](results/phase3/native-investigation/jvm-binary-hashes.json).
- [Raw baseline client без SDK](results/phase3/native-investigation/no-sdk-20261015-144035/client.txt), [Windows events](results/phase3/native-investigation/no-sdk-20261015-144035/windows-java-events.txt).
- [Minidump exception/module/thread](results/phase3/native-investigation/no-sdk-minidump.json); сам dump локально в `.evidence`, путь выше.
- `client.txt`, `result.json`, `windows-java-events.txt`, thread/ProcDump logs рядом с каждым контролем; исходные bytes не заменяются форматированным отчётом.

Для attempt-03 исходный WER временный dump уже был удалён Windows; hs_err не
создан. Позднейший автоматический захват baseline закрыл именно недостаток dump,
не установил окончательную первопричину.

WER loaded-module list содержит LWJGL 3.4.3-snapshot / SDL 3.4.14, NVIDIA OpenGL
595.71 (RTX3050), OBS `graphics-hook64.dll` и NVIDIA `nvspcap64.dll`. Присутствие —
**не доказательство причины**. Настройки OBS, overlay, драйвера, Windows и JIT не
менялись. Perflib/JNA и offline authentication401 присутствуют также в успешных
запусках и без причинного эксперимента не объясняют fatal exit.

## Воспроизведение

```powershell
rtk proxy python -X utf8 run_native_diagnostics.py --attempts 3 --procdump .evidence/native-tools/procdump64.exe
rtk proxy python -X utf8 run_native_diagnostics.py --baseline --attempts 3
rtk proxy python -X utf8 inspect_native_dump.py .evidence/native-dumps/no-sdk-20261015-144035/WER.86553cce-6297-4896-969d-8d660e63c696.tmp.dmp --output results/phase3/native-investigation/no-sdk-minidump.json
```

Каждый новый прогон создаёт свой каталог. Отсутствие ProcDump можно компенсировать
baseline WER capture; этот вариант фактически и дал сохранённый crash dump.

## Статус

Подтверждено повторение **вне SDK** и сохранён exception context. Точная внутренняя
функция и причинный компонент не установлены. Следующий диагностический шаг —
символизированная native stack и контролируемые сравнения runtime/graphics
environment. Это отдельная работа; её предполагаемый результат не подменяет
факты текущего этапа. Native stability остаётся открытым вопросом окружения.
Скрытого требования использовать Azul, отключить JIT/overlay или изменить драйвер
в SDK нет. Функциональные успехи не стирают четыре native FAIL основного runner.

## Повторение на этапе 4 — OPEN

В `results/phase4/4C/26.3/attempt-02` production WSS-клиент с настоящим публичным
Builder завершился во время resource reload, до игрового JOIN. Gradle сохранил
`-1073741819 / 0xC0000005`. Windows Application Error/WER снова фиксируют
`jvm.dll 25.0.4.0`, offset **0xA7D687**, Report ID
`30215a5d-8563-4219-a7d1-b39c5a0a5b30`.

- [Raw client](results/phase4/4C/26.3/attempt-02/client.txt).
- [Windows events](results/phase4/4C/26.3/attempt-02/windows-java-events.txt).
- [Команды, hashes артефактов и результат FAIL](results/phase4/4C/26.3/attempt-02/result.json).

Это тот же наблюдаемый module/offset, что у ранее исследованного baseline без SDK;
первопричина по этому совпадению не устанавливается. Новый dump этой попытки не
захвачен. Старый baseline minidump сохранён, как описано выше. Debugger/JIT/driver
настройки ради обхода не менялись. Последующие функциональные прогоны не закрывают
этот дефект. Неуспешный attempt-03 относится к гонке ожидания callback в consumer
fixture и имеет Java assertion, а не native crash; подробности в PHASE4_RESULTS.md.

## Повторение на этапе 5 — OPEN

`results/phase5/26.3/runtime-flags/attempt-01` завершился при resource reload
2026-10-15 15:56:32 UTC, до JOIN. Java 25.0.4, Gradle exit 1, дочерний процесс
`-1073741819 / 0xC0000005`. Application Error/WER снова фиксируют `jvm.dll`,
offset **0xA7D687**, Report ID `f1aa8214-046b-43ee-95e6-c3757d7aaa33`.

- Raw client, Windows events, команды, consumer/server JAR и hashes сохранены в каталоге попытки.
- `native-capture.json` фиксирует наличие или отсутствие исходного WER/dump и SHA сохранённых файлов.
- Сбой произошёл при одновременных локальных проверках и высоком использовании RAM. Это контекст, **не установленная причина**.
- Предыдущие offline/REST/WSS запуски этого target завершились нормально. После сохранённого native FAIL повторный Windows flags `attempt-02` прошёл полный fixture и штатно завершился; это подтверждает функциональность, но не закрывает startup defect.
- Настройки JVM, JIT, graphics driver и overlay не менялись. Совпадение module/offset с baseline без SDK не устанавливает внутреннюю функцию или причинный компонент.

Функциональные результаты последующих запусков и локальной Linux матрицы учитываются отдельно. Статус native startup остаётся **OPEN**.

## Этап 5: дополнительные локальные Linux starts — OPEN

На той же физической машине, в Ubuntu 26.04 / WSL2 с переносимым Ubuntu OpenJDK 25 и Mesa software rendering, выполнены шесть полных production client/server запусков 26.3:

| Режим | Каталог внутри `results/phase5/wsl/native/26.3` | Строгий результат |
|---|---|---|
| Offline | `runtime-offline/attempt-03` | PASS |
| REST | `runtime-rest/attempt-04` | PASS |
| WSS | `runtime-ws/attempt-02` | PASS |
| Disable flags | `runtime-flags/attempt-02` | PASS |
| Дополнительный REST | `runtime-rest/attempt-05` | PASS |
| Дополнительный REST | `runtime-rest/attempt-06` | PASS |

У каждой попытки свой JVM и тестовый сервер, нормальные exits, 21 JOIN/DISCONNECT, прежние golden payload и сохранённые исходные logs. Xvfb подтверждён через реальный `xdpyinfo`, приватный mount namespace/socket и исключённый WSLg fallback. Сам клиент пишет `Created window using SDL video driver: x11`; актуальный verifier требует эту строку. Итоги и пути всех успешных/неуспешных попыток: [native-startup.json](results/phase5/native-startup.json).

Прежние Linux process successes без Xvfb receipt использовали Wayland; они сохраняются, но не засчитываются как эти шесть headless запусков. Отказы Java GLFW/X11 у других targets относятся к подготовке тестового окружения и исследованы в PHASE5_TEST_FIXES.md. Они не подменяют Windows native access violation.

Это другая ОС и graphics stack на том же компьютере, **не независимая физическая машина**. Hosted CI не запускался: пользователь разрешил только локальные Windows/WSL проверки. Никаких JVM/JIT/driver обходов в SDK не добавлено. Шесть успехов Linux не доказывают исправление периодического Windows crash; статус остаётся **OPEN**.

## Официальный источник диагностики (ссылка)

[Microsoft ProcDump](https://learn.microsoft.com/en-us/sysinternals/downloads/procdump)
описывает захват dump по unhandled exception и присоединение по конкретному PID.
Информация о фактическом сбое берётся из сохранённых локальных событий Windows,
а не из предположения о похожем issue в интернете.
