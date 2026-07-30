package com.example.recetapp

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseRepository(private val supabase: SupabaseClient) {

    // --- USUARIOS ---

    suspend fun getUsuarioByHandle(handle: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios")
                .select {
                    filter { eq("nombre_usuario", handle) }
                }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateUsuario(usuario: Usuario) = withContext(Dispatchers.IO) {
        supabase.from("usuarios").update(usuario) {
            filter { eq("uid_usuario", usuario.uid ?: "") }
        }
    }

    suspend fun insertUsuario(usuario: Usuario): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").insert(usuario)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Imprimimos el error detallado para saber qué falló
            println("ERROR SUPABASE INSERT: ${e.message}")
            false
        }
    }

    suspend fun getUsuarioByUid(uid: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios")
                .select {
                    filter { eq("uid_usuario", uid) }
                }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getUsuarioByEmail(email: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios")
                .select {
                    filter { eq("email", email) }
                }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- RECETAS ---

    suspend fun getRecetasByAutor(autorUid: String): List<Receta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas")
                .select {
                    filter { eq("autor_uid", autorUid) }
                    order("fecha_creacion", Order.DESCENDING)
                }.decodeList<Receta>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getAllRecetas(): List<Receta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas")
                .select {
                    order("fecha_creacion", Order.DESCENDING)
                }.decodeList<Receta>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRecetaById(recetaId: String): Receta? = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas")
                .select {
                    filter { eq("receta_id", recetaId) }
                }.decodeSingleOrNull<Receta>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun insertReceta(receta: Receta): Receta? = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").insert(receta) {
                select()
            }.decodeSingle<Receta>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- INGREDIENTES Y PASOS ---

    suspend fun getIngredientes(recetaId: String): List<IngredienteReceta> = withContext(Dispatchers.IO) {
        supabase.from("ingredientes_receta")
            .select { filter { eq("receta_id", recetaId) } }
            .decodeList<IngredienteReceta>()
    }

    suspend fun getPasos(recetaId: String): List<PasoReceta> = withContext(Dispatchers.IO) {
        supabase.from("pasos_receta")
            .select { 
                filter { eq("receta_id", recetaId) }
                order("orden", Order.ASCENDING)
            }
            .decodeList<PasoReceta>()
    }
}
