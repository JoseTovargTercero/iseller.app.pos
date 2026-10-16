package com.iseller.pos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pruebas del modelo Usuario.
 *
 * OJO: isNull() tiene el nombre invertido: devuelve false si CUALQUIER campo esta vacio
 * y true solo si todos tienen valor. Estas pruebas fijan el comportamiento actual; si se
 * renombra/invierte, actualizar aqui. Lanza NPE si un campo es null (no "").
 */
public class UsuarioTest {

    @Test
    public void devuelveFalseSiAlgunCampoEstaVacio() {
        assertFalse(new Usuario("", "Ana", "0412", "123456").isNull());
        assertFalse(new Usuario("ana", "", "0412", "123456").isNull());
        assertFalse(new Usuario("ana", "Ana", "", "123456").isNull());
        assertFalse(new Usuario("ana", "Ana", "0412", "").isNull());
    }

    @Test
    public void devuelveTrueSoloSiTodosLosCamposTienenValor() {
        assertTrue(new Usuario("ana", "Ana", "0412", "123456").isNull());
    }
}
