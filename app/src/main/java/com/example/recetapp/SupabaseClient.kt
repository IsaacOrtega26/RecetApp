package com.example.recetapp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.plugins.HttpTimeout
import io.github.jan.supabase.annotations.SupabaseInternal

object SupabaseConfig {
    @OptIn(SupabaseInternal::class)
    val client = createSupabaseClient(
        supabaseUrl = "https://htmhbifqqimzipppvmwx.supabase.co",
        supabaseKey = "sb_publishable_YdgW5a0Chy1xAZPFIWsPzA_TpMZrBIT"
    ) {
        install(Postgrest)
        install(Auth)

        httpConfig {
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }
    }
}
