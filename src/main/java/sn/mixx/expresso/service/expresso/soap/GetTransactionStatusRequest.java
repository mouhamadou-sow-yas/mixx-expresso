package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class GetTransactionStatusRequest {

    @XmlElement(name = "clientReference")
    private String clientReference;

    public GetTransactionStatusRequest() {}

    public GetTransactionStatusRequest(String clientReference) {
        this.clientReference = clientReference;
    }

    public String getClientReference() { return clientReference; }
    public void setClientReference(String clientReference) { this.clientReference = clientReference; }
}
