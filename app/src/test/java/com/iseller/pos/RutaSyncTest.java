package com.iseller.pos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Pruebas de la mezcla ruta-servidor: es el punto donde una entrega hecha sin conexión
 * se puede perder (o duplicar el aviso al comercio).
 */
public class RutaSyncTest {

    private static JSONObject pedido(String numero, String estatus, boolean sincronizado) throws Exception {
        JSONObject p = new JSONObject();
        p.put("numero_pedido", numero);
        p.put("coords", "5.6749,-67.5964");
        p.put("estatus_entrega", estatus);
        p.put("sincronizado", sincronizado);
        return p;
    }

    private static JSONObject pedidos(JSONObject... lista) throws Exception {
        JSONObject contenedor = new JSONObject();
        for (int i = 0; i < lista.length; i++) {
            contenedor.put(String.valueOf(i), lista[i]);
        }
        return contenedor;
    }

    private static JSONObject pedidoDe(JSONObject pedidos, String numero) {
        for (Iterator<String> claves = pedidos.keys(); claves.hasNext(); ) {
            JSONObject p = pedidos.optJSONObject(claves.next());
            if (p != null && numero.equals(p.optString("numero_pedido"))) {
                return p;
            }
        }
        return null;
    }

    private static List<String> claves(JSONObject pedidos) {
        List<String> lista = new ArrayList<>();
        for (Iterator<String> claves = pedidos.keys(); claves.hasNext(); ) {
            lista.add(claves.next());
        }
        Collections.sort(lista);
        return lista;
    }

    @Test
    public void pedidoNuevoSeAgregaYSeCuenta() throws Exception {
        JSONObject salida = new JSONObject();
        int nuevos = RutaSync.mezclar(salida, null, pedidos(pedido("21", "PENDIENTE", false)));

        assertEquals(1, nuevos);
        JSONObject resultado = pedidoDe(salida.getJSONObject("pedidos"), "21");
        assertNotNull(resultado);
        assertEquals("PENDIENTE", resultado.getString("estatus_entrega"));
        assertFalse(resultado.getBoolean("sincronizado"));
    }

    @Test
    public void entregaLocalSinSubirMandaSobreElServidor() throws Exception {
        JSONObject salida = new JSONObject();
        JSONObject locales = pedidos(pedido("21", "ENTREGADO", false));
        JSONObject servidor = pedidos(pedido("21", "PENDIENTE", false));

        assertEquals(0, RutaSync.mezclar(salida, locales, servidor));

        JSONObject resultado = pedidoDe(salida.getJSONObject("pedidos"), "21");
        assertEquals("ENTREGADO", resultado.getString("estatus_entrega"));
        assertFalse("sigue en cola para subirse", resultado.getBoolean("sincronizado"));
    }

    @Test
    public void entregaHechaEnElServidorSincronizaElLocal() throws Exception {
        JSONObject salida = new JSONObject();
        JSONObject locales = pedidos(pedido("21", "PENDIENTE", false));
        JSONObject servidor = pedidos(pedido("21", "ENTREGADO", false));

        RutaSync.mezclar(salida, locales, servidor);

        JSONObject resultado = pedidoDe(salida.getJSONObject("pedidos"), "21");
        assertEquals("ENTREGADO", resultado.getString("estatus_entrega"));
        assertTrue(resultado.getBoolean("sincronizado"));
    }

    @Test
    public void entregaLocalYaSubidaNoSePierdeSiElServidorVuelveAPendiente() throws Exception {
        JSONObject salida = new JSONObject();
        JSONObject locales = pedidos(pedido("21", "ENTREGADO", true));
        JSONObject servidor = pedidos(pedido("21", "PENDIENTE", false));

        RutaSync.mezclar(salida, locales, servidor);

        JSONObject resultado = pedidoDe(salida.getJSONObject("pedidos"), "21");
        assertEquals("ENTREGADO", resultado.getString("estatus_entrega"));
        assertTrue(resultado.getBoolean("sincronizado"));
    }

    @Test
    public void pedidoSinSubirQueElServidorYaNoDevuelveSeConserva() throws Exception {
        JSONObject salida = new JSONObject();
        JSONObject locales = pedidos(pedido("21", "ENTREGADO", false), pedido("22", "ENTREGADO", true));
        JSONObject servidor = pedidos(pedido("22", "ENTREGADO", true));

        assertEquals(0, RutaSync.mezclar(salida, locales, servidor));

        JSONObject resultado = salida.getJSONObject("pedidos");
        assertNotNull("la entrega sin subir no se puede perder", pedidoDe(resultado, "21"));
        assertNotNull(pedidoDe(resultado, "22"));
        assertEquals(2, resultado.length());
    }

    @Test
    public void pedidoYaSubidoQueElServidorYaNoDevuelveSeElimina() throws Exception {
        JSONObject salida = new JSONObject();
        JSONObject locales = pedidos(pedido("21", "ENTREGADO", true));
        JSONObject servidor = pedidos(pedido("22", "PENDIENTE", false));

        assertEquals(1, RutaSync.mezclar(salida, locales, servidor));

        JSONObject resultado = salida.getJSONObject("pedidos");
        assertNull("el POS lo quitó de la ruta", pedidoDe(resultado, "21"));
        assertNotNull(pedidoDe(resultado, "22"));
    }

    @Test
    public void conservaElMotivoDeUnRechazoAnterior() throws Exception {
        JSONObject previo = pedido("21", "ENTREGADO", true);
        previo.put("sync_error", "El pedido está rechazado");
        JSONObject salida = new JSONObject();

        RutaSync.mezclar(salida, pedidos(previo), pedidos(pedido("21", "PENDIENTE", false)));

        assertEquals("El pedido está rechazado",
                pedidoDe(salida.getJSONObject("pedidos"), "21").getString("sync_error"));
    }

    @Test
    public void sinPedidosDelServidorNoCambiaNada() throws Exception {
        JSONObject salida = new JSONObject();
        int nuevos = RutaSync.mezclar(salida, pedidos(pedido("21", "ENTREGADO", false)), null);

        assertEquals(0, nuevos);
        assertFalse(salida.has("pedidos"));
    }

    @Test
    public void reindexaLasClavesComoObjeto() throws Exception {
        JSONObject salida = new JSONObject();
        RutaSync.mezclar(salida, null,
                pedidos(pedido("21", "PENDIENTE", false), pedido("22", "PENDIENTE", false), pedido("23", "ENTREGADO", false)));

        // El contrato pide "pedidos" como objeto con claves "0","1","2"... (no importa el orden)
        assertEquals(java.util.Arrays.asList("0", "1", "2"), claves(salida.getJSONObject("pedidos")));
    }
}
