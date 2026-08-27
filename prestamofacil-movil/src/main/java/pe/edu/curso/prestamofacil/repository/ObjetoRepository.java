package pe.edu.curso.prestamofacil.repository;

import pe.edu.curso.prestamofacil.model.Objeto;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ObjetoRepository {

    private static final String ARCHIVO = "objetos.json";

    private final AlmacenLocal almacen;

    public ObjetoRepository(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    public List<Objeto> listarActivos() {
        return leerTodos().stream().filter(o -> !o.isEliminado()).collect(Collectors.toList());
    }

    public List<Objeto> listarDisponibles() {
        return listarActivos().stream().filter(Objeto::estaDisponible).collect(Collectors.toList());
    }

    public Optional<Objeto> buscarPorUuid(String uuid) {
        return leerTodos().stream().filter(o -> o.getUuid().equals(uuid)).findFirst();
    }

    public void guardar(Objeto objeto) {
        List<Objeto> todos = leerTodos();
        todos.removeIf(o -> o.getUuid().equals(objeto.getUuid()));
        todos.add(objeto);
        almacen.escribirTodo(ARCHIVO, todos);
    }

    public List<Objeto> leerTodos() {
        return almacen.leerTodo(ARCHIVO, Objeto.class);
    }
}
