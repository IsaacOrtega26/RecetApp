package com.example.recetapp

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseRepository(private val supabase: SupabaseClient) {

    // --- STORAGE ---
    // El usuario especificó un único bucket llamado "imagenes_receta"
    private val BUCKET_NAME = "imagenes_receta"

    suspend fun uploadImage(folder: String, fileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val path = "$folder/$fileName"
            val bucketApi = supabase.storage.from(BUCKET_NAME)
            bucketApi.upload(path, bytes) { upsert = true }
            bucketApi.publicUrl(path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- USUARIOS ---

    suspend fun getUsuarioByUid(uid: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").select { filter { eq("uid_usuario", uid) } }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) { null }
    }

    suspend fun getUsuarioByEmail(email: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").select { filter { eq("email", email) } }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) { null }
    }

    suspend fun updateUsuario(usuario: Usuario) = withContext(Dispatchers.IO) {
        supabase.from("usuarios").update(usuario) { filter { eq("uid_usuario", usuario.uid ?: "") } }
    }

    suspend fun updatePrivacidad(uid: String, esPublico: Boolean) = withContext(Dispatchers.IO) {
        supabase.from("usuarios").update(mapOf("es_publico" to esPublico)) { filter { eq("uid_usuario", uid) } }
    }

    suspend fun deleteCuenta(uid: String) = withContext(Dispatchers.IO) {
        supabase.from("usuarios").delete { filter { eq("uid_usuario", uid) } }
    }

    suspend fun insertUsuario(usuario: Usuario): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").insert(usuario)
            true
        } catch (e: Exception) { false }
    }

    // --- RECETAS ---

    suspend fun getAllRecetas(): List<Receta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").select { order("fecha_creacion", Order.DESCENDING) }.decodeList<Receta>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRecetaById(id: String): Receta? = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").select { filter { eq("receta_id", id) } }.decodeSingleOrNull<Receta>()
        } catch (e: Exception) { null }
    }

    suspend fun getRecetasByAutor(autorUid: String): List<Receta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").select { filter { eq("autor_uid", autorUid) } }.decodeList<Receta>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun insertReceta(receta: Receta): Receta? = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("recetas").insert(receta) { select() }
            res.decodeSingle<Receta>()
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    // --- SOCIAL ---

    suspend fun toggleLike(usuarioUid: String, recursoId: String, tipo: String) = withContext(Dispatchers.IO) {
        try {
            val existing = supabase.from("likes").select {
                filter {
                    eq("usuario_uid", usuarioUid)
                    eq("recurso_id", recursoId)
                }
            }.decodeSingleOrNull<Like>()

            if (existing == null) {
                supabase.from("likes").insert(Like(usuarioUid = usuarioUid, recursoId = recursoId, tipoRecurso = tipo))
            } else {
                supabase.from("likes").delete { filter { eq("id", existing.id ?: "") } }
            }
        } catch (e: Exception) {}
    }

    suspend fun followUser(followerUid: String, followedUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("seguidores").insert(mapOf(
                "seguidor_uid" to followerUid,
                "seguido_uid" to followedUid
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun unfollowUser(followerUid: String, followedUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("seguidores").delete {
                filter {
                    eq("seguidor_uid", followerUid)
                    eq("seguido_uid", followedUid)
                }
            }
        } catch (e: Exception) {}
    }

    suspend fun isFollowing(followerUid: String, followedUid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("seguidores").select {
                filter {
                    eq("seguidor_uid", followerUid)
                    eq("seguido_uid", followedUid)
                }
            }
            res.data != "[]"
        } catch (e: Exception) { false }
    }

    // --- INGREDIENTES Y PASOS ---

    suspend fun insertIngredientes(ingredientes: List<IngredienteReceta>) = withContext(Dispatchers.IO) {
        supabase.from("ingredientes_receta").insert(ingredientes)
    }

    suspend fun insertPasos(pasos: List<PasoReceta>) = withContext(Dispatchers.IO) {
        supabase.from("pasos_receta").insert(pasos)
    }

    suspend fun getIngredientes(recetaId: String): List<IngredienteReceta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("ingredientes_receta").select { filter { eq("receta_id", recetaId) } }.decodeList<IngredienteReceta>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPasos(recetaId: String): List<PasoReceta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("pasos_receta").select { 
                filter { eq("receta_id", recetaId) }
                order("orden", Order.ASCENDING)
            }.decodeList<PasoReceta>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun insertImagenReceta(recetaId: String, url: String) = withContext(Dispatchers.IO) {
        supabase.from("imagenes_receta").insert(mapOf("receta_id" to recetaId, "url" to url))
    }

    suspend fun getImagenesReceta(recetaId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            supabase.from("imagenes_receta").select { filter { eq("receta_id", recetaId) } }
                .decodeList<Map<String, kotlinx.serialization.json.JsonElement>>()
                .mapNotNull { it["url"]?.toString()?.replace("\"", "") }
        } catch (e: Exception) { emptyList() }
    }
}
