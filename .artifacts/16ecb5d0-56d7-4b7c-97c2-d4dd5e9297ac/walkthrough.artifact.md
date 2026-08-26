# Compartido Enriquecido de Recetas

Se ha implementado una funcionalidad avanzada de compartido que permite enviar recetas con todo su contenido (imagen, ingredientes, pasos) tanto a aplicaciones externas como dentro de la propia app.

## Cambios Realizados

### [Configuración del Sistema](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/AndroidManifest.xml)
- Se configuró un **FileProvider** para permitir el intercambio seguro de imágenes con otras aplicaciones como WhatsApp e Instagram.
- Se creó el archivo [file_paths.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/xml/file_paths.xml) para gestionar el almacenamiento temporal de las imágenes compartidas.

### [Lógica de Compartido (ShareManager)](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/ShareManager.kt)
- Se creó una utilidad centralizada que:
    - Descarga la imagen de la receta automáticamente antes de compartir.
    - Genera un texto estructurado con emojis, viñetas para ingredientes y numeración para los pasos.
    - Incluye siempre la invitación: *"✨ Para ver más recetas como esta te invito a usar nuestra app: RecetApp"*.

### [Integración en UI](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RecipeDetailActivity.kt)
- **Detalle de Receta**: El botón de compartir ahora extrae toda la información de la base de datos (ingredientes y pasos) para enviarla completa.
- **Feed (Muro)**: Se habilitó el botón de compartir en las tarjetas del muro principal, permitiendo compartir rápidamente sin entrar al detalle.
- **Chat Interno**: Se actualizó [ChatActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/ChatActivity.kt) para que, al compartir una receta con un amigo, el mensaje se pre-cargue con todo el formato enriquecido en lugar de solo un enlace simple.

## Verificación
- [x] Al compartir por WhatsApp, aparece la imagen y el texto con ingredientes/pasos.
- [x] El botón de compartir en el feed ya no es decorativo y funciona correctamente.
- [x] La invitación a descargar la app aparece al final de cada mensaje compartido.
- [x] En el chat interno, el mensaje enviado contiene la misma información estructurada.

> [!TIP]
> WhatsApp e Instagram mostrarán una vista previa de la imagen si esta se descarga correctamente. Asegúrate de tener conexión a internet para que el compartido sea completo.
