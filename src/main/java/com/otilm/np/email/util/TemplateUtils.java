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

    private TemplateUtils() {
    }

    /**
     * Serializes the whole notification request for opt-in DEBUG logging — the sanctioned way
     * to inspect payload content when debugging. Uses explicit JSON serialization because the
     * request's {@code toString} deliberately excludes the payload-bearing fields, which would
     * make DEBUG output silently incomplete. Never throws: an unserializable request yields a
     * payload-free placeholder.
     */
    public static String describeRequestForDebug(NotificationProviderNotifyRequestDto request) {
        try {
            return new ObjectMapper().writeValueAsString(request);
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return "unserializable notification request (" + e.getClass().getSimpleName() + ")";
        }
    }

    /**
     * Renders the given FreeMarker template against the notification request.
     *
     * <p>Failure logs and exception messages carry the template label, the event and resource
     * identifiers, and the underlying error only — never the request payload or the data model.
     * The request's {@code notificationData} and {@code objectData} can hold sensitive values
     * (for example a certificate-registration credential), and the thrown exception's message
     * becomes this connector's HTTP error response toward the platform, so payload content must
     * not reach either. Full request visibility for debugging remains available through the
     * DEBUG-level logging of the send flow.</p>
     *
     * @param templateLabel identifies the rendered template in errors, e.g. "email subject"
     */
    public static String processFreeMarkerTemplate(String templateLabel, String templateSource, NotificationProviderNotifyRequestDto request) {
        // Convert request to a Map instead of using the JSON node directly
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> dataModel;
        try {
            dataModel = objectMapper.convertValue(request, new TypeReference<>() {});
        } catch (IllegalArgumentException e) {
            // Only the exception type is reported: Jackson conversion messages can embed model
            // paths or content, and this internal failure has no template-author diagnostics value.
            logger.error("Failed to build the {} template data model: event={}, resource={}, error={}",
                    templateLabel, request.getEvent(), request.getResource(), e.getClass().getSimpleName());
            throw new NotificationException("Failed to build the " + templateLabel + " template data model (" + e.getClass().getSimpleName() + ")");
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
            template = new Template(templateLabel, new StringReader(templateSource), cfg);
        } catch (IOException e) {
            logger.error("Failed to parse the {} template: event={}, resource={}, error={}",
                    templateLabel, request.getEvent(), request.getResource(), e.getMessage());
            throw new NotificationException("Failed to parse the " + templateLabel + " template: " + e.getMessage(), e);
        }

        // Process the template with the data model
        StringWriter stringWriter = new StringWriter();
        try {
            template.process(dataModel, stringWriter);
        } catch (TemplateException | IOException e) {
            logger.error("Failed to render the {} template: event={}, resource={}, error={}",
                    templateLabel, request.getEvent(), request.getResource(), e.getMessage());
            throw new NotificationException("Failed to render the " + templateLabel + " template: " + e.getMessage());
        }

        return stringWriter.toString();
    }

}
