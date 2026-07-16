package com.otilm.np.email.service.impl;

import com.otilm.api.model.common.HealthDto;
import com.otilm.api.model.common.HealthStatus;
import com.otilm.np.email.service.HealthService;
import com.otilm.np.email.service.NotificationInstanceService;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger logger = LoggerFactory.getLogger(HealthServiceImpl.class);

    private JavaMailSenderImpl emailSender;

    private NotificationInstanceService notificationInstanceService;

    @Autowired
    public void setNotificationInstanceService(NotificationInstanceService notificationInstanceService) {
        this.notificationInstanceService = notificationInstanceService;
    }

    @Autowired
    public void setEmailSender(JavaMailSenderImpl emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public HealthDto checkHealth() {
        HealthDto health = new HealthDto();

        Map<String, HealthDto> parts = new HashMap<>();
        parts.put("smtp", testConnection());
        parts.put("database", checkDbStatus());

        health.setParts(parts);

        // set the overall status
        health.setStatus(HealthStatus.OK);
        for (var entry : health.getParts().entrySet()) {
            if (entry.getValue().getStatus() == HealthStatus.NOK) {
                health.setStatus(HealthStatus.NOK);
                break;
            }
        }
        return health;
    }

    private HealthDto testConnection() {
        HealthDto h = new HealthDto();
        try {
            emailSender.testConnection();
            h.setStatus(HealthStatus.OK);
            h.setDescription("Connection to SMTP server is ok");
        } catch (MessagingException e) {
            logger.debug("Connection to SMTP server failed: " + e);
            h.setStatus(HealthStatus.NOK);
            h.setDescription(e.getMessage());
        }
        return h;
    }

    private HealthDto checkDbStatus() {
        HealthDto h = new HealthDto();
        try {
            notificationInstanceService.listNotificationInstances();
            h.setStatus(HealthStatus.OK);
            h.setDescription("Database connection ok");
        } catch (Exception e) {
            logger.debug("Database connection failed: " + e);
            h.setStatus(HealthStatus.NOK);
            h.setDescription(e.getMessage());
        }
        return h;
    }

}
