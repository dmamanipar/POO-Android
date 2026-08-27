package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.Toast;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.service.SincronizacionService;
import pe.edu.curso.prestamofacil.util.Configuracion;

/**
 * Pantalla donde el usuario, sin tocar el código, pega la URL del Web App de
 * Apps Script y el token que obtuvo al publicar apps-script/Codigo.gs sobre
 * su propia hoja de Google Sheets (ver README, "Publicar el puente de Google
 * Sheets"). Es el punto de entrada previsto por RNF10: la URL/token viven
 * solo en config.json (util/Configuracion), nunca en el código fuente.
 *
 * Si no hay nada configurado todavía, la app sigue funcionando normalmente en
 * modo local (RNF07); esta pantalla es lo que permite pasar de "solo local" a
 * "sincronizado con Sheets" sin reinstalar ni recompilar nada.
 */
public class ConfiguracionView extends View {

    private final SincronizacionService sincronizacionService;

    private final TextField campoUrl = new TextField();
    private final PasswordField campoToken = new PasswordField();
    private final Spinner<Integer> campoMinutosRecordatorio =
        new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 7 * 24 * 60, 24 * 60, 30));
    private final Label estado = new Label();
    private final ProgressIndicator progreso = new ProgressIndicator();
    private final Button botonProbar = new Button("Guardar y sincronizar ahora");

    public ConfiguracionView(SincronizacionService sincronizacionService) {
        this.sincronizacionService = sincronizacionService;
        setCenter(construirContenido());
        cargarDesdeConfiguracion();
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("Configuración de sincronización");
    }

    private VBox construirContenido() {
        campoUrl.setPromptText("https://script.google.com/macros/s/AKfycb.../exec");
        campoToken.setPromptText("Token del script (Propiedades del script > TOKEN)");
        progreso.setVisible(false);
        progreso.setMaxSize(24, 24);

        Label tituloUrl = new Label("URL del Web App de Apps Script");
        Label tituloToken = new Label("Token compartido");
        Label tituloMinutos = new Label("Recordatorio previo por defecto (minutos antes de la devolución)");
        Label ayuda = new Label(
            "1) Crea una hoja de Google Sheets con las pestañas PERSONA, CATEGORIA, OBJETO, PRESTAMO.\n"
            + "2) Extensiones > Apps Script, pega apps-script/Codigo.gs.\n"
            + "3) Configuración del proyecto > Propiedades del script > agrega TOKEN.\n"
            + "4) Implementar > Nueva implementación > Aplicación web (acceso: cualquiera con el enlace).\n"
            + "5) Pega aquí la URL de esa implementación y el mismo TOKEN.");
        ayuda.setWrapText(true);
        ayuda.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        estado.setWrapText(true);
        estado.setStyle("-fx-font-size: 12px;");

        Button botonGuardar = new Button("Guardar");
        botonGuardar.setOnAction(e -> guardar(false));
        botonProbar.setOnAction(e -> guardar(true));

        VBox botones = new VBox(8, botonGuardar, botonProbar, progreso);

        VBox raiz = new VBox(6,
            tituloUrl, campoUrl,
            tituloToken, campoToken,
            tituloMinutos, campoMinutosRecordatorio,
            botones, estado, ayuda);
        raiz.setPadding(new Insets(16));
        return raiz;
    }

    private void cargarDesdeConfiguracion() {
        Configuracion config = Configuracion.obtener();
        campoUrl.setText(config.getUrlWebApp());
        campoToken.setText(config.getToken());
        campoMinutosRecordatorio.getValueFactory().setValue(config.getMinutosRecordatorioPorDefecto());
        actualizarEstado(config);
    }

    private void actualizarEstado(Configuracion config) {
        if (!config.estaConfigurado()) {
            estado.setText("Sin configurar todavía: la app funciona en modo local; "
                + "guarda la URL y el token para sincronizar con Google Sheets.");
            return;
        }
        String ultima = config.getUltimaSincronizacionExitosa();
        estado.setText("Configurado. " + (ultima != null
            ? "Última sincronización correcta: " + ultima
            : "Todavía no se ha sincronizado con éxito — usa \"Guardar y sincronizar ahora\"."));
    }

    private void guardar(boolean sincronizarDespues) {
        String url = campoUrl.getText() == null ? "" : campoUrl.getText().trim();
        String token = campoToken.getText() == null ? "" : campoToken.getText().trim();

        if (!url.isBlank() && !(url.startsWith("https://") || url.startsWith("http://"))) {
            estado.setText("La URL debe empezar con https:// (copia la URL de la implementación del Web App).");
            return;
        }

        Configuracion config = Configuracion.obtener();
        config.setUrlWebApp(url);
        config.setToken(token);
        config.setMinutosRecordatorioPorDefecto(campoMinutosRecordatorio.getValue());
        config.guardar();

        if (!sincronizarDespues) {
            estado.setText("Configuración guardada.");
            new Toast("Configuración guardada").show();
            return;
        }

        if (!config.estaConfigurado()) {
            estado.setText("Guardado, pero falta la URL o el token: sin ambos no se puede sincronizar.");
            return;
        }

        sincronizarAhora();
    }

    private void sincronizarAhora() {
        botonProbar.setDisable(true);
        progreso.setVisible(true);
        estado.setText("Conectando con Google Sheets...");

        Task<SincronizacionService.ResultadoSincronizacion> tarea = new Task<>() {
            @Override
            protected SincronizacionService.ResultadoSincronizacion call() {
                return sincronizacionService.sincronizar();
            }
        };
        tarea.setOnSucceeded(e -> Platform.runLater(() -> {
            botonProbar.setDisable(false);
            progreso.setVisible(false);
            var r = tarea.getValue();
            if (r.seEjecuto && r.error == null) {
                estado.setText("Conexión correcta. Enviados " + r.operacionesEnviadas
                    + ", confirmados " + r.operacionesConfirmadas + ".");
                new Toast("Sincronizado con Google Sheets").show();
            } else {
                estado.setText("No se pudo sincronizar: " + r.error);
            }
            actualizarEstado(Configuracion.obtener());
        }));
        tarea.setOnFailed(e -> Platform.runLater(() -> {
            botonProbar.setDisable(false);
            progreso.setVisible(false);
            estado.setText("Error al sincronizar: " + tarea.getException().getMessage());
        }));
        new Thread(tarea, "sincronizacion-configuracion").start();
    }
}
