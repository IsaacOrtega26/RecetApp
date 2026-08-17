# Guía Paso a Paso: Configuración de Firebase

Sigue estos pasos para conectar tu aplicación con el sistema de notificaciones de Google.

## 1. Crear el Proyecto en Firebase

1.  Entra a [Firebase Console](https://console.firebase.google.com/).
2.  Haz clic en **"Agregar proyecto"**.
3.  Nombre del proyecto: `RecetApp`.
4.  Puedes desactivar Google Analytics por ahora si quieres ir más rápido, o dejarlo activado (es gratis).
5.  Haz clic en **"Crear proyecto"** y espera a que termine.

## 2. Registrar la Aplicación Android

1.  En la pantalla principal de tu nuevo proyecto, verás varios iconos. Haz clic en el de **Android** (el robot verde).
2.  **Nombre del paquete de Android**: Debes escribir exactamente:
    `com.example.recetapp.v2`
3.  Apodo de la app (Opcional): `RecetApp Android`.
4.  Haz clic en **"Registrar app"**.

## 3. Descargar el archivo de configuración

1.  Ahora verás un botón azul grande que dice **"Descargar google-services.json"**.
2.  Haz clic en él y guárdalo en tu computadora (normalmente en la carpeta de *Descargas*).
3.  En la página de Firebase, dale a **Siguiente** en todos los pasos restantes hasta volver al panel principal.

## 4. Colocar el archivo en Android Studio

Esta es la parte donde muchos se pierden, sigue esto con cuidado:

1.  Abre **Android Studio**.
2.  Mira la barra lateral izquierda (donde están tus archivos). Arriba del todo, hay un desplegable que suele decir **"Android"**. Cambialo a **"Project"**.
3.  Ahora verás una carpeta llamada `RecetApp`. Ábrela y verás otra carpeta llamada **`app`**.
4.  Busca el archivo `google-services.json` que descargaste en tu computadora.
5.  **Arrástralo y suéltalo** directamente sobre la carpeta `app` en Android Studio.
6.  Te saldrá un cuadro de confirmación, dale a **"OK"**.

## 5. ¡Listo!

Una vez hecho esto, solo queda un último clic:
1. Arriba a la derecha en Android Studio, busca un icono de un elefante con una flecha azul (se llama **"Sync Project with Gradle Files"**). Hazle clic.

> [!IMPORTANT]
> El archivo debe quedar exactamente en esta ruta:
> `RecetApp/app/google-services.json`
> Si lo pones fuera de la carpeta `app`, la aplicación no funcionará.
