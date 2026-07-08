package com.lqborges.garminpacecharts.domain

object SleepCoach {
    fun comments(
        sleepScoreLastNight: Double?,
        sleepDurationLastNight: Double?,
        sleepScoreAvg: Double?,
        sleepDurationAvg: Double?,
        sleepScorePriorNight: Double?,
    ): List<String> {
        val comments = mutableListOf<String>()

        sleepScoreLastNight?.let { score ->
            when {
                score >= 85 -> comments += "Excellent sleep last night — recovery looks strong."
                score >= 75 -> comments += "Solid sleep score. Keep your current bedtime routine."
                score >= 65 -> comments += "Sleep was acceptable, but room for a longer wind-down."
                else -> comments += "Sleep score was low — prioritize an earlier, screen-free bedtime."
            }
        }

        sleepDurationLastNight?.let { hours ->
            when {
                hours < 6.5 -> comments += "Under 6.5 hours last night — aim for 7–8h tonight."
                hours < 7.5 -> comments += "A little short on sleep — try moving bedtime 30 minutes earlier."
                hours >= 8.0 -> comments += "Good sleep duration — maintain this schedule on rest days."
            }
        }

        if (sleepScoreLastNight != null && sleepScorePriorNight != null) {
            val delta = sleepScoreLastNight - sleepScorePriorNight
            when {
                delta >= 10 -> comments += "Big improvement vs the prior night — nice rebound."
                delta <= -10 -> comments += "Sleep dropped sharply vs the prior night — check late caffeine or stress."
            }
        }

        if (sleepScoreLastNight != null && sleepScoreAvg != null && sleepScoreLastNight > sleepScoreAvg + 5) {
            comments += "Last night beat your 7-day sleep average — good recovery day."
        }
        if (sleepDurationLastNight != null && sleepDurationAvg != null && sleepDurationLastNight < sleepDurationAvg - 0.75) {
            comments += "Sleep duration trailed your weekly average — protect tonight's sleep window."
        }

        return comments.distinct().take(3)
    }
}