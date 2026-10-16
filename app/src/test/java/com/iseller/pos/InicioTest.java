package com.iseller.pos;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

/** Pruebas del calculo de porcentajes usado en el dashboard (Inicio). */
public class InicioTest {

    private Locale localeOriginal;

    // produccion formatea con el locale del dispositivo (en es sale "25,00%"); aqui lo fijamos
    // para que las aserciones no dependan del entorno.
    @Before
    public void localeFijo() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restaurarLocale() {
        Locale.setDefault(localeOriginal);
    }

    @Test
    public void porcentajeCadenaManejaDivisionPorCero() {
        assertEquals("0.00%", Inicio.calcularPorcentajeCadena(0, 0, "f"));
        assertEquals("0.00%", Inicio.calcularPorcentajeCadena(0, 5, "f"));
    }

    @Test
    public void porcentajeCadenaFormateaDosDecimales() {
        assertEquals("25.00%", Inicio.calcularPorcentajeCadena(100, 25, "f"));
        assertEquals("33.33%", Inicio.calcularPorcentajeCadena(3, 1, "f"));
        assertEquals("100.00%", Inicio.calcularPorcentajeCadena(50, 50, "f"));
    }

    @Test
    public void porcentajeEnteroTruncaSinRedondear() {
        assertEquals(0, Inicio.calcularPorcentaje(0, 0, "i"));
        assertEquals(25, Inicio.calcularPorcentaje(100, 25, "i"));
        assertEquals(33, Inicio.calcularPorcentaje(3, 1, "i"));
    }
}
