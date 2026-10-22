package com.iseller.pos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Pruebas de las utilidades de ruta: nombres de archivo, forma de "pedidos" y mensajes de error. */
public class RutaJsonTest {

    @Test
    public void nombreDeArchivoQuitaAcentosYEspacios() {
        assertEquals("Ruta_Norte_Manana.json", RutaJson.nombreArchivo("Ruta Norte - Mañana"));
        assertEquals("Ruta_1234.json", RutaJson.nombreArchivo("  Ruta 1234  "));
    }

    @Test
    public void nombreDeArchivoNuncaQuedaVacio() {
        assertEquals("ruta.json", RutaJson.nombreArchivo("***"));
        assertEquals("ruta.json", RutaJson.nombreArchivo(""));
    }

    @Test
    public void normalizarPedidosAceptaObjeto() throws Exception {
        JSONObject objeto = new JSONObject().put("0", new JSONObject().put("numero_pedido", "21"));
        assertSame(objeto, RutaJson.normalizarPedidos(objeto));
    }

    @Test
    public void normalizarPedidosConvierteArrayEnObjeto() throws Exception {
        JSONArray array = new JSONArray()
                .put(new JSONObject().put("numero_pedido", "21"))
                .put(new JSONObject().put("numero_pedido", "22"));

        JSONObject normalizado = RutaJson.normalizarPedidos(array);

        assertEquals(2, normalizado.length());
        assertEquals("21", normalizado.getJSONObject("0").getString("numero_pedido"));
        assertEquals("22", normalizado.getJSONObject("1").getString("numero_pedido"));
    }

    @Test
    public void normalizarPedidosDevuelveNullSiNoEsNiObjetoNiArray() throws Exception {
        assertNull(RutaJson.normalizarPedidos("texto"));
        assertNull(RutaJson.normalizarPedidos(null));
    }

    @Test
    public void mensajeDeErrorUsaElTextoDelServidor() {
        VolleyError error = new VolleyError(respuesta(404, "{\"error\":\"Pedido no encontrado en esta ruta\"}"));

        assertEquals("Pedido no encontrado en esta ruta",
                RutaJson.mensajeDeError(error, "Sin conexión: no se pudo avisar al servidor."));
    }

    @Test
    public void mensajeDeErrorUsaElCodigoSiElCuerpoNoEsJson() {
        VolleyError error = new VolleyError(respuesta(500, "<html>error</html>"));

        assertEquals("Error del servidor (500).",
                RutaJson.mensajeDeError(error, "Sin conexión: no se pudo avisar al servidor."));
    }

    @Test
    public void mensajeDeErrorSinRespuestaUsaElTextoDeRespaldo() {
        VolleyError error = new VolleyError("timeout");

        assertEquals("Sin conexión: no se pudo avisar al servidor.",
                RutaJson.mensajeDeError(error, "Sin conexión: no se pudo avisar al servidor."));
    }

    private static NetworkResponse respuesta(int codigo, String cuerpo) {
        return new NetworkResponse(codigo, cuerpo.getBytes(StandardCharsets.UTF_8), new HashMap<>(), false);
    }
}
