package com.aloha.startline.regatta

data class RegattaPoint(
    val latitude: Double,
    val longitude: Double
)

data class RegattaGate(
    val id: String,
    val order: Int,
    val type: String,
    val name: String,
    val pointA: RegattaPoint,
    val pointB: RegattaPoint
)

data class RegattaRaceSummary(
    val id: String,
    val name: String,
    val dayNumber: Int,
    val sequenceNumber: Int,
    val state: String,
    val countdownTargetEpochMs: Long?,
    val scoringTargetGateId: String?,
    val raceDate: String?,
    val startTime: String?,
    val endTime: String?,
    val raceLengthNm: Double,
    val gates: List<RegattaGate>
)

data class RegattaBoatSummary(
    val id: String,
    val deviceId: String?,
    val boatCode: String?,
    val boatName: String,
    val skipperName: String,
    val clubName: String,
    val lengthValue: Double?,
    val lengthUnit: String?,
    val groupCode: String?
)

data class RegattaParticipantLive(
    val boatId: String,
    val boatName: String,
    val skipperName: String,
    val groupCode: String?,
    val status: String,
    val startSnapshotEpochMs: Long?,
    val startSnapshotLatitude: Double?,
    val startSnapshotLongitude: Double?,
    val lastSignalEpochMs: Long?,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastSpeedKnots: Double?,
    val nextGateOrder: Int,
    val finishedAtEpochMs: Long?
)

data class RegattaCrossingSummary(
    val id: String,
    val boatId: String,
    val boatName: String,
    val gateId: String,
    val gateName: String,
    val gateOrder: Int,
    val gateType: String,
    val crossingEpochMs: Long,
    val source: String,
    val status: String
)

data class RegattaPenaltySummary(
    val id: String,
    val boatId: String,
    val boatName: String,
    val type: String,
    val value: Double?,
    val reason: String,
    val createdAt: String
)

data class RegattaNoticePost(
    val id: String,
    val noticeText: String,
    val publishedAt: String?
)

data class RegattaEventSnapshot(
    val eventId: String,
    val name: String,
    val joinCode: String,
    val organizerName: String,
    val startDate: String?,
    val endDate: String?,
    val isPublic: Boolean,
    val maxBoats: Int,
    val noticeBoard: String,
    val noticeBoardUpdatedAt: String?,
    val noticePosts: List<RegattaNoticePost>,
    val status: String,
    val boats: List<RegattaBoatSummary>,
    val races: List<RegattaRaceSummary>,
    val activeRaceId: String?
)

data class RegattaLiveSnapshot(
    val eventId: String,
    val raceId: String,
    val raceName: String,
    val state: String,
    val countdownTargetEpochMs: Long?,
    val scoringTargetGateId: String?,
    val gates: List<RegattaGate>,
    val participants: List<RegattaParticipantLive>,
    val crossings: List<RegattaCrossingSummary>,
    val penalties: List<RegattaPenaltySummary>
)

data class RegattaDraft(
    val name: String,
    val joinCode: String,
    val organizerName: String,
    val organizerCode: String,
    val startDate: String,
    val endDate: String,
    val isPublic: Boolean,
    val maxBoats: Int
)

data class BoatRegistrationDraft(
    val boatName: String,
    val skipperName: String,
    val clubName: String,
    val lengthValue: String,
    val lengthUnit: String,
    val groupCode: String
)

data class GateDraft(
    val order: Int,
    val type: String,
    val name: String,
    val pointA: RegattaPoint,
    val pointB: RegattaPoint
)

data class PendingGateDraft(
    val id: String,
    val name: String,
    val pointA: RegattaPoint?,
    val pointB: RegattaPoint?
)
