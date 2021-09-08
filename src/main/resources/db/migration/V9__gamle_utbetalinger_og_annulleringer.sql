CREATE TABLE gamle_utbetalinger
(
    fodselsnummer VARCHAR(11) NOT NULL,
    orgnummer     VARCHAR(32),
    opprettet     TIMESTAMP,
    fom           DATE        NOT NULL,
    tom           DATE        NOT NULL,
    grad          INT         NOT NULL,
    maksdato      DATE        NOT NULL,
    fagsystem_id  VARCHAR(32),
    fagomrade     VARCHAR(8)  NOT NULL
);
CREATE TABLE annullering
(
    fodselsnummer VARCHAR(11) NOT NULL,
    mottaker      VARCHAR(32),
    fagsystem_id  VARCHAR(32) NOT NULL UNIQUE,
    fom           DATE        NOT NULL,
    tom           DATE        NOT NULL,
    fagomrade     VARCHAR(8)
)
