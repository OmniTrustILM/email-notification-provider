package com.czertainly.np.email.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.RequestAttributeV2;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.content.data.CodeBlockAttributeContentData;
import com.czertainly.api.model.common.attribute.common.content.data.ProgrammingLanguageEnum;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.CodeBlockAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceDto;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceRequestDto;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.np.email.dao.entity.NotificationInstance;
import com.czertainly.np.email.dao.repository.NotificationInstanceRepository;
import com.czertainly.np.email.service.AttributeService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInstanceServiceImplTest {

    private static final String INSTANCE_NAME = "Test instance";
    private static final String EMAIL_FROM = "from@example.com";
    private static final String SUBJECT = "Test subject";
    private static final String TEMPLATE_PLAINTEXT = "<p>Hello</p>";
    private static final String TEMPLATE_BASE64 = Base64.getEncoder().encodeToString(TEMPLATE_PLAINTEXT.getBytes());

    @Mock
    private NotificationInstanceRepository repository;

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private AttributeService attributeService;

    private NotificationInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationInstanceServiceImpl();
        service.setNotificationInstanceRepository(repository);
        service.setEmailSender(emailSender);
        service.setAttributeService(attributeService);
    }

    @Test
    void listNotificationInstances_returnsMappedDtos() {
        NotificationInstance instance = buildPersistedInstance(UUID.randomUUID());
        when(repository.findAll()).thenReturn(List.of(instance));

        List<NotificationProviderInstanceDto> result = service.listNotificationInstances();

        assertEquals(1, result.size());
        assertEquals(instance.getUuid().toString(), result.get(0).getUuid());
        assertEquals(INSTANCE_NAME, result.get(0).getName());
    }

    @Test
    void listNotificationInstances_returnsEmptyWhenNoInstances() {
        when(repository.findAll()).thenReturn(List.of());

        List<NotificationProviderInstanceDto> result = service.listNotificationInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void createNotificationInstance_persistsAndReturnsDto() throws AlreadyExistException {
        NotificationProviderInstanceRequestDto request = buildInstanceRequest();
        when(repository.findByName(INSTANCE_NAME)).thenReturn(Optional.empty());
        when(attributeService.getAttributes("EMAIL")).thenReturn(new ArrayList<BaseAttribute>());

        NotificationProviderInstanceDto dto = service.createNotificationInstance(request);

        ArgumentCaptor<NotificationInstance> captor = ArgumentCaptor.forClass(NotificationInstance.class);
        verify(repository).save(captor.capture());
        NotificationInstance saved = captor.getValue();
        assertEquals(INSTANCE_NAME, saved.getName());
        assertEquals(EMAIL_FROM, saved.getEmailFrom());
        assertEquals(SUBJECT, saved.getSubject());
        assertEquals(TEMPLATE_PLAINTEXT, saved.getContentTemplate());
        assertNotNull(saved.getUuid());

        assertEquals(INSTANCE_NAME, dto.getName());
        assertNotNull(dto.getUuid());
    }

    @Test
    void createNotificationInstance_throwsWhenNameExists() {
        NotificationProviderInstanceRequestDto request = buildInstanceRequest();
        when(repository.findByName(INSTANCE_NAME)).thenReturn(Optional.of(buildPersistedInstance(UUID.randomUUID())));

        assertThrows(AlreadyExistException.class, () -> service.createNotificationInstance(request));
        verify(repository, never()).save(any());
    }

    @Test
    void getNotificationInstance_returnsDto() throws NotFoundException {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(buildPersistedInstance(uuid)));

        NotificationProviderInstanceDto dto = service.getNotificationInstance(uuid);

        assertEquals(uuid.toString(), dto.getUuid());
        assertEquals(INSTANCE_NAME, dto.getName());
    }

    @Test
    void getNotificationInstance_throwsWhenMissing() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getNotificationInstance(uuid));
    }

    @Test
    void updateNotificationInstance_updatesFields() throws NotFoundException {
        UUID uuid = UUID.randomUUID();
        NotificationInstance existing = buildPersistedInstance(uuid);
        existing.setEmailFrom("stale@example.com");
        existing.setSubject("stale");

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(attributeService.getAttributes("EMAIL")).thenReturn(new ArrayList<BaseAttribute>());

        NotificationProviderInstanceDto dto = service.updateNotificationInstance(uuid, buildInstanceRequest());

        verify(repository).save(existing);
        assertEquals(EMAIL_FROM, existing.getEmailFrom());
        assertEquals(SUBJECT, existing.getSubject());
        assertEquals(TEMPLATE_PLAINTEXT, existing.getContentTemplate());
        assertEquals(uuid.toString(), dto.getUuid());
    }

    @Test
    void updateNotificationInstance_throwsWhenMissing() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.updateNotificationInstance(uuid, buildInstanceRequest()));
        verify(repository, never()).save(any());
    }

    @Test
    void removeNotificationInstance_deletesEntity() throws NotFoundException {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));

        service.removeNotificationInstance(uuid);

        verify(repository).delete(instance);
    }

    @Test
    void removeNotificationInstance_throwsWhenMissing() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.removeNotificationInstance(uuid));
        verify(repository, never()).delete(any(NotificationInstance.class));
    }

    @Test
    void sendNotification_sendsEmailToRecipientFromEmailField() throws Exception {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setName("R1");
        recipient.setEmail("to@example.com");

        service.sendNotification(uuid, buildNotifyRequest(List.of(recipient)));

        verify(emailSender, times(1)).send(mimeMessage);
        assertArrayEquals(new String[]{"to@example.com"}, mimeMessage.getAllRecipients() == null
                ? new String[0]
                : new String[]{mimeMessage.getAllRecipients()[0].toString()});
        assertEquals(SUBJECT, mimeMessage.getSubject());
    }

    @Test
    void sendNotification_sendsEmailToRecipientFromMappedAttribute() throws Exception {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setName("R1");
        recipient.setMappedAttributes(List.of(buildRecipientEmailAttribute("mapped@example.com")));

        service.sendNotification(uuid, buildNotifyRequest(List.of(recipient)));

        verify(emailSender, times(1)).send(mimeMessage);
        assertEquals(1, mimeMessage.getAllRecipients().length);
        assertEquals("mapped@example.com", mimeMessage.getAllRecipients()[0].toString());
    }

    @Test
    void sendNotification_combinesEmailAndMappedAttribute() throws Exception {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setName("R1");
        recipient.setEmail("direct@example.com");
        recipient.setMappedAttributes(List.of(buildRecipientEmailAttribute("mapped@example.com")));

        service.sendNotification(uuid, buildNotifyRequest(List.of(recipient)));

        String[] addresses = new String[mimeMessage.getAllRecipients().length];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = mimeMessage.getAllRecipients()[i].toString();
        }
        assertEquals(2, addresses.length);
        assertTrue(List.of(addresses).contains("direct@example.com"));
        assertTrue(List.of(addresses).contains("mapped@example.com"));
    }

    @Test
    void sendNotification_deduplicatesEqualEmailAndMappedAttribute() throws Exception {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setName("R1");
        recipient.setEmail("same@example.com");
        recipient.setMappedAttributes(List.of(buildRecipientEmailAttribute("same@example.com")));

        service.sendNotification(uuid, buildNotifyRequest(List.of(recipient)));

        assertEquals(1, mimeMessage.getAllRecipients().length);
        assertEquals("same@example.com", mimeMessage.getAllRecipients()[0].toString());
    }

    @Test
    void sendNotification_throwsValidationWhenNoEmailAndNoMappedAttributes() {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));
        when(emailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setName("R1");

        assertThrows(ValidationException.class,
                () -> service.sendNotification(uuid, buildNotifyRequest(List.of(recipient))));
        verify(emailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendNotification_throwsValidationWhenMappedAttributesPresentButBlankEmail() {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));
        when(emailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setName("R1");
        recipient.setEmail("   ");
        recipient.setMappedAttributes(List.of(buildRecipientEmailAttribute("")));

        assertThrows(ValidationException.class,
                () -> service.sendNotification(uuid, buildNotifyRequest(List.of(recipient))));
        verify(emailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendNotification_throwsNotFoundWhenInstanceMissing() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setEmail("a@example.com");

        assertThrows(NotFoundException.class,
                () -> service.sendNotification(uuid, buildNotifyRequest(List.of(recipient))));
    }

    @Test
    void sendNotification_sendsToMultipleRecipients() throws Exception {
        UUID uuid = UUID.randomUUID();
        NotificationInstance instance = buildPersistedInstance(uuid);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(instance));

        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRecipientDto r1 = new NotificationRecipientDto();
        r1.setEmail("a@example.com");
        NotificationRecipientDto r2 = new NotificationRecipientDto();
        r2.setMappedAttributes(List.of(buildRecipientEmailAttribute("b@example.com")));

        service.sendNotification(uuid, buildNotifyRequest(List.of(r1, r2)));

        assertEquals(2, mimeMessage.getAllRecipients().length);
    }

    private NotificationProviderInstanceRequestDto buildInstanceRequest() {
        NotificationProviderInstanceRequestDto request = new NotificationProviderInstanceRequestDto();
        request.setName(INSTANCE_NAME);
        request.setKind("EMAIL");

        RequestAttributeV2 sender = new RequestAttributeV2();
        sender.setUuid(UUID.fromString(AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_UUID));
        sender.setName(AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_NAME);
        sender.setContentType(AttributeContentType.STRING);
        sender.setContent(List.<BaseAttributeContentV2<?>>of(new StringAttributeContentV2(EMAIL_FROM)));

        RequestAttributeV2 subject = new RequestAttributeV2();
        subject.setUuid(UUID.fromString(AttributeServiceImpl.DATA_SUBJECT_UUID));
        subject.setName(AttributeServiceImpl.DATA_SUBJECT_NAME);
        subject.setContentType(AttributeContentType.STRING);
        subject.setContent(List.<BaseAttributeContentV2<?>>of(new StringAttributeContentV2(SUBJECT)));

        CodeBlockAttributeContentV2 codeContent = new CodeBlockAttributeContentV2();
        codeContent.setData(new CodeBlockAttributeContentData(ProgrammingLanguageEnum.HTML, TEMPLATE_BASE64));

        RequestAttributeV2 template = new RequestAttributeV2();
        template.setUuid(UUID.fromString(AttributeServiceImpl.DATA_CONTENT_TEMPLATE_UUID));
        template.setName(AttributeServiceImpl.DATA_CONTENT_TEMPLATE_NAME);
        template.setContentType(AttributeContentType.CODEBLOCK);
        template.setContent(List.<BaseAttributeContentV2<?>>of(codeContent));

        List<RequestAttribute> attributes = new ArrayList<>();
        attributes.add(sender);
        attributes.add(subject);
        attributes.add(template);
        request.setAttributes(attributes);
        return request;
    }

    private NotificationProviderNotifyRequestDto buildNotifyRequest(List<NotificationRecipientDto> recipients) {
        NotificationProviderNotifyRequestDto request = new NotificationProviderNotifyRequestDto();
        request.setRecipients(recipients);
        request.setEvent(ResourceEvent.CERTIFICATE_STATUS_CHANGED);
        request.setResource(Resource.CERTIFICATE);
        return request;
    }

    private NotificationInstance buildPersistedInstance(UUID uuid) {
        NotificationInstance instance = new NotificationInstance();
        instance.setUuid(uuid);
        instance.setName(INSTANCE_NAME);
        instance.setEmailFrom(EMAIL_FROM);
        instance.setSubject(SUBJECT);
        instance.setContentTemplate(TEMPLATE_BASE64);
        return instance;
    }

    private RequestAttributeV3 buildRecipientEmailAttribute(String value) {
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setUuid(UUID.fromString(AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_UUID));
        attribute.setName(AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_NAME);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setContent(List.<BaseAttributeContentV3<?>>of(new StringAttributeContentV3(value)));
        return attribute;
    }
}
