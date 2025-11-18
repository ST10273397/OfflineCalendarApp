package com.example.prog7314progpoe.notifications

import androidx.core.app.NotificationCompat
import com.example.prog7314progpoe.R

class Notifications {

    var builder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.notification_icon)
        .setContentTitle(textTitle)
        .setContentText(textContent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

}