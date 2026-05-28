package com.czertainly.np.email.service.impl;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.DataAttribute;
import com.czertainly.api.model.common.attribute.common.constraint.RegexpAttributeConstraint;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.content.data.CodeBlockAttributeContentData;
import com.czertainly.api.model.common.attribute.common.content.data.ProgrammingLanguageEnum;
import com.czertainly.api.model.common.attribute.common.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v2.content.CodeBlockAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.np.email.service.AttributeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AttributeServiceImpl implements AttributeService {

    private static final Logger logger = LoggerFactory.getLogger(AttributeServiceImpl.class);

    public static final String DATA_SENDER_EMAIL_ADDRESS_UUID = "3a1aed46-7e45-4e13-b4c0-5d33e5dc73f8";
    public static final String DATA_SENDER_EMAIL_ADDRESS_NAME = "data_senderEmailAddress";
    public static final String DATA_SENDER_EMAIL_ADDRESS_DESCRIPTION = "Email address from which the email will be sent";
    public static final String DATA_SENDER_EMAIL_ADDRESS_LABEL = "Sender email address";

    public static final String DATA_SUBJECT_UUID = "cc56a091-3e99-446b-b366-1820afa75c97";
    public static final String DATA_SUBJECT_NAME = "data_emailSubject";
    public static final String DATA_SUBJECT_DESCRIPTION = "Email subject to be sent";
    public static final String DATA_SUBJECT_LABEL = "Email subject";

    public static final String DATA_CONTENT_TEMPLATE_UUID = "b31b3d66-a427-41ca-8f3f-7832e381e4c1";
    public static final String DATA_CONTENT_TEMPLATE_NAME = "data_emailContentTemplate";
    public static final String DATA_CONTENT_TEMPLATE_DESCRIPTION = "Content template for the email to be sent in html syntax";
    public static final String DATA_CONTENT_TEMPLATE_LABEL = "Email content template";

    @Override
    public List<BaseAttribute> getAttributes(String kind) {
        logger.debug("Getting the attributes for {}", kind);

        if (!kind.equals("EMAIL")) {
            throw new ValidationException(ValidationError.create("Unsupported kind {}", kind));
        }

        List<BaseAttribute> attributes = new ArrayList<>();
        attributes.add(dataSenderEmailAddress());
        attributes.add(dataSubject());
        attributes.add(dataContentTemplate());

        return attributes;
    }

    @Override
    public boolean validateAttributes(String kind, List<RequestAttribute> attributes) {
        logger.debug("Validating the attributes for kind {} with attributes: {}", kind, attributes);

        if (!kind.equals("EMAIL")) {
            throw new ValidationException(ValidationError.create("Unsupported kind {}", kind));
        }
        if (attributes == null) {
            return false;
        }

        AttributeDefinitionUtils.validateAttributes(getAttributes(kind), attributes);
        return true;
    }

    private DataAttribute dataSenderEmailAddress() {
        DataAttributeV2 attribute = new DataAttributeV2();

        attribute.setUuid(DATA_SENDER_EMAIL_ADDRESS_UUID);
        attribute.setName(DATA_SENDER_EMAIL_ADDRESS_NAME);
        attribute.setDescription(DATA_SENDER_EMAIL_ADDRESS_DESCRIPTION);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setType(AttributeType.DATA);

        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(DATA_SENDER_EMAIL_ADDRESS_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setReadOnly(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);

        attribute.setProperties(attributeProperties);

        List<StringAttributeContentV2> content = new ArrayList<>();
        StringAttributeContentV2 attributeContent = new StringAttributeContentV2("email@example.com");
        content.add(attributeContent);
        attribute.setContent(content);

        // create restrictions
        RegexpAttributeConstraint regexpAttributeConstraint = new RegexpAttributeConstraint();
        regexpAttributeConstraint.setDescription("Email address");
        regexpAttributeConstraint.setErrorMessage("Invalid email address format");
        // this is according to the W3C HTML5 specification:
        // https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address
        regexpAttributeConstraint.setData("^[a-zA-Z0-9.!#$%&'*+\\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
        attribute.setConstraints(List.of(regexpAttributeConstraint));

        return attribute;
    }

    private DataAttribute dataSubject() {
        DataAttributeV2 attribute = new DataAttributeV2();

        attribute.setUuid(DATA_SUBJECT_UUID);
        attribute.setName(DATA_SUBJECT_NAME);
        attribute.setDescription(DATA_SUBJECT_DESCRIPTION);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setType(AttributeType.DATA);

        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(DATA_SUBJECT_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setReadOnly(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);

        attribute.setProperties(attributeProperties);

        List<StringAttributeContentV2> content = new ArrayList<>();
        StringAttributeContentV2 attributeContent = new StringAttributeContentV2("Email subject");
        content.add(attributeContent);
        attribute.setContent(content);

        return attribute;
    }

    private DataAttribute dataContentTemplate() {
        DataAttributeV2 attribute = new DataAttributeV2();

        attribute.setUuid(DATA_CONTENT_TEMPLATE_UUID);
        attribute.setName(DATA_CONTENT_TEMPLATE_NAME);
        attribute.setDescription(DATA_CONTENT_TEMPLATE_DESCRIPTION);
        attribute.setContentType(AttributeContentType.CODEBLOCK);
        attribute.setType(AttributeType.DATA);

        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(DATA_CONTENT_TEMPLATE_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setReadOnly(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);

        attribute.setProperties(attributeProperties);

        List<CodeBlockAttributeContentV2> content = new ArrayList<>();
        CodeBlockAttributeContentV2 attributeContent = new CodeBlockAttributeContentV2();
        CodeBlockAttributeContentData data = new CodeBlockAttributeContentData();
        data.setLanguage(ProgrammingLanguageEnum.HTML);
        attributeContent.setData(data);
        content.add(attributeContent);
        attribute.setContent(content);

        return attribute;
    }

}
