package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class AccountSpecifier {

    @XmlElement(name = "accountTypeId")
    private String accountTypeId;

    public AccountSpecifier() {}

    public AccountSpecifier(String accountTypeId) {
        this.accountTypeId = accountTypeId;
    }

    public String getAccountTypeId() { return accountTypeId; }
    public void setAccountTypeId(String accountTypeId) { this.accountTypeId = accountTypeId; }
}
