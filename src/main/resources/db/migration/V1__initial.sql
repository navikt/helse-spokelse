CREATE TABLE vedtak
(
    id               SERIAL PRIMARY KEY,
    fodselsnummer    CHAR(11)  NOT NULL,
    gruppeId         CHAR(36)  NOT NULL,
    vedtaksperiodeId CHAR(36)  NOT NULL,
    opprettet        TIMESTAMP NOT NULL
);

CREATE TABLE utbetaling
(
    id        SERIAL PRIMARY KEY,
    vedtak_id INTEGER NOT NULL REFERENCES vedtak (id),
    fom       DATE    NOT NULL,
    tom       DATE    NOT NULL,
    grad      DECIMAL NOT NULL
);
