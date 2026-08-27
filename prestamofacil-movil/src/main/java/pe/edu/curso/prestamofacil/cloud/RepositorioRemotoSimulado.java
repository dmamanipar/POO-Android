package pe.edu.curso.prestamofacil.cloud;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria de RepositorioRemoto, para pruebas unitarias de
 * SincronizacionService sin red ni una hoja de cálculo real.
 * Ver .claude/agents/prestamofacil-qa.md.
 */
public class RepositorioRemotoSimulado implements RepositorioRemoto {

    // entidad -> uuid -> fila
    private final Map<String, Map<String, Map<String, Object>>> almacen = new ConcurrentHashMap<>();
    private boolean fallarSiguienteEnvio = false;

    public void simularFalloDeRed() {
        this.fallarSiguienteEnvio = true;
    }

    @Override
    public List<Map<String, Object>> obtenerCambios(String entidad, ZonedDateTime desde) {
        Map<String, Map<String, Object>> tabla = almacen.getOrDefault(entidad, Map.of());
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> fila : tabla.values()) {
            String fechaTexto = (String) fila.get("fecha_modificacion");
            if (desde == null || fechaTexto == null
                || ZonedDateTime.parse(fechaTexto).isAfter(desde)) {
                resultado.add(new HashMap<>(fila));
            }
        }
        return resultado;
    }

    @Override
    public SincronizacionResultado enviarCambios(String entidad, List<Map<String, Object>> registros) {
        if (fallarSiguienteEnvio) {
            fallarSiguienteEnvio = false;
            return new SincronizacionResultado(false, List.of(), Map.of(), "Fallo de red simulado");
        }

        Map<String, Map<String, Object>> tabla =
            almacen.computeIfAbsent(entidad, k -> new ConcurrentHashMap<>());
        List<String> aceptados = new ArrayList<>();
        Map<String, String> rechazados = new HashMap<>();

        for (Map<String, Object> registro : registros) {
            String uuid = (String) registro.get("uuid");
            Map<String, Object> existente = tabla.get(uuid);
            if (existente == null || esMasReciente(registro, existente)) {
                tabla.put(uuid, new HashMap<>(registro));
                aceptados.add(uuid);
            } else {
                rechazados.put(uuid, "version_mas_antigua");
            }
        }
        return new SincronizacionResultado(true, aceptados, rechazados, null);
    }

    private boolean esMasReciente(Map<String, Object> nuevo, Map<String, Object> existente) {
        ZonedDateTime fechaNueva = ZonedDateTime.parse((String) nuevo.get("fecha_modificacion"));
        ZonedDateTime fechaVieja = ZonedDateTime.parse((String) existente.get("fecha_modificacion"));
        return fechaNueva.isAfter(fechaVieja);
    }
}
