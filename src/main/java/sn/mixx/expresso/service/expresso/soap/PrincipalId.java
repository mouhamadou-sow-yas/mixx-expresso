package sn.mixx.expresso.service.expresso.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class PrincipalId {

    @XmlElement(name = "id")
    private String id;

    @XmlElement(name = "type")
    private String type;

    @XmlElement(name = "userId")
    private String userId;

    public PrincipalId() {}

    public PrincipalId(String type, String id) {
        this.type = type;
        this.id = id;
    }

    public PrincipalId(String type, String id, String userId) {
        this.type = type;
        this.id = id;
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}