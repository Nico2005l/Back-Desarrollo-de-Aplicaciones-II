-- Transactional outbox of the events published on the topic planta-libre.eventos. An event is
-- written in the same transaction as the change it announces, so it exists if and only if that
-- change committed. RelayEventos publishes the pending ones with broker confirms and marks them;
-- with the broker down they wait here and go out when it is back.
CREATE TABLE eventos_salientes (
    id           BIGSERIAL    PRIMARY KEY,
    -- Fixed when written: a re-publication after a lost confirm carries the same id, and the
    -- subscribers discard the copy.
    mensaje_id   VARCHAR(36)  NOT NULL UNIQUE,
    exchange     VARCHAR(100) NOT NULL,
    routing_key  VARCHAR(100) NOT NULL,
    tipo         VARCHAR(100) NOT NULL,
    cuerpo       TEXT         NOT NULL,
    reserva_id   BIGINT,
    creado_el    TIMESTAMP    NOT NULL DEFAULT now(),
    publicado_el TIMESTAMP,
    intentos     INTEGER      NOT NULL DEFAULT 0,
    ultimo_error VARCHAR(300)
);

CREATE INDEX idx_eventos_salientes_pendientes ON eventos_salientes (id) WHERE publicado_el IS NULL;
