package pe.edu.curso.prestamofacil.service;

import pe.edu.curso.prestamofacil.model.DetallePrestamo;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.model.TipoOperacion;
import pe.edu.curso.prestamofacil.repository.ColaSincronizacion;
import pe.edu.curso.prestamofacil.repository.ObjetoRepository;
import pe.edu.curso.prestamofacil.repository.PrestamoRepository;

import java.time.ZonedDateTime;
import java.util.List;

public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final ObjetoRepository objetoRepository;
    private final ColaSincronizacion cola;
    private final AlertaService alertaService;

    public PrestamoService(PrestamoRepository prestamoRepository, ObjetoRepository objetoRepository,
                            ColaSincronizacion cola, AlertaService alertaService) {
        this.prestamoRepository = prestamoRepository;
        this.objetoRepository = objetoRepository;
        this.cola = cola;
        this.alertaService = alertaService;
    }

    public Prestamo registrarPrestamo(String personaUuid, List<String> objetoUuids,
                                       ZonedDateTime fechaPrestamo,
                                       ZonedDateTime fechaHoraDevolucionPrevista,
                                       int minutosRecordatorioPrevio) {
        for (String objetoUuid : objetoUuids) {
            if (prestamoRepository.tienePrestamoActivo(objetoUuid)) {
                Objeto objeto = objetoRepository.buscarPorUuid(objetoUuid).orElse(null);
                String nombre = objeto != null ? objeto.getNombre() : objetoUuid;
                throw new IllegalStateException("El objeto \"" + nombre + "\" ya tiene un préstamo activo.");
            }
        }

        Prestamo prestamo = new Prestamo(personaUuid, fechaPrestamo,
            fechaHoraDevolucionPrevista, minutosRecordatorioPrevio);

        for (String objetoUuid : objetoUuids) {
            prestamo.agregarDetalle(new DetallePrestamo(objetoUuid, null));
            objetoRepository.buscarPorUuid(objetoUuid).ifPresent(o -> {
                o.marcarPrestado();
                objetoRepository.guardar(o);
                cola.encolar("OBJETO", o.getUuid(), TipoOperacion.ACTUALIZAR);
            });
        }

        alertaService.programar(prestamo);
        prestamoRepository.guardar(prestamo);
        cola.encolar("PRESTAMO", prestamo.getUuid(), TipoOperacion.CREAR);
        return prestamo;
    }

    public void registrarDevolucion(String prestamoUuid) {
        Prestamo prestamo = prestamoRepository.buscarPorUuid(prestamoUuid)
            .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado: " + prestamoUuid));

        prestamo.registrarDevolucion(ZonedDateTime.now());
        alertaService.cancelar(prestamo);

        for (DetallePrestamo detalle : prestamo.getDetalles()) {
            objetoRepository.buscarPorUuid(detalle.getObjetoUuid()).ifPresent(o -> {
                o.marcarDisponible();
                objetoRepository.guardar(o);
                cola.encolar("OBJETO", o.getUuid(), TipoOperacion.ACTUALIZAR);
            });
        }

        prestamoRepository.guardar(prestamo);
        cola.encolar("PRESTAMO", prestamo.getUuid(), TipoOperacion.ACTUALIZAR);
    }

    public void reprogramarDevolucion(String prestamoUuid, ZonedDateTime nuevaFechaHora) {
        Prestamo prestamo = prestamoRepository.buscarPorUuid(prestamoUuid)
            .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado: " + prestamoUuid));
        prestamo.reprogramarDevolucion(nuevaFechaHora);
        alertaService.programar(prestamo); // cancela la anterior y reprograma
        prestamoRepository.guardar(prestamo);
        cola.encolar("PRESTAMO", prestamo.getUuid(), TipoOperacion.ACTUALIZAR);
    }

    public List<Prestamo> listarActivos() {
        return prestamoRepository.listarActivos();
    }

    public List<Prestamo> listarHistorial() {
        return prestamoRepository.listarHistorial();
    }
}
