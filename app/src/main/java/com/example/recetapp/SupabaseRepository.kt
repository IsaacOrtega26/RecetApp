package com.example.recetapp

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class RecetaUpdatePayload(
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    val dificultad: String? = null,
    @SerialName("tiempo_estimado") val tiempoEstimado: Int? = null,
    val visibilidad: String
)

class SupabaseRepository(private val supabase: SupabaseClient) {

    private val TAG = "RecetApp_Repo"

    // --- STORAGE ---
    private val BUCKET_USUARIO = "imagenes_usuario"
    private val BUCKET_RECETA = "imagenes_receta"
    private val BUCKET_PUBLICACION = "imagenes_publicacion"

    suspend fun uploadImage(folder: String, fileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val bucketName = when (folder) {
                "avatars" -> BUCKET_USUARIO
                "recipes" -> BUCKET_RECETA
                else -> BUCKET_PUBLICACION
            }
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
            val currentEmail = usuario.email ?: return@withContext false
            Log.d(TAG, "Upserting usuario: $currentEmail (UID: ${usuario.uid})")
            
            // 1. Buscamos si existe un usuario con ese email
            val existing = getUsuarioByEmail(currentEmail)
            
            if (existing != null && existing.uid != usuario.uid) {
                Log.w(TAG, "Conflicto de UID detectado. Actualizando UID antiguo (${existing.uid}) al nuevo (${usuario.uid})")
                // El RLS ahora permite esto gracias al Paso 1
                supabase.from("usuarios").update(mapOf("uid_usuario" to usuario.uid)) {
                    filter { eq("email", currentEmail) }
                }
            }

            // 2. Ahora hacemos el upsert normal
            supabase.from("usuarios").upsert(usuario) {
                onConflict = "uid_usuario"
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "FALLO CRÍTICO en upsertUsuario: ${e.message}")
            false
        }
    }

    suspend fun searchUsuarios(query: String): List<Usuario> = withContext(Dispatchers.IO) {
        try {
            supabase.from("usuarios").select {
                filter {
                    or {
                        ilike("nombre_usuario", "%$query%")
                        ilike("nombre_completo", "%$query%")
                    }
                }
            }.decodeList<Usuario>()
        } catch (e: Exception) {
            Log.e(TAG, "Error searchUsuarios: ${e.message}")
            emptyList()
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
            val res = supabase.from("recetas").select { 
                filter { eq("autor_uid", autorUid) } 
                order("fecha_creacion", Order.DESCENDING)
            }
            res.decodeList<Receta>()
        } catch (e: Exception) { 
            Log.e(TAG, "Error getRecetasByAutor: ${e.message}")
            emptyList() 
        }
    }

    suspend fun getRecetasGuardadas(usuarioUid: String): List<Receta> = withContext(Dispatchers.IO) {
        try {
            val guardados = supabase.from("guardados").select {
                filter {
                    eq("usuario_uid", usuarioUid)
                    eq("tipo_recurso", "receta")
                }
            }.decodeList<Map<String, JsonElement>>()
            
            val ids = guardados.mapNotNull { it["recurso_id"]?.jsonPrimitive?.content }
            if (ids.isEmpty()) return@withContext emptyList()

            supabase.from("recetas").select {
                filter {
                    isIn("receta_id", ids)
                }
                order("fecha_creacion", Order.DESCENDING)
            }.decodeList<Receta>()
        } catch (e: Exception) {
            Log.e(TAG, "Error getRecetasGuardadas: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchRecetas(query: String): List<Receta> = withContext(Dispatchers.IO) {
        try {
            val baseRecipes = supabase.from("recetas").select {
                filter {
                    or {
                        ilike("nombre", "%$query%")
                        ilike("descripcion", "%$query%")
                        ilike("categoria", "%$query%")
                    }
                }
            }.decodeList<Receta>()

            val ingredientMatchIds = supabase.from("ingredientes_receta").select {
                filter {
                    ilike("nombre", "%$query%")
                }
            }.decodeList<Map<String, JsonElement>>().mapNotNull { it["receta_id"]?.jsonPrimitive?.content }

            val ingredientRecipes = if (ingredientMatchIds.isNotEmpty()) {
                supabase.from("recetas").select {
                    filter {
                        isIn("receta_id", ingredientMatchIds)
                    }
                }.decodeList<Receta>()
            } else emptyList()

            (baseRecipes + ingredientRecipes).distinctBy { it.id }.sortedByDescending { it.fechaCreacion }
        } catch (e: Exception) {
            Log.e(TAG, "Error searchRecetas: ${e.message}")
            emptyList()
        }
    }

    suspend fun insertReceta(receta: Receta): Receta? = withContext(Dispatchers.IO) {
        try {
            val currentUid = receta.autorUid
            val existingUser = getUsuarioByUid(currentUid)
            
            if (existingUser == null) {
                val sessionUser = supabase.auth.currentSessionOrNull()?.user
                if (sessionUser != null && sessionUser.id == currentUid) {
                    val email = sessionUser.email
                    val metadata = sessionUser.userMetadata
                    
                    val name = if (metadata != null) {
                        metadata.get("full_name")?.jsonPrimitive?.content?.replace("\"", "")
                            ?: metadata.get("name")?.jsonPrimitive?.content?.replace("\"", "")
                            ?: "Usuario"
                    } else "Usuario"
                    
                    val tempUser = Usuario(
                        uid = currentUid,
                        email = email,
                        nombreUsuario = email?.substringBefore("@") ?: "user_${currentUid.take(5)}",
                        nombreCompleto = name,
                        rol = "usuario",
                        activo = true
                    )
                    upsertUsuario(tempUser)
                }
            }

            val res = supabase.from("recetas").insert(receta) { select() }
            res.decodeSingle<Receta>()
        } catch (e: Exception) { 
            Log.e(TAG, "Error insertReceta: ${e.message}")
            null 
        }
    }

    suspend fun updateReceta(receta: Receta) = withContext(Dispatchers.IO) {
        try {
            val payload = RecetaUpdatePayload(
                nombre = receta.nombre,
                descripcion = receta.descripcion,
                categoria = receta.categoria,
                dificultad = receta.dificultad,
                tiempoEstimado = receta.tiempoEstimado,
                visibilidad = receta.visibilidad
            )
            supabase.from("recetas").update(payload) {
                filter { eq("receta_id", receta.id ?: "") }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updateReceta: ${e.message}")
            false
        }
    }

    suspend fun deleteReceta(recetaId: String, autorUid: String) = withContext(Dispatchers.IO) {
        try {
            deleteIngredientes(recetaId)
            deletePasos(recetaId)
            deleteImagenesReceta(recetaId)

            supabase.from("recetas").delete {
                filter { 
                    eq("receta_id", recetaId)
                    eq("autor_uid", autorUid)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleteReceta: ${e.message}")
            false
        }
    }

    // --- PUBLICACIONES ---

    suspend fun insertPublicacion(post: Publicacion): Publicacion? = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("publicaciones").insert(post) { select() }
            res.decodeSingle<Publicacion>()
        } catch (e: Exception) {
            Log.e(TAG, "Error insertPublicacion: ${e.message}")
            null
        }
    }

    suspend fun getFeedPublicaciones(): List<Publicacion> = withContext(Dispatchers.IO) {
        try {
            supabase.from("publicaciones").select {
                order("fecha_creacion", Order.DESCENDING)
            }.decodeList<Publicacion>()
        } catch (e: Exception) {
            Log.e(TAG, "Error getFeedPublicaciones: ${e.message}")
            emptyList()
        }
    }

    suspend fun deletePublicacion(postId: String, autorUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("imagenes_publicacion").delete { filter { eq("publicacion_id", postId) } }
            supabase.from("publicaciones").delete {
                filter {
                    eq("publicacion_id", postId)
                    eq("autor_uid", autorUid)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deletePublicacion: ${e.message}")
            false
        }
    }

    suspend fun insertImagenPublicacion(postId: String, url: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("imagenes_publicacion").insert(mapOf("publicacion_id" to postId, "url" to url))
        } catch (e: Exception) { }
    }

    suspend fun getImagenesPublicacion(postId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = supabase.from("imagenes_publicacion").select { filter { eq("publicacion_id", postId) } }
            response.decodeList<Map<String, JsonElement>>().mapNotNull { it["url"]?.jsonPrimitive?.content }
        } catch (e: Exception) { emptyList() }
    }

    // --- SOCIAL: LIKES ---

    suspend fun toggleLike(usuarioUid: String, recursoId: String, tipo: String) = withContext(Dispatchers.IO) {
        try {
            // Asegurar que el usuario existe en la tabla antes de dar like
            if (getUsuarioByUid(usuarioUid) == null) {
                val session = supabase.auth.currentSessionOrNull()
                if (session?.user != null) {
                    val u = session.user!!
                    upsertUsuario(Usuario(
                        uid = u.id,
                        email = u.email,
                        nombreUsuario = u.email?.substringBefore("@") ?: "user",
                        nombreCompleto = u.userMetadata?.get("full_name")?.jsonPrimitive?.content ?: "Usuario"
                    ))
                }
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
                
                // Los contadores ahora se manejan vía Triggers en Supabase
                // Solo enviamos la notificación
                if (tipo == "receta") {
                    val receta = getRecetaById(recursoId)
                    receta?.let { 
                        crearNotificacion(it.autorUid, usuarioUid, "like", recursoId) 
                    }
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

    suspend fun getLikedResourceIds(usuarioUid: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val res = supabase.from("likes").select {
                filter { eq("usuario_uid", usuarioUid) }
            }.decodeList<Map<String, JsonElement>>()
            res.mapNotNull { it["recurso_id"]?.jsonPrimitive?.content }
        } catch (e: Exception) {
            emptyList()
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
            // Asegurar que el autor existe antes de comentar (evitar error de FK)
            if (getUsuarioByUid(comentario.autorUid) == null) {
                val session = supabase.auth.currentSessionOrNull()
                if (session?.user != null && session.user?.id == comentario.autorUid) {
                    val u = session.user!!
                    upsertUsuario(Usuario(
                        uid = u.id,
                        email = u.email,
                        nombreUsuario = u.email?.substringBefore("@") ?: "user",
                        nombreCompleto = u.userMetadata?.get("full_name")?.jsonPrimitive?.content ?: "Usuario"
                    ))
                }
            }

            supabase.from("comentarios").insert(comentario)
            
            // Los contadores ahora se manejan vía Triggers en Supabase
            // Solo enviamos la notificación
            if (comentario.tipoRecurso == "receta") {
                val receta = getRecetaById(comentario.recursoId)
                receta?.let { 
                    crearNotificacion(it.autorUid, comentario.autorUid, "comentario", comentario.recursoId) 
                }
            }
            true
        } catch (e: Exception) { 
            Log.e(TAG, "Error insertComentario: ${e.message}")
            false 
        }
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
            val followedUser = getUsuarioByUid(followedUid)
            if (followedUser?.esPublico == false) {
                supabase.from("solicitudes_seguimiento").insert(mapOf(
                    "solicitante_uid" to followerUid,
                    "destino_uid" to followedUid
                ))
                crearNotificacion(followedUid, followerUid, "solicitud")
            } else {
                supabase.from("seguidores").insert(mapOf(
                    "seguidor_uid" to followerUid,
                    "seguido_uid" to followedUid
                ))
                crearNotificacion(followedUid, followerUid, "seguidor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error followUser: ${e.message}")
        }
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

    suspend fun getEstadoSeguimiento(followerUid: String, followedUid: String): String = withContext(Dispatchers.IO) {
        try {
            if (isFollowing(followerUid, followedUid)) return@withContext "siguiendo"
            val pending = supabase.from("solicitudes_seguimiento").select {
                filter {
                    eq("solicitante_uid", followerUid)
                    eq("destinatario_uid", followedUid)
                    eq("estado", "pendiente")
                }
            }.decodeList<JsonElement>()
            if (pending.isNotEmpty()) "pendiente" else "ninguno"
        } catch (e: Exception) {
            Log.e(TAG, "Error getEstadoSeguimiento: ${e.message}")
            "ninguno"
        }
    }

    suspend fun getSolicitudesPendientes(uid: String): List<SolicitudSeguimiento> = withContext(Dispatchers.IO) {
        try {
            supabase.from("solicitudes_seguimiento").select {
                filter {
                    eq("destino_uid", uid)
                    eq("estado", "pendiente")
                }
                order("fecha_creacion", Order.DESCENDING)
            }.decodeList<SolicitudSeguimiento>()
        } catch (e: Exception) {
            Log.e(TAG, "Error getSolicitudesPendientes: ${e.message}")
            emptyList()
        }
    }

    suspend fun aceptarSolicitud(solicitudId: String, seguidorUid: String, seguidoUid: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("seguidores").insert(mapOf(
                "seguidor_uid" to seguidorUid,
                "seguido_uid" to seguidoUid
            ))
            supabase.from("solicitudes_seguimiento").update(mapOf("estado" to "aceptada")) {
                filter { eq("id", solicitudId) }
            }
            crearNotificacion(seguidorUid, seguidoUid, "seguidor")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error aceptarSolicitud: ${e.message}")
            false
        }
    }

    suspend fun rechazarSolicitud(solicitudId: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("solicitudes_seguimiento").update(mapOf("estado" to "rechazada")) {
                filter { eq("id", solicitudId) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error rechazarSolicitud: ${e.message}")
            false
        }
    }

    // --- MENSAJERÍA ---

    suspend fun sendMensaje(emisorUid: String, receptorUid: String, contenido: String) = withContext(Dispatchers.IO) {
        try {
            val mensaje = Mensaje(emisorUid = emisorUid, receptorUid = receptorUid, contenido = contenido)
            supabase.from("mensajes").insert(mensaje)
            crearNotificacion(receptorUid, emisorUid, "mensaje")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sendMensaje: ${e.message}")
            false
        }
    }

    suspend fun getConversaciones(uid: String): List<Mensaje> = withContext(Dispatchers.IO) {
        try {
            supabase.from("mensajes").select {
                filter {
                    or {
                        eq("emisor_uid", uid)
                        eq("receptor_uid", uid)
                    }
                }
                order("fecha_creacion", Order.DESCENDING)
            }.decodeList<Mensaje>()
        } catch (e: Exception) {
            Log.e(TAG, "Error getConversaciones: ${e.message}")
            emptyList()
        }
    }

    suspend fun getMensajesConUsuario(miUid: String, otroUid: String): List<Mensaje> = withContext(Dispatchers.IO) {
        try {
            supabase.from("mensajes").select {
                filter {
                    or {
                        and {
                            eq("emisor_uid", miUid)
                            eq("receptor_uid", otroUid)
                        }
                        and {
                            eq("emisor_uid", otroUid)
                            eq("receptor_uid", miUid)
                        }
                    }
                }
                order("fecha_creacion", Order.ASCENDING)
            }.decodeList<Mensaje>()
        } catch (e: Exception) {
            Log.e(TAG, "Error getMensajesConUsuario: ${e.message}")
            emptyList()
        }
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

    suspend fun deleteIngredientes(recetaId: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("ingredientes_receta").delete {
                filter { eq("receta_id", recetaId) }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun deletePasos(recetaId: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("pasos_receta").delete {
                filter { eq("receta_id", recetaId) }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteImagenesReceta(recetaId: String) = withContext(Dispatchers.IO) {
        try {
            supabase.from("imagenes_receta").delete { filter { eq("receta_id", recetaId) } }
            true
        } catch (e: Exception) { false }
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
