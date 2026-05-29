package com.czertainly.np.email.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.content.CodeBlockAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceDto;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceRequestDto;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.np.email.dao.entity.NotificationInstance;
import com.czertainly.np.email.dao.repository.NotificationInstanceRepository;
import com.czertainly.np.email.exception.NotificationException;
import com.czertainly.np.email.service.AttributeService;
import com.czertainly.np.email.service.NotificationInstanceService;
import com.czertainly.np.email.util.TemplateUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NotificationInstanceServiceImpl implements NotificationInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationInstanceServiceImpl.class);

    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile(AttributeServiceImpl.EMAIL_ADDRESS_REGEX);

    // A single mapped-attribute content string may carry several addresses separated by ',' or ';'.
    private static final String EMAIL_ADDRESS_DELIMITER_REGEX = "[,;]";

    private NotificationInstanceRepository notificationInstanceRepository;

    private JavaMailSender emailSender;

    private AttributeService attributeService;

    @Autowired
    public void setNotificationInstanceRepository(NotificationInstanceRepository notificationInstanceRepository) {
        this.notificationInstanceRepository = notificationInstanceRepository;
    }

    @Autowired
    public void setEmailSender(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    public List<NotificationProviderInstanceDto> listNotificationInstances() {
        List<NotificationInstance> instances;
        instances = notificationInstanceRepository.findAll();
        if (!instances.isEmpty()) {
            return instances
                    .stream().map(NotificationInstance::mapToDto)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public NotificationProviderInstanceDto createNotificationInstance(NotificationProviderInstanceRequestDto request) throws AlreadyExistException {
        if (notificationInstanceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(NotificationInstance.class, request.getName());
        }

        final String emailFrom = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_NAME, request.getAttributes(), StringAttributeContentV2.class).getData();

        final String subject = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                AttributeServiceImpl.DATA_SUBJECT_NAME, request.getAttributes(), StringAttributeContentV2.class).getData();

        final String contentTemplate = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                AttributeServiceImpl.DATA_CONTENT_TEMPLATE_NAME, request.getAttributes(), CodeBlockAttributeContentV2.class).getData().getCode();

        NotificationInstance notificationInstance = new NotificationInstance();
        notificationInstance.setUuid(UUID.randomUUID().toString());
        notificationInstance.setName(request.getName());
        notificationInstance.setAttributes(AttributeDefinitionUtils.mergeAttributes(attributeService.getAttributes(request.getKind()), request.getAttributes()));
        notificationInstance.setEmailFrom(emailFrom);
        notificationInstance.setSubject(subject);
        notificationInstance.setContentTemplate(contentTemplate);

        notificationInstanceRepository.save(notificationInstance);

        return notificationInstance.mapToDto();
    }

    @Override
    public NotificationProviderInstanceDto getNotificationInstance(UUID uuid) throws NotFoundException {
        return notificationInstanceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(NotificationInstance.class, uuid))
                .mapToDto();
    }

    @Override
    public NotificationProviderInstanceDto updateNotificationInstance(UUID uuid, NotificationProviderInstanceRequestDto request) throws NotFoundException {
        NotificationInstance notificationInstance = notificationInstanceRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(NotificationInstance.class, uuid));

        final String emailFrom = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                AttributeServiceImpl.DATA_SENDER_EMAIL_ADDRESS_NAME, request.getAttributes(), StringAttributeContentV2.class).getData();

        final String subject = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                AttributeServiceImpl.DATA_SUBJECT_NAME, request.getAttributes(), StringAttributeContentV2.class).getData();

        final String contentTemplate = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                AttributeServiceImpl.DATA_CONTENT_TEMPLATE_NAME, request.getAttributes(), CodeBlockAttributeContentV2.class).getData().getCode();

        notificationInstance.setAttributes(AttributeDefinitionUtils.mergeAttributes(attributeService.getAttributes(request.getKind()), request.getAttributes()));
        notificationInstance.setEmailFrom(emailFrom);
        notificationInstance.setSubject(subject);
        notificationInstance.setContentTemplate(contentTemplate);

        notificationInstanceRepository.save(notificationInstance);

        return notificationInstance.mapToDto();
    }

    @Override
    public void removeNotificationInstance(UUID uuid) throws NotFoundException {
        NotificationInstance instance = notificationInstanceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(NotificationInstance.class, uuid));

        notificationInstanceRepository.delete(instance);
    }

    @Override
    public void sendNotification(UUID uuid, NotificationProviderNotifyRequestDto request) throws NotFoundException {
        logger.info("Received request to send email: event={}, resource={}", request.getEvent(), request.getResource());
        NotificationInstance notificationInstance = notificationInstanceRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(NotificationInstance.class, uuid));

        logger.debug("Request to send email received with the content: {}", request);

        MimeMessage mimeMessage = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        String htmlMsg = notificationInstance.getContentTemplate();
        String Subject = notificationInstance.getSubject();

        final String substitutedHtmlMsg = TemplateUtils.processFreeMarkerTemplate(htmlMsg, request);
        final String substitutedSubject = TemplateUtils.processFreeMarkerTemplate(Subject, request);

        logger.debug("Resolving recipients from request input: {}", request.getRecipients());
        final String[] recipients = getRecipients(request.getRecipients());

        try {
            helper.setText(substitutedHtmlMsg, true);
            helper.setTo(recipients);
            helper.setSubject(substitutedSubject);
            helper.setFrom(notificationInstance.getEmailFrom());
        } catch (MessagingException e) {
            logger.error("Error while sending email: {}", e.getMessage());
            throw new NotificationException("Error while sending email: " + e.getMessage());
        }

        emailSender.send(mimeMessage);
        logger.info("Notification email sent to {} recipient(s): {}", recipients.length, String.join(", ", recipients));
    }

    private String[] getRecipients(List<NotificationRecipientDto> recipients) {
        LinkedHashSet<String> to = new LinkedHashSet<>();
        for (NotificationRecipientDto recipient : recipients) {
            boolean emailProvided = false;
            if (!StringUtils.isBlank(recipient.getEmail())) {
                emailProvided = true;
                collectValidEmails(recipient.getEmail(), to);
            }
            if (recipient.getMappedAttributes() != null && !recipient.getMappedAttributes().isEmpty()) {
                List<StringAttributeContentV3> attributeContents = AttributeDefinitionUtils.getAttributeContentValue(
                        AttributeServiceImpl.DATA_RECIPIENT_EMAIL_ADDRESS_NAME, recipient.getMappedAttributes(), StringAttributeContentV3.class);
                if (attributeContents != null) {
                    for (StringAttributeContentV3 attributeContent : attributeContents) {
                        if (attributeContent != null && !StringUtils.isBlank(attributeContent.getData())) {
                            emailProvided = true;
                            collectValidEmails(attributeContent.getData(), to);
                        }
                    }
                }
            }
            if (!emailProvided) {
                String recipientName = StringUtils.isBlank(recipient.getName()) ? "<unnamed>" : recipient.getName();
                logger.warn("No email address is provided for recipient {}", recipientName);
                throw new ValidationException(List.of(
                        ValidationError.create("No email address was provided for recipient {}", recipientName)));
            }
        }
        if (to.isEmpty()) {
            logger.warn("No valid email address could be resolved from {} recipient(s); all addresses were empty or invalid", recipients.size());
            throw new ValidationException(List.of(
                    ValidationError.create("No valid email address was provided. All recipient addresses were empty or invalid.")));
        }
        return to.toArray(new String[0]);
    }

    /**
     * Splits a raw recipient value on ',' / ';', validates each address, and adds the valid ones
     * to {@code target}. Invalid addresses are logged and skipped so a single malformed entry does
     * not abort delivery to the remaining recipients.
     */
    private void collectValidEmails(String rawValue, Set<String> target) {
        for (String token : rawValue.split(EMAIL_ADDRESS_DELIMITER_REGEX)) {
            String email = token.trim();
            if (email.isEmpty()) {
                continue;
            }
            if (EMAIL_ADDRESS_PATTERN.matcher(email).matches()) {
                target.add(email);
            } else {
                logger.warn("Skipping invalid email address '{}' while preparing notification recipients", email);
            }
        }
    }
}
