# FunTimeEventsSDK: изолированный multi-version PoC

**Текущий этап 4:** настоящий FunTimeEventsAPI/Builder, все игровые трекеры,
REST/WSS/SSE/Captcha и отдельные исправления потоков/очередей/lifecycle.
Версия кандидата `0.0.9-phase4c`, только Minecraft 1.21.4/Java21 и 26.3/Java25.
См. [PHASE4_RESULTS.md](PHASE4_RESULTS.md), [принятый этап 3](PHASE3_RESULTS.md)
и [native диагностику, OPEN](NATIVE_CRASH_INVESTIGATION.md).

## Воспроизведение этапа 4

Нужны Python3 с зависимостями fixture, JDK21/25, доступ к Maven/Fabric/Mojang и
Windows desktop для production клиента. Настоящие ключи/backend не используются.
Команды выполняются из этого каталога; клиент открывает настоящее игровое окно.

```powershell
rtk proxy python -m pip install -r fixtures/requirements-phase4.txt
rtk proxy python -X utf8 prepare_phase4_tls.py
rtk proxy python -X utf8 restore_phase4_maven.py --version 0.0.9-phase4c
rtk proxy python -X utf8 compile_legacy_probe.py
rtk proxy python -X utf8 phase4_build.py --stage c --consumers-only
rtk proxy python -X utf8 run_phase4.py --stage c --target all --mode both
rtk proxy python -X utf8 run_phase4.py --stage c --target all --mode rest --flags
rtk proxy python -X utf8 run_phase4_network.py --stage c --kind parity
rtk proxy python -X utf8 run_phase4_network.py --stage c --kind hardening
rtk proxy python -X utf8 run_phase4_network.py --stage c --kind faults
rtk proxy python -X utf8 run_phase4_network.py --stage c --kind late
rtk proxy python -X utf8 run_phase4_network.py --stage c --kind config
rtk proxy python -X utf8 verify_network_parity.py --stage c
rtk proxy python -X utf8 verify_phase4.py --stage c
rtk proxy python -X utf8 verify_artifacts.py --version 0.0.9-phase4c --output results/phase4/4C/artifact-checks.json
rtk proxy python -X utf8 verify_phase4_compat.py --version 0.0.9-phase4c
rtk proxy python -X utf8 verify_phase4_sources.py
rtk proxy python -X utf8 collect_phase4_evidence.py
rtk proxy python -X utf8 verify_phase4_final.py
```

Production runners используют порты 28414/28633 и 29840/29841. Standalone
parity/hardening — 30040/30041, faults/late — 29940/29941. Не запускайте два
runner с одинаковыми портами одновременно. Backend HTTPS/WSS доверен только
тестовому JVM через отдельный truststore с копией стандартных roots. Private key
создаётся заново в игнорируемом `.evidence`; в SDK/отчётные артефакты не включён.

Frozen Maven JAR/POM/module/sources и SHA находятся в `results/phase4/artifacts`.
Restore отказывается перезаписывать отличающийся локальный артефакт. Для **новой
сборки** текущих исходников используйте новую экспериментальную версию, например:

```powershell
rtk proxy python -X utf8 phase4_build.py --stage c --version 0.0.10-phase4c-local
rtk proxy python -X utf8 run_phase4.py --stage c --version 0.0.10-phase4c-local --target all --mode both
```

Не пересобирайте исторические 4A/4B из текущего transport под старыми версиями.
Production 4A/4B можно повторить с retained Maven JAR через `restore_phase4_maven`
и соответствующий `run_phase4 --stage a/b`. Standalone baseline 4B также повторяется
без пересборки его SDK, с hash-verified retained JAR первым в test classpath:

```powershell
rtk proxy python -X utf8 run_phase4_network.py --stage b --kind parity --frozen
rtk proxy python -X utf8 verify_network_parity.py --stage b
```

Аналогично доступны `--kind faults/late --frozen`. В логе CodeSource показывает
реальный baseline JAR. Без `--frozen` runner требует совпадение текущих source с
версией, чтобы не выдать код 4C за baseline 4B. Historical source archives также
сохранены, но для повторной проверки transport достаточно frozen JAR.

## Исторические этапы

Этап 3: реальные Dungeon/HellMap/Tab/Mine/EventCoordinates вместе с Ban/ServerContext,
SDK `0.0.6-phase3`. Следующие команды относятся к исходникам этого этапа:

```powershell
rtk proxy .\gradlew.bat pocBuild pocPublishLocal :mc1.21.4:remapServerFixtureJar :mc26.3:serverFixtureJar --console=plain --max-workers=2
rtk proxy python -X utf8 phase3_port_spec.py
rtk proxy python -X utf8 run_phase3.py --target all --repeats26 3
rtk proxy python -X utf8 verify_artifacts.py --version 0.0.6-phase3 --output results/phase3-artifact-checks.json
rtk proxy python -X utf8 verify_phase3.py
```

Все попытки сохраняются в новых каталогах. Native startup failures не стираются
повторными успехами. Default JVM не заменяется диагностической опцией
`--vendor AZUL`; сравнение поставщиков явно записывается в результат.

Ниже сохранены инструкции предыдущих этапов. Текущая версия сборки — 0.0.9-phase4c.

**Второй этап:** см. [PHASE2_RESULTS.md](PHASE2_RESULTS.md). Экспериментальная
версия — `0.0.4-phase2`: BanTracker + ServerContext + реальные lifecycle/message
fixtures. Исторический отчёт первого этапа сохранён в `RESULTS.md`.

