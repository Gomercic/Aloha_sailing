package com.example.startline

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class CogSample(
    val timestampMs: Long,
    val cogDeg: Double,
    val sogKnots: Double
)

data class DeviationPoint(
    val timestampMs: Long,
    val deviationDeg: Double
)

enum class Mode {
    SINGLE,
    DUAL
}

class ShiftWindAnalyzer(
    minSpeedKnots: Double = 1.0,
    minSamplesPerCluster: Int = 10,
    minTackSeparationDeg: Double = 30.0,
    maxTackSeparationDeg: Double = 170.0,
    maxClusterSpreadDeg: Double = 40.0,
    historyWindowMinutes: Long = 60L
) {
    var minSpeedKnots: Double = minSpeedKnots
        set(value) {
            field = value.coerceAtLeast(0.0)
            recompute()
        }

    var minSamplesPerCluster: Int = minSamplesPerCluster
        set(value) {
            field = value.coerceAtLeast(1)
            recompute()
        }

    var minTackSeparationDeg: Double = minTackSeparationDeg
        set(value) {
            field = value.coerceIn(0.0, 180.0)
            recompute()
        }

    var maxTackSeparationDeg: Double = maxTackSeparationDeg
        set(value) {
            field = value.coerceIn(0.0, 180.0)
            recompute()
        }

    var maxClusterSpreadDeg: Double = maxClusterSpreadDeg
        set(value) {
            field = value.coerceIn(0.0, 180.0)
            recompute()
        }

    var historyWindowMinutes: Long = historyWindowMinutes
        set(value) {
            field = value.coerceAtLeast(1L)
            recompute()
        }

    var mode: Mode = Mode.SINGLE
        private set

    var monoSignInverted: Boolean = false
        private set

    var singleMeanCourse: Double? = null
        private set

    var portMeanCourse: Double? = null
        private set

    var starboardMeanCourse: Double? = null
        private set

    var centerCourse: Double? = null
        private set

    var currentDeviationDeg: Double? = null
        private set

    var series: List<DeviationPoint> = emptyList()
        private set

    private val allSamples = mutableListOf<CogSample>()

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        recompute()
    }

    fun toggleMonoSign() {
        monoSignInverted = !monoSignInverted
        if (mode == Mode.SINGLE) {
            recompute()
        }
    }

    fun clear() {
        allSamples.clear()
        resetOutputs()
    }

    fun addSample(sample: CogSample) {
        allSamples.add(sample.copy(cogDeg = normalizeAngle360(sample.cogDeg)))
        pruneHistory()
        recompute()
    }

    fun addSamples(samples: List<CogSample>) {
        if (samples.isEmpty()) return
        allSamples.addAll(samples.map { it.copy(cogDeg = normalizeAngle360(it.cogDeg)) })
        allSamples.sortBy { it.timestampMs }
        pruneHistory()
        recompute()
    }

    fun replaceSamples(samples: List<CogSample>) {
        allSamples.clear()
        allSamples.addAll(samples.map { it.copy(cogDeg = normalizeAngle360(it.cogDeg)) })
        allSamples.sortBy { it.timestampMs }
        pruneHistory()
        recompute()
    }

    fun getValidSamples(): List<CogSample> {
        pruneHistory()
        return filteredSamples()
    }

    private fun recompute() {
        pruneHistory()
        val valid = filteredSamples()
        if (valid.isEmpty()) {
            resetOutputs()
            return
        }

        when (mode) {
            Mode.SINGLE -> recomputeSingle(valid)
            Mode.DUAL -> recomputeDual(valid)
        }
    }

    private fun recomputeSingle(validSamples: List<CogSample>) {
        val mean = circularMean(validSamples.map { it.cogDeg }) ?: run {
            resetOutputs()
            return
        }

        val points = validSamples.map { sample ->
            var deviation = signedShortestAngle(sample.cogDeg, mean)
            if (monoSignInverted) deviation = -deviation
            DeviationPoint(sample.timestampMs, deviation)
        }

        singleMeanCourse = mean
        portMeanCourse = null
        starboardMeanCourse = null
        centerCourse = mean
        series = points
        currentDeviationDeg = points.lastOrNull()?.deviationDeg
    }

    private fun recomputeDual(validSamples: List<CogSample>) {
        val clusterResult = detectDualClusters(validSamples) ?: run {
            resetOutputs()
            return
        }

        val meanA = clusterResult.meanA
        val meanB = clusterResult.meanB
        val separation = abs(signedShortestAngle(meanA, meanB))
        if (separation !in minTackSeparationDeg..maxTackSeparationDeg) {
            resetOutputs()
            return
        }

        if (clusterResult.clusterA.size < minSamplesPerCluster ||
            clusterResult.clusterB.size < minSamplesPerCluster
        ) {
            resetOutputs()
            return
        }

        val spreadA = circularSpreadDeg(clusterResult.clusterA.map { it.cogDeg }, meanA)
        val spreadB = circularSpreadDeg(clusterResult.clusterB.map { it.cogDeg }, meanB)
        if (spreadA > maxClusterSpreadDeg || spreadB > maxClusterSpreadDeg) {
            resetOutputs()
            return
        }

        val center = circularMidpoint(meanA, meanB)
        val sideA = signedShortestAngle(meanA, center)
        val sideB = signedShortestAngle(meanB, center)
        if (sideA == 0.0 || sideB == 0.0) {
            resetOutputs()
            return
        }

        val portData: ClusterData
        val starboardData: ClusterData
        if (sideA < 0.0) {
            portData = ClusterData(clusterResult.clusterA, meanA)
            starboardData = ClusterData(clusterResult.clusterB, meanB)
        } else {
            portData = ClusterData(clusterResult.clusterB, meanB)
            starboardData = ClusterData(clusterResult.clusterA, meanA)
        }

        val baseAbsPort = abs(signedShortestAngle(portData.mean, center))
        val baseAbsStarboard = abs(signedShortestAngle(starboardData.mean, center))
        val assignment = assignToNearestCluster(validSamples, portData.mean, starboardData.mean)
        val points = assignment.map { (sample, nearest) ->
            val absToCenter = abs(signedShortestAngle(sample.cogDeg, center))
            val baseline = if (nearest == ClusterId.PORT) baseAbsPort else baseAbsStarboard
            val deviation = baseline - absToCenter
            DeviationPoint(sample.timestampMs, deviation)
        }

        singleMeanCourse = null
        portMeanCourse = portData.mean
        starboardMeanCourse = starboardData.mean
        centerCourse = center
        series = points
        currentDeviationDeg = points.lastOrNull()?.deviationDeg
    }

    private fun detectDualClusters(samples: List<CogSample>): ClusterResult? {
        if (samples.size < minSamplesPerCluster * 2) return null

        var meanA = circularMean(samples.take(samples.size / 2).map { it.cogDeg }) ?: return null
        var meanB = circularMean(samples.drop(samples.size / 2).map { it.cogDeg }) ?: return null
        val initialGap = abs(signedShortestAngle(meanA, meanB))
        if (initialGap < 1.0) {
            meanB = normalizeAngle360(meanA + 180.0)
        }

        repeat(24) {
            val clusterA = mutableListOf<CogSample>()
            val clusterB = mutableListOf<CogSample>()
            samples.forEach { sample ->
                val dA = abs(signedShortestAngle(sample.cogDeg, meanA))
                val dB = abs(signedShortestAngle(sample.cogDeg, meanB))
                if (dA <= dB) clusterA.add(sample) else clusterB.add(sample)
            }

            if (clusterA.isEmpty() || clusterB.isEmpty()) return null
            val newMeanA = circularMean(clusterA.map { it.cogDeg }) ?: return null
            val newMeanB = circularMean(clusterB.map { it.cogDeg }) ?: return null
            val deltaA = abs(signedShortestAngle(meanA, newMeanA))
            val deltaB = abs(signedShortestAngle(meanB, newMeanB))
            meanA = newMeanA
            meanB = newMeanB
            if (deltaA < 0.05 && deltaB < 0.05) {
                return ClusterResult(clusterA, clusterB, meanA, meanB)
            }
        }

        val finalA = mutableListOf<CogSample>()
        val finalB = mutableListOf<CogSample>()
        samples.forEach { sample ->
            val dA = abs(signedShortestAngle(sample.cogDeg, meanA))
            val dB = abs(signedShortestAngle(sample.cogDeg, meanB))
            if (dA <= dB) finalA.add(sample) else finalB.add(sample)
        }
        if (finalA.isEmpty() || finalB.isEmpty()) return null
        return ClusterResult(finalA, finalB, meanA, meanB)
    }

    private fun assignToNearestCluster(
        samples: List<CogSample>,
        portMean: Double,
        starboardMean: Double
    ): List<Pair<CogSample, ClusterId>> {
        return samples.map { sample ->
            val dPort = abs(signedShortestAngle(sample.cogDeg, portMean))
            val dStarboard = abs(signedShortestAngle(sample.cogDeg, starboardMean))
            if (dPort <= dStarboard) {
                sample to ClusterId.PORT
            } else {
                sample to ClusterId.STARBOARD
            }
        }
    }

    private fun pruneHistory() {
        if (allSamples.isEmpty()) return
        allSamples.sortBy { it.timestampMs }
        val latest = allSamples.last().timestampMs
        val windowStart = latest - historyWindowMinutes * 60_000L
        while (allSamples.isNotEmpty() && allSamples.first().timestampMs < windowStart) {
            allSamples.removeAt(0)
        }
    }

    private fun filteredSamples(): List<CogSample> {
        return allSamples
            .asSequence()
            .filter { it.sogKnots >= minSpeedKnots }
            .sortedBy { it.timestampMs }
            .toList()
    }

    private fun resetOutputs() {
        singleMeanCourse = null
        portMeanCourse = null
        starboardMeanCourse = null
        centerCourse = null
        currentDeviationDeg = null
        series = emptyList()
    }

    companion object {
        fun normalizeAngle360(angleDeg: Double): Double {
            var normalized = angleDeg % 360.0
            if (normalized < 0.0) normalized += 360.0
            return normalized
        }

        fun signedShortestAngle(fromDeg: Double, toDeg: Double): Double {
            val delta = normalizeAngle360(toDeg) - normalizeAngle360(fromDeg)
            return when {
                delta > 180.0 -> delta - 360.0
                delta < -180.0 -> delta + 360.0
                else -> delta
            }
        }

        fun circularMean(anglesDeg: List<Double>): Double? {
            if (anglesDeg.isEmpty()) return null
            var sumSin = 0.0
            var sumCos = 0.0
            anglesDeg.forEach { angle ->
                val rad = Math.toRadians(normalizeAngle360(angle))
                sumSin += sin(rad)
                sumCos += cos(rad)
            }
            if (sumSin == 0.0 && sumCos == 0.0) return null
            val mean = Math.toDegrees(atan2(sumSin, sumCos))
            return normalizeAngle360(mean)
        }

        fun circularMidpoint(angleA: Double, angleB: Double): Double {
            val a = normalizeAngle360(angleA)
            val b = normalizeAngle360(angleB)
            val delta = signedShortestAngle(a, b)
            return normalizeAngle360(a + delta / 2.0)
        }

        private fun circularSpreadDeg(anglesDeg: List<Double>, centerDeg: Double): Double {
            if (anglesDeg.isEmpty()) return 180.0
            var maxSpread = 0.0
            anglesDeg.forEach { angle ->
                val d = abs(signedShortestAngle(centerDeg, angle))
                maxSpread = maxOf(maxSpread, d)
            }
            return min(180.0, maxSpread)
        }
    }

    private data class ClusterResult(
        val clusterA: List<CogSample>,
        val clusterB: List<CogSample>,
        val meanA: Double,
        val meanB: Double
    )

    private data class ClusterData(
        val samples: List<CogSample>,
        val mean: Double
    )

    private enum class ClusterId {
        PORT,
        STARBOARD
    }
}
