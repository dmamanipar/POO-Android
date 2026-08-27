package pe.edu.curso.prestamofacil.cloud;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Puerto hacia "la nube". RepositorioGoogleSheets es la implementación real
 * (vía Apps Script); RepositorioRemotoSimulado permite probar SincronizacionService
 * sin red ni una hoja real (polimorfismo).
 */
public interface RepositorioRemoto {

    /** Filas de esa entidad modificadas después de `desde` (null = todas). */
    List<Map<String, Object>> obtenerCambios(String entidad, ZonedDateTime desde);

    /** Envía registros (cada uno como mapa de columnas) y devuelve el resultado del upsert. */
    SincronizacionResultado enviarCambios(String entidad, List<Map<String, Object>> registros);
}
