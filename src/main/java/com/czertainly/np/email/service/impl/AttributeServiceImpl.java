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
import com.czertainly.api.model.common.attribute.v3.DataAttributeV3;
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

    private static final String KIND_EMAIL = "EMAIL";
    private static final String UNSUPPORTED_KIND_MESSAGE = "Unsupported kind {}";

    // Single email address, according to the W3C HTML5 specification:
    // https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address
    private static final String EMAIL_ADDRESS_PATTERN = "[a-zA-Z0-9.!#$%&'*+\\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*";

    /** Anchored regex matching exactly one email address. Used to validate each parsed recipient address. */
    public static final String EMAIL_ADDRESS_REGEX = "^" + EMAIL_ADDRESS_PATTERN + "$";

    /** Regex matching one or more email addresses separated by ',' or ';' (surrounding whitespace allowed). */
    public static final String EMAIL_ADDRESS_LIST_REGEX = "^\\s*" + EMAIL_ADDRESS_PATTERN + "(?:\\s*[,;]\\s*" + EMAIL_ADDRESS_PATTERN + ")*\\s*$";

    public static final String DATA_SENDER_EMAIL_ADDRESS_UUID = "3a1aed46-7e45-4e13-b4c0-5d33e5dc73f8";
    public static final String DATA_SENDER_EMAIL_ADDRESS_NAME = "data_senderEmailAddress";
    public static final String DATA_SENDER_EMAIL_ADDRESS_DESCRIPTION = "Email address from which the email will be sent";
    public static final String DATA_SENDER_EMAIL_ADDRESS_LABEL = "Sender email address";

    public static final String DATA_RECIPIENT_EMAIL_ADDRESS_UUID = "522f2a57-4db2-408e-b7ed-7aee4bd96282";
    public static final String DATA_RECIPIENT_EMAIL_ADDRESS_NAME = "data_recipientEmailAddress";
    public static final String DATA_RECIPIENT_EMAIL_ADDRESS_DESCRIPTION = "Email address(es) to which the email will be sent. Multiple addresses may be provided either as a single value separated by ',' or ';', or as multiple attribute content items. Each address is validated individually and invalid addresses are skipped.";
    public static final String DATA_RECIPIENT_EMAIL_ADDRESS_LABEL = "Recipient email address";

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

        if (!KIND_EMAIL.equals(kind)) {
            throw new ValidationException(ValidationError.create(UNSUPPORTED_KIND_MESSAGE, kind));
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

        if (!KIND_EMAIL.equals(kind)) {
            throw new ValidationException(ValidationError.create(UNSUPPORTED_KIND_MESSAGE, kind));
        }
        if (attributes == null) {
            return false;
        }

        AttributeDefinitionUtils.validateAttributes(getAttributes(kind), attributes);
        return true;
    }

    @Override
    public List<DataAttribute> listMappingAttributes(String kind) {
        if (!KIND_EMAIL.equals(kind)) {
            throw new ValidationException(ValidationError.create(UNSUPPORTED_KIND_MESSAGE, kind));
        }
        return List.of(dataRecipientEmailAddress());
    }

    private DataAttribute dataRecipientEmailAddress() {
        DataAttributeV3 attribute = new DataAttributeV3();

        attribute.setUuid(DATA_RECIPIENT_EMAIL_ADDRESS_UUID);
        attribute.setName(DATA_RECIPIENT_EMAIL_ADDRESS_NAME);
        attribute.setDescription(DATA_RECIPIENT_EMAIL_ADDRESS_DESCRIPTION);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setType(AttributeType.DATA);

        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(DATA_RECIPIENT_EMAIL_ADDRESS_LABEL);
        attributeProperties.setRequired(false);
        attributeProperties.setReadOnly(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);

        attribute.setProperties(attributeProperties);

        // create restrictions
        RegexpAttributeConstraint regexpAttributeConstraint = getEmailListRegexpConstraint();
        attribute.setConstraints(List.of(regexpAttributeConstraint));

        return attribute;
    }

    private static RegexpAttributeConstraint getRegexpAttributeConstraint() {
        return buildRegexpConstraint("Email address", "Invalid email address format", EMAIL_ADDRESS_REGEX);
    }

    private static RegexpAttributeConstraint getEmailListRegexpConstraint() {
        return buildRegexpConstraint("Email address(es)",
                "Invalid email address format. Separate multiple addresses with ',' or ';'.",
                EMAIL_ADDRESS_LIST_REGEX);
    }

    private static RegexpAttributeConstraint buildRegexpConstraint(String description, String errorMessage, String regex) {
        RegexpAttributeConstraint regexpAttributeConstraint = new RegexpAttributeConstraint();
        regexpAttributeConstraint.setDescription(description);
        regexpAttributeConstraint.setErrorMessage(errorMessage);
        regexpAttributeConstraint.setData(regex);
        return regexpAttributeConstraint;
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
        RegexpAttributeConstraint regexpAttributeConstraint = getRegexpAttributeConstraint();
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
