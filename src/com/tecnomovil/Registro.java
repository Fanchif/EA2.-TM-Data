package com.tecnomovil;

import java.time.LocalDateTime;
import java.util.Objects;


public record Registro(String idUsuario, String ruta, String estacion, Accion accion, LocalDateTime timestamp) {

    public enum Accion {ENTRADA, SALIDA}
    public Registro {
        Objects.requireNonNull(idUsuario, "idUsuaio");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(estacion, "estacion");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}