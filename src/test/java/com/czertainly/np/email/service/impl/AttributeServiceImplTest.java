package com.czertainly.np.email.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.RequestAttributeV2;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.DataAttribute;
import com.czertainly.api.model.common.attribute.common.constraint.BaseAttributeConstraint;
import com.czertainly.api.model.common.attribute.common.constraint.RegexpAttributeConstraint;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.content.data.CodeBlockAttributeContentData;
import com.czertainly.api.model.common.attribute.common.content.data.ProgrammingLanguageEnum;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.CodeBlockAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.DataAttributeV3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeServiceImplTest {

    private static final String EMAIL_KIND = "EMAIL";
    private static final String UNSUPPORTED_KIND = "SMS";

    private AttributeServiceImpl attributeService;

    @BeforeEach
    void setUp() {
        attributeService = new AttributeServiceImpl();
    }

    @Test
    void getAttributes_returnsThreeAttributesForEmailKind() {
        List<BaseAttribute> attributes = attributeService.getAttributes(EMAIL_KIND);

        assertEquals(3, attributes.size());
        assertEquals(AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_NAME, attributes.get(0).getName());
        assertEquals(AttributeServiceImpl.DATA_SUBJECT_NAME, attributes.get(1).getName());
        assertEquals(AttributeServiceImpl.DATA_CONTENT_TEMPLATE_NAME, attributes.get(2).getName());
    }

    @Test
    void getAttributes_senderEmailHasRegexConstraint() {
        List<BaseAttribute> attributes = attributeService.getAttributes(EMAIL_KIND);

        DataAttribute sender = (DataAttribute) attributes.get(0);
        assertEquals(AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_UUID, sender.getUuid());
        assertEquals(AttributeContentType.STRING, sender.getContentType());
        assertEquals(AttributeType.DATA, sender.getType());
        assertTrue(sender.getProperties().isRequired());

        List<BaseAttributeConstraint<?>> constraints = sender.getConstraints();
        assertEquals(1, constraints.size());
        assertInstanceOf(RegexpAttributeConstraint.class, constraints.get(0));
    }

    @Test
    void getAttributes_throwsForUnsupportedKind() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> attributeService.getAttributes(UNSUPPORTED_KIND));
        assertNotNull(ex.getMessage());
    }

    @Test
    void validateAttributes_returnsTrueForValidAttributes() {
        List<RequestAttribute> attributes = buildValidRequestAttributes();

        boolean result = attributeService.validateAttributes(EMAIL_KIND, attributes);

        assertTrue(result);
    }

    @Test
    void validateAttributes_returnsFalseWhenAttributesNull() {
        boolean result = attributeService.validateAttributes(EMAIL_KIND, null);

        assertFalse(result);
    }

    @Test
    void validateAttributes_throwsForUnsupportedKind() {
        List<RequestAttribute> empty = List.of();

        assertThrows(ValidationException.class,
                () -> attributeService.validateAttributes(UNSUPPORTED_KIND, empty));
    }

    @Test
    void validateAttributes_throwsWhenRequiredAttributeMissing() {
        List<RequestAttribute> empty = List.of();

        assertThrows(ValidationException.class,
                () -> attributeService.validateAttributes(EMAIL_KIND, empty));
    }

    @Test
    void listMappingAttributes_returnsRecipientEmailAttribute() {
        List<com.czertainly.api.model.common.attribute.common.DataAttribute> attributes =
                attributeService.listMappingAttributes(EMAIL_KIND);

        assertEquals(1, attributes.size());
        DataAttributeV3 recipient = (DataAttributeV3) attributes.get(0);
        assertEquals(AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_UUID, recipient.getUuid());
        assertEquals(AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_NAME, recipient.getName());
        assertEquals(AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_DESCRIPTION, recipient.getDescription());
        assertEquals(AttributeContentType.STRING, recipient.getContentType());
        assertEquals(AttributeType.DATA, recipient.getType());

        assertEquals(AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_LABEL, recipient.getProperties().getLabel());
        assertFalse(recipient.getProperties().isRequired());
        assertFalse(recipient.getProperties().isReadOnly());
        assertTrue(recipient.getProperties().isVisible());
        assertFalse(recipient.getProperties().isList());
        assertFalse(recipient.getProperties().isMultiSelect());

        assertEquals(1, recipient.getConstraints().size());
        BaseAttributeConstraint<?> constraint = recipient.getConstraints().get(0);
        assertInstanceOf(RegexpAttributeConstraint.class, constraint);
        RegexpAttributeConstraint regex = (RegexpAttributeConstraint) constraint;
        assertEquals("Email address(es)", regex.getDescription());
        assertEquals("Invalid email address format. Separate multiple addresses with ',' or ';'.", regex.getErrorMessage());
        assertEquals(AttributeServiceImpl.EMAIL_ADDRESS_LIST_REGEX, regex.getData());
    }

    @Test
    void recipientEmailListRegex_acceptsSingleAndDelimitedAddresses_rejectsInvalid() {
        assertTrue("a@example.com".matches(AttributeServiceImpl.EMAIL_ADDRESS_LIST_REGEX));
        assertTrue("a@example.com, b@example.com; c@example.com".matches(AttributeServiceImpl.EMAIL_ADDRESS_LIST_REGEX));
        assertFalse("not-an-email".matches(AttributeServiceImpl.EMAIL_ADDRESS_LIST_REGEX));
        assertFalse("a@example.com, not-an-email".matches(AttributeServiceImpl.EMAIL_ADDRESS_LIST_REGEX));
    }

    @Test
    void listMappingAttributes_throwsForUnsupportedKind() {
        assertThrows(ValidationException.class,
                () -> attributeService.listMappingAttributes(UNSUPPORTED_KIND));
    }

    private List<RequestAttribute> buildValidRequestAttributes() {
        RequestAttributeV2 sender = new RequestAttributeV2();
        sender.setUuid(UUID.fromString(AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_UUID));
        sender.setName(AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_NAME);
        sender.setContentType(AttributeContentType.STRING);
        sender.setContent(List.<BaseAttributeContentV2<?>>of(new StringAttributeContentV2("from@example.com")));

        RequestAttributeV2 subject = new RequestAttributeV2();
        subject.setUuid(UUID.fromString(AttributeServiceImpl.DATA_SUBJECT_UUID));
        subject.setName(AttributeServiceImpl.DATA_SUBJECT_NAME);
        subject.setContentType(AttributeContentType.STRING);
        subject.setContent(List.<BaseAttributeContentV2<?>>of(new StringAttributeContentV2("Hello")));

        CodeBlockAttributeContentV2 codeContent = new CodeBlockAttributeContentV2();
        codeContent.setData(new CodeBlockAttributeContentData(ProgrammingLanguageEnum.HTML, "PGgxPlRlc3Q8L2gxPg=="));

        RequestAttributeV2 template = new RequestAttributeV2();
        template.setUuid(UUID.fromString(AttributeServiceImpl.DATA_CONTENT_TEMPLATE_UUID));
        template.setName(AttributeServiceImpl.DATA_CONTENT_TEMPLATE_NAME);
        template.setContentType(AttributeContentType.CODEBLOCK);
        template.setContent(List.<BaseAttributeContentV2<?>>of(codeContent));

        return List.of(sender, subject, template);
    }
}
