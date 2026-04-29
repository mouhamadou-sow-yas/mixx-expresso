package sn.mixx.expresso.service.dto.mfa;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerificationRequest {

    @NotBlank
    private String mfaToken;

    @NotBlank
    private String otpCode;
}