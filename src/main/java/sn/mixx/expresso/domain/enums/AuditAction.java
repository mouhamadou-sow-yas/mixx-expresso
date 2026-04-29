package sn.mixx.expresso.domain.enums;

public enum AuditAction {
    CREATE_TXN,
    DEBIT,
    TOPUP,
    REFUND,
    RETRY,
    STATUS_CHANGE,
    BO_INTERVENTION,
    NOTIFICATION_SENT,
    NOTIFICATION_FAILED,
    ANTI_FRAUD_CHECK,
    BALANCE_CHECK
}