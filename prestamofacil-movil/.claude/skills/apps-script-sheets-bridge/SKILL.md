---
name: apps-script-sheets-bridge
description: The contract and implementation pattern for the Google Apps Script Web App that PréstamoFácil uses as a lightweight HTTP-to-Sheets bridge, avoiding OAuth on the mobile client. Use when editing apps-script/Codigo.gs, ClienteHttpSheets.java, or RepositorioGoogleSheets.java, or when deploying/redeploying the Web App.
---

# Google Sheets bridge (Apps Script Web App)

## Why this exists
The official Google Sheets API needs OAuth 2.0 and a client library that doesn't play well with GraalVM native-image compilation for Android. Publishing a small Apps Script bound to the spreadsheet as a Web App gives the mobile client a plain JSON-over-HTTPS endpoint reachable with `java.net.http.HttpClient` — no SDK, no OAuth on the client.

## Deployment (manual, one-time per environment)
1. Open the target Google Sheet → Extensions → Apps Script.
2. Paste `apps-script/Codigo.gs`.
3. Set the shared secret: Project Settings → Script Properties → add `TOKEN` with a random value. Never hardcode the token in the script body.
4. Deploy → New deployment → type "Web app" → execute as "Me" → who has access "Anyone with the link". Copy the deployment URL into the app's `Configuracion` (never commit it to source control alongside a real token).
5. Every subsequent code change requires a **new deployment version** (or "Manage deployments" → edit → new version) — saving the script alone does not update the live Web App.

## Endpoint contract
`GET  ?entidad=PRESTAMO&desde=<ISO-8601>&token=<token>`
→ `200 { "filas": [ {...}, {...} ] }` — all rows in that tab with `fecha_modificacion > desde`.
→ `403 { "error": "token invalido" }` if the token doesn't match.

`POST` body `{ "token": "...", "entidad": "PRESTAMO", "registros": [ {uuid, ..., fecha_modificacion}, ... ] }`
→ `200 { "aceptados": ["uuid1", "uuid2"], "rechazados": [ {"uuid": "uuid3", "motivo": "version_mas_antigua"} ] }`

Upsert semantics on POST: for each incoming row, find the existing row by `uuid` in that tab.
- Not found → append a new row.
- Found, incoming `fecha_modificacion` newer than the stored one → overwrite the row in place.
- Found, incoming `fecha_modificacion` older or equal → reject with `version_mas_antigua` (this is what makes retried pushes idempotent and prevents duplicate rows).

## Skeleton
```javascript
function doGet(e) {
  if (e.parameter.token !== PropertiesService.getScriptProperties().getProperty('TOKEN')) {
    return jsonResponse({ error: 'token invalido' }, 403);
  }
  var sheet = SpreadsheetApp.getActive().getSheetByName(e.parameter.entidad);
  var rows = readRowsModifiedAfter(sheet, e.parameter.desde);
  return jsonResponse({ filas: rows }, 200);
}

function doPost(e) {
  var body = JSON.parse(e.postData.contents);
  if (body.token !== PropertiesService.getScriptProperties().getProperty('TOKEN')) {
    return jsonResponse({ error: 'token invalido' }, 403);
  }
  var sheet = SpreadsheetApp.getActive().getSheetByName(body.entidad);
  var resultado = upsertRows(sheet, body.registros); // { aceptados, rechazados }
  return jsonResponse(resultado, 200);
}
```
Implement `readRowsModifiedAfter`, `upsertRows`, and `jsonResponse` using the sheet's header row (column names from the project's data-model section) to map columns dynamically rather than hardcoding column letters — a reordered column shouldn't break the script.

## Client side
`ClienteHttpSheets` builds these requests with `HttpClient`/`HttpRequest`, serializes with the same Gson instance used for local storage, and surfaces a typed result (`SincronizacionResultado`) to `SincronizacionService`. Any non-2xx or a malformed JSON body must be treated as "this batch did not sync" — never assume partial success without parsing `aceptados` explicitly.

**A deployed Apps Script Web App responds with a 302 redirect** to `script.googleusercontent.com/macros/echo?...` before the real body — this happens for both `doGet` and `doPost`, always, not just in a browser. `HttpClient.newBuilder()` defaults to `Redirect.NEVER`, so without an explicit `.followRedirects(HttpClient.Redirect.NORMAL)` on the builder, every real request gets treated as a failed (non-200) call even though the deployment is perfectly healthy — this only surfaces against a real deployment, `RepositorioRemotoSimulado` never exercises it since it skips HTTP entirely. Already fixed in `ClienteHttpSheets`; don't remove it or rebuild the `HttpClient` elsewhere without it.

## Local testing without touching the real sheet
`RepositorioRemotoSimulado` (Java, in `cloud/`) implements the same `RepositorioRemoto` interface backed by an in-memory map, so `SincronizacionService` and its tests never need network access or a real deployment to run.

## Editing a row directly in the sheet: `onEdit(e)`
`uuid` is normally generated once by `EntidadSincronizable`'s Java constructor — there is no equivalent for a row a person types straight into Sheets (docx §23.3 explicitly allows this: "ediciones directas en la hoja se aceptan"). `Codigo.gs` has a simple `onEdit(e)` trigger that fires automatically whenever a human edits a data row (Apps Script does **not** re-fire it for the script's own `setValue`/`appendRow` calls, e.g. from `upsertFilas_`, so there's no feedback loop): if the row's `uuid` cell is empty it assigns `Utilities.getUuid()`, and it always stamps `fecha_modificacion` to the edit time. The second part matters even more than the first — without a fresh `fecha_modificacion`, `leerFilasModificadasDesde_` will never include that row in a future pull no matter how it got its `uuid`. A simple trigger named exactly `onEdit` activates automatically when the script is saved; it does **not** need a new Web App deployment (unlike `doGet`/`doPost` changes).
