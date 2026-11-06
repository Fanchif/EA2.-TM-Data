package com.tecnomovil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;


public class Main {

    private static final int USUARIOS = 200_000;
    private static final long SEMILLA = 42L;
    private static final long UMBRAL_PASAJEROS_HORA = 15_000;

    public static void main(String[] args) {
        long inicio = System.nanoTime();
        List<Registro> registros = GeneradorDatos.generar(USUARIOS, SEMILLA);
        titulo("TECNOMOVIL DATA - Procesamiento funcional de datos");
        System.out.printf("Registros generados: %,d de %,d usuarios (%d ms)%n",
            registros.size(), USUARIOS, (System.nanoTime() - inicio) / 1_000_000);
        System.out.println("Muestra de registros:");
        registros.stream().limit(3).forEach(r -> System.out.println(" " + r));

        titulo("a) Afluencia por estacion (usuarios distintos que ingresan)");
        AnalizadorTransporte.afluenciaPorEstacion(registros, true)
                .forEach((estacion, n) -> System.out.printf("  %-18s %,10d%n", estacion, n));

        titulo("b) Flujo por hora y horas pico");
        AnalizadorTransporte.flujoPorHora(registros, true)
                .forEach((hora, n) -> System.out.printf("  %02d:00  %,9d  %s%n", hora, n, "#".repeat((int) (n / 5_000))));
        System.out.println("  Top 3 horas pico:");
        AnalizadorTransporte.horasPico(registros, 3, true)
                .forEach(e -> System.out.printf("    %02d:00 -> %,d registros%n", e.getKey(), e.getValue()));

        titulo("c) Rutas mas utilizadas (entradas)");
        AnalizadorTransporte.rutasMasUtilizadas(registros, 5, true)
                .forEach(e -> System.out.printf("  %-20s %,10d%n", e.getKey(), e.getValue()));

        titulo("d) Patrones de viaje (primeros 5 usuarios)");
        AnalizadorTransporte.patronesPorUsuario(registros, true).entrySet().stream()
                .limit(5)
                .forEach(e -> System.out.printf("  %s: %s%n", e.getKey(), String.join(" > ", e.getValue())));

        titulo("e) Tiempo promedio entre estaciones (10 tramos mas largos)");
        AnalizadorTransporte.tiempoPromedioEntreEstaciones(registros, true).entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> System.out.printf("  %-36s %6.1f min%n", e.getKey(), e.getValue()));
        System.out.printf("  Promedio general: %.1f min%n", AnalizadorTransporte.tiempoPromedioGeneral(registros, true));

        titulo("f) Deteccion de sobrecarga (umbral " + UMBRAL_PASAJEROS_HORA + " pasajeros/hora)");
        AnalizadorTransporte.detectarSobrecarga(registros, UMBRAL_PASAJEROS_HORA, true)
                .forEach(e -> System.out.printf("  %-20s pico %02d:00  %,8d pasajeros  [%s]%n",
                        e.ruta(), e.horaPico(), e.pasajerosHoraPico(), e.etiqueta()));

        titulo("Prueba de inmutabilidad");
        try {
            registros.add(new Registro("X", "X", "X", Registro.Accion.ENTRADA, LocalDateTime.now()));
            System.out.println("  ERROR: la lista fue modificada");
        } catch (UnsupportedOperationException e) {
            System.out.println("  OK: la lista original no se puede modificar (UnsupportedOperationException)");
        }

        titulo("Secuencial vs paralelo (todas las operaciones)");
        ejecutarTodo(registros, false); // calentamiento de la JVM antes de medir
        ejecutarTodo(registros, true);
        Object secuencial = medir("stream()        ", () -> ejecutarTodo(registros, false));
        Object paralelo = medir("parallelStream()", () -> ejecutarTodo(registros, true));
        System.out.println("  Resultados identicos: " + secuencial.equals(paralelo)
                + " (sin condiciones de carrera)");
    }

    private static List<Object> ejecutarTodo(List<Registro> r, boolean p) {
        return List.of(
                AnalizadorTransporte.afluenciaPorEstacion(r, p),
                AnalizadorTransporte.flujoPorHora(r, p),
                AnalizadorTransporte.usoPorRuta(r, p),
                AnalizadorTransporte.patronesPorUsuario(r, p),
                AnalizadorTransporte.tiempoPromedioEntreEstaciones(r, p),
                AnalizadorTransporte.detectarSobrecarga(r, UMBRAL_PASAJEROS_HORA, p));
    }

    private static <T> T medir(String etiqueta, Supplier<T> tarea) {
        long t = System.nanoTime();
        T resultado = tarea.get();
        System.out.printf("  %s %,6d ms%n", etiqueta, (System.nanoTime() - t) / 1_000_000);
        return resultado;
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("=== " + texto + " ===");

    }

}