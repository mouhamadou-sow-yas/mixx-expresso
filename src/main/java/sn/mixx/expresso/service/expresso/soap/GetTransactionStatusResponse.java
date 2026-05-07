package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class GetTransactionStatusResponse {

    @XmlElement(name = "resultCode")
    private int resultCode;

    @XmlElement(name = "status")
    private String status;

    @XmlElement(name = "ersTransactionId")
    private String ersTransactionId;

    public int getResultCode() { return resultCode; }
    public void setResultCode(int resultCode) { this.resultCode = resultCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErsTransactionId() { return ersTransactionId; }
    public void setErsTransactionId(String ersTransactionId) { this.ersTransactionId = ersTransactionId; }
}
