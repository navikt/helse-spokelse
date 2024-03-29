CREATE TABLE tbdUtbetaling_Melding(
    id                          SERIAL PRIMARY KEY,
    melding                     JSONB NOT NULL,
    sendt                       TIMESTAMP NOT NULL,
    type                        VARCHAR NOT NULL,
    fodselsnummer               VARCHAR(11) NOT NULL
);

CREATE TABLE tbdUtbetaling_Utbetaling(
    korrelasjonsId               UUID NOT NULL PRIMARY KEY,
    kilde                        INTEGER NOT NULL REFERENCES tbdUtbetaling_Melding (id),
    personAnnuleringskilde       INTEGER REFERENCES tbdUtbetaling_Melding (id),
    arbeidsgiverAnnuleringskilde INTEGER REFERENCES tbdUtbetaling_Melding (id),
    fodselsnummer                VARCHAR(11) NOT NULL,
    gjenstaaendeSykedager        INT NOT NULL,
    arbeidsgiverFagsystemId      VARCHAR,
    personFagsystemId            VARCHAR,
    sistUtbetalt                 TIMESTAMP NOT NULL
);

CREATE TABLE tbdUtbetaling_Utbetalingslinje(
    kilde                       INTEGER NOT NULL REFERENCES tbdUtbetaling_Melding (id),
    fagsystemId                 VARCHAR NOT NULL,
    fom                         DATE NOT NULL,
    tom                         DATE NOT NULL,
    grad                        DECIMAL NOT NULL,
    utbetaling                  UUID NOT NULL REFERENCES tbdUtbetaling_Utbetaling(korrelasjonsId) ON DELETE CASCADE
);

CREATE INDEX tbdUtbetaling_Utbetaling_fodselsnummer_idx ON tbdUtbetaling_Utbetaling (fodselsnummer);
CREATE INDEX tbdUtbetaling_Utbetalingslinje_fagsystemId_idx ON tbdUtbetaling_Utbetalingslinje (fagsystemId);
CREATE INDEX tbdUtbetaling_Melding_fodselsnummer_idx ON tbdUtbetaling_Melding (fodselsnummer);
