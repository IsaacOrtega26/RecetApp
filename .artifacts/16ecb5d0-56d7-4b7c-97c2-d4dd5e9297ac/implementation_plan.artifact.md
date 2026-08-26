# Compartido Enriquecido de Recetas

Este plan detalla la implementación de una función de compartido "Rich" que incluye imagen, detalles completos de la receta (nombre, tiempo, ingredientes, pasos) y una invitación a descargar la app.

## User Review Required

> [!IMPORTANT]
> Para compartir la imagen físicamente con apps como WhatsApp o Instagram, necesitaremos configurar un `FileProvider`. Esto permite que otras aplicaciones lean temporalmente el archivo de imagen descargado.

## Proposed Changes

### [Componente] Configuración de Android

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/AndroidManifest.xml)
- Añadir la declaración del `FileProvider` para permitir el compartido de archivos temporales.

#### [NEW] [file_paths.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/xml/file_paths.xml)
- Definir la ruta de caché para las imágenes temporales compartidas.

### [Componente] Lógica de Compartido

#### [NEW] [ShareManager.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/ShareManager.kt)
- Crear una clase utilitaria para:
    - Formatear el texto de la receta (Título, Tiempo, Ingredientes con viñetas, Pasos numerados).
    - Incluir el mensaje de invitación: "Para ver mas recetas como esta te invito a usar nuestra app: RecetApp".
    - Descargar la imagen de la receta a un archivo temporal.
    - Lanzar el `Intent.ACTION_SEND` con el texto y el URI de la imagen.

### [Componente] UI (MainActivity y RecipeDetailActivity)

#### [MODIFY] [RecipeDetailActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RecipeDetailActivity.kt)
- Actualizar el menú de compartido para usar el nuevo `ShareManager`.
- Ofrecer la opción de "Enviar a un amigo en RecetApp" (compartido interno) o "Compartir en otras aplicaciones" (WhatsApp, IG, etc.).

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)
- Habilitar el botón `ivShare` en el `RecipeFeedAdapter` para usar el `ShareManager`.

## Plan de Verificación

### Pruebas Manuales
1.  **WhatsApp**: Compartir una receta. Verificar que llega la imagen, el texto estructurado (ingredientes y pasos) y la invitación final.
2.  **Instagram**: Verificar que se puede compartir la imagen y el texto (aunque IG suele priorizar la imagen, el texto irá al portapapeles o como pie de foto).
3.  **Chat Interno**: Verificar que al enviar a un amigo dentro de RecetApp, el mensaje contiene toda la información formateada.

## Preguntas Abiertas
- ¿Deseas que los ingredientes tengan algún formato especial (ej. usar emojis de comida 🥕 🥣)?
- ¿Hay algún enlace específico de descarga que debamos incluir en la invitación?
