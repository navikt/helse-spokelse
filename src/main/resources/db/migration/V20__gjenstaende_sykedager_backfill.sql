INSERT INTO gjenstående_sykedager(korrelasjons_id, fødselsnummer, fom, tom, gjenstående_sykedager, sendt, kilde)
SELECT DISTINCT ON (melding->>'korrelasjonsId')
    (melding->>'korrelasjonsId')::UUID,
    fodselsnummer,
    (melding->>'fom')::DATE,
    (melding->>'tom')::DATE,
    (melding->>'gjenståendeSykedager')::INTEGER,
    sendt,
    id
FROM tbdUtbetaling_Melding
WHERE type IN ('utbetaling_utbetalt', 'utbetaling_uten_utbetaling')
AND melding->>'korrelasjonsId' IS NOT NULL
AND melding->>'fom' IS NOT NULL
AND melding->>'tom' IS NOT NULL
AND melding->>'gjenståendeSykedager' IS NOT NULL
ORDER BY melding->>'korrelasjonsId', sendt DESC, id DESC
ON CONFLICT (korrelasjons_id) DO NOTHING;

UPDATE gjenstående_sykedager SET annullert_kilde = annullering.id
FROM (
    SELECT DISTINCT ON (melding->>'korrelasjonsId') (melding->>'korrelasjonsId')::UUID AS korrelasjons_id, id, sendt
    FROM tbdUtbetaling_Melding
    WHERE type = 'utbetaling_annullert'
    AND melding->>'korrelasjonsId' IS NOT NULL
    ORDER BY melding->>'korrelasjonsId', sendt DESC, id DESC
) AS annullering
WHERE annullering.korrelasjons_id = gjenstående_sykedager.korrelasjons_id
AND annullering.sendt > gjenstående_sykedager.sendt;
