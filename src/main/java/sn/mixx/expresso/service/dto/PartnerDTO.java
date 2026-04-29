package sn.mixx.expresso.service.dto;

import lombok.Builder;

@Builder
public record PartnerDTO(
    String codePartner,
    String namePartner,
    String emailAdmin,
    String nameAdmin,
    String apiKey,
    String apiSecret,
    Boolean actif,
    String createdAt
) {}