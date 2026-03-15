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

data class WindShiftDebugInfo(
    val mode: Mode,
    val calcWindowMinutes: Long,
    val availableDataMs: Long,
    val sampleCount: Int,
    val current5sHeadingDeg: Double?,
    val singleMeanCourseDeg: Double?,
    val singleDeviationDeg: Double?,
    val portMeanCourseDeg: Double?,
    val starboardMeanCourseDeg: Double?,
    val windAxisCourseDeg: Double?,
    val portOffsetDeg: Double?,
    val starboardOffsetDeg: Double?,
    val targetPortDeg: Double?,
    val targetStarboardDeg: Double?,
    val diffToPortDeg: Double?,
    val diffToStarboardDeg: Double?,
    val notes: List<String>
)

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

    var stdFilterSigma: Double? = 1.0
        set(value) {
            field = value?.coerceAtLeast(0.0)
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

    var debugInfo: WindShiftDebugInfo = WindShiftDebugInfo(
        mode = Mode.SINGLE,
        calcWindowMinutes = historyWindowMinutes,
        availableDataMs = 0L,
        sampleCount = 0,
        current5sHeadingDeg = null,
        singleMeanCourseDeg = null,
        singleDeviationDeg = null,
        portMeanCourseDeg = null,
        starboardMeanCourseDeg = null,
        windAxisCourseDeg = null,
        portOffsetDeg = null,
        starboardOffsetDeg = null,
        targetPortDeg = null,
        targetStarboardDeg = null,
        diffToPortDeg = null,
        diffToStarboardDeg = null,
        notes = emptyList()
    )
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
        val current5sHeadingDeg = computeCurrent5sHeading(valid)
        val availableDataMs = (valid.last().timestampMs - valid.first().timestampMs).coerceAtLeast(0L)
        val notes = mutableListOf<String>()

        val dualEvaluation = detectAndBuildDual(valid)
        notes.addAll(dualEvaluation.notes)
        val dualResult = dualEvaluation.result
        if (dualResult != null) {
            mode = Mode.DUAL
            applyDualResult(valid, dualResult, current5sHeadingDeg, availableDataMs, notes)
        } else {
            mode = Mode.SINGLE
            recomputeSingle(valid, current5sHeadingDeg, availableDataMs, notes)
        }
    }

    private fun recomputeSingle(
        validSamples: List<CogSample>,
        current5sHeadingDeg: Double?,
        availableDataMs: Long,
        notes: List<String>
    ) {
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
        val currentDeviation = current5sHeadingDeg?.let { heading ->
            var d = signedShortestAngle(heading, mean)
            if (monoSignInverted) d = -d
            d
        }
        debugInfo = WindShiftDebugInfo(
            mode = Mode.SINGLE,
            calcWindowMinutes = historyWindowMinutes,
            availableDataMs = availableDataMs,
            sampleCount = validSamples.size,
            current5sHeadingDeg = current5sHeadingDeg,
            singleMeanCourseDeg = mean,
            singleDeviationDeg = currentDeviation,
            portMeanCourseDeg = null,
            starboardMeanCourseDeg = null,
            windAxisCourseDeg = null,
            portOffsetDeg = null,
            starboardOffsetDeg = null,
            targetPortDeg = null,
            targetStarboardDeg = null,
            diffToPortDeg = null,
            diffToStarboardDeg = null,
            notes = notes
        )
    }

    private fun applyDualResult(
        validSamples: List<CogSample>,
        dualResult: DualResult,
        current5sHeadingDeg: Double?,
        availableDataMs: Long,
        notes: List<String>
    ) {
        val points = validSamples.map { sample ->
            val diffToPort = signedShortestAngle(sample.cogDeg, dualResult.targetPortDeg)
            val diffToStar = signedShortestAngle(sample.cogDeg, dualResult.targetStarboardDeg)
            val chosen = if (abs(diffToPort) <= abs(diffToStar)) diffToPort else diffToStar
            DeviationPoint(sample.timestampMs, chosen)
        }
        singleMeanCourse = null
        portMeanCourse = dualResult.portMeanDeg
        starboardMeanCourse = dualResult.starboardMeanDeg
        centerCourse = dualResult.windAxisDeg
        series = points
        currentDeviationDeg = points.lastOrNull()?.deviationDeg
        val diffToPortCurrent = current5sHeadingDeg?.let {
            signedShortestAngle(it, dualResult.targetPortDeg)
        }
        val diffToStarCurrent = current5sHeadingDeg?.let {
            signedShortestAngle(it, dualResult.targetStarboardDeg)
        }
        debugInfo = WindShiftDebugInfo(
            mode = Mode.DUAL,
            calcWindowMinutes = historyWindowMinutes,
            availableDataMs = availableDataMs,
            sampleCount = validSamples.size,
            current5sHeadingDeg = current5sHeadingDeg,
            singleMeanCourseDeg = null,
            singleDeviationDeg = null,
            portMeanCourseDeg = dualResult.portMeanDeg,
            starboardMeanCourseDeg = dualResult.starboardMeanDeg,
            windAxisCourseDeg = dualResult.windAxisDeg,
            portOffsetDeg = dualResult.portOffsetDeg,
            starboardOffsetDeg = dualResult.starboardOffsetDeg,
            targetPortDeg = dualResult.targetPortDeg,
            targetStarboardDeg = dualResult.targetStarboardDeg,
            diffToPortDeg = diffToPortCurrent,
            diffToStarboardDeg = diffToStarCurrent,
            notes = notes
        )
    }

    private fun detectAndBuildDual(samples: List<CogSample>): DualEvaluation {
        val notes = mutableListOf<String>()
        val minTotal = minSamplesPerCluster * 2
        if (samples.size < minTotal) {
            notes += "DUAL fail: samples=${samples.size}, potrebno >= $minTotal"
            return DualEvaluation(result = null, notes = notes)
        }
        val clusterResult = detectDualClusters(samples) ?: run {
            notes += "DUAL fail: 2-klaster podjela nije uspjela"
            return DualEvaluation(result = null, notes = notes)
        }
        var clusterA = clusterResult.clusterA
        var clusterB = clusterResult.clusterB
        var meanA = clusterResult.meanA
        var meanB = clusterResult.meanB

        repeat(3) { iteration ->
            val stdA = circularStdDevDeg(clusterA.map { it.cogDeg }, meanA)
            val stdB = circularStdDevDeg(clusterB.map { it.cogDeg }, meanB)
            val sigma = stdFilterSigma
            val thresholdA = sigma?.times(stdA)
            val thresholdB = sigma?.times(stdB)
            val filteredA = if (thresholdA != null && thresholdA > 0.0) {
                clusterA.filter { abs(signedShortestAngle(meanA, it.cogDeg)) <= thresholdA }
            } else {
                clusterA
            }
            val filteredB = if (thresholdB != null && thresholdB > 0.0) {
                clusterB.filter { abs(signedShortestAngle(meanB, it.cogDeg)) <= thresholdB }
            } else {
                clusterB
            }
            val rejectPctA = if (clusterA.isNotEmpty()) {
                ((clusterA.size - filteredA.size).toDouble() * 100.0 / clusterA.size.toDouble())
            } else {
                0.0
            }
            val rejectPctB = if (clusterB.isNotEmpty()) {
                ((clusterB.size - filteredB.size).toDouble() * 100.0 / clusterB.size.toDouble())
            } else {
                0.0
            }
            val sigmaLabel = if (sigma == null) "OFF" else "${"%.1f".format(sigma)}σ"
            notes += "DUAL σ-pass #${iteration + 1} ($sigmaLabel): stdA=${"%.1f".format(stdA)}°, keepA=${filteredA.size}/${clusterA.size}, rejectA=${"%.1f".format(rejectPctA)}%, stdB=${"%.1f".format(stdB)}°, keepB=${filteredB.size}/${clusterB.size}, rejectB=${"%.1f".format(rejectPctB)}%"
            if (filteredA.isEmpty() || filteredB.isEmpty()) {
                notes += if (sigma == null) {
                    "DUAL fail: nema valjanih točaka u jednom klasteru"
                } else {
                    "DUAL fail: ${"%.1f".format(sigma)}σ filter izbacio sve točke u jednom klasteru"
                }
                return DualEvaluation(result = null, notes = notes)
            }
            val newMeanA = circularMean(filteredA.map { it.cogDeg }) ?: return DualEvaluation(result = null, notes = notes + "DUAL fail: meanA nakon 1σ je null")
            val newMeanB = circularMean(filteredB.map { it.cogDeg }) ?: return DualEvaluation(result = null, notes = notes + "DUAL fail: meanB nakon 1σ je null")
            val changed = filteredA.size != clusterA.size ||
                filteredB.size != clusterB.size ||
                abs(signedShortestAngle(meanA, newMeanA)) > 0.05 ||
                abs(signedShortestAngle(meanB, newMeanB)) > 0.05
            clusterA = filteredA
            clusterB = filteredB
            meanA = newMeanA
            meanB = newMeanB
            if (!changed) return@repeat
        }

        val separation = abs(signedShortestAngle(meanA, meanB))
        notes += "DUAL check: separation=${"%.1f".format(separation)}° (traženo ${"%.0f".format(minTackSeparationDeg)}..${"%.0f".format(maxTackSeparationDeg)}°)"
        if (separation !in minTackSeparationDeg..maxTackSeparationDeg) {
            notes += "DUAL fail: separation izvan raspona"
            return DualEvaluation(result = null, notes = notes)
        }
        notes += "DUAL check: clusterA=${clusterA.size}, clusterB=${clusterB.size}, min=$minSamplesPerCluster"
        if (clusterA.size < minSamplesPerCluster || clusterB.size < minSamplesPerCluster) {
            notes += "DUAL fail: premalo uzoraka po klasteru"
            return DualEvaluation(result = null, notes = notes)
        }
        val spreadA = circularSpreadDeg(clusterA.map { it.cogDeg }, meanA)
        val spreadB = circularSpreadDeg(clusterB.map { it.cogDeg }, meanB)
        notes += "DUAL check: spreadA=${"%.1f".format(spreadA)}°, spreadB=${"%.1f".format(spreadB)}°, max=${"%.0f".format(maxClusterSpreadDeg)}°"
        if (spreadA > maxClusterSpreadDeg || spreadB > maxClusterSpreadDeg) {
            notes += "DUAL fail: spread preširok"
            return DualEvaluation(result = null, notes = notes)
        }

        val windAxis = circularMidpoint(meanA, meanB)
        val sideA = signedShortestAngle(windAxis, meanA)
        val sideB = signedShortestAngle(windAxis, meanB)
        if (sideA == 0.0 || sideB == 0.0) {
            notes += "DUAL fail: sredine su degenerirane oko osi vjetra"
            return DualEvaluation(result = null, notes = notes)
        }

        val portMean: Double
        val starboardMean: Double
        if (sideA < 0.0) {
            portMean = meanA
            starboardMean = meanB
        } else {
            portMean = meanB
            starboardMean = meanA
        }
        val portOffset = abs(signedShortestAngle(windAxis, portMean))
        val starboardOffset = abs(signedShortestAngle(windAxis, starboardMean))
        val targetPort = normalizeAngle360(windAxis - portOffset)
        val targetStarboard = normalizeAngle360(windAxis + starboardOffset)
        notes += "DUAL pass: svi uvjeti zadovoljeni"
        return DualEvaluation(
            result = DualResult(
            portMeanDeg = portMean,
            starboardMeanDeg = starboardMean,
            windAxisDeg = windAxis,
            portOffsetDeg = portOffset,
            starboardOffsetDeg = starboardOffset,
            targetPortDeg = targetPort,
            targetStarboardDeg = targetStarboard
            ),
            notes = notes
        )
    }

    private fun computeCurrent5sHeading(validSamples: List<CogSample>): Double? {
        if (validSamples.isEmpty()) return null
        val latest = validSamples.last().timestampMs
        val windowStart = latest - 5_000L
        val last5s = validSamples.filter { it.timestampMs >= windowStart }
        return circularMean(last5s.map { it.cogDeg })
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
        mode = Mode.SINGLE
        debugInfo = WindShiftDebugInfo(
            mode = Mode.SINGLE,
            calcWindowMinutes = historyWindowMinutes,
            availableDataMs = 0L,
            sampleCount = 0,
            current5sHeadingDeg = null,
            singleMeanCourseDeg = null,
            singleDeviationDeg = null,
            portMeanCourseDeg = null,
            starboardMeanCourseDeg = null,
            windAxisCourseDeg = null,
            portOffsetDeg = null,
            starboardOffsetDeg = null,
            targetPortDeg = null,
            targetStarboardDeg = null,
            diffToPortDeg = null,
            diffToStarboardDeg = null,
            notes = emptyList()
        )
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

        private fun circularStdDevDeg(anglesDeg: List<Double>, meanDeg: Double): Double {
            if (anglesDeg.isEmpty()) return 0.0
            val variance = anglesDeg
                .asSequence()
                .map { signedShortestAngle(meanDeg, it) }
                .map { it * it }
                .average()
            return kotlin.math.sqrt(variance).coerceAtLeast(0.0)
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

    private data class DualResult(
        val portMeanDeg: Double,
        val starboardMeanDeg: Double,
        val windAxisDeg: Double,
        val portOffsetDeg: Double,
        val starboardOffsetDeg: Double,
        val targetPortDeg: Double,
        val targetStarboardDeg: Double
    )

    private data class DualEvaluation(
        val result: DualResult?,
        val notes: List<String>
    )
}
