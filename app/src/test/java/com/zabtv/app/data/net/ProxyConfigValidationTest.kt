package com.zabtv.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOW 1 (RED): el puerto del proxy debe ser entero 1-65535 y el host no
 * vacío; si no, la persistencia NO debe habilitar el proxy (fail-open).
 */
class ProxyConfigValidationTest {

    @Test
    fun `puertos de borde validos`() {
        assertTrue(ProxyConfig.isValidPort(1))
        assertTrue(ProxyConfig.isValidPort(8080))
        assertTrue(ProxyConfig.isValidPort(65535))
    }

    @Test
    fun `puertos fuera de rango invalidos`() {
        assertFalse(ProxyConfig.isValidPort(0))
        assertFalse(ProxyConfig.isValidPort(-1))
        assertFalse(ProxyConfig.isValidPort(99999))
        assertFalse(ProxyConfig.isValidPort(Int.MIN_VALUE))
        assertFalse(ProxyConfig.isValidPort(Int.MAX_VALUE))
    }

    @Test
    fun `parsePort solo acepta entero 1-65535`() {
        assertEquals(8080, ProxyConfig.parsePort("8080"))
        assertEquals(1, ProxyConfig.parsePort(" 1 "))
        assertEquals(65535, ProxyConfig.parsePort("65535"))
        assertNull(ProxyConfig.parsePort("0"))
        assertNull(ProxyConfig.parsePort("-1"))
        assertNull(ProxyConfig.parsePort("99999"))
        assertNull(ProxyConfig.parsePort(""))
        assertNull(ProxyConfig.parsePort("   "))
        assertNull(ProxyConfig.parsePort("abc"))
        assertNull(ProxyConfig.parsePort(null))
    }

    @Test
    fun `isValid exige host no vacio y puerto en rango`() {
        assertTrue(ProxyConfig.isValid("proxy.local", 8080))
        assertFalse(ProxyConfig.isValid("", 8080))
        assertFalse(ProxyConfig.isValid("   ", 8080))
        assertFalse(ProxyConfig.isValid("proxy.local", 0))
        assertFalse(ProxyConfig.isValid("proxy.local", -1))
        assertFalse(ProxyConfig.isValid("proxy.local", 99999))
        assertFalse(ProxyConfig.isValid("", 0))
    }
}
