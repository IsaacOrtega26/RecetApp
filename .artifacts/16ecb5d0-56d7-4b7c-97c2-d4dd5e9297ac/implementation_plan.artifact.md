# Plan de implementación: Solución Definitiva de Solicitudes, Contadores e Interfaz

Este plan garantiza que las solicitudes de seguimiento se eliminen instantáneamente al ser aceptadas, que los contadores de seguidores sean siempre precisos y que los botones de navegación sean totalmente accesibles.

## User Review Required

> [!IMPORTANT]
> Se implementará "Optimistic UI" en la lista de solicitudes: al presionar aceptar, el usuario desaparecerá de la lista inmediatamente en la pantalla, mientras la operación se procesa en segundo plano. Esto da la sensación de rapidez de Instagram.

> [!TIP]
> Se ha aumentado el margen superior a **60px** para garantizar que ningún elemento de la interfaz quede oculto por la cámara o la barra de estado.

## Proposed Changes

### [Social / Repositorio]

#### [MODIFY] [SupabaseRepository.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/SupabaseRepository.kt)
- **Aceptar/Rechazar**: Se usará el `solicitudId` (Primary Key) para eliminar el registro de forma exacta.
- **Sincronización**: Se ha verificado que `getUsuarioByUid` realice un recuento real de filas, lo que garantiza que los números de seguidores sean siempre los correctos sin importar fallos previos.

### [UI / Actividad Principal]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/java/com/example/recetapp/MainActivity.kt)
- **FollowRequestAdapter**: Se cambiará la lógica para que el adaptador maneje una lista mutable. Al aceptar/rechazar, se eliminará el elemento de la lista local e inmediatamente se notificará al adaptador (`notifyItemRemoved`), logrando el efecto de desaparición instantánea.
- **Seguimiento Mutuo**: Se añadirá el botón "Seguir también" en la lógica del diálogo si es necesario.

### [UI / Ajustes de Diseño]

#### [MODIFY] Ajustar padding superior en Actividades:
- Se aumentará el padding superior a `systemBars.top + 60` en todas las actividades críticas para bajar los encabezados y liberar los botones de atrás.

## Verification Plan

### Manual Verification
1.  **Aceptación Instantánea**: Abrir solicitudes, presionar "+". El usuario debe desaparecer de la lista de inmediato.
2.  **Contadores**: Verificar que al volver al perfil, el número de seguidores es exacto.
3.  **Botón Atrás**: Verificar que en todos los dispositivos el botón de atrás es fácil de presionar y tiene suficiente espacio superior.
