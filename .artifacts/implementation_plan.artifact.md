# Implementación de Notificaciones Push (FCM)

Este plan describe los pasos para integrar Firebase Cloud Messaging (FCM) y permitir que los usuarios reciban notificaciones incluso con la app cerrada o el teléfono bloqueado.

## Requisitos Previos del Usuario

> [!IMPORTANT]
> Para que esto funcione, debes realizar lo siguiente fuera de la app:
> 1.  Crea un proyecto en [Firebase Console](https://console.firebase.google.com/).
> 2.  Registra tu app Android con el paquete `com.example.recetapp.v2`.
> 3.  Descarga el archivo `google-services.json` y colócalo en la carpeta `app/` de tu proyecto.
> 4.  **Habilitar Cloud Messaging**: En Firebase, ve a Project Settings > Cloud Messaging y asegúrate de que la API esté habilitada.

## Cambios Propuestos

### 1. Configuración de Firebase y Dependencias

#### [MODIFY] [build.gradle.kts (Proyecto)](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/build.gradle.kts)
- Añadir el plugin de Google Services.

#### [MODIFY] [build.gradle.kts (App)](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/build.gradle.kts)
- Añadir las librerías de Firebase (BoM y Messaging).

### 2. Modelo de Datos y Repositorio

#### [MODIFY] [RecipeModels.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RecipeModels.kt)
- Añadir el campo `fcmToken: String?` a la clase `Usuario`.

#### [MODIFY] [SupabaseRepository.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/SupabaseRepository.kt)
- Añadir función para actualizar únicamente el token FCM del usuario actual.

### 3. Servicio de Mensajería

#### [NEW] `MyFirebaseMessagingService.kt`
- Implementar el servicio que escucha las notificaciones cuando llegan al dispositivo.
- Mostrar una notificación nativa de Android en la barra de estado.

### 4. Integración en la App

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)
- Al iniciar sesión, obtener el Token de Firebase y guardarlo en la base de datos de Supabase vinculada al usuario.

## Configuración de Base de Datos (SQL)

> [!TIP]
> Debes ejecutar este comando en tu **SQL Editor** de Supabase para preparar la tabla:
> ```sql
> ALTER TABLE usuarios ADD COLUMN fcm_token TEXT;
> ```

## Plan de Verificación

### Verificación Manual
1. Abrir la app y loguearse.
2. Verificar en la base de datos (Supabase) que la columna `fcm_token` tiene un valor largo (el token).
3. Cerrar la app totalmente.
4. Enviar una notificación de prueba desde **Firebase Console > Messaging** y verificar que llega al emulador.
