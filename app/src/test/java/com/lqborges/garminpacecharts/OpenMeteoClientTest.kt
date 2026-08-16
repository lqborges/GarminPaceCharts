package com.lqborges.garminpacecharts

import com.lqborges.garminpacecharts.data.weather.OpenMeteoClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.EOFException
import javax.net.ssl.SSLHandshakeException

class OpenMeteoClientTest {
    @Test
    fun fetchCurrent_returnsNullWhenTlsHandshakeCloses() {
        val client = OpenMeteoClient(
            httpClient = throwingClient {
                SSLHandshakeException("connection closed").apply {
                    initCause(EOFException("connection closed"))
                }
            },
            maxAttempts = 2,
        )
        assertNull(client.fetchCurrent(53.3811, -1.4701))
    }

    @Test
    fun fetchCurrent_returnsNullWhenConnectionEndsDuringHandshake() {
        val client = OpenMeteoClient(
            httpClient = throwingClient { EOFException("connection closed") },
            maxAttempts = 1,
        )
        assertNull(client.fetchCurrent(53.3811, -1.4701))
    }

    private fun throwingClient(error: () -> Exception): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor {
                    throw error()
                },
            )
            .build()
}
