package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
public class RequestPrincipalInformationResponse {

    @XmlElement(name = "resultCode")
    private int resultCode;

    @XmlElement(name = "balance")
    private BigDecimal balance;

    @XmlElement(name = "currency")
    private String currency;

    @XmlElement(name = "status")
    private String status;

    public int getResultCode() { return resultCode; }
    public void setResultCode(int resultCode) { this.resultCode = resultCode; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
