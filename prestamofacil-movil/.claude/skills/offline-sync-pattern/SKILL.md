---
name: offline-sync-pattern
description: The offline-first data flow contract for PréstamoFácil — how a write reaches local storage, the sync queue, and eventually Google Sheets, and how conflicts are resolved. Use when writing or reviewing any code in repository/, cloud/, or SincronizacionService, or when a data-loss/duplication bug is reported.
---

# Offline-first sync contract

## The one rule
**Every write goes to local JSON first, synchronously, before the UI reports success.** The network is never on the critical path of a user action.

## Write path
1. Controller calls a `*Service` method (e.g. `PrestamoService.registrarPrestamo(...)`).
2. Service sets/refreshes `uuid` (generate once, on first creation, with `UUID.randomUUID()` — never regenerate on edit), `fechaModificacion = ZonedDateTime.now()`, `sincronizado = false`.
3. Service calls the matching `*Repository.guardar(entidad)`, which persists to the entity's JSON file via `AlmacenLocalJson`.
4. Service appends an `OperacionPendiente` (entidad, uuid, tipo: CREAR/ACTUALIZAR/ELIMINAR, fecha) to `ColaSincronizacion`.
5. Only after steps 3–4 succeed does the service return control to the controller/UI.

## Sync path (triggered by connectivity regained, app start, or manual button)
1. `SincronizacionService.sincronizar()` runs on a background `Task`.
2. **Push**: read `ColaSincronizacion` in FIFO order, batch by entity type, POST to the Apps Script Web App. On a 2xx response listing accepted uuids, remove exactly those operations from the queue and mark the corresponding local entities `sincronizado = true`. Anything not confirmed stays queued.
3. **Pull**: for each entity type, GET rows with `fecha_modificacion` after the last successful sync timestamp. For each returned row, compare to the local copy by `uuid`:
   - Not present locally → insert.
   - Present locally, remote `fecha_modificacion` newer → overwrite local.
   - Present locally, local `fecha_modificacion` newer or equal → keep local (it will be pushed on the next push cycle if not yet `sincronizado`).
4. If a `Prestamo`'s due date changed as a result of a pull, call `AlertaService.reprogramar(prestamo)`.
5. On full success, store the new "last successful sync" timestamp. On partial failure (e.g. push succeeded, pull failed), still persist the push results — don't roll back a confirmed push because a later step failed.

## Why last-write-wins and not something fancier
This is a single-user-per-sheet MVP (see project doc §9, "queda fuera del MVP"). A CRDT or operational-transform approach is explicitly out of scope; don't introduce one without the user asking to move past the MVP.

## Common bugs and their signature
- **Duplicated rows in Sheets**: usually a uuid regenerated on edit, or a push retried after a timeout even though the server actually applied it (server response wasn't idempotency-checked — the Apps Script bridge should upsert by uuid, not append blindly).
- **Lost edits**: usually comparing `fechaModificacion` as strings with inconsistent timezone offsets instead of parsing to `Instant`/`ZonedDateTime` before comparing.
- **Queue grows forever**: a push is failing silently (check HTTP status handling in `ClienteHttpSheets`) or the accepted-uuid response isn't being parsed correctly. This exact bug shipped once already: `Json.gson()` didn't set a field naming policy, so the `Map<String,Object>` built from a Java entity had `fechaModificacion` while `RepositorioRemotoSimulado`/`Codigo.gs` looked for `fecha_modificacion` — `esMasReciente` silently compared against `null`, the push exception was swallowed by `SincronizacionService.sincronizar()`'s broad catch, and the queue never emptied. Fixed by `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES` in `util/Json.java` — **never** add a `@SerializedName` or naming override on an individual entity field without checking it still matches its Sheets column, and never build the outgoing `Map<String,Object>` by hand (always round-trip through `Json.gson()` like `SincronizacionService.obtenerFilaParaEnviar` does).
