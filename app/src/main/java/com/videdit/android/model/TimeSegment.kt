package com.videdit.android.model

data class TimeSegment(
    val start: Double,
    val end: Double,
) {
    val duration: Double get() = end - start
    fun contains(time: Double): Boolean = time in start..end
}
