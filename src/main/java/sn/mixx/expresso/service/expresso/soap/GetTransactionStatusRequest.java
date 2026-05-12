package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class GetTransactionStatusRequest {

    @XmlElement(name = "context")
    private ClientContext context;

    @XmlElement(name = "resellerPrincipalId")
    private PrincipalId resellerPrincipalId;

    public ClientContext getContext() { return context; }
    public void setContext(ClientContext context) { this.context = context; }
    public PrincipalId getResellerPrincipalId() { return resellerPrincipalId; }
    public void setResellerPrincipalId(PrincipalId resellerPrincipalId) { this.resellerPrincipalId = resellerPrincipalId; }
}