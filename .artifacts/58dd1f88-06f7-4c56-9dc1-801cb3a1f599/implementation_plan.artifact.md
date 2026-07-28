# Implementación de la pantalla de Inicio de Sesión (Login)

Este plan detalla los pasos para crear la pantalla de inicio de sesión, manteniendo la coherencia visual con la pantalla de registro ya implementada.

## Cambios Propuestos

### Recursos (Strings)

#### [MODIFY] [strings.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/values/strings.xml)
Añadir textos para la pantalla de inicio de sesión:
- "Bienvenido de nuevo"
- "Ingresa tus credenciales para continuar."
- "¿No tienes cuenta? Regístrate"

### Actividades y Navegación

#### [NEW] [LoginActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/LoginActivity.kt)
Nueva actividad para manejar el inicio de sesión.
- Implementar validación de campos:
    - **Correo electrónico**: Formato válido y no vacío.
    - **Contraseña**: No vacía.

#### [NEW] [activity_login.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_login.xml)
Diseño basado en el estilo de `activity_register.xml`:
- Botón de retroceso.
- Título y subtítulo.
- Campos de Correo y Contraseña.
- Botón naranja "Iniciar sesión".
- Texto inferior para ir a Registro.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)
Configurar el botón "Iniciar sesión" para navegar a `LoginActivity`.

#### [MODIFY] [RegisterActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/RegisterActivity.kt)
Configurar el texto del pie de página para que navegue a `LoginActivity` si el usuario ya tiene cuenta.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/AndroidManifest.xml)
Registrar `LoginActivity`.

## Plan de Verificación
1. **Navegación**:
    - Desde Bienvenida -> Inicio de sesión.
    - Desde Registro -> Inicio de sesión.
    - Desde Inicio de sesión -> Registro.
2. **Validación**:
    - Probar campos vacíos.
    - Probar formato de correo inválido.
    - Verificar que aparezcan los errores correspondientes.
3. **Estilo**: Asegurar que los colores y fuentes sean idénticos a la pantalla de Registro.
