package com.iseller.pos;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sincronización de las rutas guardadas con el servidor.
 *
 * Todo el trabajo de red va en un hilo aparte y en este orden: primero se sube la cola de
 * entregas hechas sin conexión y solo después se descarga la ruta para mezclarla.
 *
 * La cola no es un almacén aparte: es el propio JSON de la ruta. Un pedido está en cola
 * cuando `estatus_entrega = ENTREGADO` y `sincronizado` no es `true`.
 *
 * Todos los accesos a los archivos de ruta pasan por el mismo cerrojo: el mapa marca una
 * entrega mientras otro hilo puede estar escribiendo, y sin él se perdería la marca. Las
 * llamadas HTTP se hacen SIEMPRE fuera del cerrojo (no bloquear la lectura/escritura).
 */
final class RutaSync {

    /** Aviso por cada entrega que se intenta subir. */
    interface Aviso {
        void enviado(String numeroPedido, boolean ok, String error);
    }

    /** Resultado de actualizar la ruta contra el servidor. */
    interface Resultado {
        /** @param nuevos pedidos que no estaban en el dispositivo; @param fallo null si fue bien */
        void listo(int nuevos, String fallo);
    }

    private static final Object CERROJO = new Object();

    private RutaSync() {
    }

    // ------------------------------------------------------------------
    // Cola de entregas (subida)
    // ------------------------------------------------------------------

    /** Sube la cola de todas las rutas guardadas (p. ej. al abrir la app). */
    static void enviarPendientes(Context contexto) {
        enviarPendientes(contexto, null, null);
    }

    /** Sube la cola de una ruta concreta (o de todas si {@code archivo} es null). */
    static void enviarPendientes(Context contexto, File archivo, Aviso aviso) {
        final File[] archivos = (archivo != null)
                ? new File[]{archivo}
                : RutaJson.carpeta(contexto).listFiles();
        if (archivos == null) {
            return;
        }
        final Aviso destino = aviso;
        new Thread(() -> {
            for (File f : archivos) {
                if (f.getName().toLowerCase().endsWith(".json")) {
                    subirPendientes(f, destino);
                }
            }
        }).start();
    }

