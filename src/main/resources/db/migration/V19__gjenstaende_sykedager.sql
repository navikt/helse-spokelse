CREATE TABLE gjenstående_sykedager(
    korrelasjons_id        UUID PRIMARY KEY,
    fødselsnummer          VARCHAR(11) NOT NULL,
    fom                    DATE NOT NULL,
    tom                    DATE NOT NULL,
    gjenstående_sykedager  INTEGER NOT NULL,
    sendt                  TIMESTAMP NOT NULL,
    kilde                  INTEGER NOT NULL REFERENCES tbdUtbetaling_Melding(id),
    annullert_kilde        INTEGER REFERENCES tbdUtbetaling_Melding(id)
);
CREATE INDEX gjenstående_sykedager_fødselsnummer_idx ON gjenstående_sykedager(fødselsnummer);
