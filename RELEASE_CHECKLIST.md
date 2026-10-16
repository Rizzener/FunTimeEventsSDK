# Release checklist — 2.0.0-rc.2

Статусы: PASS / FAIL / OPEN / NOT RUN. OPEN и NOT RUN не являются PASS.

| Критерий | Статус |
|---|---|
| RuntimeLease API / поколения / idempotent close | **PASS** |
| Legacy public signatures + source + old binary probe | **PASS** |
| Legacy pin и явный shutdown rejection | **PASS** |
| Два реальных consumer-мода и concurrent first initialization | **PASS** |
| CLIENT_STOPPING, pending workers и очистка ресурсов | **PASS** |
| 13 свежих hosted production targets и required matrix | **PASS** |
| Maven staging, 52 files, JIJ, namespace, Java constraints | **PASS** |
| REST/WSS/SSE/Captcha + все игровые golden fixtures | **PASS** |
| License with Backend Restriction / release hygiene | **PASS** |
| Сохранность rc.1 и historical manifests | **PASS** |
| Durable local evidence archive | **PASS** |
| Off-device backup | **NOT RUN** |
| Performance нового кандидата: измерения | **PASS** |
| Строгая matched pair / устранение неопределённости regression | **OPEN** |
| Windows native startup 26.3 | **OPEN** |
| Backend ACK/idempotency/retention agreement | **OPEN** |
| Публичная публикация JitPack нового RC | **NOT RUN** |
| Tag / GitHub Release / merge | **NOT RUN** |
| Решение владельца о выпуске | **OPEN** |

Проверенный SHA: `fc9e8611601e08431ca14e97511f85a76dbd99bc`. [Hosted run](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38876076261).

Подробности, матрица, команды, ограничения и hashes — [RELEASE_HARDENING_RESULTS.md](RELEASE_HARDENING_RESULTS.md).
Публичных действий не выполнено. Для выпуска нужно отдельное решение владельца.
