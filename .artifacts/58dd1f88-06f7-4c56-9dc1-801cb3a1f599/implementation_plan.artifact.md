# Sincronización Definitiva con el Esquema SQL de Usuarios

Este plan asegura que el modelo de datos en la aplicación Android sea un reflejo exacto de tu tabla `usuarios`, permitiendo que el registro y la carga de perfiles funcionen sin errores de "columna inexistente" o "datos faltantes".

## Cambios Propuestos

### 1. Modelo de Datos (Espejo del SQL)

#### [MODIFY] [RecipeModels.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RecipeModels.kt)
Actualizaré la clase `Usuario` para que contenga las 15 columnas exactas de tu esquema:
- `uid_usuario` (UID)
- `email`
- `nombre_usuario` (Handle)
- `nombre_completo`
- `foto_url`
- `descripcion`
- `es_publico` (Boolean)
- `rol` ("usuario" o "administrador")
- `total_seguidores` (Int)
- `total_seguidos` (Int)
- `total_recetas` (Int)
- `fecha_creacion` (String)
- `fecha_actualizacion` (String)
- `activo` (Boolean)
- `contrasena` (Text)

### 2. Registro Robusto (Populación de Columnas)

#### [MODIFY] [RegisterActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RegisterActivity.kt)
Al registrarse, el código enviará el objeto completo con valores iniciales correctos:
- Se generará un `nombre_usuario` basado en el email.
- Los contadores (`seguidores`, `recetas`, etc.) se enviarán como `0`.
- `es_publico` y `activo` se enviarán como `true`.
- Se incluirá la `contrasena` en texto plano en tu tabla (como solicitaste para el bypass).

### 3. Actualización de Interfaz y Carga

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)
- La función `showProfile` cargará y mostrará los contadores reales (`total_seguidores`, `total_recetas`, etc.) que ahora estarán disponibles en el modelo.
- Se adaptará la edición de perfil para que funcione con el nuevo modelo extendido.

## Plan de Verificación
1. **Prueba de Inserción**: Registrar un usuario nuevo. El objeto enviado ahora tendrá todas las columnas que Supabase espera, eliminando el error de "RLS" o "Columna faltante".
2. **Verificación en Supabase**: Confirmar en el panel web que la fila en `usuarios` tiene todos los campos llenos correctamente.
3. **Dashboard Real**: Comprobar que en el perfil del usuario aparecen los "0" iniciales en seguidores y recetas, confirmando que la data fluye desde la tabla.
