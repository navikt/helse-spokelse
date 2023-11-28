ALTER TABLE tbdUtbetaling_Utbetaling ADD COLUMN arbeidsgiverMottaker VARCHAR;

UPDATE tbdUtbetaling_Utbetaling SET (arbeidsgiverMottaker) = (
    SELECT melding->'arbeidsgiverOppdrag'->>'mottaker' AS arbeidsgiverMottaker
    FROM tbdUtbetaling_Melding
    WHERE tbdUtbetaling_Melding.id = tbdUtbetaling_Utbetaling.kilde
);

ALTER TABLE tbdUtbetaling_Utbetaling ALTER COLUMN arbeidsgiverMottaker SET NOT NULL;
