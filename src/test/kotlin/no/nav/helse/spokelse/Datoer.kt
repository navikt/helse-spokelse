package no.nav.helse.spokelse

import java.time.LocalDate
import java.time.Month

val Int.januar get() = LocalDate.of(2018, Month.JANUARY, this)
val Int.februar get() = LocalDate.of(2018, Month.FEBRUARY, this)
val Int.mars get() = LocalDate.of(2018, Month.MARCH, this)
val Int.april get() = LocalDate.of(2018, Month.APRIL, this)
val Int.mai get() = LocalDate.of(2018, Month.MAY, this)
