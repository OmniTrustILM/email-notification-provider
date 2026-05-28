package com.czertainly.np.email.dao.entity;

import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.MetadataAttribute;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Base64;
import java.util.List;

@Entity
@Table(name = "notification_instance")
public class NotificationInstance extends UniquelyIdentified {

    @Column(name = "name")
    private String name;

    @Column(name = "email_from")
    private String emailFrom;

    @Column(name = "subject")
    private String subject;

    @Column(name = "content_template", length = Integer.MAX_VALUE)
    private String contentTemplate;

    @Column(name = "metadata", length = Integer.MAX_VALUE)
    private String metadata;

    @Column(name = "attributes", length = Integer.MAX_VALUE)
    private String attributes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailFrom() {
        return emailFrom;
    }

    public void setEmailFrom(String emailFrom) {
        this.emailFrom = emailFrom;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContentTemplate() {
        return new String(Base64.getDecoder().decode(contentTemplate));
    }

    /**
     * @param contentTemplate - base64 encoded content template
     */
    public void setContentTemplate(String contentTemplate) {
        this.contentTemplate = contentTemplate;
    }

    public List<MetadataAttribute> getMetadata() {
        return AttributeDefinitionUtils.deserialize(metadata, MetadataAttribute.class);
    }

    public void setMetadata(List<MetadataAttribute> metadata) {
        this.metadata = AttributeDefinitionUtils.serialize(metadata);
    }

    public List<BaseAttribute> getAttributes() {
        return AttributeDefinitionUtils.deserialize(attributes, BaseAttribute.class);
    }

    public void setAttributes(List<BaseAttribute> attributes) {
        this.attributes = AttributeDefinitionUtils.serialize(attributes);
    }

    public NotificationProviderInstanceDto mapToDto() {
        NotificationProviderInstanceDto dto = new NotificationProviderInstanceDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);

        if (attributes != null) {
            dto.setAttributes(getAttributes());
        } else { // empty list of attributes
            dto.setAttributes(AttributeDefinitionUtils.deserialize("[]", BaseAttribute.class));
        }

        //if (metadata != null) {
        //    dto.setMetadata(getMetadata());
        //}

        return dto;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationInstance that = (NotificationInstance) o;
        return new EqualsBuilder().append(uuid, that.uuid).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }

}
