package com.videdit.android.core

import com.videdit.android.model.TimeSegment

object SegmentUtils {
    fun mergeSegments(segments: List<TimeSegment>): List<TimeSegment> {
        if (segments.isEmpty()) return emptyList()
        val sorted = segments.sortedBy { it.start }
        val merged = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val last = merged.last()
            if (current.start <= last.end) {
                merged[merged.lastIndex] = TimeSegment(last.start, maxOf(last.end, current.end))
            } else {
                merged.add(current)
            }
        }
        return merged
    }

    fun keptSegments(duration: Double, deletedSegments: List<TimeSegment>): List<TimeSegment> {
        if (deletedSegments.isEmpty()) return listOf(TimeSegment(0.0, duration))
        val sortedDeleted = mergeSegments(deletedSegments)
        val kept = mutableListOf<TimeSegment>()
        var cursor = 0.0

        for (deleted in sortedDeleted) {
            if (cursor < deleted.start) kept.add(TimeSegment(cursor, deleted.start))
            cursor = deleted.end
        }

        if (cursor < duration) kept.add(TimeSegment(cursor, duration))
        return kept
    }

    fun totalDuration(segments: List<TimeSegment>): Double = segments.sumOf { it.duration }
}
