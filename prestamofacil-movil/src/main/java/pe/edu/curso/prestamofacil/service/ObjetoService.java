package pe.edu.curso.prestamofacil.service;

import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.TipoOperacion;
import pe.edu.curso.prestamofacil.repository.ColaSincronizacion;
import pe.edu.curso.prestamofacil.repository.ObjetoRepository;

import java.util.List;
import java.util.Optional;

public class ObjetoService {

    private final ObjetoRepository repositorio;
    private final ColaSincronizacion cola;

    public ObjetoService(ObjetoRepository repositorio, ColaSincronizacion cola) {
        this.repositorio = repositorio;
        this.cola = cola;
    }

    public Objeto registrar(String categoriaUuid, String nombre, String descripcion) {
        Objeto objeto = new Objeto(categoriaUuid, nombre, descripcion);
        repositorio.guardar(objeto);
        cola.encolar("OBJETO", objeto.getUuid(), TipoOperacion.CREAR);
        return objeto;
    }

    public void actualizar(Objeto objeto) {
        repositorio.guardar(objeto);
        cola.encolar("OBJETO", objeto.getUuid(), TipoOperacion.ACTUALIZAR);
    }

    /** RNF06: un objeto con préstamo activo no se puede dar de baja. */
    public void eliminar(String uuid) {
        repositorio.buscarPorUuid(uuid).ifPresent(o -> {
            if (!o.estaDisponible()) {
                throw new IllegalStateException(
                    "No se puede eliminar \"" + o.getNombre() + "\": todavía está prestado.");
            }
            o.marcarEliminado();
            repositorio.guardar(o);
            cola.encolar("OBJETO", uuid, TipoOperacion.ELIMINAR);
        });
    }

    public List<Objeto> listarDisponibles() {
        return repositorio.listarDisponibles();
    }

    public List<Objeto> listarTodos() {
        return repositorio.listarActivos();
    }

    public Optional<Objeto> buscarPorUuid(String uuid) {
        return repositorio.buscarPorUuid(uuid);
    }
}
