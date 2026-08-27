package pe.edu.curso.prestamofacil.service;

import pe.edu.curso.prestamofacil.model.Categoria;
import pe.edu.curso.prestamofacil.model.TipoOperacion;
import pe.edu.curso.prestamofacil.repository.CategoriaRepository;
import pe.edu.curso.prestamofacil.repository.ColaSincronizacion;

import java.util.List;

public class CategoriaService {

    private final CategoriaRepository repositorio;
    private final ColaSincronizacion cola;

    public CategoriaService(CategoriaRepository repositorio, ColaSincronizacion cola) {
        this.repositorio = repositorio;
        this.cola = cola;
    }

    public Categoria registrar(String nombre, String descripcion) {
        Categoria categoria = new Categoria(nombre, descripcion);
        repositorio.guardar(categoria);
        cola.encolar("CATEGORIA", categoria.getUuid(), TipoOperacion.CREAR);
        return categoria;
    }

    public void eliminar(String uuid) {
        repositorio.buscarPorUuid(uuid).ifPresent(c -> {
            c.marcarEliminado();
            repositorio.guardar(c);
            cola.encolar("CATEGORIA", uuid, TipoOperacion.ELIMINAR);
        });
    }

    public List<Categoria> listar() {
        return repositorio.listarActivas();
    }
}
