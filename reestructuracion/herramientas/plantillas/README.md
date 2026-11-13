# Comando para el sonar-scanner, no para el proyecto.
#
# Lo que se corre a mano antes de cerrar una sesión. El servidor vive en esta
# máquina (:9000) y no está expuesto, así que no puede correr en GitHub Actions.
#
#   reestructuracion/herramientas/escanear/sonar.sh signall-android
#   reestructuracion/herramientas/escanear/sonar.sh signall-backend
#   reestructuracion/herramientas/escanear/sonar.sh signall-stt
#
# El repositorio de documentos no se analiza: es LaTeX, no código.
