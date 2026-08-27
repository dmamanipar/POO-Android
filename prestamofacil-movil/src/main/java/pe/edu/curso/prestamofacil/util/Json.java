package pe.edu.curso.prestamofacil.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import pe.edu.curso.prestamofacil.model.PersonaBase;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gson único de la app, con soporte para ZonedDateTime en ISO-8601 con offset.
 *
 * Usa LOWER_CASE_WITH_UNDERSCORES para que los nombres de campo Java
 * (fechaModificacion, personaUuid, ...) coincidan exactamente con las
 * columnas snake_case de la hoja de Sheets (fecha_modificacion, persona_uuid,
 * ...; ver docx §19.2 y apps-script/Codigo.gs) y con las claves que usa
 * RepositorioRemotoSimulado — sin esto, SincronizacionService envía mapas con
 * "fechaModificacion" pero el resto del sistema busca "fecha_modificacion".
 */
public final class Json {

    private static final Gson INSTANCIA = new GsonBuilder()
        .setPrettyPrinting()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
        .registerTypeAdapter(PersonaBase.class, new PersonaBaseAdapter())
        .create();

    private Json() { }

    public static Gson gson() {
        return INSTANCIA;
    }

    private static class ZonedDateTimeAdapter extends TypeAdapter<ZonedDateTime> {
        @Override
        public void write(JsonWriter out, ZonedDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        }

        @Override
        public ZonedDateTime read(JsonReader in) throws IOException {
            String texto = in.nextString();
            return texto == null ? null : ZonedDateTime.parse(texto, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
}
