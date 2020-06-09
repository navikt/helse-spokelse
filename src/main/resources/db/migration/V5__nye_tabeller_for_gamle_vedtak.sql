CREATE TABLE old_vedtak
(
    id                    SERIAL PRIMARY KEY,
    vedtaksperiode_id     UUID      NOT NULL,
    fodselsnummer         CHAR(11)  NOT NULL,
    orgnummer             CHAR(9)   NOT NULL,
    opprettet             TIMESTAMP NOT NULL,
    forbrukte_sykedager   INTEGER   NOT NULL,
    gjenstående_sykedager INTEGER,
    sykmelding_id         UUID      NOT NULL,
    soknad_id             UUID      NOT NULL,
    inntektsmelding_id    UUID
);

CREATE TABLE old_utbetaling
(
    id        SERIAL PRIMARY KEY,
    vedtak_id INTEGER NOT NULL REFERENCES old_vedtak (id),
    fom       DATE    NOT NULL,
    tom       DATE    NOT NULL,
    grad      DECIMAL NOT NULL
);

CREATE TABLE vedtak_utbetalingsref
(
    vedtaksperiode_id UUID     NOT NULL,
    utbetalingsref    CHAR(26) NOT NULL,
    PRIMARY KEY (vedtaksperiode_id, utbetalingsref)
);
