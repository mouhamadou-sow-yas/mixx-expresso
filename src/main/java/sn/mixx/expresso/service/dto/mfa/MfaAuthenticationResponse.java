package sn.mixx.expresso.service.dto.mfa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MfaAuthenticationResponse(
    boolean authenticated,
    boolean mfaRequired,
    String mfaToken,
    String maskedPhone,
    @JsonProperty("id_token") String idToken
) {
    public static MfaAuthenticationResponse authenticated(String idToken) {
        return new MfaAuthenticationResponse(true, false, null, null, idToken);
    }

    public static MfaAuthenticationResponse mfaRequired(String mfaToken, String maskedPhone) {
        return new MfaAuthenticationResponse(false, true, mfaToken, maskedPhone, null);
    }
}