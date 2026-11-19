package com.daii.backdaii.mensajeria;

/** Where the outbox relay sends an event. Returns only once the broker confirmed it; otherwise it throws. */
public interface CanalEventos {

    void publicar(EventoSaliente evento);
}
