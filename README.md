## TM Data, procesamiento funcional de datos de transporte

Caso de estudio - Tecnología en Desarrollo de Software

Módulo Java puro (sin frameworks) encargado de procesar resgistros
simulados del sistema de transporte de TecnoValle usando Streams,
lambdas, funciones puras y colecciones inmutables.

## Requisitos

JDK 17 (Hecho con JDK 21)

## Estructura

Registro.java -> Record inmutable: idUsuario, ruta, estación, acción, timestamp
GeneradorDatos.java -> Genera aprox 800.000 registros simulados de forma determinista
AnalizadorTransporte.java -> Funciones puras para los requerimientos a) a f)
Main.java -> Ejecuta el análisis, imprime resultados y compara stram() vs parallelStream()

## Requisitos implementados

    -a) Afluencia por estación
    -b) Horas pico
    -c) Rutas más usadas
    -d) Patrones de viaje según usuario
    -e) Tiempo promedio entre estaciones
    -f) Detección de sobrecarga