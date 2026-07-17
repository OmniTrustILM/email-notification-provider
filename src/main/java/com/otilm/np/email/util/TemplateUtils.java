package com.otilm.np.email.util;

import com.otilm.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.otilm.np.email.exception.NotificationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public class TemplateUtils {

    private static final Logger logger = LoggerFactory.getLogger(TemplateUtils.class);

    public static String processFreeMarkerTemplate(String data, NotificationProviderNotifyRequestDto request) {
        // Convert request to a Map instead of using the JSON node directly
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> dataModel;
        try {
            dataModel = objectMapper.convertValue(request, new TypeReference<>() {});
        } catch (IllegalArgumentException e) {
            logger.error("Error while converting request to Map: {}, {}", request, e.getMessage());
            throw new NotificationException("Error while converting request to Map: " + request + ", " + e.getMessage());
        }

        // Prepare FreeMarker configuration
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        // Create template from the HTML string
        Template template;
        try {
            template = new Template("contentTemplate", new StringReader(data), cfg);
        } catch (IOException e) {
            logger.error("Error while creating FreeMarker template: {}, {}", data, e.getMessage());
            throw new NotificationException("Error while creating FreeMarker template: " + e.getMessage(), e);
        }

        // Process the template with the data model
        StringWriter stringWriter = new StringWriter();
        try {
            template.process(dataModel, stringWriter);
        } catch (TemplateException | IOException e) {
            logger.error("Error while processing FreeMarker template: {}, {}", request, e.getMessage());
            throw new NotificationException("Error while processing FreeMarker template: " + e.getMessage());
        }

        return stringWriter.toString();
    }

}
