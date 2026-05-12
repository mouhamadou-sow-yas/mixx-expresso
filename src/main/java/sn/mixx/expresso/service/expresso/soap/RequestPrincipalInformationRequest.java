package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RequestPrincipalInformationRequest {

    @XmlElement(name = "context")
    private ClientContext context;

    @XmlElement(name = "principalId")
    private PrincipalId principalId;

    public ClientContext getContext() { return context; }
    public void setContext(ClientContext context) { this.context = context; }
    public PrincipalId getPrincipalId() { return principalId; }
    public void setPrincipalId(PrincipalId principalId) { this.principalId = principalId; }
}