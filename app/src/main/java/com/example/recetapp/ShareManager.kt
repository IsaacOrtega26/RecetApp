package com.example.recetapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareManager {

    suspend fun shareRecipe(
        context: Context,
        recipe: Receta,
        ingredients: List<IngredienteReceta> = emptyList(),
        steps: List<PasoReceta> = emptyList(),
        imageUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val shareText = generateShareText(recipe, ingredients, steps)

            val imageUri = if (!imageUrl.isNullOrEmpty()) {
                downloadImageAndGetUri(context, imageUrl)
            } else {
                null
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (imageUri != null) "image/*" else "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                if (imageUri != null) {
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Compartir receta"))
            }
        } catch (e: Exception) {
            Log.e("ShareManager", "Error al compartir receta: ${e.message}")
        }
    }

    fun generateShareText(
        recipe: Receta,
        ingredients: List<IngredienteReceta> = emptyList(),
        steps: List<PasoReceta> = emptyList()
    ): String = buildString {
        append("🍳 ¡Mira esta deliciosa receta en RecetApp!\n\n")
        append("*${recipe.nombre?.uppercase()}*\n")
        recipe.descripcion?.let { append("$it\n\n") }
        
        append("⏱ Tiempo estimado: ${recipe.tiempoEstimado} min\n")
        append("📊 Dificultad: ${recipe.dificultad?.replaceFirstChar { it.uppercase() }}\n\n")

        if (ingredients.isNotEmpty()) {
            append("🛒 *INGREDIENTES:*\n")
            ingredients.forEach { 
                append("• ${it.nombre} (${it.cantidad ?: ""} ${it.unidad ?: ""})\n")
            }
            append("\n")
        }

        if (steps.isNotEmpty()) {
            append("👨‍🍳 *PASOS:*\n")
            steps.sortedBy { it.orden }.forEachIndexed { index, paso ->
                append("${index + 1}. ${paso.descripcion}\n")
            }
            append("\n")
        }

        append("✨ Para ver más recetas como esta te invito a usar nuestra app: *RecetApp*")
    }

    private suspend fun downloadImageAndGetUri(context: Context, url: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Necesario para obtener el bitmap
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                
                val cachePath = File(context.cacheDir, "shared_images")
                cachePath.mkdirs()
                
                val file = File(cachePath, "shared_recipe_image.png")
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()

                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ShareManager", "Error al descargar imagen: ${e.message}")
            null
        }
    }
}