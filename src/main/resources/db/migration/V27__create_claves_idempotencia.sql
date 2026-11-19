-- Idempotency-Key of the creation endpoints (POST). The first request with a key reserves it
-- (EN_CURSO); when it ends, its answer is stored (COMPLETA) and a repeated request with the same
-- key gets that same answer instead of creating the resource again. A key is valid for 24 hours.
CREATE TABLE claves_idempotencia (
    usuario        VARCHAR(255) NOT NULL,
    clave          VARCHAR(255) NOT NULL,
    metodo         VARCHAR(10)  NOT NULL,
    ruta           VARCHAR(255) NOT NULL,
    -- SHA-256 of method, path and body: the same key with another request is rejected (422).
    huella         CHAR(64)     NOT NULL,
    estado         VARCHAR(12)  NOT NULL,
    status_http    INTEGER,
    tipo_contenido VARCHAR(100),
    cuerpo         TEXT,
    creada_el      TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario, clave)
);

CREATE INDEX idx_claves_idempotencia_creada_el ON claves_idempotencia (creada_el);
