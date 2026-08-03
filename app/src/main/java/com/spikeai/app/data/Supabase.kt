package com.spikeai.app.data

import com.spikeai.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single Supabase client for the whole app. Points at the existing Spike AI project.
 * Session is persisted automatically by auth-kt (SharedPreferences-backed) and
 * auto-refreshed. Deep links (spikeai://login-callback) are handled by [Auth].
 */
object Supabase {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                scheme = "spikeai"
                host = "login-callback"
                flowType = FlowType.PKCE
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Functions)
        }
    }
}
