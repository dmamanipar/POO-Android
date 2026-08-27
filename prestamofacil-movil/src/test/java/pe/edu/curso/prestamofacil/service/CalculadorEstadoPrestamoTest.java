package pe.edu.curso.prestamofacil.service;

import org.junit.jupiter.api.Test;
import pe.edu.curso.prestamofacil.model.EstadoPrestamo;
import pe.edu.curso.prestamofacil.model.Prestamo;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadorEstadoPrestamoTest {

    private final CalculadorEstadoPrestamo calculador = new CalculadorEstadoPrestamo();

    @Test
    void prestamoVigenteLejosDelVencimiento() {
        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo p = new Prestamo("persona-1", ahora.minusDays(1), ahora.plusDays(5), 0);
        assertEquals(EstadoPrestamo.VIGENTE, calculador.calcular(p, ahora));
    }

    @Test
    void prestamoProximoAVencerDentroDe24Horas() {
        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo p = new Prestamo("persona-1", ahora.minusDays(1), ahora.plusHours(10), 0);
        assertEquals(EstadoPrestamo.PROXIMO_A_VENCER, calculador.calcular(p, ahora));
    }

    @Test
    void prestamoAtrasadoSiYaPasoLaFecha() {
        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo p = new Prestamo("persona-1", ahora.minusDays(5), ahora.minusHours(1), 0);
        assertEquals(EstadoPrestamo.ATRASADO, calculador.calcular(p, ahora));
    }

    @Test
    void prestamoDevueltoSiTieneFechaDevolucionReal() {
        ZonedDateTime ahora = ZonedDateTime.now();
        Prestamo p = new Prestamo("persona-1", ahora.minusDays(5), ahora.minusHours(1), 0);
        p.registrarDevolucion(ahora);
        assertEquals(EstadoPrestamo.DEVUELTO, calculador.calcular(p, ahora));
    }
}
