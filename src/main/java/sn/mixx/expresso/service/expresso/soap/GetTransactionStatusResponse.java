package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class GetTransactionStatusResponse {

    @XmlElement(name = "resultCode")
    private int resultCode;

    @XmlElement(name = "resultDescription")
    private String resultDescription;

    @XmlElement(name = "ersReference")
    private String ersReference;

    public int getResultCode() { return resultCode; }
    public void setResultCode(int resultCode) { this.resultCode = resultCode; }
    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }
    public String getErsReference() { return ersReference; }
    public void setErsReference(String ersReference) { this.ersReference = ersReference; }
}