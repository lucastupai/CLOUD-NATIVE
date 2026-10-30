package cl.duoc.vidasalud.bff.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private static final Logger log = LoggerFactory.getLogger(AudienceValidator.class);
    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null) {
            String cleanAudience = audience.startsWith("api://") ? audience.substring(6) : audience;
            for (String aud : audiences) {
                String cleanAud = aud.startsWith("api://") ? aud.substring(6) : aud;
                if (aud.equalsIgnoreCase(audience) || cleanAud.equalsIgnoreCase(cleanAudience)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
        }
        log.warn("Rechazando token: audiencia no coincide. Esperada='{}', token={}", audience, audiences);
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "El token no contiene la audiencia esperada (" + audience + "). Audiencias presentes: " + audiences, null));
    }
}
