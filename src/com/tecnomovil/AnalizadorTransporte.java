package com.tecnomovil;

import com.tecnomovil.Registro.Accion;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/* 
Módulo de TecnoMovil
Con todas las operaciones siendo funciones puras:
Estas dependen solo de sus parámetros, no modifican la
lista recibida, ni usan estado compartido y devuelven
colecciones inmutables. 
*/

public final class AnalizadorTransporte {
    private AnalizadorTransporte() {    }

/* Crea un stram scuencial o paralelo sin alterar la colección original */
    public static final Predicate<Registro> ES_ENTRADA = r -> r.accion() == Accion.ENTRADA;
    public static final Predicate<Registro> TODOS = r -> true;
    public static final Function<Registro, Integer> HORA = r -> r.timestamp().getHour();

    private static <T> Stream<T> flujo (Collection<T> datos, boolean paralelo) {
        return paralelo ? datos.parallelStream() : datos.stream();
    }

/* Función de orden superior: esta recibe un filtro y una función de clasificación y devuelve el conteo agrupado. */    
public static <K extends Comparable<K>> Map<K, Long> contarPor(List<Registro> registros, boolean paralelo,
            Predicate<Registro> filtro, Function<Registro, K> clave) {
        return flujo(registros, paralelo)
                .filter(filtro)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(clave, TreeMap::new, Collectors.counting()),
                        Collections::unmodifiableMap));
    }

    /* Devuelve n elementos con mayor conteo */
    public static <K> List<Map.Entry<K, Long>> topN(Map<K, Long> conteos, int n) {
        return conteos.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue().reversed())
                .limit(n)
                .map(e -> Map.entry(e.getKey(), e.getValue()))
                .toList();
    }

    /* a) Afluencia según estación */
    /* Cantidad de usuarios que ingresan a cada estación */
    public static Map<String, Long> afluenciaPorEstacion(List<Registro> registros, boolean paralelo) {
        return flujo(registros, paralelo)
                .filter(ES_ENTRADA)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(Registro::estacion, TreeMap::new,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(Registro::idUsuario, Collectors.toSet()),
                                        usuarios -> (long) usuarios.size())),
                        Collections::unmodifiableMap));
    }
    /* b) Horas pico */
    /* Número de registros por hora del día. */
    public static Map<Integer, Long> flujoPorHora(List<Registro> registros, boolean paralelo) {
        return contarPor(registros, paralelo, TODOS, HORA);
    }

    public static List<Map.Entry<Integer, Long>> horasPico(List<Registro> registros, int n, boolean paralelo) {
        return topN(flujoPorHora(registros, paralelo), n);
    }

    /* c) Rutas más usadas */
    public static Map<String, Long> usoPorRuta(List<Registro> registros, boolean paralelo) {
        return contarPor(registros, paralelo, ES_ENTRADA, Registro::ruta);
    }

    public static List<Map.Entry<String, Long>> rutasMasUtilizadas(List<Registro> registros, int n, boolean paralelo) {
        return topN(usoPorRuta(registros, paralelo), n);
    }

        /* d) Patrones de viaje por usuario */
        /* Para cada usuario, las estaciones en el orden en que aparecen en los registros */
        public static Map<String, List<String>> patronesPorUsuario(List<Registro> registros, boolean paralelo) {
            return flujo(registros, paralelo)
                    .collect(Collectors.collectingAndThen(
                            Collectors.groupingBy(Registro::idUsuario, TreeMap::new,
                                    Collectors.mapping(Registro::estacion, Collectors.toUnmodifiableList())),
                            Collections::unmodifiableMap));
        }
    
        /* e) Tiempo promedio entre estaciones */
        /* Desplazamiento de un usuario entre dos estaciones consecutivas */
        public record Tramo(String origen, String destino, long minutos) {
            public String clave() { return origen + " -> " + destino; }
        }
    
        /* Convierte los registros de un usuario en tramos (entrada seguida de salida) */
        public static List<Tramo> tramos(List<Registro> registrosUsuario) {
            return IntStream.range(0, registrosUsuario.size() - 1)
                    .mapToObj(i -> Map.entry(registrosUsuario.get(i), registrosUsuario.get(i + 1)))
                    .filter(par -> ES_ENTRADA.test(par.getKey()) && !ES_ENTRADA.test(par.getValue()))
                    .map(par -> new Tramo(par.getKey().estacion(), par.getValue().estacion(),
                            Duration.between(par.getKey().timestamp(), par.getValue().timestamp()).toMinutes()))
                    .toList();
        }

    /* Todos los tramos del día, usuario por usuario */
    private static Stream<Tramo> todosLosTramos(List<Registro> registros, boolean paralelo) {
        Map<String, List<Registro>> porUsuario = flujo(registros, paralelo)
                .collect(Collectors.groupingBy(Registro::idUsuario));
        return flujo(porUsuario.values(), paralelo).flatMap(lista -> tramos(lista).stream());
    }

    /* Todos los tramos del día, usuario por usuaior */
    public static Map<String, Double> tiempoPromedioEntreEstaciones(List<Registro> registros, boolean paralelo) {
        return todosLosTramos(registros, paralelo)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(Tramo::clave, TreeMap::new, Collectors.averagingLong(Tramo::minutos)),
                        Collections::unmodifiableMap));
    }

    /* Tiempo promedio por cada par de estaciones de origen a destino */
    public static double tiempoPromedioGeneral(List<Registro> registros, boolean paralelo) {
        return todosLosTramos(registros, paralelo).mapToLong(Tramo::minutos).average().orElse(0);
    }

    /* f) Detección de sobrecarga */
    /* Estado de una ruta según ocupación */
    public record EstadoRuta(String ruta, int horaPico, long pasajerosHoraPico, boolean critica) {
        public String etiqueta() { return critica ? "CRITICA" : "NORMAL"; }
    }

    /* Ocupación simulada = pasajeros usando rutas en horas pico */
    /* Si se supera el umbral, esta se marca como crítica */
    public static List<EstadoRuta> detectarSobrecarga(List<Registro> registros, long umbral, boolean paralelo) {
        return flujo(registros, paralelo)
                .filter(ES_ENTRADA)
                .collect(Collectors.groupingBy(Registro::ruta, Collectors.groupingBy(HORA, Collectors.counting())))
                .entrySet().stream()
                .map(ruta -> ruta.getValue().entrySet().stream()
                        .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                        .map(pico -> new EstadoRuta(ruta.getKey(), pico.getKey(), pico.getValue(), pico.getValue() > umbral))
                        .orElseThrow())
                .sorted(Comparator.comparingLong(EstadoRuta::pasajerosHoraPico).reversed())
                .toList();
    }

}