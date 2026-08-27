package pe.edu.curso.prestamofacil.repository;

import pe.edu.curso.prestamofacil.model.Prestamo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PrestamoRepository {

    private static final String ARCHIVO = "prestamos.json";

    private final AlmacenLocal almacen;

    public PrestamoRepository(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    public List<Prestamo> listarActivos() {
        return leerTodos().stream()
            .filter(p -> !p.isEliminado() && !p.estaDevuelto())
            .collect(Collectors.toList());
    }

    public List<Prestamo> listarHistorial() {
        return leerTodos().stream()
            .filter(p -> !p.isEliminado() && p.estaDevuelto())
            .collect(Collectors.toList());
    }

    public Optional<Prestamo> buscarPorUuid(String uuid) {
        return leerTodos().stream().filter(p -> p.getUuid().equals(uuid)).findFirst();
    }

    /** RNF06: un objeto con préstamo activo no puede tener otro préstamo activo simultáneo. */
    public boolean tienePrestamoActivo(String objetoUuid) {
        return listarActivos().stream()
            .anyMatch(p -> p.getDetalles().stream()
                .anyMatch(d -> d.getObjetoUuid().equals(objetoUuid)));
    }

    public void guardar(Prestamo prestamo) {
        List<Prestamo> todos = leerTodos();
        todos.removeIf(p -> p.getUuid().equals(prestamo.getUuid()));
        todos.add(prestamo);
        almacen.escribirTodo(ARCHIVO, todos);
    }

    public List<Prestamo> leerTodos() {
        return almacen.leerTodo(ARCHIVO, Prestamo.class);
    }
}
