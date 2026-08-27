package pe.edu.curso.prestamofacil.repository;

import java.util.List;

/**
 * Abstracción del almacenamiento local. La implementación por defecto es JSON
 * (AlmacenLocalJson); el resto del sistema depende solo de esta interfaz,
 * de modo que podría reemplazarse por SQLite u otra base sin tocar los servicios.
 */
public interface AlmacenLocal {

    <T> List<T> leerTodo(String nombreArchivo, Class<T> tipo);

    <T> void escribirTodo(String nombreArchivo, List<T> elementos);
}
