package com.daii.backdaii.mensajeria;

/** Names of the broker objects. Kept in one place so the producer and the consumer cannot drift. */
public final class Colas {

    /** Direct exchange of the bookings domain: each routing key reaches exactly one queue. */
    public static final String EXCHANGE_RESERVAS = "planta-libre.reservas";
    public static final String COBRO_SOLICITADO = "cobro.solicitado";

    /** Point-to-point queue: every charge request is processed once, by one consumer. */
    public static final String COBROS = "planta-libre.cobros";

    /** Where a request lands after its retries run out, to be inspected by hand. */
    public static final String EXCHANGE_DLX = "planta-libre.dlx";
    public static final String COBROS_DLQ = "planta-libre.cobros.dlq";

    /**
     * Topic exchange of the domain events: one message, a copy for every queue whose pattern
     * matches its key. The publisher does not know who listens.
     */
    public static final String EXCHANGE_EVENTOS = "planta-libre.eventos";
    public static final String RESERVA_CONFIRMADA = "reserva.confirmada";
    /** Every booking event, whatever happened to it: what the audit subscribes to. */
    public static final String TODO_RESERVA = "reserva.*";

    /** One queue per subscriber of reserva.confirmada, each with its own dead-letter queue. */
    public static final String AVISOS = "planta-libre.avisos.reserva-confirmada";
    public static final String CONTROL_ACCESO = "planta-libre.control-acceso.reserva-confirmada";
    public static final String AUDITORIA = "planta-libre.auditoria.reservas";

    /** Every dead-letter queue, for MonitorDlq. */
    public static final java.util.List<String> DLQS = java.util.List.of(COBROS_DLQ, dlq(AVISOS), dlq(CONTROL_ACCESO), dlq(AUDITORIA));

    public static String dlq(String cola) {
        return cola + ".dlq";
    }

    private Colas() {
    }
}
