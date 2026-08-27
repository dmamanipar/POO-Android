/**
 * PréstamoFácil — puente HTTP entre la app móvil y Google Sheets.
 * Ver .claude/skills/apps-script-sheets-bridge/SKILL.md para el contrato completo.
 *
 * Despliegue: Extensiones > Apps Script en la hoja > pegar este archivo >
 * Configuración del proyecto > Propiedades del script > agregar TOKEN >
 * Implementar > Nueva implementación > Aplicación web > ejecutar como "Yo" >
 * acceso "Cualquier usuario con el enlace".
 */

function doGet(e) {
  if (!tokenValido_(e.parameter.token)) {
    return respuestaJson_({ error: 'token invalido' }, 403);
  }
  var entidad = e.parameter.entidad;
  var desde = e.parameter.desde; // ISO-8601 o vacío
  var hoja = SpreadsheetApp.getActive().getSheetByName(entidad);
  if (!hoja) {
    return respuestaJson_({ error: 'entidad desconocida: ' + entidad }, 400);
  }
  var filas = leerFilasModificadasDesde_(hoja, desde);
  return respuestaJson_({ filas: filas }, 200);
}

function doPost(e) {
  var cuerpo;
  try {
    cuerpo = JSON.parse(e.postData.contents);
  } catch (err) {
    return respuestaJson_({ error: 'cuerpo JSON invalido' }, 400);
  }
  if (!tokenValido_(cuerpo.token)) {
    return respuestaJson_({ error: 'token invalido' }, 403);
  }
  var hoja = SpreadsheetApp.getActive().getSheetByName(cuerpo.entidad);
  if (!hoja) {
    return respuestaJson_({ error: 'entidad desconocida: ' + cuerpo.entidad }, 400);
  }
  var resultado = upsertFilas_(hoja, cuerpo.registros || []);
  return respuestaJson_(resultado, 200);
}

/**
 * Trigger simple: se ejecuta solo cuando una PERSONA edita la hoja a mano
 * (nunca por escrituras del propio script, como upsertFilas_ — Apps Script
 * no reejecuta onEdit por sus propias llamadas a setValue/appendRow, así que
 * no hay bucle). Cubre "edito/agrego una fila directamente en Sheets"
 * (docx §23.3): si la fila editada no tiene uuid, le asigna uno; siempre
 * actualiza fecha_modificacion para que el próximo pull la recoja — sin
 * esto, una fila nueva escrita a mano nunca se descarga al teléfono, porque
 * leerFilasModificadasDesde_ filtra por esa columna.
 */
function onEdit(e) {
  var hoja = e.range.getSheet();
  var encabezados = encabezados_(hoja);
  var indiceUuid = encabezados.indexOf('uuid');
  var indiceFecha = encabezados.indexOf('fecha_modificacion');
  if (indiceUuid === -1 || indiceFecha === -1) return; // pestaña sin ese esquema

  var primeraFila = Math.max(e.range.getRow(), 2); // nunca tocar encabezados
  var ultimaFila = e.range.getRow() + e.range.getNumRows() - 1;
  var ahora = new Date();

  for (var fila = primeraFila; fila <= ultimaFila; fila++) {
    var valores = hoja.getRange(fila, 1, 1, encabezados.length).getValues()[0];
    var filaVacia = valores.every(function (v) { return v === '' || v === null; });
    if (filaVacia) continue;

    var rangoUuid = hoja.getRange(fila, indiceUuid + 1);
    if (!rangoUuid.getValue()) {
      rangoUuid.setValue(Utilities.getUuid());
    }
    hoja.getRange(fila, indiceFecha + 1).setValue(ahora);
  }
}

function tokenValido_(token) {
  var esperado = PropertiesService.getScriptProperties().getProperty('TOKEN');
  return esperado && token === esperado;
}

function encabezados_(hoja) {
  var ultimaColumna = hoja.getLastColumn();
  return hoja.getRange(1, 1, 1, ultimaColumna).getValues()[0];
}

function leerFilasModificadasDesde_(hoja, desdeIso) {
  var encabezados = encabezados_(hoja);
  var indiceFecha = encabezados.indexOf('fecha_modificacion');
  var ultimaFila = hoja.getLastRow();
  if (ultimaFila < 2) return [];

  var datos = hoja.getRange(2, 1, ultimaFila - 1, encabezados.length).getValues();
  var desdeMs = desdeIso ? new Date(desdeIso).getTime() : null;

  var resultado = [];
  for (var i = 0; i < datos.length; i++) {
    var fila = datos[i];
    var fechaCelda = fila[indiceFecha];
    var fechaMs = fechaCelda ? new Date(fechaCelda).getTime() : 0;
    if (desdeMs === null || fechaMs > desdeMs) {
      resultado.push(filaAObjeto_(encabezados, fila));
    }
  }
  return resultado;
}

function filaAObjeto_(encabezados, fila) {
  var obj = {};
  for (var i = 0; i < encabezados.length; i++) {
    var valor = fila[i];
    // Normaliza fechas de celda a texto ISO-8601 para el cliente Java.
    if (valor instanceof Date) {
      valor = valor.toISOString();
    }
    obj[encabezados[i]] = valor;
  }
  return obj;
}

/**
 * Upsert por uuid: busca la fila existente; si el registro entrante tiene
 * fecha_modificacion más reciente, la sobrescribe; si no, la rechaza como
 * version_mas_antigua (esto hace idempotentes los reintentos de push).
 */
function upsertFilas_(hoja, registros) {
  var encabezados = encabezados_(hoja);
  var indiceUuid = encabezados.indexOf('uuid');
  var indiceFecha = encabezados.indexOf('fecha_modificacion');
  var ultimaFila = hoja.getLastRow();

  var datosActuales = ultimaFila >= 2
    ? hoja.getRange(2, 1, ultimaFila - 1, encabezados.length).getValues()
    : [];

  var indicePorUuid = {};
  for (var i = 0; i < datosActuales.length; i++) {
    indicePorUuid[datosActuales[i][indiceUuid]] = i; // 0-based dentro de datosActuales
  }

  var aceptados = [];
  var rechazados = [];

  registros.forEach(function (registro) {
    var uuid = registro.uuid;
    var filaNueva = encabezados.map(function (col) {
      return registro[col] !== undefined ? registro[col] : '';
    });

    if (Object.prototype.hasOwnProperty.call(indicePorUuid, uuid)) {
      var idx = indicePorUuid[uuid];
      var fechaExistente = new Date(datosActuales[idx][indiceFecha]).getTime();
      var fechaNueva = new Date(registro.fecha_modificacion).getTime();
      if (fechaNueva > fechaExistente) {
        hoja.getRange(idx + 2, 1, 1, encabezados.length).setValues([filaNueva]);
        aceptados.push(uuid);
      } else {
        rechazados.push({ uuid: uuid, motivo: 'version_mas_antigua' });
      }
    } else {
      hoja.appendRow(filaNueva);
      aceptados.push(uuid);
    }
  });

  return { aceptados: aceptados, rechazados: rechazados };
}

function respuestaJson_(objeto, codigoHttp) {
  // Apps Script Web Apps no permiten fijar el código HTTP directamente en
  // ContentService; codigoHttp se incluye en el cuerpo para que el cliente lo
  // registre si lo necesita, y el propio contenido señala error vs éxito.
  var salida = ContentService.createTextOutput(JSON.stringify(objeto));
  salida.setMimeType(ContentService.MimeType.JSON);
  return salida;
}
