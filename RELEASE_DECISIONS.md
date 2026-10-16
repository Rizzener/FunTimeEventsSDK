# Решения перед публичным выпуском

Статус: предложения этапа 6. Транспортный протокол и legacy Builder пока сохранены.

## 1. Владение runtime

Проверенное поведение: один runtime и одна Fabric bridge, совпадающая полная
конфигурация разделяется, конфликт отклоняется. Статический shutdown() не знает
вызывающий мод. Ни volatile, ни проверка конфигурации не решают проблему владения:
один legacy consumer способен остановить другого. Риск нельзя скрывать за singleton.

Рекомендация — небольшой `RuntimeLease implements AutoCloseable` и дополнительный
`Builder.buildLease()`. Прежний build() и тип результата остаются. Каждый lease
принадлежит конкретному runtime generation, close идемпотентен; последний close
останавливает runtime только если он создан исключительно через leases. Вызов
build() закрепляет legacy lifetime до явного shutdown/CLIENT_STOPPING, иначе
последний lease мог бы преждевременно остановить старый consumer.

При активных leases legacy shutdown() должен явно бросать IllegalStateException,
предлагая закрыть собственный lease. Это осознанное изменение семантики только
нового смешанного режима, с сохранением старого бинарного дескриптора. На остановке
клиента внутренний force-close закрывает runtime независимо от забытых handles;
старые handles не затрагивают последующую generation. Build/close остаются на
client thread. Конфликт ключа или другой настройки отвергается до выдачи lease.

Почему не только owner token: второй мод всё равно нуждается в явном lifetime.
Почему не считать каждый повторный build(): в прежнем API нет парного release,
повторные build() уже идемпотентны, неизвестно число владельцев. Неявный refcount
сломает shutdown и создаст утечки. Никакого отдельного event bus/DI framework не нужно.

Для внедрения нужны тесты: два leases, двойной close, close старой generation,
ошибка init, конфликт config, legacy pin, отказ shutdown при leases, последний
close, CLIENT_STOPPING, отсутствие повторных Fabric subscriptions и сетевых threads.
Пользователю отправлен отдельный вопрос о внедрении. До ответа модель не изменяется.
До решения: один host-мод владеет legacy shutdown; не вызывать его на DISCONNECT.

## 2. Snapshot и историческое событие

| Данные | Смысл | Безопасная политика |
|---|---|---|
| TAB/текущие игроки | текущее состояние | coalesce по server/type; старое заменить |
| Данжи, игроки шахт, HellMap | состояние зоны/таймеров на момент наблюдения | короткий TTL; не переносить в другую игровую сессию |
| BanPayload | исторический факт | потеря нежелательна; для гарантии нужен контракт ACK/idempotency |
| Координаты событий | наблюдение о текущем событии | ограниченный срок; старые координаты после нового события могут навредить |

Текущий контракт: 64 snapshots, TTL 15 секунд; 256 FIFO важных событий (bans и
coordinates), TTL 30 секунд, overflow отвергает новый payload. Shutdown очищает
очереди; session fence отклоняет старую игровую сессию. Эти ограничения явны и не
означают гарантированной доставки. Бан при reconnect может потеряться. Это
допустимый только явно заявленный best effort, а не надёжный журнал модерации.

Без ACK повтор после успешного sendText future не доказывает сохранения на backend.
После сетевого разрыва неизвестно, применён ли POST/frame. Слепой retry способен
создать дубликат; дедупликация только по player/reason/time может склеить разные баны.
Увеличение TTL само по себе не решает ни delivery, ни session identity.

Конкретные вопросы backend: есть ли application ACK для банов; поддерживается ли
idempotency key и каков retention; можно ли повторять один event ID через REST/WSS;
нужны ли исторические события после смены сервера; какой предел возраста/объёма;
различаются ли две одинаковые санкции; что означает успешный HTTP response?

Предложение после согласования: отдельная bounded очередь банов с immutable server
identity/event ID, ограниченными retries/backoff и согласованным retention; ACK
удаляет запись, server-side unique idempotency key подавляет повторы. Сохранение на
диск — отдельное требование, не добавлять его автоматически. Coordinates требуют
собственного срока актуальности/event identity. Snapshot никогда не переигрывать
как историю. Новые поля/protocol envelopes без согласования не добавляются.

Безопасный default RC: сохранить прежний wire format и best effort, не обещать
at-least-once/exactly-once и не повторять неоднозначную отправку автоматически.
Вопрос пользователю о существующем ACK/idempotency отправлен отдельно.

