DELETE
FROM utbetaling;
DELETE
FROM oppdrag;
DELETE
FROM vedtak;

ALTER TABLE vedtak
    ADD hendelse_id UUID NOT NULL;
