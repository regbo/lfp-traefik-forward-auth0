package dniel.forwardauth.application

import com.auth0.jwt.interfaces.Claim
import dniel.forwardauth.AuthProperties
import dniel.forwardauth.domain.authorize.service.Authenticator
import dniel.forwardauth.domain.authorize.service.AuthenticatorStateMachine
import dniel.forwardauth.domain.shared.JwtToken
import dniel.forwardauth.domain.shared.Token
import dniel.forwardauth.domain.shared.VerifyTokenService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AuthenticateHandler(val properties: AuthProperties,
                          val verifyTokenService: VerifyTokenService) : CommandHandler<AuthenticateHandler.AuthenticateCommand> {

    private val LOGGER = LoggerFactory.getLogger(this::class.java)

    /**
     * This is the input parameter object for the handler to pass inn all
     * needed parameters to the handler.
     */
    data class AuthenticateCommand(val accessToken: String?,
                                   val idToken: String?,
                                   val host: String
    ) : Command


    /**
     * This command can produce a set of events as response from the handle method.
     */
    sealed class AuthentiationEvent : Event {
        class AuthenticatedEvent(val accessToken: Token, val idToken: Token,
                                 val userinfo: Map<String, String>) : AuthentiationEvent()

        class AnonymousUserEvent() : AuthentiationEvent()
        class Error(error: Authenticator.Error?) : AuthentiationEvent() {
            val reason: String = error?.message ?: "Unknown error"
        }
    }

    /**
     * Main handle method.
     */
    override fun handle(params: AuthenticateHandler.AuthenticateCommand): Event {
        val app = properties.findApplicationOrDefault(params.host)
        val accessToken = verifyTokenService.verify(params.accessToken, app.audience)
        val idToken = verifyTokenService.verify(params.idToken, app.clientId)

        val authenticator = Authenticator.create(accessToken, idToken, app)
        val (state, error) = authenticator.authenticate()

        LOGGER.debug("Authenticator State: ${state}")
        LOGGER.debug("Authenticator Error: ${error}")

        return when (state) {
            AuthenticatorStateMachine.State.ANONYMOUS -> AuthentiationEvent.AnonymousUserEvent()
            AuthenticatorStateMachine.State.AUTHENTICATED -> AuthentiationEvent.AuthenticatedEvent(accessToken, idToken, getUserinfoFromToken(app, idToken as JwtToken))
            else -> AuthenticateHandler.AuthentiationEvent.Error(error)
        }
    }

    private fun getUserinfoFromToken(app: AuthProperties.Application, token: JwtToken): Map<String, String> {
        app.claims.forEach { s -> LOGGER.trace("Should add Claim from token: ${s}") }
        return token.value.claims
                .onEach { entry: Map.Entry<String, Claim> -> LOGGER.trace("Token Claim ${entry.key}=${getClaimValue(entry.value)}") }
                .filterKeys { app.claims.contains(it) }
                .onEach { entry: Map.Entry<String, Claim> -> LOGGER.trace("Filtered claim ${entry.key}=${getClaimValue(entry.value)}") }
                .mapValues { getClaimValue(it.value) }
                .filterValues { it != null } as Map<String, String>
    }

    private fun getClaimValue(claim: Claim): String? {
        return when {
            claim.asArray(String::class.java) != null -> claim.asArray(String::class.java).joinToString()
            claim.asBoolean() != null -> claim.asBoolean().toString()
            claim.asString() != null -> claim.asString().toString()
            claim.asLong() != null -> claim.asLong().toString()
            else -> null
        }
    }
}