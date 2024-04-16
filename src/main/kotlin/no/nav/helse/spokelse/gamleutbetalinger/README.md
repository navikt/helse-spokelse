# gamle utbetalinger

Før Spøkelse begynte å lytte på `tbd.utbetaling` var Spøkelses kilde til data den interne rapiden. Det lagres ikke ny data her, men brukes som oppslag for konsumenter som skal ha data som strekker seg tilbake til tiden før Spøkelse gikk over til `tbd.utbetaling`

Om en gammel utbetaling annulleres så håndteres dette ved at det lagres i tabellen `alle_annulleringer` som brukes ved oppslag for gamle utbetalinger. Denne inneholder alt som lå i den gamle tabellen for annulleringer som lyttet på annulleringer fra rapiden, i tillegg til at den legger til nye annulleringer fra `tbd.utbetaling`.
