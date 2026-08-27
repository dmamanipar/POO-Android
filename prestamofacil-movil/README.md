# PréstamoFácil — Móvil (JavaFX + Gluon Mobile)

Código fuente base para el proyecto descrito en
`docs/Proyecto_PrestamoFacil_Movil.docx`: registro y seguimiento de objetos
prestados, con alertas de devolución, modo sin conexión y sincronización con
Google Sheets.

## Qué contiene este paquete

- `pom.xml` — proyecto Maven con JavaFX, Gluon Glisten, Gluon Attach
  (storage, lifecycle, connectivity, local-notifications), Gson y el plugin
  `gluonfx-maven-plugin` para compilar a Android/iOS.
- `src/main/java/...` — modelo, repositorios JSON, servicios (personas,
  objetos, categorías, préstamos, alertas, sincronización), capa de
  plataforma (notificaciones/conectividad) y las siete pantallas del MVP
  (Dashboard, Personas, Objetos, Nuevo Préstamo, Préstamos Activos,
  Historial, Configuración), navegables desde un NavigationDrawer.
- `src/test/java/...` — pruebas JUnit 5 de la lógica de negocio (estado del
  préstamo, registrar/devolver, sincronización con un repositorio remoto
  simulado en memoria).
- `apps-script/Codigo.gs` — el puente HTTP↔Sheets (Google Apps Script Web App).
- `.claude/agents/` y `.claude/skills/` — agentes y skills de Claude Code
  especializados en cada parte del proyecto, para seguir desarrollándolo con
  ayuda de Claude.

## ⚠️ Estado real de este código — léelo antes de asumir que "ya funciona"

Este código se escribió originalmente sin poder compilarlo (el entorno donde
se generó no tenía acceso a Maven Central ni al repositorio de Gluon). Ya se
compiló y se probó de verdad en una máquina con Maven + JDK 21 + acceso a
internet: `mvn test` corre y pasa (11/11) y `mvn javafx:run` abre la app en
escritorio, navega Dashboard ↔ Configuración y guarda `config.json`
correctamente. Ese proceso encontró y corrigió estos problemas reales (no
hipotéticos) que tenía el código generado sin compilar:

- **`pom.xml` apuntaba a un BOM inexistente** (`com.gluonhq.attach:attach:4.0.20`,
  `pom`) — no existe en ningún repositorio. Se reemplazó por versiones
  explícitas por módulo (`attach.version=4.0.22`, la última en Maven Central)
  y se agregó el repositorio propio de Gluon
  (`https://nexus.gluonhq.com/nexus/content/repositories/releases`), que es
  donde vive `charm-glisten` (no está en Maven Central).
- **Faltaban dos módulos de Attach que sí hacen falta**: `util` en scope
  compile (`FabricaPlataforma` usa `Platform.isDesktop()`, pero `storage` solo
  trae `util` en scope runtime) y `display` (el propio `charm-glisten`
  necesita `DisplayService` en tiempo de ejecución para dibujar el `AppBar`;
  sin él la app crashea al abrir la primera vista con
  `NoClassDefFoundError: com/gluonhq/attach/display/DisplayService`).
- **`NotificadorMovil` usaba una API de Gluon Attach que no es la de la
  versión publicada**: la interfaz real es `LocalNotificationsService`
  (paquete `com.gluonhq.attach.localnotifications`, no
  `com.gluonhq.attach.notifications`), no tiene `Notification.builder()`
  (se construye con su constructor) y no expone `notify()`/
  `unregisterNotification()` sino una `ObservableList<Notification>`
  (`getNotifications()`) a la que se agrega/quita. Ya corregido.
- **`PersonaRepository` no podía leer `personas.json` una vez escrito una
  vez**: Gson no puede instanciar `PersonaBase` (abstracta) al deserializar
  `List<PersonaBase>`. Se agregó `util/PersonaBaseAdapter.java`, un
  `JsonSerializer`/`JsonDeserializer` que usa el mismo campo `tipo` de la
  pestaña PERSONA (sección 19 del documento) como discriminador para elegir
  `UsuarioPrestatario` vs `Administrador`.
