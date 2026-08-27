package pe.edu.curso.prestamofacil.repository;

import pe.edu.curso.prestamofacil.model.Categoria;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CategoriaRepository {

    private static final String ARCHIVO = "categorias.json";

    private final AlmacenLocal almacen;

    public CategoriaRepository(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    public List<Categoria> listarActivas() {
        return leerTodas().stream().filter(c -> !c.isEliminado()).collect(Collectors.toList());
    }

    public Optional<Categoria> buscarPorUuid(String uuid) {
        return leerTodas().stream().filter(c -> c.getUuid().equals(uuid)).findFirst();
    }

    public void guardar(Categoria categoria) {
        List<Categoria> todas = leerTodas();
        todas.removeIf(c -> c.getUuid().equals(categoria.getUuid()));
        todas.add(categoria);
        almacen.escribirTodo(ARCHIVO, todas);
    }

    public List<Categoria> leerTodas() {
        return almacen.leerTodo(ARCHIVO, Categoria.class);
    }
}
