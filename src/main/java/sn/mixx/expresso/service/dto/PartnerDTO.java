package sn.mixx.expresso.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartnerDTO(
    @NotBlank(message = "Le code partenaire est obligatoire")
    String codePartner,

    @NotBlank(message = "Le nom du partenaire est obligatoire")
    String namePartner,

    @NotBlank(message = "L'email admin est obligatoire")
    String emailAdmin,

    @NotBlank(message = "Le nom de l'admin est obligatoire")
    String nameAdmin,

    String apiKey,
    String apiSecret,
    Boolean actif,
    String createdAt
) {}