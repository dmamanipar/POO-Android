package pe.edu.curso.prestamofacil.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import pe.edu.curso.prestamofacil.cloud.RepositorioRemoto;
import pe.edu.curso.prestamofacil.cloud.SincronizacionResultado;
import pe.edu.curso.prestamofacil.model.Administrador;
import pe.edu.curso.prestamofacil.model.Categoria;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.OperacionPendiente;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.model.UsuarioPrestatario;
import pe.edu.curso.prestamofacil.platform.MonitorConectividad;
import pe.edu.curso.prestamofacil.repository.CategoriaRepository;
import pe.edu.curso.prestamofacil.repository.ColaSincronizacion;
import pe.edu.curso.prestamofacil.repository.ObjetoRepository;
import pe.edu.curso.prestamofacil.repository.PersonaRepository;
import pe.edu.curso.prestamofacil.repository.PrestamoRepository;
import pe.edu.curso.prestamofacil.util.Configuracion;
import pe.edu.curso.prestamofacil.util.Json;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orquesta push (subir cola pendiente) y pull (bajar cambios remotos) contra
 * RepositorioRemoto. Ver .claude/skills/offline-sync-pattern/SKILL.md para el
 * contrato completo — esta clase debe reflejarlo fielmente.
 *
 * Diseñada para ejecutarse en background (envolver en una javafx.concurrent.Task
 * desde la vista/controlador, nunca en el hilo de FX).
 */
public class SincronizacionService {

    private final RepositorioRemoto repositorioRemoto;
    private final MonitorConectividad monitorConectividad;
    private final ColaSincronizacion cola;
    private final PersonaRepository personaRepository;
    private final ObjetoRepository objetoRepository;
    private final PrestamoRepository prestamoRepository;
    private final CategoriaRepository categoriaRepository;
    private final AlertaService alertaService;
    private final Gson gson = Json.gson();

    public SincronizacionService(RepositorioRemoto repositorioRemoto,
                                  MonitorConectividad monitorConectividad,
                                  ColaSincronizacion cola,
                                  PersonaRepository personaRepository,
                                  ObjetoRepository objetoRepository,
                                  PrestamoRepository prestamoRepository,
                                  CategoriaRepository categoriaRepository,
                                  AlertaService alertaService) {
        this.repositorioRemoto = repositorioRemoto;
        this.monitorConectividad = monitorConectividad;
        this.cola = cola;
        this.personaRepository = personaRepository;
        this.objetoRepository = objetoRepository;
        this.prestamoRepository = prestamoRepository;
        this.categoriaRepository = categoriaRepository;
        this.alertaService = alertaService;
    }

    public static class ResultadoSincronizacion {
        public final boolean seEjecuto;
        public final int operacionesEnviadas;
        public final int operacionesConfirmadas;
        public final String error;

        ResultadoSincronizacion(boolean seEjecuto, int enviadas, int confirmadas, String error) {
            this.seEjecuto = seEjecuto;
            this.operacionesEnviadas = enviadas;
            this.operacionesConfirmadas = confirmadas;
            this.error = error;
        }
    }

