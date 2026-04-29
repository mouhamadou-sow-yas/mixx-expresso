package sn.mixx.expresso.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.util.Objects;

@Table("jhi_authority")
@JsonIgnoreProperties(value = { "new", "id" })
public class Authority implements Serializable, Persistable<String> {

    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 50)
    @Id
    @Column("name")
    private String name;

    @org.springframework.data.annotation.Transient
    private boolean isPersisted;

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String getId() { return this.name; }

    @org.springframework.data.annotation.Transient
    @Override
    public boolean isNew() { return !this.isPersisted; }

    public Authority setIsPersisted() {
        this.isPersisted = true;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Authority)) return false;
        return getName() != null && getName().equals(((Authority) o).getName());
    }

    @Override
    public int hashCode() { return Objects.hashCode(getName()); }

    @Override
    public String toString() { return "Authority{name=" + getName() + "}"; }
}