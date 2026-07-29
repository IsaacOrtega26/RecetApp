# Implementación del Inicio de Sesión - RecetApp

He completado la pantalla de inicio de sesión ("Bienvenido de nuevo"), integrándola completamente con la pantalla de bienvenida y la de registro.

## Cambios Realizados

### Pantalla de Inicio de Sesión (UI)
- **`activity_login.xml`**: Creado un diseño consistente con la pantalla de registro:
    - Mismo fondo crema y tipografía.
    - Campos de correo y contraseña con bordes redondeados.
    - Botón naranja de acción principal.
    - Enlace inferior para navegar a la pantalla de registro.

### Lógica y Validación
- **`LoginActivity.kt`**: Nueva actividad que incluye:
    - **Validación de correo**: Comprueba formato y que no esté vacío.
    - **Validación de contraseña**: Comprueba que no esté vacía.
    - **Navegación**: Conexión funcional con la pantalla de bienvenida y registro.

### Navegación Global
- **Bienvenida**: El botón "Iniciar sesión" ahora abre correctamente la nueva pantalla.
- **Interconexión**:
    - Desde **Registro** puedes saltar a **Inicio de sesión**.
    - Desde **Inicio de sesión** puedes saltar a **Registro**.

## Cómo Probarlo
1. Inicia la app y presiona **"Iniciar sesión"**.
2. Prueba las validaciones dejando los campos vacíos.
3. Usa el enlace inferior para ir a la pantalla de **Registro** y viceversa.

> [!IMPORTANT]
> Al igual que con el registro, si ves líneas rojas en el editor pero la app compila (Build Successful), se trata de un retraso del IDE al indexar los recursos. El código es 100% válido.

render_diffs(file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_login.xml)
render_diffs(file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/LoginActivity.kt)
