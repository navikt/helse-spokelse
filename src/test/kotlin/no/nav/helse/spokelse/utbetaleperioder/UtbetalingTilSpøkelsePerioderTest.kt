package no.nav.helse.spokelse.utbetaleperioder

import no.nav.helse.spokelse.april
import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.januar
import no.nav.helse.spokelse.mars
import no.nav.helse.spokelse.tbdutbetaling.Oppdrag
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling
import no.nav.helse.spokelse.tbdutbetaling.Utbetaling.Companion.somSpøkelsePerioder
import no.nav.helse.spokelse.tbdutbetaling.Utbetalingslinje
import no.nav.helse.spokelse.utbetalteperioder.Personidentifikator
import no.nav.helse.spokelse.utbetalteperioder.SpøkelsePeriode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class UtbetalingTilSpøkelsePerioderTest {

    @Test
    fun `endring i refusjon`() {
        val personidentifikator = Personidentifikator("11111111111")
        val organisasjonsnummer = "111111111"

        val utbetaling = Utbetaling(
            fødselsnummer = "$personidentifikator",
            organisasjonsnummer = organisasjonsnummer,
            korrelasjonsId = UUID.randomUUID(),
            gjenståendeSykedager = 1,
            sistUtbetalt = LocalDateTime.now(),
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "ARBEIDSGIVER",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(17.januar, 31.januar, 100.0),
                    Utbetalingslinje(1.mars, 31.mars, 100.0)
                )
            ),
            personOppdrag = Oppdrag(
                fagsystemId = "PERSON",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(1.februar, 28.februar, 100.0),
                    Utbetalingslinje(1.mars, 31.mars, 100.0),
                    Utbetalingslinje(1.april, 30.april, 100.0)
                )
            )
        )


        assertEquals(
            listOf(
                SpøkelsePeriode(personidentifikator, 17.januar, 31.januar, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling")),
                SpøkelsePeriode(personidentifikator, 1.mars, 31.mars, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling")),
                SpøkelsePeriode(personidentifikator, 1.februar, 28.februar, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling")),
                SpøkelsePeriode(personidentifikator, 1.mars, 31.mars, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling")),
                SpøkelsePeriode(personidentifikator, 1.april, 30.april, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling")),
            ),
            listOf(utbetaling).somSpøkelsePerioder()
        )
    }

    @Test
    fun `forskjellig refusjon før og etter kort gap`() {
        val personidentifikator = Personidentifikator("11111111111")
        val organisasjonsnummer = "111111111"

        val utbetaling = Utbetaling(
            fødselsnummer = "$personidentifikator",
            organisasjonsnummer = organisasjonsnummer,
            korrelasjonsId = UUID.randomUUID(),
            gjenståendeSykedager = 1,
            sistUtbetalt = LocalDateTime.now(),
            arbeidsgiverOppdrag = Oppdrag(
                fagsystemId = "ARBEIDSGIVER",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(17.januar, 31.januar, 100.0)
                )
            ),
            personOppdrag = Oppdrag(
                fagsystemId = "PERSON",
                utbetalingslinjer = listOf(
                    Utbetalingslinje(10.februar, 28.februar, 100.0)
                )
            )
        )

        assertEquals(
            listOf(
                SpøkelsePeriode(personidentifikator, 17.januar, 31.januar, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling")),
                SpøkelsePeriode(personidentifikator, 10.februar, 28.februar, 100, organisasjonsnummer, setOf("Spleis", "TbdUtbetaling"))
            ),
            listOf(utbetaling).somSpøkelsePerioder()
        )
    }
}
