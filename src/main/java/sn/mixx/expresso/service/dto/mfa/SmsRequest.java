package sn.mixx.expresso.service.dto.mfa;

public record SmsRequest(
    String phoneNumber,
    String message,
    String sender,
    int type
) {}