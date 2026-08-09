# Plan de Implementación: Solución de Conflicto de Base de Datos en Perfil

He analizado los logs y he identificado que el problema se debe a un conflicto de "llave duplicada" en el correo electrónico al intentar guardar la información de Google. Esto sucede si ya existe un perfil con ese correo pero con un ID interno diferente.

## Cambios Propuestos

### Repositorio

#### [MODIFICAR] [SupabaseRepository.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/SupabaseRepository.kt)
- Cambiar la estrategia de `upsert`. En lugar de entrar en conflicto solo por el ID de usuario (`uid_usuario`), priorizaremos el conflicto por el correo (`email`).
- Esto permitirá que si ya existe un perfil con ese correo, se actualice con el nuevo ID y la nueva foto de Google, en lugar de fallar y no mostrar nada.

### UI y Diagnóstico

#### [MODIFICAR] [MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)
- Mejorar el manejo de errores en `showProfile`. Si la carga falla, intentaremos buscar al usuario por su correo como plan de respaldo, asegurando que la información siempre se muestre.

## Plan de Verificación

### Pruebas Manuales
1. **Login con Google**: Verificar que ahora, a pesar del conflicto previo, el perfil se cargue correctamente.
2. **Revisión de Logs**: Confirmar en Logcat que el usuario se carga satisfactoriamente tras la sincronización.

> [!IMPORTANT]
> Este cambio es fundamental para usuarios que ya tenían una cuenta y deciden vincular o entrar con Google por primera vez, evitando que su perfil quede "bloqueado".
