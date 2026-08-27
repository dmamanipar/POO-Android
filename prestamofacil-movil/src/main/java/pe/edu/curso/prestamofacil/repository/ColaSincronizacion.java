package pe.edu.curso.prestamofacil.repository;

import pe.edu.curso.prestamofacil.model.OperacionPendiente;
import pe.edu.curso.prestamofacil.model.TipoOperacion;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cola FIFO de operaciones pendientes de subir a Google Sheets.
 * Una operación solo se retira después de que el servidor confirme que la
 * aceptó (ver SincronizacionService) — nunca se retira de forma optimista.
 */
public class ColaSincronizacion {

    private static final String ARCHIVO = "cola_sincronizacion.json";

    private final AlmacenLocal almacen;

    public ColaSincronizacion(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    public synchronized void encolar(String entidad, String uuidEntidad, TipoOperacion tipo) {
        List<OperacionPendiente> cola = leer();
        cola.add(new OperacionPendiente(entidad, uuidEntidad, tipo, ZonedDateTime.now()));
        almacen.escribirTodo(ARCHIVO, cola);
    }

    public synchronized List<OperacionPendiente> leer() {
        List<OperacionPendiente> cola = almacen.leerTodo(ARCHIVO, OperacionPendiente.class);
        return cola != null ? cola : new ArrayList<>();
    }

    /** Retira solo las operaciones cuyo uuidEntidad esté en la lista de aceptados. */
    public synchronized void retirarAceptadas(List<String> uuidsAceptados) {
        List<OperacionPendiente> cola = leer();
        cola.removeIf(op -> uuidsAceptados.contains(op.getUuidEntidad()));
        almacen.escribirTodo(ARCHIVO, cola);
    }

    public synchronized boolean estaVacia() {
        return leer().isEmpty();
    }
}
