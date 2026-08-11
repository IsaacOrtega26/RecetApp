# Arreglo de Navegación (Hit Area y Posicionamiento)

El usuario reporta dificultades para usar el botón de retroceso porque los encabezados están muy arriba (posiblemente bajo la barra de estado). Se ajustarán los layouts para que respeten las áreas seguras del sistema (`fitsSystemWindows`) y se bajará visualmente el Toolbar para mejorar la usabilidad.

## Proposed Changes

### Ajuste de Layouts (Sistema de Ventanas)

Se activará `android:fitsSystemWindows="true"` en los contenedores principales de todas las actividades secundarias. Esto hará que el sistema automáticamente reserve el espacio de la barra de estado, bajando el Toolbar a una posición cómoda y clicable.

#### [MODIFY] [activity_recipe_detail.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_recipe_detail.xml)
- Añadir `android:fitsSystemWindows="true"` al `CoordinatorLayout` raíz.
- Añadir `android:fitsSystemWindows="true"` al `AppBarLayout`.

#### [MODIFY] [activity_create_recipe.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_create_recipe.xml)
- Añadir `android:fitsSystemWindows="true"` al `ConstraintLayout` raíz.

#### [MODIFY] [activity_create_post.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_create_post.xml)
- Añadir `android:fitsSystemWindows="true"` al `ConstraintLayout` raíz.

#### [MODIFY] [activity_messages.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_messages.xml)
- Añadir `android:fitsSystemWindows="true"` al `LinearLayout` raíz.

#### [MODIFY] [activity_chat.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_chat.xml)
- Añadir `android:fitsSystemWindows="true"` al `ConstraintLayout` raíz.

#### [MODIFY] [activity_cooking_mode.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_cooking_mode.xml)
- Añadir `android:fitsSystemWindows="true"` al `ConstraintLayout` raíz.

#### [MODIFY] [activity_settings.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_settings.xml)
- Añadir `android:fitsSystemWindows="true"` al `LinearLayout` raíz.

#### [MODIFY] [activity_admin_panel.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_admin_panel.xml)
- Añadir `android:fitsSystemWindows="true"` al `CoordinatorLayout` raíz.

### Refuerzo en MainActivity

#### [MODIFY] [activity_main.xml](file:///C:/Users/Brittany Barquero/Downloads/RecetApp/app/src/main/res/layout/activity_main.xml)
- Asegurar que el contenedor principal tenga `android:fitsSystemWindows="true"`.

## Verification Plan

### Manual Verification
1. Abrir la pantalla de detalle de una receta.
2. Verificar que la flecha de retroceso ya no está detrás del reloj o los iconos de batería.
3. Pulsar el botón de retroceso. Debe cerrarse la actividad sin resistencia.
4. Repetir la prueba en el chat y en la creación de recetas.
