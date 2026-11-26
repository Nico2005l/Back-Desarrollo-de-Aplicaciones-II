package com.daii.backdaii.domain.disponibilidad;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** Opening hours of a building: the same every weekday; buildings are closed on weekends. */
public record Horario(LocalTime apertura, LocalTime cierre) {

    private static final Set<DayOfWeek> FIN_DE_SEMANA = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    public boolean abre(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return !FIN_DE_SEMANA.contains(dia);
    }

    /** The first day, from {@code fecha} on, the building opens. */
    public LocalDate proximoDiaHabil(LocalDate fecha) {
        LocalDate dia = fecha;
        while (!abre(dia)) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    public LocalDateTime aperturaEn(LocalDate fecha) {
        return fecha.atTime(apertura);
    }

    public LocalDateTime cierreEn(LocalDate fecha) {
        return fecha.atTime(cierre);
    }

    public Franja jornada(LocalDate fecha) {
        return new Franja(aperturaEn(fecha), cierreEn(fecha));
    }

    /** True when the slot starts and ends inside the same day's opening hours. */
    public boolean contiene(Franja franja) {
        LocalDate fecha = franja.inicio().toLocalDate();
        return abre(fecha) && franja.fin().toLocalDate().equals(fecha) && jornada(fecha).contiene(franja);
    }
}
