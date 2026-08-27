package pe.edu.curso.prestamofacil.util;

import com.gluonhq.attach.storage.StorageService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Configuración persistida en config.json dentro del almacenamiento privado
 * del dispositivo. Nunca commitear un config.json real con token al repositorio.
 */
public class Configuracion {

    private static final String ARCHIVO = "config.json";
    private static Configuracion instancia;

    private String urlWebApp = "";
    private String token = "";
    private int minutosRecordatorioPorDefecto = 24 * 60; // 24 horas antes
    private String ultimaSincronizacionExitosa; // ISO-8601, null si nunca sincronizó

    public static synchronized Configuracion obtener() {
        if (instancia == null) {
            instancia = cargar();
        }
        return instancia;
    }

    private static File archivoConfig() {
        Optional<File> raiz = StorageService.create()
            .flatMap(StorageService::getPrivateStorage);
        File dir = raiz.orElseGet(() -> new File(System.getProperty("user.home"), ".prestamofacil"));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, ARCHIVO);
    }

    private static Configuracion cargar() {
        File archivo = archivoConfig();
        if (!archivo.exists()) {
            return new Configuracion();
        }
        Gson gson = new GsonBuilder().create();
        try (Reader r = Files.newBufferedReader(archivo.toPath(), StandardCharsets.UTF_8)) {
            Configuracion c = gson.fromJson(r, Configuracion.class);
            return c != null ? c : new Configuracion();
        } catch (IOException | RuntimeException e) {
            // RuntimeException incluye fallos de Gson (p.ej. reflexión no
            // registrada para native-image): sin este catch amplio, un fallo
            // aquí se ve igual que "nunca se guardó nada".
            System.err.println("No se pudo leer " + ARCHIVO + ": " + e);
            return new Configuracion();
        }
    }

    public void guardar() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = Files.newBufferedWriter(archivoConfig().toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(this, w);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la configuración.", e);
        }
    }

    public String getUrlWebApp() { return urlWebApp; }
    public void setUrlWebApp(String urlWebApp) { this.urlWebApp = urlWebApp; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getMinutosRecordatorioPorDefecto() { return minutosRecordatorioPorDefecto; }
    public void setMinutosRecordatorioPorDefecto(int minutos) { this.minutosRecordatorioPorDefecto = minutos; }

    public String getUltimaSincronizacionExitosa() { return ultimaSincronizacionExitosa; }
    public void setUltimaSincronizacionExitosa(String iso8601) { this.ultimaSincronizacionExitosa = iso8601; }

    public boolean estaConfigurado() {
        return urlWebApp != null && !urlWebApp.isBlank() && token != null && !token.isBlank();
    }
}
