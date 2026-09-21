package com.loadalert.messaging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.loadalert.data.model.MessagingApp
import com.loadalert.data.model.RoommateContact
import java.net.URLEncoder

/**
 * Turns "tell my roommate I started a load" into an Intent.
 *
 * Nothing is sent automatically. Every path lands the user in their messaging app with the
 * send button still to press, because no mainstream messaging app lets a third-party app
 * send on the user's behalf.
 */
class RoommateMessenger(private val context: Context) {

    fun buildStartMessageIntent(contact: RoommateContact, durationMinutes: Int): Intent? {
        val message = startMessage(durationMinutes)
        val intent = when (contact.app) {
            MessagingApp.SMS -> smsIntent(contact.contactValue, message)
            MessagingApp.WHATSAPP -> whatsAppIntent(contact.contactValue, message)
            MessagingApp.INSTAGRAM -> instagramIntent(message)
            MessagingApp.SNAPCHAT -> shareIntent(message, MessagingApp.SNAPCHAT.packageName)
            MessagingApp.CHOOSER -> null
        }
        val resolved = intent?.takeIf { it.canBeHandled() }
        // Anything we cannot target directly degrades to the system share sheet rather than
        // failing silently.
        return (resolved ?: chooserIntent(message)).addNewTaskFlagIfNeeded()
    }

    fun startMessage(durationMinutes: Int): String =
        "Started a load, ready in $durationMinutes min"

    /** True when the chosen app is actually installed, used to warn during setup. */
    fun isAppInstalled(app: MessagingApp): Boolean {
        val packageName = app.packageName ?: return true
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun smsIntent(number: String, message: String): Intent? {
        if (number.isBlank()) return null
        return Intent(Intent.ACTION_SENDTO, ("smsto:" + number.trim()).toUri())
            .putExtra("sms_body", message)
    }

    /**
     * wa.me is WhatsApp's own deep link and is the only documented way to pre-fill a chat.
     * It needs a full international number with no punctuation.
     */
    private fun whatsAppIntent(number: String, message: String): Intent? {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val url = "https://wa.me/$digits?text=" + URLEncoder.encode(message, "UTF-8")
        return Intent(Intent.ACTION_VIEW, url.toUri())
            .setPackage(MessagingApp.WHATSAPP.packageName)
    }

    /**
     * Instagram accepts a share but drops the text for direct messages, so we also put the
     * message on the clipboard. The onboarding copy tells the user to paste.
     */
    private fun instagramIntent(message: String): Intent? {
        copyToClipboard(message)
        return shareIntent(message, MessagingApp.INSTAGRAM.packageName)
    }

    private fun shareIntent(message: String, packageName: String?): Intent? {
        if (packageName == null) return null
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, message)
            .setPackage(packageName)
    }

    private fun chooserIntent(message: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, message)
        return Intent.createChooser(send, "Tell your roommate")
    }

    private fun copyToClipboard(message: String) {
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText("LoadAlert", message))
    }

    private fun Intent.canBeHandled(): Boolean =
        resolveActivity(context.packageManager) != null

    private fun Intent.addNewTaskFlagIfNeeded(): Intent = apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