- **Los mapas que `SincronizacionService` manda a `RepositorioRemoto` usaban
  claves camelCase** (`fechaModificacion`) mientras que
  `RepositorioRemotoSimulado` y `apps-script/Codigo.gs` esperan snake_case
  (`fecha_modificacion`, como las columnas reales de la hoja) — el push nunca
  fallaba visiblemente, pero `esMasReciente(...)` comparaba contra `null` y
  la cola nunca se vaciaba. Se corrigió configurando
  `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES` en `util/Json.java`, que
  además hace que los nombres de campo Java coincidan exactamente con los
  nombres de columna documentados (`personaUuid` → `persona_uuid`, etc.).
- **No existía ninguna pantalla para RF01–RF10**: `Categoria` no tenía
  repositorio ni servicio (aunque `Objeto` depende de ella), y `Personas`,
  `Objetos`, `Nuevo Préstamo`, `Préstamos Activos` e `Historial` no tenían
  vista — el Dashboard solo mostraba un resumen de lectura. Se agregaron
  `CategoriaRepository`/`CategoriaService` (con su entidad `CATEGORIA` en
  `SincronizacionService`) y las cinco vistas, navegables desde un
  `NavigationDrawer` (`Main.construirMenu()`) con un ícono ☰ en cada `AppBar`.
  Probado a mano de punta a punta: alta/edición/baja/búsqueda de personas y
  objetos (con categoría inline), registrar préstamo, ver préstamos activos,
  devolver, ver historial, y que el Dashboard refleje los conteos reales.
- **Las vistas no se refrescaban al volver a ellas**: Gluon cachea cada
  vista (la fábrica de `addViewFactory` se llama una sola vez), así que
  cargar los datos solo en el constructor deja todo "congelado" desde el
  primer show — por ejemplo, el Dashboard seguía mostrando "0 disponibles"
  después de registrar un objeto. Las seis vistas con datos llaman a su
  método de carga también en `setOnShowing(...)`, no solo en el constructor.
- **Tocar una fila de una lista para editarla no hacía nada** en
  `PersonasView`/`ObjetosView`: el clic estaba en el `HBox` interno de la
  celda, que no ocupa todo el ancho de la fila (solo su contenido), así que
  clics fuera de ese `HBox` (la mayor parte de la fila) no disparaban nada,
  aunque la fila sí se veía "seleccionada" por el comportamiento por defecto
  de `ListView`. Se movió el manejador a la celda completa; el botón
  eliminar sigue funcionando aparte porque `Button` consume su propio evento.

## Confirmación al eliminar, aviso al crear, y objetos prestados

- **`view/Dialogos.java`**: diálogo de confirmación reutilizable (`Alert` de
  Glisten, no `javafx.scene.control.Alert`) usado por `PersonasView` y
  `ObjetosView` antes de dar de baja un registro — "¿Eliminar a...? Esta
  acción no se puede deshacer." con botones Cancelar/Eliminar.
- **`Toast`** de confirmación al registrar (no al editar) en Personas,
  Objetos, Categoría (alta rápida) y Nuevo Préstamo.
- **RNF06 reforzado**: `ObjetoService.eliminar(...)` ahora lanza
  `IllegalStateException` si el objeto todavía está prestado
  (`!estaDisponible()`); `ObjetosView` muestra el motivo en vez de dejar
  pasar la excepción. Cubierto por
  `PrestamoServiceTest.noPermiteEliminarUnObjetoPrestado`.

## Sincronización con Sheets: pull implementado, y un bug de redirecciones corregido

Confirmado contra un despliegue real (no solo contra `RepositorioRemotoSimulado`):
el usuario desplegó `apps-script/Codigo.gs`, agregó la fila de encabezados en
cada pestaña y probó `doGet` desde el navegador, obteniendo `200 {"filas":[]}`.
Eso reveló y permitió corregir un bug real:

- **`ClienteHttpSheets` no seguía las redirecciones 302 que emite un Web App
  de Apps Script real** — responde con un `302` hacia
  `script.googleusercontent.com/macros/echo?...` antes de entregar el cuerpo,
  tanto en `doGet` como en `doPost`. `HttpClient` de Java no sigue
  redirecciones por defecto (política `NEVER`), así que cada llamada real
  habría llegado como "HTTP 302" y `SincronizacionService` la habría tratado
  como fallo, aunque el despliegue estuviera perfectamente sano — nunca se
  manifestaba en las pruebas porque `RepositorioRemotoSimulado` no pasa por
  HTTP en absoluto. Corregido agregando
  `.followRedirects(HttpClient.Redirect.NORMAL)` al builder en
  `cloud/ClienteHttpSheets.java`.

