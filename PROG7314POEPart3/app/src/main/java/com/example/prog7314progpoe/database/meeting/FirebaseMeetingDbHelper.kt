package com.example.prog7314progpoe.database.meeting

import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.SharedUserInfo
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.util.Log

object FirebaseMeetingDbHelper {

    private const val RTDB_URL = "https://chronosync-f3425-default-rtdb.europe-west1.firebasedatabase.app/"
    private val db = FirebaseDatabase.getInstance(RTDB_URL).reference
    private val auth = FirebaseAuth.getInstance()

    fun ensureMeetingCalendar(
        userId: String,
        onComplete: (String?) -> Unit
    ) {
        db.child("meeting_calendars").child(userId).get()
            .addOnSuccessListener { snapshot ->
                val existingCalId = snapshot.getValue(String::class.java)

                if (existingCalId != null) {
                    db.child("calendars").child(existingCalId).child("calendarId").get()
                        .addOnSuccessListener { calIdCheck ->
                            if (calIdCheck.exists()) {
                                Log.d("MeetingDbHelper", "Found existing meeting calendar: $existingCalId")
                                onComplete(existingCalId)
                            } else {
                                Log.w("MeetingDbHelper", "Meeting calendar reference broken, creating new one")
                                createMeetingCalendar(userId, onComplete)
                            }
                        }
                        .addOnFailureListener {
                            Log.e("MeetingDbHelper", "Error checking calendar, creating new one: ${it.message}")
                            createMeetingCalendar(userId, onComplete)
                        }
                } else {
                    Log.d("MeetingDbHelper", "No meeting calendar found, creating one")
                    createMeetingCalendar(userId, onComplete)
                }
            }
            .addOnFailureListener {
                Log.e("MeetingDbHelper", "Error checking meeting_calendars: ${it.message}")
                onComplete(null)
            }
    }

