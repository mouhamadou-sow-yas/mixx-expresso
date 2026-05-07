package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RequestTopupResponse {

    @XmlElement(name = "resultCode")
    private int resultCode;

    @XmlElement(name = "resultDescription")
    private String resultDescription;

    @XmlElement(name = "ersTransactionId")
    private String ersTransactionId;

    public int getResultCode() { return resultCode; }
    public void setResultCode(int resultCode) { this.resultCode = resultCode; }
    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }
    public String getErsTransactionId() { return ersTransactionId; }
    public void setErsTransactionId(String ersTransactionId) { this.ersTransactionId = ersTransactionId; }
}
