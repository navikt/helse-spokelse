CREATE TABLE tbdUtbetaling_Melding(
    id                          SERIAL PRIMARY KEY,
    melding                     JSONB NOT NULL,
    tidspunkt                   TIMESTAMP NOT NULL
);

CREATE TABLE tbdUtbetaling_Utbetaling(
    id                          SERIAL PRIMARY KEY,
    kilde                       INTEGER NOT NULL REFERENCES tbdUtbetaling_Melding (id),
    fodselsnummer               VARCHAR(11) NOT NULL,
    gjenstaaendeSykedager       INT NOT NULL,
    arbeidsgiverFagsystemId     VARCHAR,
    personFagsystemId           VARCHAR
);

CREATE TABLE tbdUtbetaling_Utbetalingslinje(
    kilde                       INTEGER NOT NULL REFERENCES tbdUtbetaling_Melding (id),
    fagsystemId                 VARCHAR NOT NULL,
    fom                         DATE NOT NULL,
    tom                         DATE NOT NULL,
    grad                        DECIMAL NOT NULL
);
