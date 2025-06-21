package com.czertainly.np.email.util;

import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.HealthStatus;
import com.czertainly.api.model.common.events.data.CertificateActionPerformedEventData;
import com.czertainly.api.model.common.events.data.CertificateStatusChangedEventData;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.np.email.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

@SpringBootTest
public class BaseSpringBootTest {

    @MockitoBean
    private HealthService healthService;

    @MockitoBean
    private JavaMailSender javaMailSender;

    protected static final String SUBJECT_DN = "CN=Test Certificate";
    protected static final String SERIAL_NUMBER = "a7fd36db9b9f818d401730cb76b58bd2586d8a4a";
    protected static final String ISSUER_DN = "CN=Test CA";
    protected static final String CERTIFICATE_UUID = "f9c2bb96-e3f8-4a96-beb2-36759eee8be3";
    protected static final String FINGERPRINT = "94f8aeb7d37961ea643ba8ee3e3a36386b3ca77d77be88975110def883423058";
    protected static final String NEW_STATUS = "REVOKED";
    protected static final String ERROR_MESSAGE = "Test error message";

    @BeforeEach
    public void setUp() {
        // Define behavior for the mock if needed
        HealthDto healthDto = new HealthDto();
        healthDto.setStatus(HealthStatus.OK);
        Mockito.when(healthService.checkHealth()).thenReturn(healthDto);
    }

    protected NotificationProviderNotifyRequestDto createCertStatusChangedNotificationRequest() {
        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setEmail("test@czertainly.com");
        recipient.setName("Test User");

        CertificateStatusChangedEventData notificationData = new CertificateStatusChangedEventData();
        notificationData.setSubjectDn(SUBJECT_DN);
        notificationData.setSerialNumber(SERIAL_NUMBER);
        notificationData.setIssuerDn(ISSUER_DN);
        notificationData.setCertificateUuid(UUID.fromString(CERTIFICATE_UUID));
        notificationData.setFingerprint(FINGERPRINT);
        notificationData.setNewStatus(NEW_STATUS);

        NotificationProviderNotifyRequestDto request = new NotificationProviderNotifyRequestDto();
        request.setEvent(ResourceEvent.CERTIFICATE_STATUS_CHANGED);
        request.setResource(Resource.CERTIFICATE);
        request.setRecipients(List.of(recipient));
        request.setNotificationData(notificationData);

        return request;
    }

    protected NotificationProviderNotifyRequestDto createCertActionPerformedNotificationRequest() {
        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setEmail("test@czertainly.com");
        recipient.setName("Test User");

        CertificateActionPerformedEventData notificationData = new CertificateActionPerformedEventData();
        notificationData.setSubjectDn(SUBJECT_DN);
        notificationData.setSerialNumber(SERIAL_NUMBER);
        notificationData.setIssuerDn(ISSUER_DN);
        notificationData.setCertificateUuid(UUID.fromString(CERTIFICATE_UUID));
        notificationData.setFingerprint(FINGERPRINT);
        notificationData.setErrorMessage(ERROR_MESSAGE);

        NotificationProviderNotifyRequestDto request = new NotificationProviderNotifyRequestDto();
        request.setEvent(ResourceEvent.CERTIFICATE_ACTION_PERFORMED);
        request.setResource(Resource.CERTIFICATE);
        request.setRecipients(List.of(recipient));
        request.setNotificationData(notificationData);

        return request;
    }

}
