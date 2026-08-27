package pe.edu.curso.prestamofacil.cloud;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import pe.edu.curso.prestamofacil.util.Configuracion;
import pe.edu.curso.prestamofacil.util.Json;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP puro (java.net.http, sin SDK de Google) hacia el Web App de
 * Apps Script. Ver .claude/skills/apps-script-sheets-bridge/SKILL.md para el
 * contrato completo de la API.
 */
public class ClienteHttpSheets implements RepositorioRemoto {

    // Apps Script Web Apps responden con una redirección 302 hacia
    // script.googleusercontent.com/macros/echo?... (tanto en GET como en
    // POST) antes de entregar el cuerpo real. HttpClient.Builder no sigue
    // redirecciones por defecto (política NEVER) — sin esto, cada llamada
    // recibiría un 302 y este cliente lo trataría como error.
    private final HttpClient cliente = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final Gson gson = Json.gson();

    @Override
    public List<Map<String, Object>> obtenerCambios(String entidad, ZonedDateTime desde) {
        Configuracion config = Configuracion.obtener();
        if (!config.estaConfigurado()) {
            throw new IllegalStateException("Configuracion.urlWebApp/token no están configurados.");
        }
        String desdeTexto = desde != null ? desde.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "";
        String url = config.getUrlWebApp()
            + "?entidad=" + enc(entidad)
            + "&desde=" + enc(desdeTexto)
            + "&token=" + enc(config.getToken());

        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();

        try {
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new IOException("HTTP " + respuesta.statusCode() + ": " + respuesta.body());
            }
            Type tipoRespuesta = new TypeToken<Map<String, Object>>() { }.getType();
            Map<String, Object> cuerpo = gson.fromJson(respuesta.body(), tipoRespuesta);
            Object filas = cuerpo.get("filas");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultado = (List<Map<String, Object>>) (List<?>)
                (filas instanceof List ? (List<?>) filas : new ArrayList<>());
            return resultado;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("No se pudieron obtener cambios remotos de " + entidad, e);
        }
    }

    @Override
    public SincronizacionResultado enviarCambios(String entidad, List<Map<String, Object>> registros) {
        Configuracion config = Configuracion.obtener();
        if (!config.estaConfigurado()) {
            return new SincronizacionResultado(false, List.of(), Map.of(), "Configuracion incompleta");
        }

        Map<String, Object> cuerpo = Map.of(
            "token", config.getToken(),
            "entidad", entidad,
            "registros", registros);

        HttpRequest peticion = HttpRequest.newBuilder(URI.create(config.getUrlWebApp()))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(cuerpo), StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                return new SincronizacionResultado(false, List.of(), Map.of(),
                    "HTTP " + respuesta.statusCode() + ": " + respuesta.body());
            }
            Type tipoRespuesta = new TypeToken<Map<String, Object>>() { }.getType();
            Map<String, Object> json = gson.fromJson(respuesta.body(), tipoRespuesta);

            @SuppressWarnings("unchecked")
            List<String> aceptados = (List<String>) (List<?>) json.getOrDefault("aceptados", List.of());

            return new SincronizacionResultado(true, aceptados, Map.of(), null);
        } catch (IOException | InterruptedException e) {
            return new SincronizacionResultado(false, List.of(), Map.of(), e.getMessage());
        }
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
