package com.example.prog7314progpoe.database.calendar

import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.database.user.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Helper object for interacting with Firebase Realtime Database for Calendars.
 * Supports CRUD, sharing, invites, and permission management.
 */
object FirebaseCalendarDbHelper {

    private const val RTDB_URL =
        "https://chronosync-f3425-default-rtdb.europe-west1.firebasedatabase.app/"

    private val db = FirebaseDatabase.getInstance(RTDB_URL).reference
    private val auth = FirebaseAuth.getInstance()

    //-----------------------------------------------------------------------------------------------
    // CREATE
    //-----------------------------------------------------------------------------------------------

    /**
     * Create a new calendar for a user
     */
    fun insertCalendar(
        ownerId: String,
        title: String,
        description: String? = null,
        holidays: List<HolidayModel>? = null,
        onComplete: (String?) -> Unit = {}
    ) {
        val key = db.child("calendars").push().key ?: return onComplete(null)

        // Map holidays by ID (generate ID if missing)
        val holidayMap = holidays?.associateBy {
            it.holidayId ?: db.child("calendars/$key/holidays").push().key!!
        }

        // Owner gets full permissions and auto-accepted status
        val ownerInfo = SharedUserInfo(
            status = "accepted",
            canEdit = true,
            canShare = true,
            invitedAt = System.currentTimeMillis(),
            acceptedAt = System.currentTimeMillis()
        )

        val calendar = CalendarModel(
            calendarId = key,
            title = title,
            description = description,
            ownerId = ownerId,
            sharedWith = mapOf(ownerId to ownerInfo),
            holidays = holidayMap
        )

        val updates = hashMapOf<String, Any>(
            "/calendars/$key" to calendar,
            "/user_calendars/$ownerId/$key" to true
        )

        db.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) onComplete(key) else onComplete(null)
        }
    }

    //-----------------------------------------------------------------------------------------------
    // SHARE / INVITES
    //-----------------------------------------------------------------------------------------------

    /**
     * Share calendar with another user (pending invite)
     */
    fun shareCalendar(
        calendarId: String,
        userId: String,
        canEdit: Boolean = false,
        canShare: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        // Create pending invite instead of immediate share
        val inviteInfo = SharedUserInfo(
            status = "pending",
            canEdit = canEdit,
            canShare = canShare,
            invitedAt = System.currentTimeMillis(),
            acceptedAt = null
        )

        val updates = hashMapOf<String, Any>(
            "/calendars/$calendarId/sharedWith/$userId" to inviteInfo,
            "/user_invites/$userId/$calendarId" to true
        )
        db.updateChildren(updates).addOnCompleteListener { onComplete() }
    }

    /**
     * Share calendar with user by email
     */
    fun shareCalendarByEmail(
        calendarId: String,
        targetEmail: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val usersRef = db.child("users")

        // Search for user with matching email
        usersRef.orderByChild("email").equalTo(targetEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // There should only be one user with that email
                    val userId = snapshot.children.first().key ?: return@addOnSuccessListener
                    shareCalendar(calendarId, userId) {
                        onSuccess()
                    }
                } else {
                    onError("User with that email not found.")
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to find user.")
            }
    }

    // ========== NEW METHODS FOR UPGRADED SHARING SYSTEM ==========

    // Accept an invite
    fun acceptInvite(
        calendarId: String,
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Get invite info
        db.child("calendars/$calendarId/sharedWith/$userId")
            .get()
            .addOnSuccessListener { snapshot ->
                val info = snapshot.getValue(SharedUserInfo::class.java)
                if (info == null) {
                    onError("Invite not found")
                    return@addOnSuccessListener
                }

                // Update to accepted
                info.status = "accepted"
                info.acceptedAt = System.currentTimeMillis()

                val updates = hashMapOf<String, Any?>(
                    "/calendars/$calendarId/sharedWith/$userId" to info,
                    "/user_calendars/$userId/$calendarId" to true,
                    "/user_invites/$userId/$calendarId" to null  // Remove from invites
                )

                db.updateChildren(updates)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e.message ?: "Failed to accept") }
            }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to load invite") }
    }

    /**
     * Decline an invite
     */
    fun declineInvite(
        calendarId: String,
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val updates = hashMapOf<String, Any?>(
            "/calendars/$calendarId/sharedWith/$userId" to null,
            "/user_invites/$userId/$calendarId" to null
        )

        db.updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to decline") }
    }

    /**
     * Update a shared user's permissions (owner-only)
     */
    fun updatePermissions(
        calendarId: String,
        targetUserId: String,
        canEdit: Boolean,
        canShare: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUserId = auth.currentUser?.uid ?: return onError("Not logged in")

        // Verify caller is owner
        db.child("calendars/$calendarId/ownerId") //  Verify caller is owner
            .get()
            .addOnSuccessListener { snapshot ->
                val ownerId = snapshot.getValue(String::class.java)
                if (ownerId != currentUserId) {
                    onError("Only owner can change permissions")
                    return@addOnSuccessListener
                }

                // Get current info to preserve status
                db.child("calendars/$calendarId/sharedWith/$targetUserId")
                    .get()
                    .addOnSuccessListener { userSnap ->
                        val info = userSnap.getValue(SharedUserInfo::class.java)
                        if (info == null) {
                            onError("User not found")
                            return@addOnSuccessListener
                        }

                        info.canEdit = canEdit
                        info.canShare = canShare

                        db.child("calendars/$calendarId/sharedWith/$targetUserId")
                            .setValue(info)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { e -> onError(e.message ?: "Failed") }
                    }
            }
    }

    //-----------------------------------------------------------------------------------------------
    // READ / FETCH
    //-----------------------------------------------------------------------------------------------

    /**
     * Get all shared users for a calendar
     */
    fun getSharedUsers(calendarId: String, onComplete: (List<UserModel>) -> Unit) {
        val usersRef = db.child("users")

        db.child("calendars").child(calendarId).child("sharedWith").get()
            .addOnSuccessListener { snapshot ->
                val sharedUserIds = snapshot.children.mapNotNull { it.key }
                if (sharedUserIds.isEmpty()) return@addOnSuccessListener onComplete(emptyList())

                val usersList = mutableListOf<UserModel>()
                var remaining = sharedUserIds.size

                sharedUserIds.forEach { userId ->
                    usersRef.child(userId).get()
                        .addOnSuccessListener { userSnap ->
                            userSnap.getValue(UserModel::class.java)?.let { usersList.add(it) }
                            remaining--
                            if (remaining == 0) onComplete(usersList)
                        }
                        .addOnFailureListener { remaining--; if (remaining == 0) onComplete(usersList) }
                }
            }
            .addOnFailureListener { onComplete(emptyList()) }
    }

    /**
     * Check if a user has a specific permission ("edit" or "share") on a calendar
     */
    fun checkPermission(
        calendarId: String,
        userId: String,
        permission: String, // "edit" or "share"
        onResult: (Boolean) -> Unit
    ) {
        db.child("calendars/$calendarId")
            .get()
            .addOnSuccessListener { snapshot ->
                val calendar = snapshot.getValue(CalendarModel::class.java)

                //owner always has all permissions
                if (calendar?.ownerId == userId) {
                    onResult(true) // Owner has all permissions
                    return@addOnSuccessListener
                }

                // Check user specific permission.
                val userInfo = calendar?.sharedWith?.get(userId)
                val hasPermission = when (permission) {
                    "edit" -> userInfo?.canEdit == true && userInfo.status == "accepted"
                    "share" -> userInfo?.canShare == true && userInfo.status == "accepted"
                    else -> false
                }
                onResult(hasPermission)
            }
            .addOnFailureListener { onResult(false) }
    }

    /**
     * Get all calendars for a user
     */
    fun getCalendarsForUser(userId: String, onResult: (List<CalendarModel>) -> Unit) {
        db.child("user_calendars").child(userId).get()
            .addOnSuccessListener { snapshot ->
                val ids = snapshot.children.mapNotNull { it.key }
                if (ids.isEmpty()) return@addOnSuccessListener onResult(emptyList())

                val result = mutableListOf<CalendarModel>()
                var remaining = ids.size
                ids.forEach { id ->
                    db.child("calendars").child(id).get()
                        .addOnSuccessListener { calSnap ->
                            calSnap.getValue(CalendarModel::class.java)?.let {
                                it.calendarId = calSnap.key.toString()
                                result.add(it)
                            }
                            remaining--
                            if (remaining == 0) onResult(result)
                        }
                        .addOnFailureListener {
                            remaining--
                            if (remaining == 0) onResult(result)
                        }
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    //-----------------------------------------------------------------------------------------------
    // UPDATE / DELETE
    //-----------------------------------------------------------------------------------------------

    /**
     * Update an existing calendar
     */
    fun updateCalendar(calendar: CalendarModel, onComplete: (Boolean) -> Unit) {
        calendar.calendarId?.let { id ->
            db.child("calendars").child(id).setValue(calendar)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
    }

    /**
     * Delete a calendar (owner only)
     */
    fun deleteCalendar(calendarId: String, ownerId: String, onComplete: () -> Unit = {}) {
        val updates = hashMapOf<String, Any?>(
            "/calendars/$calendarId" to null,
            "/user_calendars/$ownerId/$calendarId" to null
        )
        db.updateChildren(updates).addOnCompleteListener { onComplete() }
    }

    /**
     * Delete a calendar and unlink all users and invites
     */
    fun deleteCalendarAndUnlink(
        calendarId: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        db.child("calendars").child(calendarId).get()
            .addOnSuccessListener { snap ->
                val ownerId = snap.child("ownerId").getValue(String::class.java)
                val shared = snap.child("sharedWith").children.mapNotNull { it.key }.toSet()

                val allMembers = if (ownerId == null) shared else shared + ownerId
                val updates = hashMapOf<String, Any?>("/calendars/$calendarId" to null)

                allMembers.forEach { uid ->
                    updates["/user_calendars/$uid/$calendarId"] = null
                    updates["/user_invites/$uid/$calendarId"] = null
                }

                db.updateChildren(updates)
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }
            .addOnFailureListener { onComplete(false) }
    }

    //-----------------------------------------------------------------------------------------------
    // USER ACTIONS
    //-----------------------------------------------------------------------------------------------

    /**
     * Remove a user from a calendar
     */
    fun removeUserFromCalendar(
        calendarId: String,
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val updates = hashMapOf<String, Any?>(
            "/calendars/$calendarId/sharedWith/$userId" to null,
            "/user_calendars/$userId/$calendarId" to null,
            "/user_invites/$userId/$calendarId" to null
        )
        db.updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to remove user.") }
    }

    /**
     * Leave a calendar (cannot leave if owner)
     */
    fun leaveCalendar(
        calendarId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUserId = auth.currentUser?.uid ?: return onError("Not logged in")

        db.child("calendars").child(calendarId).child("ownerId").get()
            .addOnSuccessListener { snapshot ->
                val ownerId = snapshot.value?.toString()
                if (ownerId == currentUserId) return@addOnSuccessListener onError("You cannot leave your own calendar")

                removeUserFromCalendar(calendarId, currentUserId, onSuccess, onError)
            }
            .addOnFailureListener { onError("Error checking calendar ownership: ${it.message}") }
    }
}
