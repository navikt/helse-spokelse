DELETE FROM gamle_utbetalinger;
ALTER TABLE gamle_utbetalinger DROP COLUMN maksdato;
ALTER TABLE gamle_utbetalinger ADD COLUMN gjenstaende_sykedager INT;
