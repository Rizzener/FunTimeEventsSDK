# Результаты изолированного multi-version PoC

Дата: **2026-10-15**. Результаты получены реальными сборками и запусками на Windows.

## Вывод

**PASS:** один Gradle multi-project одновременно собирает Minecraft 1.21.4
с Mojang mappings/remapJar и Minecraft 26.3 без mappings/с обычным jar.
Один и тот же исходник платформы работает в обеих production-средах.
Core физически включён в SDK; опубликованные Maven-артефакты работают из
jar-in-jar двух независимых consumer-проектов.

Это подтверждает техническую основу предлагаемой архитектуры, но не перенос
существующих трекеров и не поддержку всего диапазона Minecraft.

## Изоляция

- Все новые исходники и конфигурация находятся в `poc/multiversion`.
- SDK group: `net.funtimeevents.poc`; version: `0.0.1-poc`.
- Target artifacts: `mc1.21.4`, `mc26.3`.
- SDK mod ID: `fte-multiversion-poc`; consumer IDs: `poc-consumer-1214`, `poc-consumer-263`.
- Публикация выполнена в стандартный Maven Local: `%USERPROFILE%/.m2/repository`.
- Не публиковались координаты `net.funtimeevents:fte-api`, JitPack или удалённый Maven.
- `git diff --exit-code -- fte-api build.gradle.kts settings.gradle.kts gradle.properties gradle jitpack.yml README.md DOC.md AGENTS.md` завершился с кодом 0.
- Исходный release Gradle build не запускался; трекеры и сетевые клиенты не переносились.

Пользовательская инструкция на PoC явно разрешает проверку multi-project,
поэтому для этого каталога исследуется альтернатива категорическому запрету
из существующего AGENTS.md. Старый SDK и consumer workflow не менялись.

## Окружение и зависимости

| Компонент | Значение |
| --- | --- |
| ОС | Windows |
| Gradle wrapper | 9.6.1, собственная копия wrapper внутри PoC |
| JVM Gradle | Temurin 25.0.4 |
| Java toolchains | 21 для core/legacy; 25 для modern |
| Loom | 1.17.21, одна версия реализации плагина |
| Legacy plugin | `net.fabricmc.fabric-loom-remap` |
| Modern plugin | `net.fabricmc.fabric-loom` |
| Fabric Loader | 0.19.5 в обеих целях/consumer |
| Fabric API 1.21.4 | 0.113.0+1.21.4, только в consumer |
| Fabric API 26.3 | 0.163.0+26.3, только в consumer |
| Python | 3.12, независимый анализ артефактов и логов |

Оба Loom-модуля были сконфигурированы и собраны **одним запуском Gradle**,
без перезаписи properties, отдельного Maven core, Python build-orchestrator
или изолированного build на каждую SDK-цель.

## Матрица проверок

| Проверка | 1.21.4 | 26.3 | Доказательство |
| --- | --- | --- | --- |
| Один multi-project и две Loom-конфигурации | PASS | PASS | `build-publish.log`, финальный `final-build-publish.log` |
| Компиляция одного общего исходника платформы | PASS | PASS | `platform/common/.../PocEventsAPI.java`; обе compileJava выполнены |
| Чистый core без Minecraft/Fabric | PASS | PASS | Анализ class references; выполнение `verifyCore` на Java 21 |
| Java bytecode платформы | PASS: major 65 | PASS: major 69 | `results/artifact-checks.json` |
| Java bytecode включённого core | PASS: major 65 | PASS: major 65 | `results/artifact-checks.json` |
| Mappings и итоговый namespace | PASS: Mojmap → Intermediary | PASS: no mappings → official | Bytecode references и production runtime |
| Выходной task | PASS: remapJar | PASS: jar | Логи SDK-сборки |
| Maven Local JAR/POM/module/sources | PASS | PASS | `net/funtimeevents/poc/mc<version>/0.0.1-poc` |
| Нет dependency на core в POM/module | PASS | PASS | Полный анализ обоих форматов metadata |
| Точный Minecraft и требование Java в mod metadata | PASS | PASS | `fabric.mod.json` после expand |
| Независимый consumer build только через Maven | PASS | PASS | У consumer собственный settings; SDK подключён строкой координат |
| Один SDK внутри consumer JAR | PASS | PASS | `jars` в consumer metadata, ZIP-анализ |
| Вложенный SDK совпадает с опубликованным | PASS | PASS | SHA-256 nested JAR == Maven Local JAR |
| Core не распакован непосредственно в consumer | PASS | PASS | ZIP-анализ |
| Production client, development=false | PASS | PASS | Положительные runtime-маркеры |
| Loader обнаружил SDK как NESTED | PASS | PASS | Проверка `ModOrigin.Kind.NESTED` внутри consumer |
| Повторный Builder возвращает тот же экземпляр | PASS | PASS | Runtime-assertion до регистрации tick callback |
| Вызов Minecraft API из общей платформы | PASS | PASS | Возвращён ожидаемый результат `probe` |
| 20 клиентских тиков и нормальное завершение | PASS | PASS | `RUNTIME_PASS`, `Stopping!`, успешный Gradle exit |

