package pe.edu.curso.prestamofacil.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import pe.edu.curso.prestamofacil.model.Administrador;
import pe.edu.curso.prestamofacil.model.PersonaBase;
import pe.edu.curso.prestamofacil.model.UsuarioPrestatario;

import java.lang.reflect.Type;

/**
 * PersonaBase es abstracta (herencia, ver docx §15.3): Gson no puede
 * instanciarla directamente al leer personas.json ni la fila PERSONA de
 * Sheets. Este adaptador guarda/lee el discriminador "tipo" (la misma columna
 * "tipo" de la pestaña PERSONA, docx §19.2) para reconstruir la subclase
 * concreta correcta.
 */
public class PersonaBaseAdapter implements JsonSerializer<PersonaBase>, JsonDeserializer<PersonaBase> {

    private static final String CAMPO_TIPO = "tipo";
    private static final String ADMINISTRADOR = "ADMINISTRADOR";
    private static final String PRESTATARIO = "PRESTATARIO";

    @Override
    public JsonElement serialize(PersonaBase src, Type typeOfSrc, JsonSerializationContext context) {
        boolean esAdministrador = src instanceof Administrador;
        Class<? extends PersonaBase> claseReal = esAdministrador ? Administrador.class : UsuarioPrestatario.class;
        JsonObject json = context.serialize(src, claseReal).getAsJsonObject();
        json.addProperty(CAMPO_TIPO, esAdministrador ? ADMINISTRADOR : PRESTATARIO);
        return json;
    }

    @Override
    public PersonaBase deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject objeto = json.getAsJsonObject();
        JsonElement tipo = objeto.get(CAMPO_TIPO);
        Class<? extends PersonaBase> claseDestino =
            tipo != null && ADMINISTRADOR.equals(tipo.getAsString()) ? Administrador.class : UsuarioPrestatario.class;
        return context.deserialize(objeto, claseDestino);
    }
}
