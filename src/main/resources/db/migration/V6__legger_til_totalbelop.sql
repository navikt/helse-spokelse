DELETE
FROM utbetaling;
DELETE
FROM oppdrag;
DELETE
FROM vedtak;
DELETE
FROM old_utbetaling;
DELETE
FROM old_vedtak;

ALTER TABLE old_utbetaling
    ADD dagsats INTEGER NOT NULL;
ALTER TABLE old_utbetaling
    ADD belop INTEGER NOT NULL;
ALTER TABLE old_utbetaling
    ADD totalbelop INTEGER NOT NULL;
