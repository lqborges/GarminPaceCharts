package com.lqborges.garminpacecharts.data.garmin

class GarminApiException(message: String, val type: String = "GARMIN_API") : Exception(message)
