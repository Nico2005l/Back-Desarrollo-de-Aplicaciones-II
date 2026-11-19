-- Version of the resources that can be updated through the API, served as their ETag. The
-- database increments it on every UPDATE, whoever makes it, so no write path can forget it. An
-- update with If-Match locks the row, compares the version and answers 412 if it changed.
ALTER TABLE reservas    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE medios_pago ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuarios    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE FUNCTION incrementar_version() RETURNS trigger AS $$
BEGIN
    NEW.version := OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservas_version    BEFORE UPDATE ON reservas    FOR EACH ROW EXECUTE FUNCTION incrementar_version();
CREATE TRIGGER medios_pago_version BEFORE UPDATE ON medios_pago FOR EACH ROW EXECUTE FUNCTION incrementar_version();
CREATE TRIGGER usuarios_version    BEFORE UPDATE ON usuarios    FOR EACH ROW EXECUTE FUNCTION incrementar_version();
