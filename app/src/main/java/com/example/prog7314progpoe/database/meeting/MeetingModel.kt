package com.example.prog7314progpoe.database.meeting

// MAin meeting data structure
data class MeetingModel(
    var meetingId: String? = null,
    var title: String? = null,
    var description: String? = null,
    var creatorId: String? = null,
    var date: DateModel? = null,
    var timeStart: Long? = null,
    var timeEnd: Long? = null,
    var participants: Map<String, ParticipantInfo>? = null,
    var createdAt: Long? = null
) {
    constructor() : this(null, null, null, null, null, null, null, null, null)
}

//Tracks individual participant response
data class ParticipantInfo(
    var status: String? = "pending",
    var email: String? = null,
    var invitedAt: Long? = null,
    var respondedAt: Long? = null
) {
    constructor() : this(null, null, null, null)
}

data class DateModel(
    var iso: String? = null
) {
    constructor() : this(null)
}