package com.lqborges.garminpacecharts.data.garmin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GarminJwtTest {
    @Test
    fun expiresSoon_detectsExpiredToken() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1}""".toByteArray())
        val token = "aaa.$payload.bbb"
        assertTrue(GarminJwt.expiresSoon(token))
    }

    @Test
    fun expiresSoon_allowsFreshToken() {
        val exp = (System.currentTimeMillis() / 1000) + 3600
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":$exp}""".toByteArray())
        val token = "aaa.$payload.bbb"
        assertFalse(GarminJwt.expiresSoon(token))
    }
}
