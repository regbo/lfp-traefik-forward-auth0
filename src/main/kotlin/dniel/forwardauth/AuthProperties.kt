package dniel.forwardauth

import dniel.forwardauth.domain.shared.Application
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.util.*
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

@Validated
@ConfigurationProperties()
abstract class AuthProperties {
    @NotEmpty
    lateinit var domain: String

    @NotEmpty
    lateinit var tokenEndpoint: String

    @NotEmpty
    lateinit var logoutEndpoint: String

    @NotEmpty
    lateinit var userinfoEndpoint: String

    @NotEmpty
    lateinit var authorizeUrl: String

    @Min(-1)
    var nonceMaxAge: Int = 60

    override fun toString(): String {
        return "AuthProperties(domain='$domain', tokenEndpoint='$tokenEndpoint', logoutEndpoint='$logoutEndpoint', userinfoEndpoint='$userinfoEndpoint', authorizeUrl='$authorizeUrl', nonceMaxAge=$nonceMaxAge)"
    }

    /**
     * Return application with application specific values, default values or inherited values.
     */
    abstract fun findApplicationOrDefault(name: String?): Application


}