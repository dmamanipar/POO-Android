package pe.edu.curso.prestamofacil.service;

import pe.edu.curso.prestamofacil.model.EstadoPrestamo;
import pe.edu.curso.prestamofacil.model.Prestamo;

import java.time.Duration;
import java.time.ZonedDateTime;

/** Calcula el estado del semáforo sin depender de internet ni de un campo "estado" persistido. */
public class CalculadorEstadoPrestamo {

    /** Ventana antes del vencimiento que se considera "próximo a vencer". */
    private final Duration ventanaProximoAVencer;

    public CalculadorEstadoPrestamo() {
        this(Duration.ofHours(24));
    }

    public CalculadorEstadoPrestamo(Duration ventanaProximoAVencer) {
        this.ventanaProximoAVencer = ventanaProximoAVencer;
    }

    public EstadoPrestamo calcular(Prestamo prestamo, ZonedDateTime ahora) {
        if (prestamo.estaDevuelto()) {
            return EstadoPrestamo.DEVUELTO;
        }
        ZonedDateTime vencimiento = prestamo.getFechaHoraDevolucionPrevista();
        if (ahora.isAfter(vencimiento)) {
            return EstadoPrestamo.ATRASADO;
        }
        Duration restante = Duration.between(ahora, vencimiento);
        if (restante.compareTo(ventanaProximoAVencer) <= 0) {
            return EstadoPrestamo.PROXIMO_A_VENCER;
        }
        return EstadoPrestamo.VIGENTE;
    }
}
