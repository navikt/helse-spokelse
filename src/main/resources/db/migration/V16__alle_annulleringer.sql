CREATE TABLE alle_annulleringer(
  fagsystem_id  VARCHAR PRIMARY KEY
);

INSERT INTO alle_annulleringer (fagsystem_id)
SELECT fagsystem_id FROM annullering;