- **`SincronizacionService.conciliarPersona/conciliarObjeto/conciliarPrestamo/conciliarCategoria`**
  ya están implementados (antes eran `TODO` vacíos): deserializan el
  `Map<String,Object>` que llega de Sheets a la subclase de dominio
  correspondiente y aplican last-write-wins comparando `fecha_modificacion`
  (mismo patrón que `RepositorioRemotoSimulado.esMasReciente(...)`).
  `conciliarPrestamo` además reprograma o cancela la alerta local si la fecha
  de devolución cambió, exactamente como pide
  `.claude/skills/offline-sync-pattern/SKILL.md`. Cubierto por tres pruebas
  nuevas en `SincronizacionServiceTest` (edición remota más reciente que gana,
  edición remota más antigua que se ignora, cambio de fecha de préstamo que
  no revienta al reprogramar la alerta).
  **Limitación conocida**: la pestaña `PRESTAMO` (docx §19.2) no incluye qué
  objeto(s) se prestaron — eso vive en una pestaña `DETALLE_PRESTAMO` que
  este proyecto todavía no sincroniza en ningún sentido (ni push ni pull).
  Un préstamo bajado desde Sheets queda con `detalles` vacío; las pantallas
  que muestran el objeto prestado mostrarán "(sin objetos)" para esos casos.
- **`apps-script/Codigo.gs` ahora tiene un trigger `onEdit(e)`** para cuando
  alguien edita la hoja directamente (docx §23.3 lo permite): si la fila no
  tiene `uuid` se lo asigna (`Utilities.getUuid()`), y siempre actualiza
  `fecha_modificacion` — sin eso, esa fila nunca se habría bajado al teléfono
  en un pull posterior. No dispara con las escrituras que hace el propio
  script (`upsertFilas_`), solo con ediciones humanas, así que no hay bucle.
  Al ser un trigger simple (se llama exactamente `onEdit`), se activa solo
  con guardar el script — no hace falta una nueva implementación del Web App.

**Nota sobre el diálogo de confirmación (`Dialogos.confirmar`, `Alert`)**: su
API se verificó línea por línea contra el jar real de `charm-glisten` (mismo
método usado para detectar los problemas de `Notification`/`TimePicker`
documentados arriba), y el patrón de creación/registro sigue el mismo estilo
ya probado en vivo para `Toast` en esta app (`ConfiguracionView`). No se pudo
volver a hacer clic-a-clic en vivo sobre el diálogo de confirmación en la
sesión en que se agregó, por inestabilidad del propio entorno de
automatización de UI (no del código): antes de confiar en él, prueba
manualmente eliminar una persona u objeto (debe aparecer el diálogo) y
prestar un objeto y luego intentar eliminarlo (debe rechazarse con un
mensaje, no un diálogo).

Pendiente de verificar en tu máquina (esto sí requiere Android SDK/NDK, que
este entorno de desarrollo no tiene):

```bash
mvn test                              # ✅ ya verificado, 16/16 en verde
mvn javafx:run                        # ✅ ya verificado, abre y navega
mvn -Pandroid gluonfx:build gluonfx:package   # compilación nativa Android — no verificado aquí
```

Fuera de esos puntos, la lógica de negocio (estados del préstamo, cola de
sincronización, alertas, repositorios JSON) y las siete pantallas del MVP
están completas, compiladas, probadas a mano en escritorio y con pruebas
unitarias en verde.

## Configurar la sincronización desde la propia app (sin tocar código)

No hace falta ningún archivo de configuración en el repositorio ni recompilar
para conectar la app a una hoja de Google Sheets: la app funciona en modo
100% local desde el primer arranque (RNF07), y el ícono de engranaje ⚙️ en la
barra superior del Dashboard abre la pantalla **Configuración de
sincronización**, donde se pega la URL del Web App de Apps Script y el token
(ver el paso 3 más abajo para obtenerlos) y se guarda con
"Guardar y sincronizar ahora". Esto reemplaza cualquier necesidad de traer un
`config.json` preexistente: el usuario final la completa desde el teléfono la
primera vez que tiene internet, y `Configuracion` la persiste en el
almacenamiento privado del dispositivo (nunca en el código fuente, RNF10).

## Cómo compilar y ejecutar

### 1. Desarrollo en escritorio (recomendado primero)
```bash
mvn gluonfx:run
```
Corre con `NotificadorEscritorio` (diálogo en vez de notificación del
sistema) y `MonitorConectividadEscritorio` (hace ping a 8.8.8.8), así que
puedes probar toda la lógica sin Android.

