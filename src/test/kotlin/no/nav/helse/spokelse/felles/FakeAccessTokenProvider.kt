package no.nav.helse.spokelse.felles

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider

internal class FakeAccessTokenProvider : AccessTokenProvider {
    override fun machineToken(scope: String) = "token-for-$scope"

    override fun oboToken(
        accessToken: String,
        scope: String,
    ) = error("spøkelse bruker ikke OBO-token")
}
