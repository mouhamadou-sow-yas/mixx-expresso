package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RequestTopupRequest {

    @XmlElement(name = "context")
    private ClientContext context;

    @XmlElement(name = "senderPrincipalId")
    private PrincipalId senderPrincipalId;

    @XmlElement(name = "topupPrincipalId")
    private PrincipalId topupPrincipalId;

    @XmlElement(name = "senderAccountSpecifier")
    private AccountSpecifier senderAccountSpecifier;

    @XmlElement(name = "topupAccountSpecifier")
    private AccountSpecifier topupAccountSpecifier;

    @XmlElement(name = "productId")
    private String productId;

    @XmlElement(name = "amount")
    private ErsAmount amount;

    public ClientContext getContext() { return context; }
    public void setContext(ClientContext context) { this.context = context; }
    public PrincipalId getSenderPrincipalId() { return senderPrincipalId; }
    public void setSenderPrincipalId(PrincipalId senderPrincipalId) { this.senderPrincipalId = senderPrincipalId; }
    public PrincipalId getTopupPrincipalId() { return topupPrincipalId; }
    public void setTopupPrincipalId(PrincipalId topupPrincipalId) { this.topupPrincipalId = topupPrincipalId; }
    public AccountSpecifier getSenderAccountSpecifier() { return senderAccountSpecifier; }
    public void setSenderAccountSpecifier(AccountSpecifier senderAccountSpecifier) { this.senderAccountSpecifier = senderAccountSpecifier; }
    public AccountSpecifier getTopupAccountSpecifier() { return topupAccountSpecifier; }
    public void setTopupAccountSpecifier(AccountSpecifier topupAccountSpecifier) { this.topupAccountSpecifier = topupAccountSpecifier; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public ErsAmount getAmount() { return amount; }
    public void setAmount(ErsAmount amount) { this.amount = amount; }
}