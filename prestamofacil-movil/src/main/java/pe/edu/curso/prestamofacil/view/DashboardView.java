package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.model.EstadoPrestamo;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.service.CalculadorEstadoPrestamo;
import pe.edu.curso.prestamofacil.service.ObjetoService;
import pe.edu.curso.prestamofacil.service.PrestamoService;
import pe.edu.curso.prestamofacil.service.SincronizacionService;
import pe.edu.curso.prestamofacil.util.Configuracion;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pantalla inicial: cuatro tarjetas (RF11) + lista de préstamos activos con
 * semáforo + botón de sincronización manual (RF18). Es la vista de referencia:
 * las demás (Personas, Objetos, NuevoPréstamo, Historial) siguen el mismo
 * patrón view -> service, ver .claude/skills/gluon-glisten-ui/SKILL.md.
 */
public class DashboardView extends View {

    private final PrestamoService prestamoService;
    private final ObjetoService objetoService;
    private final SincronizacionService sincronizacionService;
    private final CalculadorEstadoPrestamo calculador = new CalculadorEstadoPrestamo();

    private final Label tarjetaDisponibles = tarjeta();
    private final Label tarjetaPrestados = tarjeta();
    private final Label tarjetaProximos = tarjeta();
    private final Label tarjetaAtrasados = tarjeta();
    private final Label estadoSincronizacion = new Label();
    private final ListView<String> listaPrestamos = new ListView<>();

    public DashboardView(PrestamoService prestamoService, ObjetoService objetoService,
                          SincronizacionService sincronizacionService) {
        this.prestamoService = prestamoService;
        this.objetoService = objetoService;
        this.sincronizacionService = sincronizacionService;

        setCenter(construirContenido());
        refrescar();
        // Las vistas se instancian una sola vez y se reutilizan (CachedFactory):
        // sin esto, volver al Dashboard desde otra pantalla muestra datos
        // congelados del momento en que se creó la vista, no los actuales.
        setOnShowing(e -> refrescar());
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("PréstamoFácil");
        appBar.getActionItems().add(MaterialDesignIcon.SYNC.button(e -> sincronizarAhora()));
        appBar.getActionItems().add(MaterialDesignIcon.REFRESH.button(e -> refrescar()));
        appBar.getActionItems().add(MaterialDesignIcon.SETTINGS.button(
            e -> MobileApplication.getInstance().switchView(pe.edu.curso.prestamofacil.Main.CONFIGURACION_VIEW)));
    }

    private VBox construirContenido() {
        GridPane tarjetas = new GridPane();
        tarjetas.setHgap(12);
        tarjetas.setVgap(12);
        tarjetas.setPadding(new Insets(16));
        tarjetas.addRow(0, envolver("Disponibles", tarjetaDisponibles), envolver("Prestados", tarjetaPrestados));
        tarjetas.addRow(1, envolver("Próx. a vencer", tarjetaProximos), envolver("Atrasados", tarjetaAtrasados));

        estadoSincronizacion.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        VBox raiz = new VBox(8, tarjetas, estadoSincronizacion, listaPrestamos);
        raiz.setPadding(new Insets(8));
        VBox.setVgrow(listaPrestamos, javafx.scene.layout.Priority.ALWAYS);
        return raiz;
    }

    private VBox envolver(String titulo, Label valor) {
        Label etiqueta = new Label(titulo);
        etiqueta.setStyle("-fx-font-size: 11px;");
        VBox caja = new VBox(4, valor, etiqueta);
        caja.setAlignment(Pos.CENTER);
        caja.setPadding(new Insets(12));
        caja.setStyle("-fx-background-color: -fx-background; -fx-background-radius: 8; "
            + "-fx-border-color: derive(-fx-background, -10%); -fx-border-radius: 8;");
        return caja;
    }

    private Label tarjeta() {
        Label l = new Label("0");
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        return l;
    }

    private void refrescar() {
        int disponibles = objetoService.listarDisponibles().size();
        int prestados = objetoService.listarTodos().size() - disponibles;

        List<Prestamo> activos = prestamoService.listarActivos();
        ZonedDateTime ahora = ZonedDateTime.now();

        long proximos = activos.stream()
            .filter(p -> calculador.calcular(p, ahora) == EstadoPrestamo.PROXIMO_A_VENCER)
            .count();
        long atrasados = activos.stream()
            .filter(p -> calculador.calcular(p, ahora) == EstadoPrestamo.ATRASADO)
            .count();

        tarjetaDisponibles.setText(String.valueOf(disponibles));
        tarjetaPrestados.setText(String.valueOf(prestados));
        tarjetaProximos.setText(String.valueOf(proximos));
        tarjetaAtrasados.setText(String.valueOf(atrasados));

        ObservableList<String> filas = FXCollections.observableArrayList(
            activos.stream()
                .map(p -> semaforo(calculador.calcular(p, ahora)) + "  vence "
                    + p.getFechaHoraDevolucionPrevista().toLocalDate() + " "
                    + p.getFechaHoraDevolucionPrevista().toLocalTime())
                .collect(Collectors.toList()));
        listaPrestamos.setItems(filas);

        if (!Configuracion.obtener().estaConfigurado() && estadoSincronizacion.getText().isBlank()) {
            estadoSincronizacion.setText("Funcionando sin conexión — toca el engranaje para sincronizar con Google Sheets.");
        }
    }

    private String semaforo(EstadoPrestamo estado) {
        return switch (estado) {
            case VIGENTE -> "🟢";
            case PROXIMO_A_VENCER -> "🟡";
            case ATRASADO -> "🔴";
            case DEVUELTO -> "⚪";
        };
    }

    private void sincronizarAhora() {
        if (!Configuracion.obtener().estaConfigurado()) {
            estadoSincronizacion.setText("Sin configurar todavía — toca el engranaje para agregar tu hoja de Sheets.");
            return;
        }
        estadoSincronizacion.setText("Sincronizando...");
        Task<SincronizacionService.ResultadoSincronizacion> tarea = new Task<>() {
            @Override
            protected SincronizacionService.ResultadoSincronizacion call() {
                return sincronizacionService.sincronizar();
            }
        };
        tarea.setOnSucceeded(e -> Platform.runLater(() -> {
            var r = tarea.getValue();
            estadoSincronizacion.setText(r.seEjecuto
                ? "Última sincronización: " + ZonedDateTime.now().toLocalTime()
                : "No se sincronizó: " + r.error);
            refrescar();
        }));
        tarea.setOnFailed(e -> Platform.runLater(() ->
            estadoSincronizacion.setText("Error al sincronizar: " + tarea.getException().getMessage())));
        new Thread(tarea, "sincronizacion-dashboard").start();
    }
}