    private static void subirPendientes(File archivo, Aviso aviso) {
        String codigo;
        List<String> pendientes = new ArrayList<>();
        synchronized (CERROJO) {
            JSONObject pedidos = pedidosDe(archivo);
            if (pedidos == null) {
                return;
            }
            codigo = leerCodigo(archivo);
            if (codigo.isEmpty()) {
                return;   // ruta antigua sin código: no se puede subir nada
            }
            Iterator<String> claves = pedidos.keys();
            while (claves.hasNext()) {
                JSONObject pedido = pedidos.optJSONObject(claves.next());
                if (pedido != null && estabaEntregado(pedido) && !pedido.optBoolean("sincronizado", false)) {
                    pendientes.add(pedido.optString("numero_pedido"));
                }
            }
        }

        for (String numero : pendientes) {
            Envio envio = enviarEntrega(codigo, numero);   // HTTP fuera del cerrojo
            if (envio.ok || envio.terminal) {
                // Terminal = el servidor no lo aceptará nunca (pedido borrado, rechazado…):
                // se saca de la cola y se guarda el motivo, en vez de reintentar en vano.
                marcarSubido(archivo, numero, envio.ok ? null : envio.error);
            }
            if (aviso != null) {
                aviso.enviado(numero, envio.ok, envio.ok ? null : envio.error);
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualización de la ruta (bajada + mezcla)
    // ------------------------------------------------------------------

    /** Sube la cola y después descarga la ruta para mezclarla con lo que hay en el dispositivo. */
    static void actualizar(final File archivo, final Resultado resultado) {
        new Thread(() -> {
            int nuevos = 0;
            String fallo = null;
            try {
                // 1) La cola primero: lo que el repartidor marcó manda sobre el servidor
                subirPendientes(archivo, null);

                String codigo = leerCodigo(archivo);
                if (codigo.isEmpty()) {
                    fallo = "Esta ruta no trae código: vuelve a cargarla.";
                } else {
                    // 2) Descarga (fuera del cerrojo) y mezcla (dentro, para no perder marcas)
                    JSONObject servidor = new JSONObject(descargarRuta(codigo));
                    if (servidor.has("error")) {
                        fallo = servidor.optString("error", "Error del servidor");
                    } else {
                        JSONObject delServidor = RutaJson.normalizarPedidos(servidor.opt("pedidos"));
                        if (delServidor != null) {
                            synchronized (CERROJO) {
                                nuevos = mezclar(servidor, pedidosDe(archivo), delServidor);
                                RutaJson.escribir(archivo, servidor.toString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                fallo = "Sin conexión con el servidor.";
            }

            final int n = nuevos;
            final String f = fallo;
            if (resultado != null) {
                new Handler(Looper.getMainLooper()).post(() -> resultado.listo(n, f));
            }
        }).start();
    }

    /**
     * Mezcla los pedidos del servidor con los que ya había en el dispositivo y deja el
     * resultado en {@code salida} (que suele ser el propio JSON del servidor).
     * Gana lo que el repartidor ya marcó; los pedidos nuevos se añaden. Devuelve cuántos son nuevos.
     */
    static int mezclar(JSONObject salida, JSONObject locales, JSONObject delServidor) throws Exception {
        if (delServidor == null) {
            return 0;
        }

        JSONObject mezclados = new JSONObject();
        int nuevos = 0;
        int indice = 0;
        Iterator<String> claves = delServidor.keys();
        while (claves.hasNext()) {
            JSONObject pedido = new JSONObject(delServidor.getJSONObject(claves.next()).toString());
            String numero = pedido.optString("numero_pedido");
            boolean entregadoServidor = estabaEntregado(pedido);
            JSONObject previo = buscarPorNumero(locales, numero);

            if (previo == null) {
                nuevos++;
                pedido.put("estatus_entrega", entregadoServidor ? "ENTREGADO" : "PENDIENTE");
                pedido.put("sincronizado", entregadoServidor);
            } else {
                boolean entregado = estabaEntregado(previo) || entregadoServidor;
                pedido.put("estatus_entrega", entregado ? "ENTREGADO" : "PENDIENTE");
                // Sigue en cola si lo entregó el repartidor y el servidor todavía no lo sabe
                pedido.put("sincronizado", entregado && (previo.optBoolean("sincronizado", false) || entregadoServidor));
                if (previo.has("sync_error")) {
                    pedido.put("sync_error", previo.optString("sync_error"));
                }
            }
            mezclados.put(String.valueOf(indice++), pedido);
        }

        // Pedidos que el servidor ya no devuelve: se conservan solo si están sin subir,
        // para no perder una entrega hecha sin conexión.
        if (locales != null) {
            Iterator<String> clavesLocales = locales.keys();
            while (clavesLocales.hasNext()) {
                JSONObject previo = locales.getJSONObject(clavesLocales.next());
                boolean sinSubir = estabaEntregado(previo) && !previo.optBoolean("sincronizado", false);
                if (sinSubir && buscarPorNumero(delServidor, previo.optString("numero_pedido")) == null) {
                    mezclados.put(String.valueOf(indice++), previo);
                }
            }
        }

        salida.put("pedidos", mezclados);
        return nuevos;
    }

    // ------------------------------------------------------------------
    // Marcas locales (bajo cerrojo)
    // ------------------------------------------------------------------

    /** Marca un pedido como entregado en el JSON local, pendiente de subir. */
    static void marcarEntregado(File archivo, String numeroPedido) {
        synchronized (CERROJO) {
            JSONObject ruta = leerRuta(archivo);
            if (ruta == null) {
                return;
            }
            try {
                JSONObject pedidos = RutaJson.normalizarPedidos(ruta.opt("pedidos"));
                JSONObject pedido = buscarPorNumero(pedidos, numeroPedido);
                if (pedido == null) {
                    return;
                }
                pedido.put("estatus_entrega", "ENTREGADO");
                pedido.put("sincronizado", false);
                pedido.remove("sync_error");
                ruta.put("pedidos", pedidos);
                RutaJson.escribir(archivo, ruta.toString());
            } catch (Exception ignored) {
                // sin permiso de escritura: la marca no se guarda
            }
        }
    }

    /** Marca que un pedido ya se subió (o que el servidor lo rechazó: {@code error} con motivo). */
    private static void marcarSubido(File archivo, String numeroPedido, String error) {
        synchronized (CERROJO) {
            JSONObject ruta = leerRuta(archivo);
            if (ruta == null) {
                return;
            }
            try {
                JSONObject pedidos = RutaJson.normalizarPedidos(ruta.opt("pedidos"));
                JSONObject pedido = buscarPorNumero(pedidos, numeroPedido);
                if (pedido == null) {
                    return;
                }
                pedido.put("sincronizado", true);
                if (error == null) {
                    pedido.remove("sync_error");
                } else {
                    pedido.put("sync_error", error);
                }
                ruta.put("pedidos", pedidos);
                RutaJson.escribir(archivo, ruta.toString());
            } catch (Exception ignored) {
                // sin permiso de escritura: se reintentará en la próxima pasada
            }
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private static final class Envio {
        final boolean ok;
        final boolean terminal;
        final String error;

        Envio(boolean ok, boolean terminal, String error) {
            this.ok = ok;
            this.terminal = terminal;
            this.error = error;
        }
    }

    /** POST /api/pedido_entregado.php. Sin red devuelve un fallo no terminal (queda en cola). */
    private static Envio enviarEntrega(String codigo, String numeroPedido) {
        HttpURLConnection conexion = null;
        try {
            JSONObject cuerpo = new JSONObject();
            cuerpo.put("codigo", codigo);
            cuerpo.put("numero_pedido", numeroPedido);

            conexion = (HttpURLConnection) new URL(BuildConfig.POS_BASE_URL + "/api/pedido_entregado.php").openConnection();
            conexion.setRequestMethod("POST");
            conexion.setConnectTimeout(15000);
            conexion.setReadTimeout(15000);
            conexion.setDoOutput(true);
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conexion.setRequestProperty("Accept", "application/json");
            conexion.setRequestProperty("X-Api-Key", BuildConfig.POS_API_KEY);
            OutputStream salida = conexion.getOutputStream();
            try {
                salida.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                salida.close();
            }

            int estado = conexion.getResponseCode();
            if (estado >= 200 && estado < 300) {
                return new Envio(true, true, null);
            }
            String mensaje = leerError(conexion.getErrorStream(), estado);
            // 4xx de datos: no se arregla reintentando
            boolean terminal = estado == 400 || estado == 404 || estado == 409;
            return new Envio(false, terminal, mensaje);
        } catch (Exception e) {
            return new Envio(false, false, null);
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    /** GET /api/ruta_entrega.php. Devuelve el cuerpo; en error devuelve {"error": "..."}. */
    private static String descargarRuta(String codigo) throws Exception {
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) new URL(
                    BuildConfig.POS_BASE_URL + "/api/ruta_entrega.php?codigo=" + Uri.encode(codigo)).openConnection();
            conexion.setConnectTimeout(15000);
            conexion.setReadTimeout(15000);
            conexion.setRequestProperty("Accept", "application/json");
            conexion.setRequestProperty("X-Api-Key", BuildConfig.POS_API_KEY);

            int estado = conexion.getResponseCode();
            if (estado >= 200 && estado < 300) {
                return leerTexto(conexion.getInputStream());
            }
            return new JSONObject()
                    .put("error", leerError(conexion.getErrorStream(), estado))
                    .toString();
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private static String leerError(InputStream entrada, int estado) {
        String texto = leerTexto(entrada);
        try {
            String mensaje = new JSONObject(texto).optString("error", "");
            if (!mensaje.isEmpty()) {
                return mensaje;
            }
        } catch (Exception ignored) {
            // el cuerpo no era JSON
        }
        return "Error del servidor (" + estado + ").";
    }

    private static String leerTexto(InputStream entrada) {
        if (entrada == null) {
            return "";
        }
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
            return salida.toString("UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try {
                entrada.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static JSONObject leerRuta(File archivo) {
        try {
            return new JSONObject(RutaJson.leer(archivo));
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject pedidosDe(File archivo) {
        JSONObject ruta = leerRuta(archivo);
        if (ruta == null) {
            return null;
        }
        try {
            return RutaJson.normalizarPedidos(ruta.opt("pedidos"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String leerCodigo(File archivo) {
        JSONObject ruta = leerRuta(archivo);
        return ruta != null ? ruta.optString("codigo", "") : "";
    }

    private static boolean estabaEntregado(JSONObject pedido) {
        return "ENTREGADO".equals(pedido.optString("estatus_entrega"));
    }

    private static JSONObject buscarPorNumero(JSONObject pedidos, String numero) {
        if (pedidos == null) {
            return null;
        }
        Iterator<String> claves = pedidos.keys();
        while (claves.hasNext()) {
            JSONObject pedido = pedidos.optJSONObject(claves.next());
            if (pedido != null && numero.equals(pedido.optString("numero_pedido"))) {
                return pedido;
            }
        }
        return null;
    }
}
