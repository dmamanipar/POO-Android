package pe.edu.curso.prestamofacil.view;

import com.gluonhq.charm.glisten.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Diálogo de confirmación reutilizable para acciones destructivas (dar de
 * baja). Usa com.gluonhq.charm.glisten.control.Alert (con estilo Glisten),
 * no javafx.scene.control.Alert — ver .claude/skills/gluon-glisten-ui/SKILL.md.
 * Los botones se agregan a mano y fijan el resultado explícitamente en vez de
 * depender de los botones por defecto que Alert pudiera agregar según el
 * AlertType, para no depender de un comportamiento no verificado.
 */
final class Dialogos {

    private Dialogos() { }

    static boolean confirmar(String titulo, String mensaje, String textoBotonConfirmar) {
        Alert alerta = new Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alerta.setTitleText(titulo);
        alerta.setContentText(mensaje);

        Button cancelar = new Button("Cancelar");
        Button confirmar = new Button(textoBotonConfirmar);
        cancelar.setOnAction(e -> alerta.setResult(ButtonType.CANCEL));
        confirmar.setOnAction(e -> alerta.setResult(ButtonType.OK));
        alerta.getButtons().addAll(cancelar, confirmar);

        Optional<ButtonType> resultado = alerta.showAndWait();
        return resultado.isPresent() && resultado.get() == ButtonType.OK;
    }
}