Исторические команды этапа 2 для соответствующей версии исходников
(текущий build публикует уже 0.0.9-phase4c):

```powershell
rtk proxy .\gradlew.bat pocBuild pocPublishLocal :mc1.21.4:remapServerFixtureJar :mc26.3:serverFixtureJar --console=plain
rtk proxy python -X utf8 verify_source_parity.py
rtk proxy python -X utf8 run_phase2.py --target all
rtk proxy python -X utf8 verify_artifacts.py --version 0.0.4-phase2 --output results/phase2-artifact-checks.json
rtk proxy python -X utf8 verify_phase2.py
```

`run_phase2.py` запускает настоящие локальные offline Fabric-серверы и production
клиенты. Серверы привязаны к `127.0.0.1`, порты 27414/27633; перед воспроизведением
они должны быть свободны. Тесты соединяются только с loopback. Ожидаемое автоматическое
завершение обоих серверов и клиентов проверяется runner. Логи и JSON сохраняются
в `results/phase2/<version>/`. Python здесь управляет интеграционным прогоном,
а компиляция, упаковка и публикация остаются Gradle-задачами.

Ниже — описание исходного smoke test первого этапа. Его версия артефактов была
`0.0.1-poc`; старые результаты не заменяются результатами второго этапа.

Эксперимент проверяет сборку и доставку библиотеки, а не совместимость трекеров.
Существующий `fte-api` не участвует в сборке. Новые артефакты публикуются только
в группе `net.funtimeevents.poc`, версия `0.0.1-poc`, mod ID `fte-multiversion-poc`.
Они не заменяют релизные координаты или mod ID `fte-api`.

## Состав

- `core`: независимый Java 21 код и исполняемая проверка контракта.
- `platform/common`: один исходник фасада/Builder с прямыми вызовами
  `Minecraft.getInstance()` и `Component.literal(...).getString()`.
- `targets/mc1.21.4`: Java 21, official Mojang mappings, remapping Loom.
- `targets/mc26.3`: Java 25, unobfuscated Minecraft, non-remapping Loom.
- `gradle/target.gradle`: общие правила компиляции, упаковки и публикации.
- `consumers/mc1.21.4` и `consumers/mc26.3`: два самостоятельных Gradle roots;
  нет project dependency или includeBuild на SDK. Только Maven dependency.
- `consumers/common`: общие правила и runtime smoke test обоих consumer-модов.
- `verify_artifacts.py`: bytecode, namespaces, metadata, POM, sources, jar-in-jar.
- `verify_runtime.py`: проверка положительных маркеров production-запусков.
- `results/`: сохранённые результаты проверки артефактов и запуска.
- `RESULTS.md`: итоги, ошибки, обходные решения и пределы выводов.
- `NETWORK_REVIEW.md`: отдельная статическая оценка текущего сетевого SDK.

Общий core физически включается в target JAR; не публикуется как обязательная
транзитивная зависимость. Consumer включает весь target через Loom `include`.
Fabric API и Loader не включаются в SDK JAR; в production smoke test Fabric API
передаётся отдельно через `productionRuntimeMods`.

## Требования

- Доступные JDK 21 и 25; Gradle wrapper 9.6.1 запускается на JDK 25.
- Loom 1.17.21, Fabric Loader 0.19.5.
- Доступ к Maven/Fabric/Mojang для зависимостей и assets.
- Python 3 для независимого анализа ZIP/class/POM (не для оркестрации сборки).
- Windows с рабочим графическим окружением для показанных production runs.

Если toolchain не обнаружен автоматически, передайте стандартное свойство
`org.gradle.java.installations.paths` с путями к установленным JDK. Абсолютные
пути конкретного компьютера не зашиты в проект.

## Воспроизведение (из этого каталога)

```powershell
rtk proxy .\gradlew.bat pocBuild pocPublishLocal --console=plain
rtk proxy .\gradlew.bat -p consumers/mc1.21.4 build --console=plain
rtk proxy .\gradlew.bat -p consumers/mc26.3 build --console=plain
rtk proxy python verify_artifacts.py
```

Для сохранения stdout/stderr production run в `.evidence` создайте этот каталог
и используйте команды ниже. Запускается настоящий Minecraft с окном; тест сам
завершает его после 20 клиентских тиков. Подключение к серверу не выполняется.
Фиктивный accessToken `0` используется исключительно для локального запуска.

```powershell
rtk proxy python -c "from pathlib import Path; Path('.evidence').mkdir(exist_ok=True)"
rtk proxy .\gradlew.bat -p consumers/mc1.21.4 prodClient --console=plain 2>&1 | Tee-Object -FilePath .evidence/consumer-1214-production.log
rtk proxy .\gradlew.bat -p consumers/mc26.3 prodClient --console=plain 2>&1 | Tee-Object -FilePath .evidence/consumer-263-production.log
rtk proxy python verify_runtime.py
```

На Windows PowerShell 5 `Tee-Object` может использовать UTF-16 вместо UTF-8.
Проверяющий скрипт принимает оба варианта. В PowerShell 7 логи записываются UTF-8.

При желании `-Dmaven.repo.local=<isolated-directory>` можно одинаково передать
в публикацию и consumer builds; для Python проверяющего скрипта соответствующий
аргумент — `--maven-local`. Реальный прогон выполнен со стандартным Maven Local.

Публичной публикации, JitPack и подключения к FunTime нет. Перенос одного трекера
во втором этапе описан отдельно; совместимость остальных API и версий не заявляется.
