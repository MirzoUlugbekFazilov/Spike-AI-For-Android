package com.spikeai.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These models map 1:1 to the live Spike AI Supabase schema. Column names are
// authoritative (verified against the database). The DB is never modified by this app.

// ── profiles ────────────────────────────────────────────────────────────────
@Serializable
data class Profile(
    val id: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("avatar_color") val avatarColor: String? = null,
    @SerialName("name_setup_done") val nameSetupDone: Boolean = false,
) {
    val displayName: String
        get() = listOfNotNull(firstName?.takeIf { it.isNotBlank() }, lastName?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "" }

    val initials: String
        get() {
            val f = firstName?.firstOrNull()?.uppercaseChar()
            val l = lastName?.firstOrNull()?.uppercaseChar()
            return listOfNotNull(f, l).joinToString("").ifBlank { "S" }
        }
}

@Serializable
data class ProfileUpsert(
    val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("avatar_color") val avatarColor: String,
    @SerialName("name_setup_done") val nameSetupDone: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)

// ── tasks ─────────────────────────────────────────────────────────────────────
@Serializable
data class TaskRecord(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val category: String? = null,
    val priority: String? = "medium",
    val completed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
)

@Serializable
data class NewTask(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val category: String = "General",
    val priority: String = "medium",
    val completed: Boolean = false,
    @SerialName("scheduled_date") val scheduledDate: String,
)

@Serializable
data class TaskCompletedUpdate(val completed: Boolean)

@Serializable
data class TaskFieldsUpdate(
    val title: String,
    val priority: String,
    @SerialName("scheduled_date") val scheduledDate: String,
)

enum class TaskPriority(val raw: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    companion object {
        fun from(raw: String?): TaskPriority =
            entries.firstOrNull { it.raw == raw?.lowercase() } ?: MEDIUM
    }
}

// ── goals ─────────────────────────────────────────────────────────────────────
@Serializable
data class GoalRecord(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val description: String? = null,
    val status: String = "active",
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("is_long_term") val isLongTerm: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class NewGoal(
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String = "",
    val status: String = "active",
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("is_long_term") val isLongTerm: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = false,
)

@Serializable
data class GoalStatusUpdate(
    val status: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

enum class GoalStatus(val raw: String) {
    ACTIVE("active"), COMPLETED("completed"), ABANDONED("abandoned");

    companion object {
        fun from(raw: String?): GoalStatus =
            entries.firstOrNull { it.raw == raw?.lowercase() } ?: ACTIVE
    }
}

// ── focus_days ──────────────────────────────────────────────────────────────
@Serializable
data class FocusDayRecord(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val day: String,                              // yyyy-MM-dd
    @SerialName("was_successful") val wasSuccessful: Boolean = true,
)

@Serializable
data class NewFocusDay(
    @SerialName("user_id") val userId: String,
    val day: String,
    @SerialName("was_successful") val wasSuccessful: Boolean,
)

// ── quote_tracking ────────────────────────────────────────────────────────────
@Serializable
data class QuoteTrackingRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("used_indices") val usedIndices: List<Int> = emptyList(),
    @SerialName("last_quote_index") val lastQuoteIndex: Int? = null,
    @SerialName("last_quote_date") val lastQuoteDate: String? = null,
)

@Serializable
data class QuoteTrackingUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("used_indices") val usedIndices: List<Int>,
    @SerialName("last_quote_index") val lastQuoteIndex: Int?,
    @SerialName("last_quote_date") val lastQuoteDate: String?,
    @SerialName("updated_at") val updatedAt: String,
)

// ── progress_summaries ──────────────────────────────────────────────────────
@Serializable
data class ProgressSummaryRecord(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    val title: String,
    val summary: String,
    val wins: List<String> = emptyList(),
    @SerialName("next_action") val nextAction: String,
)

// Edge function payloads ------------------------------------------------------
@Serializable
data class ProgressSummaryRequest(
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("best_streak") val bestStreak: Int,
    @SerialName("monthly_consistency_percent") val monthlyConsistencyPercent: Int,
    @SerialName("monthly_focus_days") val monthlyFocusDays: Int,
    @SerialName("monthly_missed_days") val monthlyMissedDays: Int,
    @SerialName("completed_tasks") val completedTasks: List<String>,
    @SerialName("missed_tasks") val missedTasks: List<String>,
    @SerialName("screen_time_trend") val screenTimeTrend: String,
    @SerialName("average_screen_time_minutes") val averageScreenTimeMinutes: Int,
    val goals: List<String>,
    @SerialName("force_refresh") val forceRefresh: Boolean = false,
    val language: String = "en",
)

@Serializable
data class TranslateQuoteRequest(
    @SerialName("quote_index") val quoteIndex: Int,
    val text: String,
    val author: String,
    val language: String,
)

@Serializable
data class TranslateQuoteResponse(
    @SerialName("translated_text") val translatedText: String,
    @SerialName("translated_author") val translatedAuthor: String? = null,
    val untranslated: Boolean? = null,
)