    /** Punto de entrada único. Seguro de llamar repetidamente (idempotente por diseño). */
    public ResultadoSincronizacion sincronizar() {
        if (!monitorConectividad.hayConexion()) {
            return new ResultadoSincronizacion(false, 0, 0, "Sin conexión a internet.");
        }
        if (!Configuracion.obtener().estaConfigurado()) {
            return new ResultadoSincronizacion(false, 0, 0, "Falta configurar la URL del Web App y el token.");
        }

        try {
            int enviadas = 0;
            int confirmadas = 0;
            var resultadoPush = subirCambiosPendientes();
            enviadas = resultadoPush.enviadas;
            confirmadas = resultadoPush.confirmadas;

            descargarCambiosRemotos();

            Configuracion config = Configuracion.obtener();
            config.setUltimaSincronizacionExitosa(
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            config.guardar();

            return new ResultadoSincronizacion(true, enviadas, confirmadas, null);
        } catch (Exception e) {
            return new ResultadoSincronizacion(true, 0, 0, e.getMessage());
        }
    }

    private record ResumenPush(int enviadas, int confirmadas) { }

    private ResumenPush subirCambiosPendientes() {
        List<OperacionPendiente> pendientes = cola.leer();
        if (pendientes.isEmpty()) {
            return new ResumenPush(0, 0);
        }

        int totalEnviadas = 0;
        int totalConfirmadas = 0;

        // Agrupar por entidad para enviar en lotes, preservando orden FIFO dentro de cada grupo.
        Map<String, List<OperacionPendiente>> porEntidad = pendientes.stream()
            .collect(Collectors.groupingBy(OperacionPendiente::getEntidad, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<OperacionPendiente>> grupo : porEntidad.entrySet()) {
            String entidad = grupo.getKey();
            List<Map<String, Object>> registros = new ArrayList<>();
            for (OperacionPendiente op : grupo.getValue()) {
                obtenerFilaParaEnviar(entidad, op.getUuidEntidad()).ifPresent(registros::add);
            }
            if (registros.isEmpty()) {
                continue;
            }
            totalEnviadas += registros.size();

            SincronizacionResultado resultado = repositorioRemoto.enviarCambios(entidad, registros);
            if (resultado.exitoso && !resultado.aceptados.isEmpty()) {
                cola.retirarAceptadas(resultado.aceptados);
                marcarSincronizadosEnLocal(entidad, resultado.aceptados);
                totalConfirmadas += resultado.aceptados.size();
            }
            // Lo no confirmado permanece en la cola para el próximo intento —
            // no se hace nada más aquí, deliberadamente.
        }
        return new ResumenPush(totalEnviadas, totalConfirmadas);
    }

    private java.util.Optional<Map<String, Object>> obtenerFilaParaEnviar(String entidad, String uuid) {
        Type tipoMapa = new TypeToken<Map<String, Object>>() { }.getType();
        switch (entidad) {
            case "PERSONA":
                return personaRepository.buscarPorUuid(uuid)
                    .map(p -> gson.<Map<String, Object>>fromJson(gson.toJson(p), tipoMapa));
            case "OBJETO":
                return objetoRepository.buscarPorUuid(uuid)
                    .map(o -> gson.<Map<String, Object>>fromJson(gson.toJson(o), tipoMapa));
            case "PRESTAMO":
                return prestamoRepository.buscarPorUuid(uuid)
                    .map(p -> gson.<Map<String, Object>>fromJson(gson.toJson(p), tipoMapa));
            case "CATEGORIA":
                return categoriaRepository.buscarPorUuid(uuid)
                    .map(c -> gson.<Map<String, Object>>fromJson(gson.toJson(c), tipoMapa));
            default:
                return java.util.Optional.empty();
        }
    }

    private void marcarSincronizadosEnLocal(String entidad, List<String> uuids) {
        switch (entidad) {
            case "PERSONA" -> uuids.forEach(uuid -> personaRepository.buscarPorUuid(uuid).ifPresent(p -> {
                p.marcarSincronizado();
                personaRepository.guardar(p);
            }));
            case "OBJETO" -> uuids.forEach(uuid -> objetoRepository.buscarPorUuid(uuid).ifPresent(o -> {
                o.marcarSincronizado();
                objetoRepository.guardar(o);
            }));
            case "PRESTAMO" -> uuids.forEach(uuid -> prestamoRepository.buscarPorUuid(uuid).ifPresent(p -> {
                p.marcarSincronizado();
                prestamoRepository.guardar(p);
            }));
            case "CATEGORIA" -> uuids.forEach(uuid -> categoriaRepository.buscarPorUuid(uuid).ifPresent(c -> {
                c.marcarSincronizado();
                categoriaRepository.guardar(c);
            }));
            default -> { }
        }
    }

    private void descargarCambiosRemotos() {
        ZonedDateTime desde = obtenerUltimaSincronizacion();

        descargarEntidad("CATEGORIA", desde);
        descargarEntidad("PERSONA", desde);
        descargarEntidad("OBJETO", desde);
        descargarEntidad("PRESTAMO", desde);
    }

    private ZonedDateTime obtenerUltimaSincronizacion() {
        String valor = Configuracion.obtener().getUltimaSincronizacionExitosa();
        return valor != null ? ZonedDateTime.parse(valor, DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }

    @SuppressWarnings("unchecked")
    private void descargarEntidad(String entidad, ZonedDateTime desde) {
        List<Map<String, Object>> filas = repositorioRemoto.obtenerCambios(entidad, desde);
        for (Map<String, Object> fila : filas) {
            switch (entidad) {
                case "PERSONA" -> conciliarPersona(fila);
                case "OBJETO" -> conciliarObjeto(fila);
                case "PRESTAMO" -> conciliarPrestamo(fila);
                case "CATEGORIA" -> conciliarCategoria(fila);
                default -> { }
            }
        }
    }

    // Mapeo Map -> modelo de dominio según el esquema publicado por Codigo.gs
    // (ver apps-script/Codigo.gs y .claude/skills/apps-script-sheets-bridge).
    // Las claves llegan en snake_case porque Json.gson() usa
    // FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES tanto al construir la fila
    // que se envía como al leer la que llega — coinciden con las columnas
    // reales de la hoja. Patrón común a las cuatro entidades: last-write-wins
    // por fecha_modificacion, igual que RepositorioRemotoSimulado.esMasReciente(...).

    private void conciliarPersona(Map<String, Object> fila) {
        String uuid = asString(fila.get("uuid"));
        ZonedDateTime fechaRemota = parseFecha(fila.get("fecha_modificacion"));
        if (uuid == null || fechaRemota == null) {
            return;
        }
        Optional<PersonaBase> existente = personaRepository.buscarPorUuid(uuid);
        if (!remotoGana(fechaRemota, existente.map(PersonaBase::getFechaModificacion))) {
            return;
        }
        String nombre = asString(fila.get("nombre"));
        String telefono = asString(fila.get("telefono"));
        String correo = asString(fila.get("correo"));
        boolean eliminado = asBoolean(fila.get("eliminado"));
        PersonaBase persona = "ADMINISTRADOR".equals(asString(fila.get("tipo")))
            ? new Administrador(uuid, fechaRemota, true, eliminado, nombre, telefono, correo)
            : new UsuarioPrestatario(uuid, fechaRemota, true, eliminado, nombre, telefono, correo);
        personaRepository.guardar(persona);
    }

    private void conciliarObjeto(Map<String, Object> fila) {
        String uuid = asString(fila.get("uuid"));
        ZonedDateTime fechaRemota = parseFecha(fila.get("fecha_modificacion"));
        if (uuid == null || fechaRemota == null) {
            return;
        }
        Optional<Objeto> existente = objetoRepository.buscarPorUuid(uuid);
        if (!remotoGana(fechaRemota, existente.map(Objeto::getFechaModificacion))) {
            return;
        }
        String categoriaUuid = asString(fila.get("categoria_uuid"));
        String nombre = asString(fila.get("nombre"));
        String descripcion = asString(fila.get("descripcion"));
        Objeto.EstadoObjeto estado = parseEstadoObjeto(asString(fila.get("estado")));
        boolean eliminado = asBoolean(fila.get("eliminado"));
        Objeto objeto = new Objeto(uuid, fechaRemota, true, eliminado, categoriaUuid, nombre, descripcion, estado);
        objetoRepository.guardar(objeto);
    }

    private void conciliarCategoria(Map<String, Object> fila) {
        String uuid = asString(fila.get("uuid"));
        ZonedDateTime fechaRemota = parseFecha(fila.get("fecha_modificacion"));
        if (uuid == null || fechaRemota == null) {
            return;
        }
        Optional<Categoria> existente = categoriaRepository.buscarPorUuid(uuid);
        if (!remotoGana(fechaRemota, existente.map(Categoria::getFechaModificacion))) {
            return;
        }
        boolean eliminado = asBoolean(fila.get("eliminado"));
        Categoria categoria = new Categoria(uuid, fechaRemota, true, eliminado,
            asString(fila.get("nombre")), asString(fila.get("descripcion")));
        categoriaRepository.guardar(categoria);
    }

    /**
     * A diferencia de las otras tres entidades, la pestaña PRESTAMO (docx
     * §19.2) no incluye qué objeto(s) están prestados — eso vive en
     * DETALLE_PRESTAMO, que este servicio todavía no sincroniza. Un préstamo
     * conciliado desde Sheets llega entonces con `detalles` vacío: las
     * pantallas que muestran el nombre del objeto mostrarán "(sin objetos)"
     * para esos préstamos hasta que se agregue esa pestaña.
     */
    private void conciliarPrestamo(Map<String, Object> fila) {
        String uuid = asString(fila.get("uuid"));
        ZonedDateTime fechaRemota = parseFecha(fila.get("fecha_modificacion"));
        if (uuid == null || fechaRemota == null) {
            return;
        }
        Optional<Prestamo> existente = prestamoRepository.buscarPorUuid(uuid);
        if (!remotoGana(fechaRemota, existente.map(Prestamo::getFechaModificacion))) {
            return;
        }

        ZonedDateTime fechaHoraDevolucionPrevista = parseFecha(fila.get("fecha_hora_devolucion_prevista"));
        boolean fechaDevolucionCambio = existente.isEmpty()
            || !Objects.equals(fechaHoraDevolucionPrevista, existente.get().getFechaHoraDevolucionPrevista());

        Prestamo prestamo = new Prestamo(uuid, fechaRemota, true, asBoolean(fila.get("eliminado")),
            asString(fila.get("persona_uuid")), parseFecha(fila.get("fecha_prestamo")),
            fechaHoraDevolucionPrevista, parseFecha(fila.get("fecha_devolucion_real")),
            asInt(fila.get("minutos_recordatorio_previo")));

        // Igual que PrestamoService.registrarPrestamo: (re)programar antes de
        // guardar, porque programar()/cancelar() dejan su resultado en
        // prestamo.alerta, que debe quedar incluido en lo que se persiste.
        if (prestamo.estaDevuelto() || prestamo.isEliminado()) {
            alertaService.cancelar(prestamo);
        } else if (fechaDevolucionCambio) {
            alertaService.programar(prestamo);
        }
        prestamoRepository.guardar(prestamo);
    }

    /** El remoto gana si no hay copia local, o si su fecha_modificacion es estrictamente más nueva. */
    private static boolean remotoGana(ZonedDateTime fechaRemota, Optional<ZonedDateTime> fechaLocal) {
        return fechaLocal.isEmpty() || fechaRemota.isAfter(fechaLocal.get());
    }

    private static Objeto.EstadoObjeto parseEstadoObjeto(String valor) {
        try {
            return Objeto.EstadoObjeto.valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Objeto.EstadoObjeto.DISPONIBLE;
        }
    }

    private static ZonedDateTime parseFecha(Object valor) {
        return valor == null ? null : ZonedDateTime.parse(String.valueOf(valor), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String asString(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static boolean asBoolean(Object valor) {
        if (valor instanceof Boolean b) {
            return b;
        }
        return valor != null && Boolean.parseBoolean(String.valueOf(valor));
    }

    /** Sheets/Gson entregan números como Double incluso para campos int; nunca hacer (int) directo. */
    private static int asInt(Object valor) {
        if (valor instanceof Number n) {
            return n.intValue();
        }
        if (valor == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(valor).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
