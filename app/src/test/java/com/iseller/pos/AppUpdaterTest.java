package com.iseller.pos;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppUpdaterTest {

    @Test
    public void comparaVersionesCorrectamente() {
        assertTrue(AppUpdater.esVersionNueva(1, 2));
        assertFalse(AppUpdater.esVersionNueva(2, 2));
        assertFalse(AppUpdater.esVersionNueva(2, 1));
    }

    @Test
    public void calculaSha256Correctamente() throws Exception {
        File tmp = File.createTempFile("apk_test", ".bin");
        tmp.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AppUpdater.sha256Hex(tmp)
        );
    }

    /** El servidor corta a los 50 KB: el segundo intento debe reanudar con Range y completar el archivo. */
    @Test
    public void reanudaLaDescargaCuandoSeCortaLaConexion() throws Exception {
        byte[] contenido = new byte[200_000];
        new Random(7).nextBytes(contenido);

        File destino = File.createTempFile("apk_resume", ".bin");
        destino.delete();
        destino.deleteOnExit();

        List<String> rangos = Collections.synchronizedList(new ArrayList<>());
        try (ServerSocket servidor = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            Thread hilo = new Thread(() -> {
                try {
                    try (Socket s = servidor.accept()) {
                        servir(s, contenido, 0, 50_000, rangos);
                    }
                    try (Socket s = servidor.accept()) {
                        servir(s, contenido, 50_000, contenido.length - 50_000, rangos);
                    }
                } catch (Exception ignored) {
                    // el test falla por el archivo incompleto o por los rangos esperados
                }
            });
            hilo.setDaemon(true);
            hilo.start();

            String url = "http://127.0.0.1:" + servidor.getLocalPort() + "/api/app_version.php?descargar=1";
            File descargado = AppUpdater.descargar(url, "clave", destino, contenido.length, 4, null);
            hilo.join(5000);

            assertArrayEquals(contenido, Files.readAllBytes(descargado.toPath()));
            assertEquals(2, rangos.size());
            assertEquals("", rangos.get(0));
            assertEquals("bytes=50000-", rangos.get(1));
        }
    }

    private static void servir(Socket socket, byte[] datos, int desde, int cantidad,
                               List<String> rangos) throws Exception {
        BufferedReader lector = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String rango = null;
        lector.readLine(); // línea de petición
        String cabecera;
        while ((cabecera = lector.readLine()) != null && !cabecera.isEmpty()) {
            if (cabecera.toLowerCase(Locale.US).startsWith("range:")) {
                rango = cabecera.substring(6).trim();
            }
        }
        rangos.add(rango == null ? "" : rango);

        OutputStream salida = socket.getOutputStream();
        salida.write(("HTTP/1.1 206 Partial Content\r\n"
                + "Content-Length: " + cantidad + "\r\n"
                + "Content-Range: bytes " + desde + "-" + (desde + cantidad - 1) + "/" + datos.length + "\r\n"
                + "Accept-Ranges: bytes\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        salida.write(datos, desde, cantidad);
        salida.flush();
    }
}
