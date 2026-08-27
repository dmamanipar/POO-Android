package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.Toast;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pe.edu.curso.prestamofacil.model.Objeto;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.Prestamo;
import pe.edu.curso.prestamofacil.service.ObjetoService;
import pe.edu.curso.prestamofacil.service.PersonaService;
import pe.edu.curso.prestamofacil.service.PrestamoService;
import pe.edu.curso.prestamofacil.util.Configuracion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * RF05: registrar un préstamo. Sigue la recomendación del docx §14 (un
 * préstamo ↔ un objeto para el MVP) y programa la alerta de devolución al
 * guardar (RF13, vía PrestamoService → AlertaService).
 */
public class NuevoPrestamoView extends View {

    private final PersonaService personaService;
    private final ObjetoService objetoService;
    private final PrestamoService prestamoService;

    private final ComboBox<PersonaBase> comboPersona = new ComboBox<>();
    private final ComboBox<Objeto> comboObjeto = new ComboBox<>();
    private final DatePicker fechaDevolucion = new DatePicker(LocalDate.now());
    private final ChoiceBox<Integer> hora = new ChoiceBox<>(
        FXCollections.observableArrayList(java.util.stream.IntStream.range(0, 24).boxed().toList()));
    private final ChoiceBox<Integer> minuto = new ChoiceBox<>(
        FXCollections.observableArrayList(0, 15, 30, 45));
    private final Spinner<Integer> minutosRecordatorio =
        new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 7 * 24 * 60, 0, 30));
    private final Label estado = new Label();

    public NuevoPrestamoView(PersonaService personaService, ObjetoService objetoService,
                              PrestamoService prestamoService) {
        this.personaService = personaService;
        this.objetoService = objetoService;
        this.prestamoService = prestamoService;
        setCenter(construirContenido());
        cargarOpciones();
        setOnShowing(e -> cargarOpciones());
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(
            e -> MobileApplication.getInstance().getDrawer().open()));
        appBar.setTitleText("Nuevo préstamo");
        appBar.getActionItems().add(MaterialDesignIcon.REFRESH.button(e -> cargarOpciones()));
    }

    private VBox construirContenido() {
        comboPersona.setPromptText("Persona");
        comboPersona.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(PersonaBase p) { return p == null ? "" : p.getNombre(); }
            @Override public PersonaBase fromString(String s) { return null; }
        });

        comboObjeto.setPromptText("Objeto disponible");
        comboObjeto.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Objeto o) { return o == null ? "" : o.getNombre(); }
            @Override public Objeto fromString(String s) { return null; }
        });

        hora.setValue(18);
        minuto.setValue(0);
        HBox filaHora = new HBox(6, hora, new Label(":"), minuto);

        Button botonRegistrar = new Button("Registrar préstamo");
        botonRegistrar.setOnAction(e -> registrar());
        estado.setWrapText(true);
        estado.setStyle("-fx-font-size: 12px;");

        VBox raiz = new VBox(6,
            new Label("Persona*"), comboPersona,
            new Label("Objeto*"), comboObjeto,
            new Label("Fecha de devolución prevista*"), fechaDevolucion,
            new Label("Hora de devolución*"), filaHora,
            new Label("Recordarme antes (minutos)"), minutosRecordatorio,
            botonRegistrar, estado);
        raiz.setPadding(new Insets(16));
        return raiz;
    }

    private void cargarOpciones() {
        List<PersonaBase> personas = personaService.listar();
        comboPersona.setItems(FXCollections.observableArrayList(personas));

        List<Objeto> disponibles = objetoService.listarDisponibles();
        comboObjeto.setItems(FXCollections.observableArrayList(disponibles));

        minutosRecordatorio.getValueFactory().setValue(Configuracion.obtener().getMinutosRecordatorioPorDefecto());

        estado.setStyle("-fx-font-size: 12px;");
        if (personas.isEmpty()) {
            estado.setText("No hay personas registradas todavía — ve a Personas y registra una primero.");
        } else if (disponibles.isEmpty()) {
            estado.setText("No hay objetos disponibles todavía — ve a Objetos y registra uno primero.");
        } else {
            estado.setText("");
        }
    }

    private void registrar() {
        PersonaBase persona = comboPersona.getValue();
        Objeto objeto = comboObjeto.getValue();
        LocalDate fecha = fechaDevolucion.getValue();
        Integer h = hora.getValue();
        Integer m = minuto.getValue();

        if (persona == null || objeto == null || fecha == null || h == null || m == null) {
            estado.setText("Completa persona, objeto, fecha y hora de devolución.");
            return;
        }

        ZonedDateTime fechaHoraDevolucion = ZonedDateTime.of(
            fecha.atTime(h, m), ZoneId.systemDefault());

        try {
            Prestamo prestamo = prestamoService.registrarPrestamo(persona.getUuid(),
                List.of(objeto.getUuid()), ZonedDateTime.now(), fechaHoraDevolucion,
                minutosRecordatorio.getValue());
            estado.setStyle("-fx-font-size: 12px; -fx-text-fill: green;");
            estado.setText("Préstamo registrado: " + objeto.getNombre() + " a " + persona.getNombre()
                + ", vence " + prestamo.getFechaHoraDevolucionPrevista().toLocalDate() + " "
                + prestamo.getFechaHoraDevolucionPrevista().toLocalTime() + ".");
            comboObjeto.setValue(null);
            cargarOpciones();
            new Toast("Préstamo registrado").show();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            estado.setStyle("-fx-font-size: 12px; -fx-text-fill: #b00020;");
            estado.setText(ex.getMessage());
        }
    }
}