## 3. Канал публикации и миграция

Основной канал остаётся JitPack. Новый target:
`com.github.Rizzener.FunTimeEventsSDK:mc1.21.8:<release>`.
Старые tags и `...:fte-api:<old-release>` не переписываются. Нельзя корректно сделать
универсальный relocation старого artifactId: Maven не знает Minecraft consumer.
Нужны явная смена dependency/include и таблица миграции. Не публиковать aggregate,
который притянет все несовместимые MC targets. Имена модулей и multi-module group
соответствуют [официальной схеме JitPack](https://docs.jitpack.io/building/).

Фактическая read-only проверка 2026-10-16: JitPack API сообщает `ok` для v1.0.5,
но POM `com.github.Rizzener.FunTimeEventsSDK:fte-api:v1.0.5` возвращает HTTP 404.
Доступный POM имеет GAV `com.github.Rizzener:FunTimeEventsSDK:v1.0.5` (HTTP 200).
Metadata старой сборки содержит modules=[]; это согласуется с отдельной схемой
JitPack для repo с единственным модулем, но точную причину маршрутизации по одному
metadata не доказываем. Raw ответы сохранены в results/phase6/jitpack-existing.
Старые артефакты не перестраивались. Build status=ok не доказывает доступность
старой module-координаты.

Для будущего tag предлагается `2.0.0-rc.1` без префикса v: Maven version, mod version
и Fabric semantic version constraints тогда совпадают. Старые v-tags не меняются.

| Канал | Анонимное получение | Сборка/эксплуатация | Решение сейчас |
|---|---|---|---|
| JitPack | для публичного repo | сборка по tag/commit по запросу; требуется проверить dual JDK на его окружении | сохранить основной канал; не инициировать RC build запросом |
| Maven Central | обычный публичный Maven | собственная CI сборка, verified namespace, signatures, sources/Javadoc и POM metadata | кандидат на будущий отдельный переход |
| Публичный Nexus/Artifactory/static Maven | если владелец включил anonymous read | владелец обеспечивает TLS, storage, immutable versions, backups и uptime | хороший вариант без изменения dependency UX, требует согласованного hosting |
| GitHub Packages Maven | требуется token даже для public packages | GitHub auth на стороне consumer | не соответствует требованию без обязательной авторизации |

Central потребует регистрации namespace (обычно `io.github.rizzener` через
GitHub), а не предположения о праве на `com.github.*`; также Javadoc, signatures и
developer metadata. Это отдельная подготовка, текущий staging не объявляется
готовым Central deployment. Источники: [namespace](https://central.sonatype.org/register/namespace/),
[требования публикации](https://central.sonatype.org/publish/requirements/).
Требование токена Maven GitHub Packages прямо указано в
[документации GitHub](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry).

Обновлённый jitpack.yml устанавливает два hash-pinned JDK и вызывает Gradle
sdkBuild/sdkPublishLocal. Это подготовленная конфигурация, не доказательство
успешной сборки в JitPack. Тест нового JitPack build означал бы публичное получение
артефактов, поэтому без отдельного решения пользователя он не выполняется.

## 4. Предлагаемый порядок

1. Завершить hosted CI на неизменном commit и повторную матрицу новых JAR.
2. Разрешить ownership/delivery вопросы и выпускные ограничения native OPEN.
3. При изменении SDK создать новый immutable RC, повторить обязательные проверки.
4. Предлагаемая версия: 2.0.0-rc.1, затем 2.0.0 после отдельного решения. Major
   отражает отдельные MC artifacts, новые runtime constraints и безопасную миграцию.
5. Только после разрешения пользователя tag/public publication; затем проверить
   свежий anonymous consumer. Не публиковать заново под старыми версиями/координатами.

В этом этапе не создаются публичные Maven releases, GitHub Release или merge.

## 5. Принятая граница API совместимости

Пользователь подтвердил: сохраняется заявленный SDK API; прямые внутренние вызовы
мигрируют явно. Обязательный контракт — 36 проверяемых классов facade/config/Builder,
models, network/cache, PayloadSender и FteLogger. Полный inventory 54 Java-public
классов сохраняется дополнительно. Из реализации исчезли старые TrackerManager,
tracker.ban.BanTracker, tracker.server.ServerContext и HoverEventUtil; у части
tracker/bootstrap/util helpers изменились descriptors. Это не совместимость всей
Java-public поверхности. Compatibility-facades для внутренних точек не добавляются.