Автоматический анализ артефактов: **44 PASS, 0 FAIL**.
Автоматический анализ runtime: **8/8 условий для каждой цели**.
Core check проверяет недопустимую конфигурацию и контракт обработки строки;
это dependency-free Java harness, а не полноценный unit-test suite SDK.

## Положительные маркеры production run

1.21.4:

```text
[FTE-POC] INITIALIZED namespace=intermediary java=21 origin=NESTED development=false probe=core[PoCConsumer/1]:shared-platform-ok
[FTE-POC] RUNTIME_PASS ticks=20 namespace=intermediary
BUILD SUCCESSFUL
```

26.3:

```text
[FTE-POC] INITIALIZED namespace=official java=25 origin=NESTED development=false probe=core[PoCConsumer/1]:shared-platform-ok
[FTE-POC] RUNTIME_PASS ticks=20 namespace=official
BUILD SUCCESSFUL
```

Не использовался `runClient`: `prodClient` имеет тип Loom
`ClientProductionRunTask`, загружает готовый consumer `remapJar`/`jar` и отдельный
production Fabric API. Standalone SDK JAR не добавляется в список production mods:
его единственный источник — вложение consumer. Проверка `origin=NESTED` обязательна.

Логи содержат время хоста. Для дат отчёта использована дата сессии пользователя;
времена хоста не интерпретируются как локальное время пользователя.

## Ошибки и обходные решения

| Попытка / проблема | Результат и решение |
| --- | --- |
| Первый wrapper launch в sandbox | `FileNotFoundException` / access denied на `.gradle/wrapper/...zip.lck`. Повтор с разрешённым доступом к стандартному пользовательскому Gradle cache; файлы release SDK не затрагивались. |
| Первая сборка обоих consumer | `unable to resolve class net.fabricmc.loom.task.prod.ClientProductionRunTask` в подключаемом Groovy-скрипте. Причина — classpath applied script. Регистрация типизированного task перенесена в каждый маленький Kotlin target build; общая настройка task остаётся в consumer.gradle. Обе сборки затем прошли. |
| Первая версия core verification Gradle DSL | Delegate-формы `sourceSets.creating`/`tasks.registering` вызвали deprecation предупреждения Gradle 9.6. Заменены на `sourceSets.create`/`tasks.register`; финальная сборка без этих предупреждений. |
| Чтение build log с предположением UTF-16 | `UnicodeError`: Tee-Object текущего PowerShell записывает UTF-8. Runtime verifier определяет UTF-16 по BOM, иначе читает UTF-8. |
| 26.3: `Mappings not present!` | Информационная строка Loader для необфусцированного Minecraft. Не ошибка и не причина добавлять `officialMojangMappings()` в modern target. |
| 26.3: restricted native access и deprecated Unsafe от LWJGL | JVM warnings сторонней библиотеки. Зафиксированы без suppress-флагов; оба runtime-условия и выход успешны. При production launcher нужно рассмотреть документированные native-access flags отдельно. |
| 26.3: Windows Perflib/Pdh warnings/errors | Не найдены English performance counters, error 0x80070057/0xC0000BB8. Ошибка окружения при сборе системной информации; registry не менялся. На загрузку SDK и 20 тиков не повлияло. |
| 26.3: user properties / Realms auth errors | Использован фиктивный offline accessToken `0`; Minecraft Services не авторизовал профиль, Realms не смог разобрать JWT. Это ожидаемое ограничение локального smoke test. Настоящие credentials не использовались; тест не проверяет online account services. |
| Начальные команды разведки окружения | PowerShell потребовал кавычки у путей с пробелами. Для поиска JDK использован Python glob. На итоговую сборку не влияет. |

В исходных логах не скрывались ERROR/WARN сторонних подсистем.
`results/runtime-checks.json` сохраняет их отдельно от положительных маркеров.
PASS означает прохождение конкретных утверждений PoC, а не отсутствие любой
строки ERROR во всём Minecraft.

