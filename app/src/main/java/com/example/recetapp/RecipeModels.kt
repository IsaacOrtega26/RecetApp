package com.example.recetapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Usuario(
    @SerialName("uid_usuario") val uid: String? = null,
    val email: String? = null,
    @SerialName("nombre_usuario") val nombreUsuario: String? = null,
    @SerialName("nombre_completo") val nombreCompleto: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    val descripcion: String? = null,
    @SerialName("es_publico") val esPublico: Boolean = true,
    val rol: String = "usuario",
    @SerialName("total_seguidores") val totalSeguidores: Int = 0,
    @SerialName("total_seguidos") val totalSeguidos: Int = 0,
    @SerialName("total_recetas") val totalRecetas: Int = 0,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null,
    val activo: Boolean = true,
    val contrasena: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null
)

@Serializable
data class Receta(
    @SerialName("receta_id") val id: String? = null,
    @SerialName("autor_uid") val autorUid: String,
    val nombre: String,
    @SerialName("nombre_normalizado") val nombreNormalizado: String? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val dificultad: String? = null, // 'baja', 'media', 'alta'
    @SerialName("tiempo_estimado") val tiempoEstimado: Int? = null,
    val visibilidad: String = "publica",
    @SerialName("total_likes") val totalLikes: Int = 0,
    @SerialName("total_comentarios") val totalComentarios: Int = 0,
    @SerialName("total_guardados") val totalGuardados: Int = 0,
    @SerialName("total_favoritos") val totalFavoritos: Int = 0,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class Publicacion(
    @SerialName("publicacion_id") val id: String? = null,
    @SerialName("autor_uid") val autorUid: String,
    val descripcion: String? = null,
    @SerialName("receta_id") val recetaId: String? = null,
    val visibilidad: String = "publica",
    @SerialName("total_likes") val totalLikes: Int = 0,
    @SerialName("total_comentarios") val totalComentarios: Int = 0,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class IngredienteReceta(
    val id: String? = null,
    @SerialName("receta_id") val recetaId: String,
    val nombre: String,
    val cantidad: Double? = null,
    val unidad: String? = null,
    val orden: Int = 0
)

@Serializable
data class PasoReceta(
    val id: String? = null,
    @SerialName("receta_id") val recetaId: String,
    val orden: Int,
    val descripcion: String,
    @SerialName("tiempo_segundos") val tiempoSegundos: Int? = null
)

@Serializable
data class Comentario(
    @SerialName("comentario_id") val id: String? = null,
    @SerialName("autor_uid") val autorUid: String,
    @SerialName("recurso_id") val recursoId: String,
    @SerialName("tipo_recurso") val tipoRecurso: String, // 'receta', 'publicacion'
    val contenido: String,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class Reporte(
    val id: String? = null,
    @SerialName("reportante_uid") val reportanteUid: String,
    @SerialName("objeto_id") val objetoId: String,
    @SerialName("tipo_objeto") val tipoObjeto: String, // 'receta', 'publicacion', 'usuario'
    val motivo: String,
    val estado: String = "pendiente", // 'pendiente', 'revisado', 'ignorado'
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class Notificacion(
    @SerialName("notificacion_id") val id: String? = null,
    @SerialName("destinatario_uid") val destinatarioUid: String,
    @SerialName("actor_uid") val actorUid: String,
    val tipo: String, // 'like', 'comentario', 'seguidor', 'mensaje', 'solicitud'
    @SerialName("objeto_id") val objetoId: String? = null,
    val mensaje: String? = null,
    val leida: Boolean = false,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class SolicitudSeguimiento(
    @SerialName("id") val id: String? = null,
    @SerialName("solicitante_uid") val solicitanteUid: String,
    @SerialName("destino_uid") val destinoUid: String,
    val estado: String = "pendiente", // 'pendiente', 'aceptada', 'rechazada'
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class Mensaje(
    @SerialName("id") val id: String? = null,
    @SerialName("emisor_uid") val emisorUid: String,
    @SerialName("receptor_uid") val receptorUid: String,
    val contenido: String,
    val leido: Boolean = false,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)

@Serializable
data class Like(
    @SerialName("usuario_uid") val usuarioUid: String,
    @SerialName("recurso_id") val recursoId: String,
    @SerialName("tipo_recurso") val tipoRecurso: String,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null
)
