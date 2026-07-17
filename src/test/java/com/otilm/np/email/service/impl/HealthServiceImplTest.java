package com.otilm.np.email.service.impl;

import com.otilm.api.model.common.HealthDto;
import com.otilm.api.model.common.HealthStatus;
import com.otilm.np.email.service.NotificationInstanceService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceImplTest {

    private JavaMailSenderImpl emailSender;
    private NotificationInstanceService notificationInstanceService;
    private HealthServiceImpl healthService;

    @BeforeEach
    void setUp() {
        emailSender = mock(JavaMailSenderImpl.class);
        notificationInstanceService = mock(NotificationInstanceService.class);
        healthService = new HealthServiceImpl();
        healthService.setEmailSender(emailSender);
        healthService.setNotificationInstanceService(notificationInstanceService);
    }

    @Test
    void checkHealthReportsOkWhenSmtpAndDatabaseAreReachable() throws MessagingException {
        doNothing().when(emailSender).testConnection();
        when(notificationInstanceService.listNotificationInstances()).thenReturn(List.of());

        HealthDto health = healthService.checkHealth();

        assertEquals(HealthStatus.OK, health.getStatus());
        assertEquals(HealthStatus.OK, health.getParts().get("smtp").getStatus());
        assertEquals(HealthStatus.OK, health.getParts().get("database").getStatus());
    }

    @Test
    void checkHealthReportsNokWhenSmtpConnectionFails() throws MessagingException {
        doThrow(new MessagingException("SMTP unreachable")).when(emailSender).testConnection();
        when(notificationInstanceService.listNotificationInstances()).thenReturn(List.of());

        HealthDto health = healthService.checkHealth();

        assertEquals(HealthStatus.NOK, health.getStatus());
        assertEquals(HealthStatus.NOK, health.getParts().get("smtp").getStatus());
        assertEquals("SMTP unreachable", health.getParts().get("smtp").getDescription());
        assertEquals(HealthStatus.OK, health.getParts().get("database").getStatus());
    }

    @Test
    void checkHealthReportsNokWhenDatabaseAccessFails() throws MessagingException {
        doNothing().when(emailSender).testConnection();
        when(notificationInstanceService.listNotificationInstances())
                .thenThrow(new RuntimeException("Database unreachable"));

        HealthDto health = healthService.checkHealth();

        assertEquals(HealthStatus.NOK, health.getStatus());
        assertEquals(HealthStatus.OK, health.getParts().get("smtp").getStatus());
        assertEquals(HealthStatus.NOK, health.getParts().get("database").getStatus());
        assertEquals("Database unreachable", health.getParts().get("database").getDescription());
    }

}
