package dniel.forwardauth.domain.shared

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import javax.validation.constraints.NotEmpty

class Application {
    @NotEmpty
    lateinit var name: String

    @get:JsonIgnore
    var clientId: String = ""

    @get:JsonIgnore
    var clientSecret: String = ""
    var audience: String = ""
    var scope: String = "profile openid email"
    var redirectUri: String = ""
    var tokenCookieDomain: String = ""
    var returnTo: String = ""
    var restrictedMethods: List<String> = listOf("DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT")
    var requiredPermissions: List<String> = emptyList()
    var claims: List<String> = emptyList()
    override fun toString(): String {
        return "Application(name='$name', audience='$audience', scope='$scope', redirectUri='$redirectUri', tokenCookieDomain='$tokenCookieDomain', returnTo='$returnTo', restrictedMethods=$restrictedMethods, requiredPermissions=$requiredPermissions, claims=$claims)"
    }


}