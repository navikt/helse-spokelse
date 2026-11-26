-- TbdUtbetalingslinjeBackfill er ferdig i prod, og alle linjer har dagsats og totalbelop.
DROP TABLE tbd_utbetalingslinje_backfill;
ALTER TABLE tbdUtbetaling_Utbetalingslinje ALTER COLUMN dagsats SET NOT NULL;
ALTER TABLE tbdUtbetaling_Utbetalingslinje ALTER COLUMN totalbelop SET NOT NULL;
