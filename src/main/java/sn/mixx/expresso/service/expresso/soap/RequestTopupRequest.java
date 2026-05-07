package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RequestTopupRequest {

    @XmlElement(name = "channel")
    private String channel;

    @XmlElement(name = "clientId")
    private String clientId;

    @XmlElement(name = "initiatorPrincipalId")
    private PrincipalId initiatorPrincipalId;

    @XmlElement(name = "senderPrincipalId")
    private PrincipalId senderPrincipalId;

    @XmlElement(name = "topupPrincipalId")
    private PrincipalId topupPrincipalId;

    @XmlElement(name = "topupAccountSpecifier")
    private AccountSpecifier topupAccountSpecifier;

    @XmlElement(name = "productId")
    private String productId;

    @XmlElement(name = "amount")
    private ErsAmount amount;

    @XmlElement(name = "clientReference")
    private String clientReference;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public PrincipalId getInitiatorPrincipalId() { return initiatorPrincipalId; }
    public void setInitiatorPrincipalId(PrincipalId initiatorPrincipalId) { this.initiatorPrincipalId = initiatorPrincipalId; }
    public PrincipalId getSenderPrincipalId() { return senderPrincipalId; }
    public void setSenderPrincipalId(PrincipalId senderPrincipalId) { this.senderPrincipalId = senderPrincipalId; }
    public PrincipalId getTopupPrincipalId() { return topupPrincipalId; }
    public void setTopupPrincipalId(PrincipalId topupPrincipalId) { this.topupPrincipalId = topupPrincipalId; }
    public AccountSpecifier getTopupAccountSpecifier() { return topupAccountSpecifier; }
    public void setTopupAccountSpecifier(AccountSpecifier topupAccountSpecifier) { this.topupAccountSpecifier = topupAccountSpecifier; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public ErsAmount getAmount() { return amount; }
    public void setAmount(ErsAmount amount) { this.amount = amount; }
    public String getClientReference() { return clientReference; }
    public void setClientReference(String clientReference) { this.clientReference = clientReference; }
}
