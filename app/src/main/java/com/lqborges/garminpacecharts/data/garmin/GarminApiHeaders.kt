package com.lqborges.garminpacecharts.data.garmin

import okhttp3.Headers

object GarminApiHeaders {
    fun native(): Headers = Headers.headersOf(
        "User-Agent", "GCM-Android-5.23",
        "X-Garmin-User-Agent",
        "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
        "X-Garmin-Paired-App-Version", "10861",
        "X-Garmin-Client-Platform", "Android",
        "X-App-Ver", "10861",
        "X-Lang", "en",
        "X-GCExperience", "GC5",
        "Accept-Language", "en-US,en;q=0.9",
    )

    fun connectApi(bearerToken: String): Headers {
        val builder = native().newBuilder()
            .add("Authorization", "Bearer $bearerToken")
            .add("Accept", "application/json")
            .add("DI-Backend", "connectapi.garmin.com")
        return builder.build()
    }
}
