ALTER TABLE vedtak
    DROP CONSTRAINT vedtak_sykmelding_id_fkey;
ALTER TABLE vedtak
    DROP CONSTRAINT vedtak_soknad_id_fkey;
ALTER TABLE vedtak
    DROP CONSTRAINT vedtak_inntektsmelding_id_fkey;
ALTER TABLE hendelse
    DROP CONSTRAINT hendelse_pkey;
ALTER TABLE hendelse
    ADD PRIMARY KEY (dokument_id, hendelse_id, type);
