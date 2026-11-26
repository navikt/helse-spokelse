-- Fremdrift for TbdUtbetalingslinjeBackfill, som fyller dagsats og totalbelop fra tbdUtbetaling_Melding i bakgrunnen.
CREATE TABLE tbd_utbetalingslinje_backfill(
    id        BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    ferdig    BOOLEAN NOT NULL,
    siste_id  BIGINT NOT NULL
);
INSERT INTO tbd_utbetalingslinje_backfill(ferdig, siste_id) VALUES (false, 0);
