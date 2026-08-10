package com.example.recetapp

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SupabaseRepository(private val supabase: SupabaseClient) {

    private val TAG = "RecetApp_Repo"

    // --- STORAGE ---
    private val BUCKET_USUARIO = "imagenes_usuario"
    private val BUCKET_RECETA = "imagenes_receta"

    suspend fun uploadImage(folder: String, fileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val bucketName = if (folder == "avatars") BUCKET_USUARIO else BUCKET_RECETA
            val bucketApi = supabase.storage.from(bucketName)
            
            Log.d(TAG, "Iniciando subida a bucket: $bucketName, archivo: $fileName")
            
            // Subida al Storage
            bucketApi.upload(fileName, bytes) { upsert = true }
            
            // Obtención de URL pública
            val url = bucketApi.publicUrl(fileName)
            Log.d(TAG, "Subida exitosa. URL: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error en uploadImage: ${e.message}", e)
            null
        }
    }

    // --- USUARIOS ---

    suspend fun getUsuarioByUid(uid: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").select { 
                filter { eq("uid_usuario", uid) } 
            }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) { 
            Log.e(TAG, "Error getUsuarioByUid: ${e.message}")
            null 
        }
    }

    suspend fun getUsuarioByEmail(email: String): Usuario? = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").select { 
                filter { eq("email", email) } 
            }.decodeSingleOrNull<Usuario>()
        } catch (e: Exception) { null }
    }

    suspend fun updateUsuario(usuario: Usuario) = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").update(usuario) { 
                filter { eq("uid_usuario", usuario.uid ?: "") } 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updateUsuario: ${e.message}")
        }
    }

    suspend fun insertUsuario(usuario: Usuario): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").insert(usuario)
            true
        } catch (e: Exception) { 
            Log.e(TAG, "Error insertUsuario: ${e.message}")
            false 
        }
    }

    suspend fun upsertUsuario(usuario: Usuario): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Upserting usuario: ${usuario.email} (UID: ${usuario.uid})")
            supabase.from("usuarios").upsert(usuario) {
                onConflict = "uid_usuario"
            }
            true
        } catch (e: Exception) {
            try {
                supabase.from("usuarios").upsert(usuario) {
                    onConflict = "email"
                }
                true
            } catch (e2: Exception) {
                Log.e(TAG, "FALLO CRÍTICO en upsertUsuario: ${e2.message}")
                false
            }
        }
    }

    suspend fun updatePrivacidad(uid: String, esPublico: Boolean) = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").update(mapOf("es_publico" to esPublico)) { 
                filter { eq("uid_usuario", uid) } 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updatePrivacidad: ${e.message}")
        }
    }

    suspend fun deleteCuenta(uid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").delete { 
                filter { eq("uid_usuario", uid) } 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleteCuenta: ${e.message}")
        }
    }

    // --- RECETAS ---

    suspend fun getAllRecetas(): List<Receta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").select { 
                order("fecha_creacion", Order.DESCENDING) 
            }.decodeList<Receta>()
        } catch (e: Exception) { 
            Log.e(TAG, "Error getAllRecetas: ${e.message}")
            emptyList() 
        }
    }

    suspend fun getRecetaById(id: String): Receta? = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").select { 
                filter { eq("receta_id", id) } 
            }.decodeSingleOrNull<Receta>()
        } catch (e: Exception) { null }
    }

    suspend fun getRecetasByAutor(autorUid: String): List<Receta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("recetas").select { 
                filter { eq("autor_uid", autorUid) } 
            }.decodeList<Receta>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun insertReceta(receta: Receta): Receta? = withContext(Dispatchers.IO) {
        try {
            val currentUid = receta.autorUid
            if (getUsuarioByUid(currentUid) == null) {
                insertUsuario(Usuario(uid = currentUid, nombreUsuario = "user_${currentUid.take(5)}", rol = "usuario"))
            }

            val res = supabase.from("recetas").insert(receta) { select() }
            res.decodeSingle<Receta>()
        } catch (e: Exception) { 
            Log.e(TAG, "Error insertReceta: ${e.message}")
            null 
        }
    }

    // --- SOCIAL: LIKES ---

    suspend fun toggleLike(usuarioUid: String, recursoId: String, tipo: String) = withContext(Dispatchers.IO) {
        try {
            if (getUsuarioByUid(usuarioUid) == null) {
                insertUsuario(Usuario(uid = usuarioUid, nombreUsuario = "user_${usuarioUid.take(5)}", rol = "usuario"))
            }

            val existing = supabase.from("likes").select {
                filter {
                    eq("usuario_uid", usuarioUid)
                    eq("recurso_id", recursoId)
                }
            }.decodeList<Map<String, JsonElement>>()
            
            val isLiked = existing.isNotEmpty()
            
            if (!isLiked) {
                supabase.from("likes").insert(mapOf(
                    "usuario_uid" to usuarioUid,
                    "recurso_id" to recursoId,
                    "tipo_recurso" to tipo
                ))
                if (tipo == "receta") {
                    val receta = getRecetaById(recursoId)
                    receta?.let { crearNotificacion(it.autorUid, usuarioUid, "like", recursoId) }
                }
            } else {
                supabase.from("likes").delete { 
                    filter { 
                        eq("usuario_uid", usuarioUid)
                        eq("recurso_id", recursoId)
                    } 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "social: Error toggleLike: ${e.message}")
            throw e
        }
    }

    // --- SOCIAL: COMENTARIOS ---

    suspend fun getComentarios(recursoId: String): List<Comentario> = withContext(Dispatchers.IO) {
        try {
            supabase.from("comentarios").select {
                filter { eq("recurso_id", recursoId) }
                order("fecha_creacion", Order.DESCENDING)
            }.decodeList<Comentario>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun insertComentario(comentario: Comentario) = withContext(Dispatchers.IO) {
        try {
            supabase.from("comentarios").insert(comentario)
            if (comentario.tipoRecurso == "receta") {
                val receta = getRecetaById(comentario.recursoId)
                receta?.let { crearNotificacion(it.autorUid, comentario.autorUid, "comentario", comentario.recursoId) }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteComentario(comentarioId: String, usuarioUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("comentarios").delete {
                filter {
                    eq("comentario_id", comentarioId)
                    eq("autor_uid", usuarioUid)
                }
            }
            true
        } catch (e: Exception) { false }
    }

    // --- SOCIAL: NOTIFICACIONES ---

    suspend fun getNotificaciones(destinatarioUid: String): List<Notificacion> = withContext(Dispatchers.IO) {
        try {
            supabase.from("notificaciones").select {
                filter { eq("destinatario_uid", destinatarioUid) }
                order("fecha_creacion", Order.DESCENDING)
            }.decodeList<Notificacion>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun crearNotificacion(destinatarioUid: String, actorUid: String, tipo: String, objetoId: String? = null) = withContext(Dispatchers.IO) {
        try {
            if (destinatarioUid == actorUid) return@withContext false
            supabase.from("notificaciones").insert(Notificacion(
                destinatarioUid = destinatarioUid,
                actorUid = actorUid,
                tipo = tipo,
                objetoId = objetoId
            ))
            true
        } catch (e: Exception) { false }
    }

    // --- SOCIAL: REPORTES ---

    suspend fun reportarRecurso(reportanteUid: String, objetoId: String, tipoObjeto: String, motivo: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("reportes").insert(mapOf(
                "reportante_uid" to reportanteUid,
                "objeto_id" to objetoId,
                "tipo_objeto" to tipoObjeto,
                "motivo" to motivo
            ))
            true
        } catch (e: Exception) { false }
    }

    suspend fun getReportes(): List<JsonElement> = withContext(Dispatchers.IO) {
        try {
            supabase.from("reportes").select().decodeList<JsonElement>()
        } catch (e: Exception) { emptyList() }
    }

    // --- SOCIAL: SEGUIDORES ---

    suspend fun followUser(followerUid: String, followedUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("seguidores").insert(mapOf(
                "seguidor_uid" to followerUid,
                "seguido_uid" to followedUid
            ))
            crearNotificacion(followedUid, followerUid, "seguidor")
        } catch (e: Exception) { }
    }

    suspend fun unfollowUser(followerUid: String, followedUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("seguidores").delete {
                filter {
                    eq("seguidor_uid", followerUid)
                    eq("seguido_uid", followedUid)
                }
            }
        } catch (e: Exception) { }
    }

    suspend fun isFollowing(followerUid: String, followedUid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("seguidores").select {
                filter {
                    eq("seguidor_uid", followerUid)
                    eq("seguido_uid", followedUid)
                }
            }
            res.decodeList<JsonElement>().isNotEmpty()
        } catch (e: Exception) { false }
    }

    // --- SOCIAL: BLOQUEOS ---

    suspend fun bloquearUsuario(bloqueadorUid: String, bloqueadoUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("bloqueos").insert(mapOf(
                "bloqueador_uid" to bloqueadorUid,
                "bloqueado_uid" to bloqueadoUid
            ))
            true
        } catch (e: Exception) { false }
    }

    // --- SOCIAL: GUARDADOS ---

    suspend fun toggleSave(usuarioUid: String, recursoId: String, tipo: String) = withContext(Dispatchers.IO) {
        try {
            val response = supabase.from("guardados").select {
                filter {
                    eq("usuario_uid", usuarioUid)
                    eq("recurso_id", recursoId)
                }
            }
            if (response.data == "[]") {
                supabase.from("guardados").insert(mapOf(
                    "usuario_uid" to usuarioUid,
                    "recurso_id" to recursoId,
                    "tipo_recurso" to tipo
                ))
            } else {
                supabase.from("guardados").delete {
                    filter {
                        eq("usuario_uid", usuarioUid)
                        eq("recurso_id", recursoId)
                    }
                }
            }
        } catch (e: Exception) { }
    }

    suspend fun isSaved(usuarioUid: String, recursoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("guardados").select {
                filter {
                    eq("usuario_uid", usuarioUid)
                    eq("recurso_id", recursoId)
                }
            }
            res.data != "[]"
        } catch (e: Exception) { false }
    }

    // --- ADMIN STATS ---

    suspend fun getTotalUsersCount(): Int = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("usuarios").select {
                count(Count.EXACT)
                head = true
            }
            res.countOrNull()?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    // --- INGREDIENTES Y PASOS ---

    suspend fun insertIngredientes(ingredientes: List<IngredienteReceta>) = withContext(Dispatchers.IO) {
        try {
            supabase.from("ingredientes_receta").insert(ingredientes)
        } catch (e: Exception) { }
    }

    suspend fun insertPasos(pasos: List<PasoReceta>) = withContext(Dispatchers.IO) {
        try {
            supabase.from("pasos_receta").insert(pasos)
        } catch (e: Exception) { }
    }

    suspend fun getIngredientes(recetaId: String): List<IngredienteReceta> = withContext(Dispatchers.IO) {
        try {
            supabase.from("ingredientes_receta").select { 
                filter { eq("receta_id", recetaId) } 
            }.decodeList<IngredienteReceta>()
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
        try {
            supabase.from("imagenes_receta").insert(mapOf(
                "receta_id" to recetaId,
                "url" to url
            ))
        } catch (e: Exception) { }
    }

    suspend fun getImagenesReceta(recetaId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = supabase.from("imagenes_receta").select { 
                filter { eq("receta_id", recetaId) } 
            }
            response.decodeList<Map<String, JsonElement>>()
             .mapNotNull { it["url"]?.jsonPrimitive?.content }
        } catch (e: Exception) { emptyList() }
    }
}
