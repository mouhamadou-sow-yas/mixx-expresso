package sn.mixx.expresso.service.dto.mfa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SmsRequest(
    @JsonProperty("receiverMSISDN")
    String phoneNumber,
    @JsonProperty("messageToSend")
    String message,
    @JsonProperty("sender")
    String sender,
    @JsonProperty("type")
    int type
) {}