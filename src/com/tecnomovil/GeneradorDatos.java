package com.tecnomovil;

import com.tecnomovil.Registro.Accion;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/*
Se genera conjunto de registros simulados.
Siendo determinista, la misma cantidad de usuarios y la misma semilla
producen siempre los mismos datos, devolviendo una lista inmutable.
*/
public final class GeneradorDatos {

    private GeneradorDatos() {  }
    public static final LocalDate FECHA = LocalDate.of(2026, 11, 5);

    /* Rutas y estaciones */
    public static final Map<String, List<String>> RUTAS = Map.of(
            "T1-Troncal Norte", List.of("Portal Norte", "Calle 100", "Calle 72", "Centro", "Museo"),
            "T2-Troncal Sur", List.of("Centro", "Ricaurte", "Calle 6", "Portal Sur"),
            "T3-Av. Oriental", List.of("Museo", "Universidades", "Las Aguas", "Calle 72"),
            "A1-Alim. Occidente", List.of("Portal Occidente", "Banderas", "Ricaurte"),
            "A2-Alim. Oriente", List.of("Las Aguas", "La Paz", "San Cristobal"));

    /* Rutas repetidas según demanda */        
    private static final List<String> RUTAS_PONDERADAS = Stream.of(
                    Map.entry("T1-Troncal Norte", 4), Map.entry("T2-Troncal Sur", 3),
                    Map.entry("T3-Av. Oriental", 2), Map.entry("A1-Alim. Occidente", 2),
                    Map.entry("A2-Alim. Oriente", 1))
            .flatMap(e -> Stream.generate(e::getKey).limit(e.getValue()))
            .toList();
    
    /* Franjas horarias, con mayor probabilidad en horas pico */        
    private static final List<Integer> MANANA = List.of(5, 6, 6, 7, 7, 7, 8, 8, 9);
    private static final List<Integer> MEDIODIA = List.of(11, 12, 12, 13, 14);
    private static final List<Integer> TARDE = List.of(16, 17, 17, 18, 18, 18, 19, 20);

    /* Genera todos los registros del día */
    public static List<Registro> generar(int cantidadUsuarios, long semilla) {
        return IntStream.range(0, cantidadUsuarios)
                .parallel()
                .boxed()
                .flatMap(i -> viajesDeUsuario(i, semilla))
                .sorted(Comparator.comparing(Registro::timestamp).thenComparing(Registro::idUsuario))
                .toList(); // lista inmutable
    }

    /* Viajes de un usuario, de 1 a 3 viajes */
    private static Stream<Registro> viajesDeUsuario(int indice, long semilla) {
        Random rnd = new Random(semilla * 31 + indice); // local: no hay estado compartido
        String id = String.format("U%06d", indice);
        int viajes = 1 + rnd.nextInt(3);
        List<List<Integer>> franjas = switch (viajes) {
            case 1 -> List.of(MANANA);
            case 2 -> List.of(MANANA, TARDE);
            default -> List.of(MANANA, MEDIODIA, TARDE);
        };
        return franjas.stream().flatMap(franja -> viaje(id, franja, rnd));
    }

    private static Stream<Registro> viaje(String id, List<Integer> franja, Random rnd) {
        String ruta = RUTAS_PONDERADAS.get(rnd.nextInt(RUTAS_PONDERADAS.size()));
        List<String> estaciones = RUTAS.get(ruta);
        int origen = rnd.nextInt(estaciones.size());
        int destino = (origen + 1 + rnd.nextInt(estaciones.size() - 1)) % estaciones.size();
        LocalDateTime inicio = FECHA.atTime(franja.get(rnd.nextInt(franja.size())), rnd.nextInt(60), rnd.nextInt(60));
        long minutos = Math.abs(destino - origen) * 6L + 2 + rnd.nextInt(10);
        return Stream.of(
                new Registro(id, ruta, estaciones.get(origen), Accion.ENTRADA, inicio),
                new Registro(id, ruta, estaciones.get(destino), Accion.SALIDA, inicio.plusMinutes(minutos)));
    }
}