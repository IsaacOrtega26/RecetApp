# Walkthrough: Implementación de Notificaciones Push (FCM)

He completado la integración de Firebase Cloud Messaging (FCM) para que la aplicación pueda recibir notificaciones push incluso cuando está cerrada.

## Cambios Realizados

### 1. Configuración de Firebase
- **Dependencias**: Se añadieron las librerías de Firebase Messaging y Analytics al proyecto.
- **Plugin**: Se configuró el plugin `google-services` para procesar la configuración de Firebase.

### 2. Gestión de Tokens
- **[RecipeModels.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RecipeModels.kt)**: Se añadió el campo `fcm_token` al modelo de usuario.
- **[SupabaseRepository.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/SupabaseRepository.kt)**: Se implementó la función `updateFcmToken` para guardar el identificador del dispositivo en Supabase.
- **[MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)**: Al iniciar la app, se obtiene automáticamente el token de Firebase y se sincroniza con tu perfil de usuario.

### 3. Servicio de Notificaciones
- **[NEW] [MyFirebaseMessagingService.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MyFirebaseMessagingService.kt)**: Se creó un servicio que escucha los mensajes enviados desde Firebase y muestra una notificación nativa en la barra de estado de Android.

## Pasos Cruciales para ti

Para que las notificaciones lleguen a los teléfonos reales, debes completar estos dos pasos:

### Paso 1: Configuración de Base de Datos
Ejecuta este comando en el **SQL Editor** de tu panel de Supabase:
```sql
ALTER TABLE usuarios ADD COLUMN fcm_token TEXT;
```

### Paso 2: Archivo de Configuración de Firebase
1. Ve a [Firebase Console](https://console.firebase.google.com/).
2. Descarga el archivo `google-services.json` de tu proyecto.
3. Colócalo dentro de la carpeta `app/` de este proyecto en Android Studio.

## Resultados de la Verificación

### Verificación Automática
- [x] **Gradle Sync**: Sincronización exitosa.
- [x] **Build**: El proyecto compila sin errores (`assembleDebug`).

> [!IMPORTANT]
> Sin el archivo `google-services.json`, la aplicación podría cerrarse al intentar conectar con Firebase. Asegúrate de colocarlo antes de ejecutarla.
