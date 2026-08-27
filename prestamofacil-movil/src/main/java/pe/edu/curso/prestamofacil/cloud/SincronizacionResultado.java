package pe.edu.curso.prestamofacil.cloud;

import java.util.List;
import java.util.Map;

public class SincronizacionResultado {
    public final boolean exitoso;
    public final List<String> aceptados;
    public final Map<String, String> rechazados; // uuid -> motivo
    public final String error;

    public SincronizacionResultado(boolean exitoso, List<String> aceptados,
                                    Map<String, String> rechazados, String error) {
        this.exitoso = exitoso;
        this.aceptados = aceptados;
        this.rechazados = rechazados;
        this.error = error;
    }
}
