package no.nav.helse.spokelse

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import tools.jackson.databind.SerializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.nio.file.Files
import java.nio.file.Path

internal class OpenApiTest : AbstractE2ETest() {
    @Test
    fun `Swagger UI er tilgjengelig uten token`() {
        val html = assertApiRequest(path = "swagger", httpMethod = "GET", rolle = null, app = null)
        assertTrue(html.contains("swagger-ui"))
    }

    @Test
    fun `OpenAPI-specen er tilgjengelig uten token og lik snapshoten`() {
        val generert = assertApiRequest(path = "openapi.json", httpMethod = "GET", rolle = null, app = null)
        val formatert = mapper.writeValueAsString(mapper.readTree(generert)) + "\n"

        if (System.getProperty(OPPDATER_SNAPSHOT).toBoolean()) {
            Files.createDirectories(snapshot.parent)
            Files.writeString(snapshot, formatert)
        }

        assertTrue(Files.exists(snapshot)) { "Fant ikke $snapshot. $OPPDATER" }
        try {
            JSONAssert.assertEquals(Files.readString(snapshot), formatert, true)
        } catch (err: AssertionError) {
            throw AssertionError("OpenAPI-specen er endret. Se over endringen, og oppdater snapshoten. $OPPDATER\n${err.message}", err)
        }
    }

    private companion object {
        private const val OPPDATER_SNAPSHOT = "oppdaterOpenApiSnapshot"
        private const val OPPDATER = "Kjør ./gradlew test --tests '*OpenApiTest*' -D$OPPDATER_SNAPSHOT=true for å oppdatere."
        private val snapshot = Path.of("src/test/resources/openapi/spokelse.json")
        private val mapper = jacksonMapperBuilder().enable(SerializationFeature.INDENT_OUTPUT).build()
    }
}
