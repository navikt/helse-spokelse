CREATE INDEX oppdrag_vedtak_id_idx ON oppdrag (vedtak_id);
CREATE INDEX utbetaling_oppdrag_id_idx ON utbetaling (oppdrag_id);
CREATE INDEX old_utbetaling_vedtak_id_idx ON old_utbetaling (vedtak_id);
