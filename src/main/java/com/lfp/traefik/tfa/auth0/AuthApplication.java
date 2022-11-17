package com.lfp.traefik.tfa.auth0;

import dniel.forwardauth.AuthProperties;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SecurityScheme(name = "forwardauth", paramName = "ACCESS_TOKEN", type = SecuritySchemeType.OAUTH2, in = SecuritySchemeIn.COOKIE, bearerFormat = "jwt")

@SpringBootApplication
public class AuthApplication {


    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }


    @Bean
    protected static AuthProperties authProperties(@Autowired TFAAuth0ServiceClient tfaAuth0ServiceClient) {
        return tfaAuth0ServiceClient.authProperties()
                .block();
    }

}
