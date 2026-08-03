package com.spikeai.app.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Base (English) string table. Keys are stable identifiers used across the UI. */
object Strings {
    val en: Map<String, String> = mapOf(
        // Tabs
        "tab_home" to "Home",
        "tab_focus" to "Focus",
        "tab_mode" to "Mode",
        "tab_progress" to "Progress",
        "tab_profile" to "Profile",

        // Generic
        "app_name" to "Spike AI",
        "save" to "Save",
        "cancel" to "Cancel",
        "delete" to "Delete",
        "edit" to "Edit",
        "done" to "Done",
        "continue" to "Continue",
        "back" to "Back",
        "prev_month" to "Previous month",
        "next_month" to "Next month",
        "more_options" to "More options",
        "loading" to "Loading…",
        "retry" to "Retry",

        // Auth
        "auth_title" to "Spike AI",
        "auth_subtitle" to "Your personal productivity system",
        "auth_login" to "Log in or sign up",
        "auth_email_placeholder" to "name@example.com",
        "auth_send_link" to "Send magic link",
        "auth_link_sent" to "Check your email for a sign-in link.",
        "auth_continue_google" to "Continue with Google",
        "auth_continue_email" to "Continue with Email",
        "auth_or" to "or",
        "auth_invalid_email" to "Please enter a valid email address.",
        "auth_google_unconfigured" to "Google sign-in isn't configured yet.",
        "auth_by_continuing" to "By continuing, you agree to Spike AI's",
        "auth_and" to "and",
        // Auth — email entry screen
        "auth_enter_email" to "Enter your email",
        "auth_enter_email_sub" to "We'll send you a sign-in link",
        "auth_continue_btn" to "Continue",
        // Auth — check-your-email screen
        "auth_check_email" to "Check your email",
        "auth_sent_link_to" to "We sent a sign-in link to",
        "auth_tap_link" to "Tap the link in the email to log in automatically.",
        "auth_open_mail" to "Open Mail App",
        "auth_didnt_get_email" to "Didn't get the email?",
        "auth_resend" to "Resend",
        "auth_resend_in" to "Resend in %ds",
        "auth_new_link_sent" to "A new link has been sent!",

        // Permissions intro (shown before login)
        "perm_intro_title" to "Set up app blocking",
        // Carries both facts the old bottom-of-screen hint used to repeat: granting is
        // optional, and blocking features re-ask at the point they are actually used.
        "perm_intro_sub" to "App blocking needs two Android permissions. Both are optional — Spike AI will ask again when you start a session that blocks apps.",
        // Card titles name the permission the way Android's own Settings name it, so the
        // user can recognise what they are looking for once they get there. The previous
        // "Enable app blocking" also collided with the screen title above it.
        "perm_accessibility_title" to "Accessibility access",
        "perm_accessibility_desc" to "Lets Spike AI see which app you open, so it can block that app during a focus session. Tap Grant to review exactly what this uses.",
        "perm_accessibility_steps" to "Accessibility → Installed apps → Spike AI App Blocking → On",
        "perm_overlay_title" to "Display over other apps",
        "perm_overlay_desc" to "Lets Spike AI show the focus screen on top of a blocked app.",
        "perm_accessibility_needs_consent" to "App blocking is switched on in Android settings, but it stays off until you agree to how Spike AI uses it. Tap Grant to read what it uses and choose.",
        "perm_granted" to "Granted",
        "perm_status_pending" to "Needed",
        "perm_status_incomplete" to "App blocking is not active yet. Both permissions are needed for it to work.",
        // %d, not a positional "%1$s": tr(key, arg) does a literal replace of "%d"/"%s".
        "perm_step_label" to "STEP %d",
        "perm_grant" to "Grant",
        "perm_withdraw_confirm" to "Withdraw",
        "perm_continue" to "Continue",
        "perm_done" to "Done",
        "perm_withdraw_consent" to "Withdraw permissions",
        "perm_withdraw_consent_hint" to "App blocking stops immediately, and Spike AI turns the accessibility service off for you. It will no longer check which app you have open. Everything else in Spike AI keeps working, and you can grant the permissions again here whenever you want.",
        // Shown only when the overlay permission is actually granted. Android has no API
        // for an app to drop this permission, so this one step is the user's to finish —
        // and the dialog says so before they confirm rather than after.
        "perm_withdraw_consent_hint_overlay" to "Android does not let an app remove \"Display over other apps\" by itself, so Settings will open for you to switch it off.",
        "perm_skip" to "Skip for now",
        "perm_all_set" to "App blocking is active. Both permissions are granted.",

        // Prominent disclosure for the AccessibilityService API (Google Play User Data
        // policy). Shown before the permission is requested, with an explicit
        // accept/decline. Keep this wording consistent with the
        // accessibility_service_description string in res/values/strings.xml.
        // Names the permission being asked for, not the feature it unlocks. "Enable Focus
        // Mode" read as a feature toggle rather than a data-access disclosure — and it was
        // the odd one out, since every other locale already asked about the Accessibility
        // Service. English is what a Play reviewer reads, so it must be the clearest.
        "a11y_disclosure_title" to "Allow Spike AI to use the Accessibility Service?",
        // Two paragraphs, verbatim per the compliance spec. The first states what is
        // accessed and why; the second states how it is handled. Do not merge or reword.
        "a11y_disclosure_intro" to "Spike AI uses Android's Accessibility Service to detect which apps you are using and block distracting apps during focus sessions.",
        "a11y_disclosure_intro2" to "Your data is processed locally on your device and is not shared with third parties.",
        "a11y_disclosure_collect" to "Collects: only the package name of the app you currently have open (for example, com.instagram.android).",
        "a11y_disclosure_purpose" to "Purpose: so Spike AI can show the focus shield the moment you open an app you chose to block during a focus session.",
        "a11y_disclosure_never" to "Never: Spike AI does not read, record, store or share the text, passwords, messages or any other content shown on your screen. This information is used on your device only and never leaves it.",
        "a11y_disclosure_revoke" to "You can turn this off at any time in Settings → Accessibility → Spike AI App Blocking. If you decline, the rest of Spike AI keeps working — only app blocking will be unavailable.",
        "a11y_disclosure_accept" to "Allow Access",
        "a11y_disclosure_decline" to "Cancel",
        // Step 2 of setup — a separate screen, never bundled with the accessibility
        // request above.
        "overlay_disclosure_step_done" to "Accessibility Service enabled",
        "overlay_disclosure_title" to "One more step",
        // Used when this screen is opened directly from the overlay card, with the
        // accessibility step still pending. "One more step" would be describing a
        // sequence the user is not in — there was no earlier step. Phrased like the
        // accessibility disclosure's title so the two read as a matched pair.
        "overlay_disclosure_title_solo" to "Allow Spike AI to display over other apps?",
        "overlay_disclosure_body" to "To show the focus screen on top of a blocked app, Spike AI needs permission to display over other apps.",
        "overlay_disclosure_note" to "This permission lets Spike AI draw the focus screen. It does not give Spike AI access to any additional data, and nothing leaves your device.",
        // Posted when the accessibility service is enabled in Android settings by a user
        // who has never seen the in-app disclosure. The service reads nothing until they do.
        "a11y_consent_notif_title" to "App blocking needs your permission",
        "a11y_consent_notif_body" to "You turned on Spike AI app blocking in Android settings. Tap to see what it uses and choose whether to allow it — until then it stays off.",
        "perm_required_hint" to "You can continue without these. Spike AI will ask again when you start a focus session that blocks apps.",

        // Name setup
        "name_title" to "What's your name?",
        "name_subtitle" to "This helps personalize your experience.",
        "name_first" to "First Name",
        "name_last" to "Last Name",

        // Home
        "home_today" to "Today",
        "home_tomorrow" to "Tomorrow",
        "home_yesterday" to "Yesterday",
        "home_back_to_today" to "Back to Today",
        "home_no_tasks" to "No tasks on this day",
        "home_add_first_task" to "Tap + to add your first task",
        "home_quote_of_day" to "Quote of the Day",
        "home_quote_daily_note" to "New quotes arrive daily at 5:00 AM",
        "quote_next_in" to "Next quote in %s",
        "quote_time_h_m" to "%dh %dm",
        "quote_time_m" to "%dm",
        "home_task_limit" to "You've reached the 25-task limit for this day.",

        // Task editor
        "task_new" to "New Task",
        "task_edit" to "Edit Task",
        "task_name" to "Task name",
        "task_name_placeholder" to "What do you want to get done?",
        "task_description" to "Description (optional)",
        "task_description_placeholder" to "Add a few details…",
        "task_schedule" to "Schedule",
        "task_priority" to "Priority",
        "priority_low" to "Low",
        "priority_medium" to "Medium",
        "priority_high" to "High",
        "task_reminder" to "Reminder",
        "task_reminder_off" to "Off",
        "task_reminder_at" to "Notify me at %s",
        "task_reminder_pick" to "Reminder time",
        "task_reminder_past" to "That time has already passed today.",

        // Notifications (resolved when the alarm fires, so they follow the live language)
        "notif_task_title_fallback" to "Task reminder",
        "notif_task_text" to "It's time to focus on this task.",
        "notif_goal_title_fallback" to "Goal reminder",
        "notif_goal_text" to "A small step today moves your goal forward.",
        "notif_quote_title" to "Quote of the Day",
        "notif_quote_text" to "Open Spike for today's dose of motivation.",

        // Goals
        "goals_title" to "Goals",
        "goals_empty" to "No goals yet. Set one to aim for.",
        "goal_new" to "New Goal",
        "goal_title_field" to "Goal",
        "goal_description" to "Description",
        "goal_target_date" to "Target date",
        "goal_long_term" to "Long-term goal",
        "goal_public" to "Make public",
        "goal_active" to "Active",
        "goal_completed" to "Completed",
        "goal_abandoned" to "Abandoned",
        "goal_mark_complete" to "Mark complete",
        "goal_reactivate" to "Reactivate",

        // Progress
        "progress_title" to "Progress",
        "progress_current_streak" to "Current streak",
        "progress_best_streak" to "Best streak",
        "progress_focus_days" to "Focus days",
        "progress_consistency" to "Consistency",
        "progress_today" to "Today",
        "progress_completion" to "Completion",
        "progress_daily_breakdown" to "Daily breakdown",
        "progress_weekly_summary" to "AI Weekly Summary",
        "progress_summary_loading" to "Generating your weekly summary…",
        "progress_summary_locked" to "Your first AI summary unlocks after a full week of tracking.",
        "progress_summary_error" to "Couldn't generate this week's summary just now.",
        "progress_summary_offline" to "Connect to the internet to generate your AI summary.",
        "progress_next_summary" to "Next summary %s",
        "progress_wins" to "Wins",
        "progress_next_action" to "Next action",
        "progress_calendar" to "Focus calendar",
        "progress_scope_monthly" to "Monthly",
        "progress_scope_yearly" to "Yearly",
        "progress_streak_day" to "Streak day",
        "progress_missed_day" to "Not a streak day",
        "progress_completed" to "Completed goals",
        "progress_not_completed" to "Missed goals",
        "progress_no_goals" to "No goals were tracked on this day.",
        "progress_no_mode" to "No focus mode used",
        "progress_mode_used" to "%s mode",
        "progress_milestones" to "Milestones",
        "milestone_3day" to "3-day streak",
        "milestone_7day" to "7-day streak",
        "milestone_30days" to "30 focus days",
        "milestone_10days" to "10 total focus days",
        "progress_tasks_completed" to "tasks completed",
        "days_unit" to "days",

        // Focus
        "focus_title" to "Mode",
        "focus_chill" to "Chill",
        "focus_chill_sub" to "Gentle reminders, no blocking",
        "focus_focus" to "Focus",
        "focus_focus_sub" to "Ask before opening distractions",
        "focus_lockin" to "Lock-in",
        "focus_lockin_sub" to "Fully block selected apps",
        "focus_sweat" to "Athlete",
        "focus_sweat_sub" to "Move to unlock your apps",
        "focus_open_camera_check" to "Open Camera Check",
        "focus_start" to "Start focus session",
        "focus_stop" to "End session",
        "focus_active" to "Focus session active",
        "sweat_title" to "Move to unlock",
        "sweat_subtitle" to "Each push-up or stand-up earns 1 minute of access. Keep your whole body in view of the camera and move through the full range.",
        "sweat_reps" to "reps",
        "sweat_camera_needed" to "Camera permission is needed to count your reps.",
        "sweat_claim" to "Claim %d min",
        "sweat_do_one" to "Do at least 1 rep",
        "sweat_access_ready" to "Blocked-app access ready: %d min",
        "sweat_access_remaining" to "Blocked-app access remaining: %d min",
        // Live form-coaching cues shown under the camera during sweat mode.
        "sweat_fb_frame" to "Position your whole body in the camera view",
        "sweat_fb_sideways" to "Turn sideways to the camera for accurate push-up counting",
        "sweat_fb_straight" to "Keep your body in a straight line",
        "sweat_fb_lower" to "Lower down until your elbows bend to about 90°",
        "sweat_fb_push" to "Now push all the way back up",
        "sweat_fb_good" to "Great form — keep going!",
        "focus_select_apps" to "Select apps to block",
        "focus_apps_selected" to "%d app(s) selected",
        "focus_grant_accessibility" to "Enable app blocking",
        "focus_grant_overlay" to "Allow display over apps",
        "focus_permissions_needed" to "A few permissions are needed for blocking to work.",
        "focus_permissions_title" to "Permission required",
        "focus_permissions_body" to "To block apps, Spike AI needs accessibility and display-over-apps access. Open settings to enable them, then try again.",
        "focus_open_settings" to "Open Settings",
        "focus_permissions_continue" to "Continue",
        "focus_blocked_title" to "Stay focused",
        "focus_blocked_message" to "This app is blocked during your focus session.",
        "focus_blocked_close" to "Back to focus",
        // Block overlay / shield (mirrors iOS ShieldConfiguration copy)
        "shield_are_you_sure" to "Are you sure?",
        "shield_are_you_sure_subtitle" to "Are you sure you want to open this app?",
        "shield_no_go_back" to "No, go back",
        "shield_yes_open" to "Yes, open app",
        "shield_blocked_title" to "Blocked for Focus",
        "shield_blocked_mode" to "This app is blocked while Lock-in mode is active.\nReturn to Spike AI to change your mode.",
        "shield_close" to "Close",
        "shield_exercise_title" to "Exercise Required",
        "shield_exercise_subtitle" to "Go to Spike AI Sweat Mode and do a push-up or stand-up to open this app.\nEach rep gives 1 minute.",
        "shield_move_to_unlock" to "Move to unlock",
        // Focus gating
        "focus_need_permissions" to "Grant the permissions above to start blocking.",
        "focus_need_apps" to "Select at least one app to block.",
        // Task gate (Focus & Lock-in require an uncompleted task for today)
        "focus_no_tasks_title" to "Nothing to focus on",
        "focus_no_tasks_body" to "Focus and Lock-in only run while you have an uncompleted task for today. Add a task on the Home tab, then start your session.",
        "focus_no_tasks_action" to "Got it",

        // Profile
        "profile_title" to "Profile",
        "profile_preferences" to "Preferences",
        "profile_language" to "Language",
        "profile_app_blocking" to "App blocking",
        "profile_appearance" to "Appearance",
        "appearance_system" to "System",
        "appearance_light" to "Light",
        "appearance_dark" to "Dark",
        "profile_show_completed" to "Show completed tasks",
        "profile_personal" to "Personal details",
        "profile_avatar_color" to "Avatar color",
        "profile_support" to "Support & Legal",
        "profile_privacy" to "Privacy Policy",
        "profile_terms" to "Terms of Service",
        "profile_sign_out" to "Sign out",
        "profile_delete" to "Delete account",
        "profile_delete_confirm" to "This permanently deletes your account and all data. This can't be undone.",

        // Onboarding
        "onb_welcome_title" to "Spike AI",
        "onb_welcome_sub" to "Make your phone work for you",
        "onb_start" to "Start setup",
        "onb_didyouknow" to "Did you know?",
        "onb_stat_month" to "9 days per month",
        "onb_stat_year" to "108 days per year",
        "onb_stat_life" to "30 years of life",
        "onb_stat_caption" to "This is how much time the average person spends on their phone.",
        "onb_solution_title" to "Don't Worry",
        "onb_solution_sub" to "Spike helps you take it back",
        "onb_perm_title" to "Enable app blocking",
        "onb_perm_sub" to "Spike needs permission to detect and block distracting apps during focus sessions.",
        "onb_select_title" to "Which apps distract you most?",
        "onb_select_sub" to "Choose the apps to block during focus.",
        "onb_ready_title" to "Ready to change your life?",
        "onb_ready_sub" to "You're all set. Let's build momentum.",
        "onb_get_started" to "Get started",
        "onb_choose_language" to "Choose your language",

        // Progress (additions)
        "progress_daily_progress" to "Daily Progress",
        "progress_streaks" to "Streaks",
        "progress_todays_completion" to "Today's Completion",
        "progress_set_first_goal" to "Set your first goal",
        "progress_all_goals_done" to "All goals completed!",
        "progress_goals_completed" to "%1\$d of %2\$d daily goals completed",
        "progress_streak_going" to "%d-day streak going strong",

        // Profile (additions)
        "profile_account" to "Account",
        "profile_follow_us" to "Follow us",
        "profile_account_actions" to "Account",
        "profile_instagram" to "Instagram",
        "profile_tiktok" to "TikTok",
        "profile_live_activity" to "Live Activity",
        "profile_live_activity_desc" to "Show your daily goals on the Lock Screen.",
        "appearance_desc" to "Choose how Spike AI looks on this device.",
        "profile_show_completed_desc" to "Display finished tasks in your list.",
        "language_desc" to "Spike AI will use this language across the app.",
        "profile_email_not_editable" to "Your email address can't be changed.",
        "profile_save_changes" to "Save Changes",
        "profile_email" to "Email",

        // Focus (additions)
        "focus_select_mode" to "Select Mode",
        "focus_chill_desc" to "Get gentle reminders to stay on track without any hard blocking.",
        "focus_focus_desc" to "Spike asks you to confirm before opening a distracting app.",
        "focus_lockin_desc" to "Selected apps are fully blocked. No bypass, no excuses.",
        "focus_sweat_desc" to "Do push-ups and squats to earn 1 minute for each rep.",
        // Athlete mode — current blocked-app access status shown on the Mode page.
        "focus_sweat_access_available" to "You have %d min of blocked-app access",
        "focus_sweat_access_none" to "No access yet — each rep earns 1 minute of blocked-app access",
        "focus_info_title" to "Focus Modes",
        "focus_info_subtitle" to "Take control of your digital habits with focused, professional modes.",
        "focus_info_permissions_title" to "About Permissions",
        "focus_info_permissions_body" to "Chill needs notification permission for reminders. Focus, Lock-in, and Sweat need app-blocking access to manage your apps. Sweat also uses the camera to verify your reps.",
        "focus_change_selection" to "Change Selection",
        "focus_goals_required" to "Goals Required",
        "focus_goals_required_desc" to "Add at least one task today to use this mode.",

        // Onboarding (additions)
        "onb_welcome_desc" to "Reduce mindless scrolling, protect focus time, and turn real tasks into visible progress.",
        "onb_protect_focus" to "Protect your focus",
        "onb_block_feeds" to "Block distracting feeds",
        "onb_finish_tasks" to "Finish what matters",
        "onb_energy_plus" to "+3 hours of energy",
        "onb_didyouknow_sub" to "How much time screens really take from us.",
        "onb_days_month" to "days per month",
        "onb_days_year" to "days per year",
        "onb_years_life" to "years of your life",
        "onb_spike_back" to "Spike has your back",
        "onb_solution_desc" to "Spike turns your phone into a tool for focus, not distraction.",
        "onb_benefit_no_distraction" to "Distractions blocked",
        "onb_benefit_modes" to "Focus modes ready",
        "onb_benefit_progress" to "Progress tracking on",
        "auth_send_failed" to "Couldn't send the sign-in link. Please try again.",
        "auth_google_failed" to "Google sign-in didn't complete. Please try again.",
        "auth_no_internet" to "No internet connection. Please connect to the internet to sign in.",
    )
}

/** Composable string lookup using the active language. Usage: `tr("tab_home")`. */
@Composable
@ReadOnlyComposable
fun tr(key: String): String =
    AppLocalization.string(key, LocalLanguage.current)

/** Composable string with a single %d/%s substitution. */
@Composable
@ReadOnlyComposable
fun tr(key: String, arg: Any): String =
    AppLocalization.string(key, LocalLanguage.current).replace("%d", arg.toString()).replace("%s", arg.toString())

/**
 * Composable string with positional substitutions via [String.format] (e.g.
 * "%1${'$'}d of %2${'$'}d"). Falls back to the raw template if formatting fails.
 */
@Composable
@ReadOnlyComposable
fun tr(key: String, vararg args: Any): String {
    val template = AppLocalization.string(key, LocalLanguage.current)
    return runCatching { String.format(template, *args) }.getOrDefault(template)
}
