DROP TABLE utbetaling;
DROP TABLE vedtak;

CREATE TABLE hendelse
(
    dokument_id UUID PRIMARY KEY,
    hendelse_id UUID,
    type        VARCHAR
);

CREATE TABLE vedtak
(
    id                    SERIAL PRIMARY KEY,
    fodselsnummer         CHAR(11)  NOT NULL,
    orgnummer             CHAR(9)   NOT NULL,
    opprettet             TIMESTAMP NOT NULL,
    fom                   DATE      NOT NULL,
    tom                   DATE      NOT NULL,
    forbrukte_sykedager   INTEGER   NOT NULL,
    gjenstående_sykedager INTEGER   NOT NULL,
    sykmelding_id         UUID      NOT NULL references hendelse (dokument_id),
    soknad_id             UUID      NOT NULL references hendelse (dokument_id),
    inntektsmelding_id    UUID references hendelse (dokument_id)
);

CREATE TABLE oppdrag
(
    id          SERIAL PRIMARY KEY,
    vedtak_id   INTEGER NOT NULL REFERENCES vedtak (id),
    mottaker    VARCHAR,
    fagområde   VARCHAR,
    fagsystemId VARCHAR,
    totalbeløp  INTEGER
);


CREATE TABLE utbetaling
(
    id         SERIAL PRIMARY KEY,
    oppdrag_id INTEGER NOT NULL REFERENCES oppdrag (id),
    fom        DATE    NOT NULL,
    tom        DATE    NOT NULL,
    dagsats    INTEGER NOT NULL,
    grad       DECIMAL NOT NULL,
    belop      INTEGER NOT NULL,
    sykedager  INTEGER NOT NULL
);