    private fun createMeetingCalendar(
        userId: String,
        onComplete: (String?) -> Unit
    ) {
        val calendarId = db.child("calendars").push().key ?: return onComplete(null)

        val meetingCalendar = CalendarModel(
            calendarId = calendarId,
            title = "My Meetings",
            description = "All my meeting invitations and accepted meetings",
            ownerId = userId,
            sharedWith = mapOf(userId to SharedUserInfo(
                status = "accepted",
                canEdit = false,
                canShare = false,
                invitedAt = System.currentTimeMillis(),
                acceptedAt = System.currentTimeMillis()
            )),
            holidays = null,
            isMeetingCalendar = true
        )

        val updates = hashMapOf<String, Any>(
            "/calendars/$calendarId" to meetingCalendar,
            "/user_calendars/$userId/$calendarId" to true,
            "/meeting_calendars/$userId" to calendarId
        )

        Log.d("MeetingDbHelper", "Creating new meeting calendar: $calendarId for user: $userId")

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("MeetingDbHelper", "Successfully created meeting calendar")
                onComplete(calendarId)
            }
            .addOnFailureListener {
                Log.e("MeetingDbHelper", "Failed to create meeting calendar: ${it.message}")
                onComplete(null)
            }
    }

    fun createMeeting(
        creatorId: String,
        title: String,
        description: String,
        dateIso: String,
        timeStart: Long,
        timeEnd: Long,
        participantEmails: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val meetingId = db.child("meetings").push().key ?: return onError("Failed to generate ID")

        Log.d("MeetingDbHelper", "Creating meeting: $meetingId")

        db.child("users").child(creatorId).child("email").get()
            .addOnSuccessListener { emailSnapshot ->
                val creatorEmail = emailSnapshot.getValue(String::class.java) ?: ""
                Log.d("MeetingDbHelper", "Creator email: $creatorEmail")

                findUsersByEmails(participantEmails) { userMap ->
                    Log.d("MeetingDbHelper", "Found ${userMap.size} users from emails")

                    val participants = mutableMapOf<String, ParticipantInfo>()

                    participants[creatorId] = ParticipantInfo(
                        status = "accepted",
                        email = creatorEmail,
                        invitedAt = System.currentTimeMillis(),
                        respondedAt = System.currentTimeMillis()
                    )

                    val foundEmails = mutableListOf<String>()
                    val notFoundEmails = mutableListOf<String>()

                    participantEmails.forEach { email ->
                        val userId = userMap.entries.find { it.value.equals(email, ignoreCase = true) }?.key
                        if (userId != null && userId != creatorId) {
                            participants[userId] = ParticipantInfo(
                                status = "pending",
                                email = email,
                                invitedAt = System.currentTimeMillis(),
                                respondedAt = null
                            )
                            foundEmails.add(email)
                            Log.d("MeetingDbHelper", "Added participant: $email ($userId)")
                        } else if (userId != creatorId) {
                            notFoundEmails.add(email)
                        }
                    }

                    if (notFoundEmails.isNotEmpty()) {
                        val errorMsg = "Users not found: ${notFoundEmails.joinToString(", ")}"
                        Log.w("MeetingDbHelper", errorMsg)
                    }

                    if (foundEmails.isEmpty() && participantEmails.isNotEmpty()) {
                        onError("None of the invited users could be found. Check email addresses.")
                        return@findUsersByEmails
                    }

                    val meeting = MeetingModel(
                        meetingId = meetingId,
                        title = title,
                        description = description,
                        creatorId = creatorId,
                        date = DateModel(dateIso),
                        timeStart = timeStart,
                        timeEnd = timeEnd,
                        participants = participants,
                        createdAt = System.currentTimeMillis()
                    )

                    ensureMeetingCalendar(creatorId) { calendarId ->
                        if (calendarId == null) {
                            Log.e("MeetingDbHelper", "Failed to ensure meeting calendar")
                            onError("Failed to create meeting calendar")
                            return@ensureMeetingCalendar
                        }

                        Log.d("MeetingDbHelper", "Using meeting calendar: $calendarId")

                        val holiday = HolidayModel(
                            holidayId = meetingId,
                            name = title,
                            desc = description,
                            date = HolidayModel.DateInfo(iso = dateIso),
                            timeStart = timeStart,
                            timeEnd = timeEnd,
                            type = listOf("Meeting")
                        )

                        val updates = hashMapOf<String, Any?>(
                            "/meetings/$meetingId" to meeting,
                            "/calendars/$calendarId/holidays/$meetingId" to holiday
                        )

                        participants.keys.filter { it != creatorId }.forEach { userId ->
                            updates["/user_meeting_invites/$userId/$meetingId"] = true
                            Log.d("MeetingDbHelper", "Creating invite for user: $userId")
                        }

                        db.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d("MeetingDbHelper", "Meeting created successfully")
                                onSuccess(meetingId)
                            }
                            .addOnFailureListener {
                                Log.e("MeetingDbHelper", "Failed to create meeting: ${it.message}")
                                onError(it.message ?: "Failed to create meeting")
                            }
                    }
                }
            }
            .addOnFailureListener {
                Log.e("MeetingDbHelper", "Failed to fetch creator email: ${it.message}")
                onError("Failed to fetch creator email: ${it.message}")
            }
    }

    fun acceptMeeting(
        meetingId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d("MeetingDbHelper", "Accepting meeting: $meetingId for user: $userId")

        db.child("meetings/$meetingId").get()
            .addOnSuccessListener { snapshot ->
                val meeting = snapshot.getValue(MeetingModel::class.java)
                if (meeting == null) {
                    Log.e("MeetingDbHelper", "Meeting not found")
                    onError("Meeting not found")
                    return@addOnSuccessListener
                }

                val participantInfo = meeting.participants?.get(userId)
                if (participantInfo == null) {
                    Log.e("MeetingDbHelper", "User not invited to meeting")
                    onError("You are not invited to this meeting")
                    return@addOnSuccessListener
                }

                participantInfo.status = "accepted"
                participantInfo.respondedAt = System.currentTimeMillis()

                ensureMeetingCalendar(userId) { calendarId ->
                    if (calendarId == null) {
                        Log.e("MeetingDbHelper", "Failed to get meeting calendar")
                        onError("Failed to add to calendar")
                        return@ensureMeetingCalendar
                    }

                    val holiday = HolidayModel(
                        holidayId = meetingId,
                        name = meeting.title,
                        desc = meeting.description,
                        date = meeting.date?.let { HolidayModel.DateInfo(iso = it.iso) },
                        timeStart = meeting.timeStart,
                        timeEnd = meeting.timeEnd,
                        type = listOf("Meeting")
                    )

                    val updates = hashMapOf<String, Any?>(
                        "/meetings/$meetingId/participants/$userId" to participantInfo,
                        "/user_meeting_invites/$userId/$meetingId" to null,
                        "/calendars/$calendarId/holidays/$meetingId" to holiday
                    )

                    db.updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d("MeetingDbHelper", "Meeting accepted successfully")
                            onSuccess()
                        }
                        .addOnFailureListener {
                            Log.e("MeetingDbHelper", "Failed to accept meeting: ${it.message}")
                            onError(it.message ?: "Failed to accept")
                        }
                }
            }
            .addOnFailureListener {
                Log.e("MeetingDbHelper", "Failed to load meeting: ${it.message}")
                onError("Failed to load meeting")
            }
    }

    fun declineMeeting(
        meetingId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d("MeetingDbHelper", "Declining meeting: $meetingId for user: $userId")

        db.child("meetings/$meetingId/participants/$userId").get()
            .addOnSuccessListener { snapshot ->
                val participantInfo = snapshot.getValue(ParticipantInfo::class.java)
                if (participantInfo == null) {
                    onError("Participant info not found")
                    return@addOnSuccessListener
                }

                participantInfo.status = "declined"
                participantInfo.respondedAt = System.currentTimeMillis()

                val updates = hashMapOf<String, Any?>(
                    "/meetings/$meetingId/participants/$userId" to participantInfo,
                    "/user_meeting_invites/$userId/$meetingId" to null
                )

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("MeetingDbHelper", "Meeting declined successfully")
                        onSuccess()
                    }
                    .addOnFailureListener {
                        Log.e("MeetingDbHelper", "Failed to decline: ${it.message}")
                        onError(it.message ?: "Failed to decline")
                    }
            }
            .addOnFailureListener {
                Log.e("MeetingDbHelper", "Failed to load participant info: ${it.message}")
                onError("Failed to load participant info")
            }
    }

    fun getMeetingsCreatedByUser(
        userId: String,
        onResult: (List<MeetingModel>) -> Unit
    ) {
        Log.d("MeetingDbHelper", "Getting accepted meetings for user: $userId")

        ensureMeetingCalendar(userId) { calendarId ->
            if (calendarId == null) {
                Log.e("MeetingDbHelper", "No meeting calendar for user")
                onResult(emptyList())
                return@ensureMeetingCalendar
            }

            Log.d("MeetingDbHelper", "Using calendar: $calendarId")

            db.child("calendars/$calendarId/holidays").get()
                .addOnSuccessListener { snapshot ->
                    val meetingIds = snapshot.children.mapNotNull { it.key }
                    Log.d("MeetingDbHelper", "Found ${meetingIds.size} meetings in calendar")

                    if (meetingIds.isEmpty()) {
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }

                    fetchMeetingsByIds(meetingIds, onResult)
                }
                .addOnFailureListener {
                    Log.e("MeetingDbHelper", "Failed to load meetings: ${it.message}")
                    onResult(emptyList())
                }
        }
    }

    fun getMeetingInvitesForUser(
        userId: String,
        onResult: (List<MeetingModel>) -> Unit
    ) {
        Log.d("MeetingDbHelper", "Getting pending invites for user: $userId")

        db.child("user_meeting_invites/$userId").get()
            .addOnSuccessListener { snapshot ->
                val meetingIds = snapshot.children.mapNotNull { it.key }
                Log.d("MeetingDbHelper", "Found ${meetingIds.size} pending invites")

                if (meetingIds.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }

                fetchMeetingsByIds(meetingIds, onResult)
            }
            .addOnFailureListener {
                Log.e("MeetingDbHelper", "Failed to load invites: ${it.message}")
                onResult(emptyList())
            }
    }

    fun getUserMeetings(
        userId: String,
        onResult: (List<MeetingModel>) -> Unit
    ) {
        getMeetingsCreatedByUser(userId, onResult)
    }

    fun getPendingInvites(
        userId: String,
        onResult: (List<MeetingModel>) -> Unit
    ) {
        getMeetingInvitesForUser(userId, onResult)
    }

    private fun findUsersByEmails(
        emails: List<String>,
        onResult: (Map<String, String>) -> Unit
    ) {
        val userMap = mutableMapOf<String, String>()
        val notFound = mutableListOf<String>()
        var remaining = emails.size

        if (emails.isEmpty()) {
            onResult(emptyMap())
            return
        }

        emails.forEach { email ->
            val normalizedEmail = email.trim().lowercase()

            db.child("users").get()
                .addOnSuccessListener { snapshot ->
                    var found = false

                    for (userSnapshot in snapshot.children) {
                        val userEmail = userSnapshot.child("email").getValue(String::class.java)
                        if (userEmail?.trim()?.lowercase() == normalizedEmail) {
                            val userId = userSnapshot.key
                            if (userId != null) {
                                userMap[userId] = email
                                found = true
                                Log.d("MeetingDbHelper", "Found user: $email -> $userId")
                                break
                            }
                        }
                    }

                    if (!found) {
                        notFound.add(email)
                        Log.w("MeetingDbHelper", "User not found: $email")
                    }

                    remaining--
                    if (remaining == 0) {
                        if (notFound.isNotEmpty()) {
                            Log.w("MeetingDbHelper", "Could not find users for: ${notFound.joinToString(", ")}")
                        }
                        Log.d("MeetingDbHelper", "Email lookup complete. Found ${userMap.size}/${emails.size} users")
                        onResult(userMap)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("MeetingDbHelper", "Error finding user: $email - ${error.message}")
                    notFound.add(email)

                    remaining--
                    if (remaining == 0) {
                        if (notFound.isNotEmpty()) {
                            Log.w("MeetingDbHelper", "Could not find users for: ${notFound.joinToString(", ")}")
                        }
                        onResult(userMap)
                    }
                }
        }
    }

    private fun fetchMeetingsByIds(
        meetingIds: List<String>,
        onResult: (List<MeetingModel>) -> Unit
    ) {
        val meetings = mutableListOf<MeetingModel>()
        var remaining = meetingIds.size

        meetingIds.forEach { id ->
            db.child("meetings/$id").get()
                .addOnSuccessListener { snapshot ->
                    snapshot.getValue(MeetingModel::class.java)?.let { meeting ->
                        meeting.meetingId = id
                        meetings.add(meeting)
                    }
                    remaining--
                    if (remaining == 0) {
                        onResult(meetings)
                    }
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining == 0) {
                        onResult(meetings)
                    }
                }
        }
    }

    fun acceptMeetingInvite(
        meetingId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        acceptMeeting(meetingId, userId, onSuccess, onError)
    }

    fun declineMeetingInvite(
        meetingId: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        declineMeeting(meetingId, userId, onSuccess, onError)
    }
}