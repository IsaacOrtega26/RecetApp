package com.example.recetapp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseConfig {
    val client = createSupabaseClient(
        supabaseUrl = "https://htmhbifqqimzipppvmwx.supabase.co",
        supabaseKey = "sb_publishable_YdgW5a0Chy1xAZPFIWsPzA_TpMZrBIT"
    ) {
        install(Postgrest)
        install(Auth)
    }
}