### 2. Compilar e instalar en Android
Requiere Android SDK/NDK y la distribución de GraalVM que indique la
documentación de Gluon para tu versión del plugin, en Linux o WSL2:
```bash
mvn -Pandroid gluonfx:build gluonfx:package
mvn -Pandroid gluonfx:install   # con el teléfono conectado y depuración USB activa
```

**O generarlo en la nube sin instalar nada**:
`.github/workflows/prestamofacil-android.yml` compila el APK en un runner de
GitHub Actions (basado en el workflow oficial de Gluon,
[`hello-gluon-ci`](https://github.com/gluonhq/hello-gluon-ci/blob/master/.github/workflows/android.yml)):
pestaña Actions → **"PréstamoFácil - Generar APK Android"** → **Run
workflow**, o simplemente haz push a `main`/`master` tocando algo dentro de
`prestamofacil-movil/`. El APK queda disponible unos minutos después como
artefacto de esa ejecución (pestaña Actions → la ejecución → "Artifacts" al
final de la página), sin necesidad de tener Android SDK/NDK/GraalVM
instalados localmente. Detalles, límites (sin firma de release, sin licencia
de Gluon por defecto) y cómo activar ambas cosas si las necesitas:
comentarios dentro del propio archivo.

El nombre no es simplemente `android.yml` a propósito: si vas a poner este
proyecto dentro de un repositorio de GitHub que ya tiene otros proyectos
(cada uno con su propio workflow de Android), los nombres de archivo en
`.github/workflows/` deben ser únicos en todo el repo, aunque los proyectos
vivan en carpetas distintas. El workflow usa `working-directory` y un filtro
de `paths` (variable `PRESTAMOFACIL_DIR` al principio del archivo) para
compilar solo esta carpeta y solo cuando algo dentro de ella cambia — ajusta
esa variable si copias `prestamofacil-movil/` con otro nombre o en otra
ubicación del repo. No hace falta un repositorio separado para esto.

### 3. Publicar el puente de Google Sheets
1. Crea una hoja de Google Sheets con las pestañas `PERSONA`, `CATEGORIA`,
   `OBJETO`, `PRESTAMO` (ver sección 19 del documento del proyecto para las
   columnas exactas).
2. Extensiones → Apps Script → pega `apps-script/Codigo.gs`.
3. Configuración del proyecto → Propiedades del script → agrega `TOKEN` con
   un valor aleatorio.
4. Implementar → Nueva implementación → Aplicación web → ejecutar como "Yo",
   acceso "Cualquier usuario con el enlace".
5. Copia la URL de implementación y el token a la configuración de la app:
   ábrela, toca el engranaje ⚙️ del Dashboard (pantalla **Configuración de
   sincronización**, `view/ConfiguracionView.java`) y pégalos ahí — no hace
   falta editar ningún archivo. `Configuracion` (`util/Configuracion.java`)
   los guarda en `config.json` en el almacenamiento privado del dispositivo,
   nunca en el código fuente.

## Estructura de paquetes

```
model/       Persona, Objeto, Prestamo, Alerta, EntidadSincronizable, ...
repository/  AlmacenLocal (JSON local) + repos + ColaSincronizacion
service/     PrestamoService, AlertaService, SincronizacionService, ...
platform/    Notificador y MonitorConectividad (móvil vs. escritorio)
cloud/       RepositorioRemoto: ClienteHttpSheets (real) / Simulado (pruebas)
view/        Las siete pantallas del MVP (Dashboard, Personas, Objetos,
             NuevoPrestamo, PrestamosActivos, Historial, Configuración),
             navegación por NavigationDrawer
util/        Configuracion, Json (Gson con soporte ZonedDateTime), Validador
```

## Trabajar en esto con Claude Code

Este repo trae agentes especializados en `.claude/agents/`:
- `gluon-mobile-architect` — pantallas y navegación Glisten.
- `sync-and-storage-engineer` — esquema JSON local ↔ Google Sheets.
- `android-build-specialist` — notificaciones, manifest, compilación GluonFX.
- `prestamofacil-qa` — pruebas unitarias y scripts de prueba manual.

Y skills de referencia en `.claude/skills/` con los patrones y contratos que
esos agentes deben respetar (UI Glisten, sincronización offline-first,
notificaciones Attach, puente Apps Script).
