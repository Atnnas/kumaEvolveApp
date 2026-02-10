---
description: Cómo instalar o actualizar la App en tu S25 Ultra
---

Para actualizar la App directamente en tu Samsung S25 Ultra, tienes tres caminos principales. Elige el que te resulte más cómodo:

### Opción 1: Usar el botón "Run" (La más fácil)
Si tienes **Android Studio** abierto con el proyecto:
1. Conecta tu S25 Ultra a la PC con el cable USB.
2. Asegúrate de que el dispositivo aparezca en la lista desplegable de la barra superior.
3. Haz clic en el botón **Run** (el icono verde de "Play" ▶️).
4. El app se compilará e instalará automáticamente.

### Opción 2: Instalación Manual (APK)
He generado el archivo del App para ti. Puedes copiarlo a tu teléfono e instalarlo:
1. El archivo está en: `C:\Dev\kuma-evolve-app\app\build\intermediates\apk\debug\app-debug.apk`
2. Pásalo a tu S25 Ultra (por cable, Drive, o WhatsApp Web).
3. En el teléfono, abre el archivo y dale a "Actualizar" o "Instalar".

### Opción 3: Vía Línea de Comandos (Si tienes ADB configurado)
Si tienes las herramientas de Android en tu PATH, puedes usar:
```powershell
adb install -r "C:\Dev\kuma-evolve-app\app\build\intermediates\apk\debug\app-debug.apk"
```
*(El comando `adb install -r` reinstala el app manteniendo tus datos).*

> [!IMPORTANT]
> **Habilitar Depuración USB**: Para las opciones 1 y 3, debes ir en tu S25 Ultra a:
> **Ajustes > Acerca del teléfono > Información de software** y tocar 7 veces en **"Número de compilación"**. Luego vuelve a Ajustes, entra en **"Opciones de desarrollador"** y activa **"Depuración por USB"**.
