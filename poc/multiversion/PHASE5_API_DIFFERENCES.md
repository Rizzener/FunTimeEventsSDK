# Phase 5 — реестр различий API

Реестр основан на официальных Minecraft/Fabric metadata, `javap` фактических Minecraft/authlib JAR и компиляции **каждого** из 13 target. Компиляция не заменяет production runtime; результаты запусков приведены отдельно в `PHASE5_RESULTS.md`.

## SDK: четыре узких исключения

| Место | Версии | Реальная сигнатура / обращение | Исходники |
|---|---|---|---|
| Hover SHOW_TEXT | 1.21.4 | `getValue(HoverEvent.Action.SHOW_TEXT)` | `platform/hover-legacy` |
| Hover SHOW_TEXT | 1.21.5–26.3, только перечисленные targets | `HoverEvent.ShowText.value()` | `platform/hover-record` |
| GameProfile | 1.21.4–1.21.8 | `getName()`, `getId()` | `platform/profile-getters` |
| GameProfile | 1.21.9–26.3, перечисленные targets | `name()`, `id()` | `platform/profile-record` |
| ResourceKey | 1.21.4–1.21.10 | `location()` | `platform/dimension-location` |
| ResourceKey | 1.21.11–26.3, перечисленные targets | `identifier()` | `platform/dimension-identifier` |
| Boss overlay | 1.21.4–26.1.2 | `client.gui.getBossOverlay()` | `platform/boss-gui` |
| Boss overlay | 26.2, 26.3 | `client.gui.hud.getBossOverlay()` | `platform/boss-hud` |

Это **8 небольших файлов для 4 мест несовместимости**. Общий `VersionAccess` делегирует трём package-private helpers; HoverAccess остаётся отдельным маленьким helper. Три платформенных контракта и общие трекеры не расширялись. Reflection не добавлялась.

Границы различий пересекают legacy/modern. Поэтому два монолитных VersionAccess заменены общей фасадной реализацией и независимым выбором leaves в `targets.json`. Это конкретная причина локального изменения структуры, без пересмотра согласованной архитектуры.

Сигнатуры и происхождение JAR сохранены в `results/phase5/signatures/*`. Отсутствие альтернативного класса (`EntityType`/`EntityTypes`, старого `HoverEvent$ShowText`) в этих диагностических dumps ожидаемо и не означает успешную проверку отсутствующей возможности.

## Только fixtures

| Изменение | Решение |
|---|---|
| Hover constructor/record с 1.21.5 | Два `FixtureHover`; одинаковые plain/hover golden fixtures. |
| ResourceKey.identifier с 1.21.11, но прежний EntityType | Небольшой `legacy-identifier/FixtureVersion`. |
| 26.1–26.1.2: EntityType, новый constructor boss event с UUID | `modern-entitytype/FixtureVersion`. |
| 26.2–26.3: EntityTypes и constructor boss event с UUID | Существующий modern FixtureVersion. |
| Data pack format 82+ использует major/minor | `min_format`/`max_format` из фактической MC metadata; для старых releases сохраняется `pack_format`. |

Итого: четыре варианта fixture factory и два Hover factory. Полные server/client сценарии общие. Правила парсинга, зоны, интервалы и golden payload не изменялись.

## Источники и воспроизведение

- [Fabric game metadata](https://meta.fabricmc.net/v2/versions/game).
- [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml).
- [Minecraft version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json).
- [Официальное описание major/minor data pack formats](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9).
- [Fabric production run tasks](https://docs.fabricmc.net/develop/loom/production-run-tasks).

`python phase5_metadata.py` сохраняет официальные ответы и проверяет Minecraft SHA-1. `python phase5_signatures.py --targets all` сохраняет реальные JVM descriptors, включая точную authlib каждого target. `targets.json` содержит явные bindings каждой версии; диапазоны выше не являются обещанием поддержки будущих releases.
