package no.nav.helse.spokelse.personoversikt

import no.nav.helse.spokelse.felles.Personidentifikator
import no.nav.helse.spokelse.januar
import no.nav.helse.spokelse.personoversikt.Mottaker.ARBEIDSGIVER
import no.nav.helse.spokelse.personoversikt.Mottaker.PERSON
import no.nav.helse.spokelse.personoversikt.Versjon.INFOTRYGD_V1
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V1
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V2
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V3
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V4
import no.nav.helse.spokelse.personoversikt.Versjon.SPEIL_V5
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SykepengeperioderTest {
    @Test
    fun `én linje gir én periode med én yrkesaktivitet, én mottaker og én utbetalingsperiode`() {
        val perioder = listOf(lagLinje(fom = 1.januar, tom = 31.januar)).somSykepengeperioder()

        assertEquals(
            listOf(
                SpeilV4Periode(
                    fom = 1.januar,
                    tom = 31.januar,
                    yrkesaktiviteter =
                        listOf(
                            Yrkesaktivitet(ORGNR_A, listOf(Mottakerutbetalinger(ARBEIDSGIVER, listOf(Utbetalingsperiode(1.januar, 31.januar, 100))))),
                        ),
                ),
            ),
            perioder,
        )
    }

    @Test
    fun `opphold på en hverdag gir to perioder`() {
        // 3. januar 2018 er en onsdag
        val perioder = listOf(lagLinje(1.januar, 2.januar), lagLinje(4.januar, 10.januar)).somSykepengeperioder()

        assertEquals(listOf(1.januar to 2.januar, 4.januar to 10.januar), perioder.map { it.fom to it.tom })
    }

    @Test
    fun `helg teller ikke som opphold`() {
        // 5. januar 2018 er en fredag, og 8. januar er en mandag
        val perioder = listOf(lagLinje(1.januar, 5.januar), lagLinje(8.januar, 12.januar)).somSykepengeperioder()

        assertEquals(listOf(1.januar to 12.januar), perioder.map { it.fom to it.tom })
    }

    @Test
    fun `arbeidsgivere og mottakere i samme tidsrom havner i samme periode, i fast rekkefølge`() {
        val perioder =
            listOf(
                lagLinje(1.januar, 31.januar, organisasjonsnummer = null, mottaker = PERSON),
                lagLinje(1.januar, 31.januar, organisasjonsnummer = ORGNR_B, mottaker = PERSON),
                lagLinje(15.januar, 31.januar, organisasjonsnummer = ORGNR_A, mottaker = PERSON),
                lagLinje(1.januar, 31.januar, organisasjonsnummer = ORGNR_A, mottaker = ARBEIDSGIVER),
            ).somSykepengeperioder()

        assertEquals(
            listOf(
                SpeilV4Periode(
                    fom = 1.januar,
                    tom = 31.januar,
                    yrkesaktiviteter =
                        listOf(
                            Yrkesaktivitet(
                                ORGNR_A,
                                listOf(
                                    Mottakerutbetalinger(ARBEIDSGIVER, listOf(Utbetalingsperiode(1.januar, 31.januar, 100))),
                                    Mottakerutbetalinger(PERSON, listOf(Utbetalingsperiode(15.januar, 31.januar, 100))),
                                ),
                            ),
                            Yrkesaktivitet(ORGNR_B, listOf(Mottakerutbetalinger(PERSON, listOf(Utbetalingsperiode(1.januar, 31.januar, 100))))),
                            Yrkesaktivitet(null, listOf(Mottakerutbetalinger(PERSON, listOf(Utbetalingsperiode(1.januar, 31.januar, 100))))),
                        ),
                ),
            ),
            perioder,
        )
    }

    @Test
    fun `linjer fra ulike versjoner havner i ulike perioder, også når de henger sammen`() {
        val perioder =
            listOf(
                lagLinje(16.januar, 31.januar, versjon = SPEIL_V4),
                lagLinje(1.januar, 15.januar, versjon = SPEIL_V3),
            ).somSykepengeperioder()

        assertEquals(listOf(Triple(SPEIL_V3, 1.januar, 15.januar), Triple(SPEIL_V4, 16.januar, 31.januar)), perioder.map { Triple(it.versjon, it.fom, it.tom) })
    }

    @Test
    fun `linjer med skjæringstidspunkt gir én periode per tilfelle, også med opphold i utbetalingen`() {
        // 11. januar 2018 er en torsdag
        val perioder =
            listOf(
                lagLinje(1.januar, 10.januar, versjon = SPEIL_V5),
                lagLinje(15.januar, 31.januar, versjon = SPEIL_V5, organisasjonsnummer = ORGNR_B),
            ).somSykepengeperioder()

        assertEquals(
            listOf(
                SpeilV5Periode(
                    skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                    fom = 1.januar,
                    tom = 31.januar,
                    yrkesaktiviteter =
                        listOf(
                            Yrkesaktivitet(ORGNR_A, listOf(Mottakerutbetalinger(ARBEIDSGIVER, listOf(Utbetalingsperiode(1.januar, 10.januar, 100))))),
                            Yrkesaktivitet(ORGNR_B, listOf(Mottakerutbetalinger(ARBEIDSGIVER, listOf(Utbetalingsperiode(15.januar, 31.januar, 100))))),
                        ),
                ),
            ),
            perioder,
        )
    }

    @Test
    fun `to tilfeller som henger sammen i tid, gir to perioder`() {
        val perioder =
            listOf(
                lagLinje(16.januar, 31.januar, versjon = INFOTRYGD_V1, skjæringstidspunkt = 16.januar),
                lagLinje(1.januar, 15.januar, versjon = INFOTRYGD_V1, skjæringstidspunkt = 1.januar),
            ).somSykepengeperioder()

        assertEquals(
            listOf(Triple(1.januar, 1.januar, 15.januar), Triple(16.januar, 16.januar, 31.januar)),
            perioder.map { (it as InfotrygdV1Periode).let { periode -> Triple(periode.skjæringstidspunkt, periode.fom, periode.tom) } },
        )
    }

    @Test
    fun `utbetalingsperioder hos samme mottaker slås sammen når de henger sammen og har samme grad`() {
        val perioder =
            listOf(
                lagLinje(1.januar, 10.januar, grad = 100),
                lagLinje(11.januar, 20.januar, grad = 100),
                lagLinje(21.januar, 25.januar, grad = 50),
            ).somSykepengeperioder()

        assertEquals(
            listOf(Utbetalingsperiode(1.januar, 20.januar, 100), Utbetalingsperiode(21.januar, 25.januar, 50)),
            (perioder.single() as SpeilV4Periode)
                .yrkesaktiviteter
                .single()
                .mottakere
                .single()
                .utbetalingsperioder,
        )
    }

    @Test
    fun `utbetalingsperioder i Infotrygd slås bare sammen når også usikkerGrad er lik`() {
        val perioder =
            listOf(
                lagLinje(1.januar, 10.januar, versjon = INFOTRYGD_V1),
                lagLinje(11.januar, 20.januar, versjon = INFOTRYGD_V1),
                lagLinje(21.januar, 25.januar, versjon = INFOTRYGD_V1, usikkerGrad = true),
            ).somSykepengeperioder()

        assertEquals(
            listOf(InfotrygdUtbetalingsperiode(1.januar, 20.januar, 100, false), InfotrygdUtbetalingsperiode(21.januar, 25.januar, 100, true)),
            (perioder.single() as InfotrygdV1Periode)
                .yrkesaktiviteter
                .single()
                .mottakere
                .single()
                .utbetalingsperioder,
        )
    }

    @Test
    fun `SPEIL_V2 har én oppføring for utbetalingene, uten mottaker`() {
        val perioder = listOf(lagLinje(1.januar, 31.januar, versjon = SPEIL_V2)).somSykepengeperioder()

        assertEquals(
            listOf(SpeilV2Periode(1.januar, 31.januar, listOf(Yrkesaktivitet(ORGNR_A, listOf(UtbetalingerUtenKjentMottaker(listOf(Utbetalingsperiode(1.januar, 31.januar, 100)))))))),
            perioder,
        )
    }

    @Test
    fun `en linje som finnes i flere kilder i Speil er bare med fra kilden som rangeres høyest`() {
        fun versjonerEtterDuplikater(vararg versjoner: Versjon) = versjoner.map { lagLinje(1.januar, 31.januar, versjon = it) }.somSykepengeperioder().map { it.versjon }

        assertEquals(listOf(SPEIL_V4), versjonerEtterDuplikater(SPEIL_V1, SPEIL_V2, SPEIL_V3, SPEIL_V4))
        assertEquals(listOf(SPEIL_V3), versjonerEtterDuplikater(SPEIL_V1, SPEIL_V2, SPEIL_V3))
        assertEquals(listOf(SPEIL_V2), versjonerEtterDuplikater(SPEIL_V2, SPEIL_V1))
    }

    @Test
    fun `linjer til arbeidsgiver og person i samme versjon er ikke duplikater, selv om de er like`() {
        val perioder =
            listOf(
                lagLinje(1.januar, 31.januar, versjon = SPEIL_V4, mottaker = ARBEIDSGIVER),
                lagLinje(1.januar, 31.januar, versjon = SPEIL_V4, mottaker = PERSON),
                lagLinje(1.januar, 31.januar, versjon = SPEIL_V3, mottaker = PERSON),
            ).somSykepengeperioder()

        assertEquals(listOf(SPEIL_V4), perioder.map { it.versjon })
        assertEquals(
            listOf(ARBEIDSGIVER, PERSON),
            (perioder.single() as SpeilV4Periode)
                .yrkesaktiviteter
                .single()
                .mottakere
                .map { it.mottaker },
        )
    }

    @Test
    fun `linjer som bare delvis overlapper, eller som er like i Infotrygd og Speil, er ikke duplikater`() {
        val perioder =
            listOf(
                lagLinje(1.januar, 31.januar, versjon = SPEIL_V3),
                lagLinje(1.januar, 30.januar, versjon = SPEIL_V4),
                lagLinje(1.januar, 30.januar, versjon = INFOTRYGD_V1),
            ).somSykepengeperioder()

        assertEquals(listOf(SPEIL_V3, SPEIL_V4, INFOTRYGD_V1), perioder.map { it.versjon })
    }

    private companion object {
        private val PERSONIDENTIFIKATOR = Personidentifikator(lagIdentitetsnummer())
        private const val ORGNR_A = "111111111"
        private const val ORGNR_B = "222222222"
        private val SKJÆRINGSTIDSPUNKT = 1.januar

        private fun lagLinje(
            fom: LocalDate,
            tom: LocalDate,
            versjon: Versjon = SPEIL_V4,
            organisasjonsnummer: String? = ORGNR_A,
            mottaker: Mottaker = ARBEIDSGIVER,
            grad: Int = 100,
            usikkerGrad: Boolean = false,
            skjæringstidspunkt: LocalDate = SKJÆRINGSTIDSPUNKT,
        ): UtbetaltLinje =
            when (versjon) {
                SPEIL_V1 -> SpeilV1Linje(PERSONIDENTIFIKATOR, organisasjonsnummer, mottaker, fom, tom, grad)
                SPEIL_V2 -> SpeilV2Linje(PERSONIDENTIFIKATOR, organisasjonsnummer, fom, tom, grad)
                SPEIL_V3 -> SpeilV3Linje(PERSONIDENTIFIKATOR, organisasjonsnummer, mottaker, fom, tom, grad)
                SPEIL_V4 -> SpeilV4Linje(PERSONIDENTIFIKATOR, organisasjonsnummer, mottaker, fom, tom, grad)
                SPEIL_V5 -> SpeilV5Linje(PERSONIDENTIFIKATOR, organisasjonsnummer, mottaker, fom, tom, grad, skjæringstidspunkt)
                INFOTRYGD_V1 -> InfotrygdV1Linje(PERSONIDENTIFIKATOR, organisasjonsnummer, mottaker, fom, tom, grad, usikkerGrad, skjæringstidspunkt)
            }
    }
}
