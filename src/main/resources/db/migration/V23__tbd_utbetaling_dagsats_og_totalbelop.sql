-- Fylles inn for eksisterende linjer av TbdUtbetalingslinjeBackfill, og blir NOT NULL når den er ferdig.
ALTER TABLE tbdUtbetaling_Utbetalingslinje ADD COLUMN dagsats NUMERIC(19, 2);
ALTER TABLE tbdUtbetaling_Utbetalingslinje ADD COLUMN totalbelop NUMERIC(19, 2);
