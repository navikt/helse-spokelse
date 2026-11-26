package no.nav.helse.spokelse.personoversikt

import no.nav.helse.spokelse.februar
import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.januar
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

// Januar 2018 starter på en mandag, nyttårsdag, og har 23 dager fra mandag til fredag.
internal class UtbetalingsdagerTest {
    @Test
    fun `teller dagene fra mandag til fredag, også helligdager`() {
        assertEquals(5, utbetalingsdager(1.januar, 7.januar))
        assertEquals(23, utbetalingsdager(1.januar, 31.januar))
    }

    @Test
    fun `feriedager inni perioden trekkes fra`() {
        assertEquals(18, utbetalingsdager(1.januar, 31.januar, lagFerie(8.januar, 12.januar)))
    }

    @Test
    fun `bare feriedager fra mandag til fredag innenfor perioden trekkes fra`() {
        // 26. januar er en fredag, og 29.–31. januar er mandag til onsdag
        assertEquals(19, utbetalingsdager(1.januar, 31.januar, lagFerie(26.januar, 4.februar)))
    }

    @Test
    fun `ferie for et annet sykefraværstilfelle eller en annen person trekkes ikke fra`() {
        val annenPerson = Personidentifikator(lagIdentitetsnummer())
        assertEquals(
            23,
            utbetalingsdager(
                1.januar,
                31.januar,
                lagFerie(8.januar, 12.januar, skjæringstidspunkt = 1.februar),
                lagFerie(15.januar, 19.januar, personidentifikator = annenPerson),
            ),
        )
    }

    private companion object {
        private val PERSON = Personidentifikator(lagIdentitetsnummer())
        private val SKJÆRINGSTIDSPUNKT = 1.januar

        private fun utbetalingsdager(
            fom: LocalDate,
            tom: LocalDate,
            vararg ferie: Infotrygdferie,
        ): Int = utbetalingsdager(PERSON, SKJÆRINGSTIDSPUNKT, fom, tom, ferie.toList())

        private fun lagFerie(
            fom: LocalDate,
            tom: LocalDate,
            personidentifikator: Personidentifikator = PERSON,
            skjæringstidspunkt: LocalDate = SKJÆRINGSTIDSPUNKT,
        ) = Infotrygdferie(personidentifikator, skjæringstidspunkt, fom, tom)
    }
}
