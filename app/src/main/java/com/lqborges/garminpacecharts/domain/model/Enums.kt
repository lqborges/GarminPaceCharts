package com.lqborges.garminpacecharts

enum class PaceSource {
    INTERVAL_ACTIVE,
    RWD_RUN,
    FALLBACK_RUN_SPLIT,
    IMPORTED,
    UNKNOWN,
}

enum class RefreshStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
}

enum class MetricSource {
    GARMIN_API,
    GARMIN_EXPORT,
    MANUAL_IMPORT,
}

enum class TrendDirection {
    IMPROVING,
    DECLINING,
    STABLE,
    INSUFFICIENT_DATA,
}

enum class ChartRange {
    LAST_4_WEEKS,
    LAST_YEAR,
    ALL_TIME,
}
