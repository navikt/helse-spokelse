UPDATE tbdUtbetaling_Utbetaling SET (organisasjonsnummer) = (
    SELECT melding->>'organisasjonsnummer' AS organisasjonsnummer
    FROM tbdUtbetaling_Melding
    WHERE tbdUtbetaling_Melding.id = tbdUtbetaling_Utbetaling.kilde
);
