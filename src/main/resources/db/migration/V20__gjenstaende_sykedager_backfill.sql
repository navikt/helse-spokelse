-- Fremdrift for GjenståendeSykedagerBackfill, som fyller gjenstående_sykedager fra tbdUtbetaling_Melding i bakgrunnen.
CREATE TABLE gjenstående_sykedager_backfill(
    id        BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    fase      VARCHAR NOT NULL,
    siste_id  BIGINT NOT NULL
);
INSERT INTO gjenstående_sykedager_backfill(fase, siste_id) VALUES ('UTBETALINGER', 0);
