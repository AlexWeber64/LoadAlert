package com.loadalert.data.model

/** A saved geographic point that represents the shared laundry room. */
data class LaundryLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String = "Laundry room",
)

/**
 * How well each target app supports a pre-filled message body when we hand it an Intent.
 * Surfaced in the UI so the user is not surprised when an app opens with an empty compose box.
 */
enum class PrefillSupport {
    /** The message body arrives filled in and addressed to the contact. */
    FULL,

    /** The app opens on the right screen but the user still taps send (or picks the chat). */
    PARTIAL,

    /** The app only accepts a share sheet; recipient and/or text must be chosen by hand. */
    NONE,
}

enum class MessagingApp(
    val displayName: String,
    val packageName: String?,
    val prefillSupport: PrefillSupport,
    val caveat: String,
) {
    SMS(
        displayName = "Text message (SMS)",
        packageName = null,
        prefillSupport = PrefillSupport.FULL,
        caveat = "Opens your default SMS app with the message already typed. You tap send.",
    ),
    WHATSAPP(
        displayName = "WhatsApp",
        packageName = "com.whatsapp",
        prefillSupport = PrefillSupport.FULL,
        caveat = "Opens the WhatsApp chat with the message already typed. You tap send.",
    ),
    INSTAGRAM(
        displayName = "Instagram DM",
        packageName = "com.instagram.android",
        prefillSupport = PrefillSupport.NONE,
        caveat = "Instagram does not accept pre-filled DMs from other apps. " +
            "LoadAlert copies the message to your clipboard and opens Instagram's share sheet " +
            "so you can pick the chat and paste.",
    ),
    SNAPCHAT(
        displayName = "Snapchat",
        packageName = "com.snapchat.android",
        prefillSupport = PrefillSupport.PARTIAL,
        caveat = "Snapchat opens its own send-to screen with the text attached. " +
            "You pick the friend and send.",
    ),
    CHOOSER(
        displayName = "Ask me each time",
        packageName = null,
        prefillSupport = PrefillSupport.PARTIAL,
        caveat = "Shows the Android share sheet so you can pick any app that is installed.",
    ),
}

data class RoommateContact(
    val name: String,
    /** Phone number for SMS/WhatsApp, or a handle for Instagram/Snapchat. May be blank for share-sheet apps. */
    val contactValue: String,
    val app: MessagingApp,
)

/** How the escalation ladder behaves once the user has ignored [LaundrySettings.escalateAfterIgnoredChecks] reminders. */
data class EscalationSettings(
    val escalateAfterIgnoredChecks: Int = 3,
    val shortenInterval: Boolean = true,
    val escalatedIntervalMinutes: Int = 2,
    val increaseUrgency: Boolean = true,
)

data class QuietHours(
    val enabled: Boolean = false,
    /** Minutes from midnight, local time. A start after the end means the window wraps past midnight. */
    val startMinuteOfDay: Int = 22 * 60,
    val endMinuteOfDay: Int = 7 * 60,
)

data class LaundrySettings(
    val onboardingComplete: Boolean = false,
    val defaultDurationMinutes: Int = 45,
    val laundryLocation: LaundryLocation? = null,
    val geofenceRadiusMeters: Float = 100f,
    val roommate: RoommateContact? = null,
    val reminderIntervalMinutes: Int = 5,
    val escalation: EscalationSettings = EscalationSettings(),
    val quietHours: QuietHours = QuietHours(),
    val maxCycleMinutes: Int = 180,
    /** Opt-in: ask which machine the load went into, and name it in the roommate message. */
    val trackWasherNumber: Boolean = false,
    /** The machine used last time, pre-filled so a regular user does not retype it. */
    val lastWasherLabel: String = "",
) {
    companion object {
        val DURATION_CHOICES = listOf(20, 25, 30, 35, 40, 45, 50, 55, 60, 75, 90, 105, 120)
        val INTERVAL_CHOICES = listOf(2, 3, 5, 10, 15, 20, 30)
        val MAX_CYCLE_CHOICES = listOf(60, 90, 120, 180, 240, 360)
        val RADIUS_CHOICES = listOf(50f, 75f, 100f, 150f, 200f)

        /** Bounds for a hand-typed wash length. One minute is the floor WorkManager can honour. */
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 600

        /** Machines are labelled like "3" or "B2"; long enough for both, short enough to read. */
        const val MAX_WASHER_LABEL_LENGTH = 4
    }
}

/** Clamps a hand-typed wash length into the range the scheduler can actually honour. */
fun coerceDurationMinutes(minutes: Int): Int = minutes.coerceIn(
    LaundrySettings.MIN_DURATION_MINUTES,
    LaundrySettings.MAX_DURATION_MINUTES,
)

/**
 * Normalises a washer label for storage and for dropping into a message.
 *
 * Punctuation and whitespace are stripped rather than rejected so that "#3" and " 3 " both
 * become "3"; an unusable label collapses to blank, which every caller treats as "not set".
 */
fun sanitizeWasherLabel(raw: String): String =
    raw.filter { it.isLetterOrDigit() }.take(LaundrySettings.MAX_WASHER_LABEL_LENGTH).uppercase()
