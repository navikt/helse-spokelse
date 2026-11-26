CREATE TABLE tbd_vedtak(
    id                       BIGSERIAL PRIMARY KEY,
    fødselsnummer            VARCHAR(11) NOT NULL,
    organisasjonsnummer      VARCHAR(9),
    fom                      DATE NOT NULL,
    tom                      DATE NOT NULL,
    skjæringstidspunkt       DATE NOT NULL,
    vedtak_fattet_tidspunkt  TIMESTAMPTZ NOT NULL,
    melding                  JSONB NOT NULL,
    UNIQUE NULLS NOT DISTINCT (fødselsnummer, organisasjonsnummer, fom, tom, vedtak_fattet_tidspunkt)
);