## Фактически выполненные команды и логи

Все команды запускались из `poc/multiversion` через `rtk proxy`.

| Команда | Итог | Лог в `.evidence/` |
| --- | --- | --- |
| `gradlew.bat pocBuild pocPublishLocal --console=plain --stacktrace` | PASS, первый прогон 1m40s | `build-publish.log` |
| consumer 1.21.4: `build` (первая попытка) | FAIL, исправлен applied script classpath | `consumer-1214-build-initial.log` |
| consumer 26.3: `build` (первая попытка) | FAIL, та же причина | `consumer-263-build-initial.log` |
| `gradlew.bat -p consumers/mc1.21.4 build` | PASS, 1m14s | `consumer-1214-build.log` |
| `gradlew.bat -p consumers/mc26.3 build` | PASS, 33s | `consumer-263-build.log` |
| `gradlew.bat :core:verifyCore` | PASS, Java 21; затем DSL warnings исправлены | `core-check.log` |
| `python verify_artifacts.py` | PASS, 44/44 | `results/artifact-checks.json` |
| `gradlew.bat -p consumers/mc1.21.4 prodClient` | PASS, 35s | `consumer-1214-production.log` |
| `gradlew.bat -p consumers/mc26.3 prodClient` | PASS, 34s | `consumer-263-production.log` |
| `python verify_runtime.py` | PASS, обе цели | `results/runtime-checks.json` |
| `gradlew.bat pocBuild pocPublishLocal` (финальный) | PASS, 2s, core check выполнен | `final-build-publish.log` |

Длительности не являются benchmark: пользовательские Gradle-кеши были доступны,
некоторые Minecraft/Fabric зависимости могли уже присутствовать. Холодный CI и
JitPack, configuration cache и одновременный запуск всех 13 целей не проверялись.
Полные логи остаются локально в игнорируемом `.evidence/`; компактные JSON-результаты
и этот отчёт входят в исходники PoC.

## Архитектурные выводы

1. Для проверенных двух целей не нужны полностью отдельные SDK builds.
2. Разные JVM target и пространства имён сосуществуют с одной реализацией Loom 1.17.21.
3. Прямые вызовы части Minecraft API можно делить как исходники между 1.21.4 Mojmap и 26.3.
4. Maven publication из Loom `components.java` выбрала правильный remap/non-remap artifact.
5. `compileOnly core` + включение собственного core output исключили транзитивную вторую копию core.
6. Consumer development dependency и физическое включение — разные настройки: для доставки нужен `include`.
7. Applied script оказался достаточен для общих правил PoC; при расширении production-проекта типизированный convention plugin удобнее, чем динамическое применение скриптов.
8. ServiceLoader, runtime-определение адаптера и рефлексия Minecraft для этого эксперимента не потребовались.

## Что не доказано и что делать дальше

- Не переносились существующий Builder, модели, трекеры и сеть. Фасад PoC имеет другой пакет/имя специально для изоляции.
- Нет проверки JOIN/DISCONNECT, миров, scoreboard, TAB, boss bar, hover, данжей или FunTime: production smoke test выполняется до входа на сервер.
- Не проверялось совместное использование SDK двумя модами одной версии Minecraft; здесь два независимых consumer **для разных целей**.
- Не проверялся resolver при разных версиях SDK, несовместимой конфигурации или старом standalone JAR.
- Нет доказательства полной совместимости общих исходников для всех остальных 11 релизов.
- Не проверялась публикация JitPack/remote Maven и холодный Linux CI; Windows graphics runtime не заменяет такой прогон.
- Оценка потоков и transport в `NETWORK_REVIEW.md` статическая; исправления специально не смешаны с PoC.

Следующий обоснованный шаг — узкий перенос одного реального пути (например, chat
message + hover) и lifecycle на эти две цели с runtime fixtures. Массовый перенос
всего SDK и заявление поддержки 13 релизов данным PoC не оправдываются.

## Источники

- [Fabric Loom: plugin IDs, dependencies, include](https://docs.fabricmc.net/develop/loom/).
- [Fabric: porting to 26.1](https://docs.fabricmc.net/26.1.2/develop/porting/).
- [Loom production run tasks](https://docs.fabricmc.net/develop/loom/production-run-tasks).
- [Исходники ClientProductionRunTask](https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/task/prod/ClientProductionRunTask.java).
- [Исходники AbstractProductionRunTask](https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/task/prod/AbstractProductionRunTask.java).
