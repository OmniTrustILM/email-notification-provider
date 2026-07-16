package com.otilm.np.email.util;

import com.otilm.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.otilm.np.email.exception.NotificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateUtilsErrorTest {

    private NotificationProviderNotifyRequestDto request() {
        return new NotificationProviderNotifyRequestDto();
    }

    @Test
    void malformedTemplateThrowsCreationError() {
        NotificationException ex = assertThrows(NotificationException.class,
                () -> TemplateUtils.processFreeMarkerTemplate("${unclosed", request()));
        assertTrue(ex.getMessage().contains("creating FreeMarker template"));
    }

    @Test
    void unresolvedReferenceThrowsProcessingError() {
        NotificationException ex = assertThrows(NotificationException.class,
                () -> TemplateUtils.processFreeMarkerTemplate("${totallyMissingVar}", request()));
        assertTrue(ex.getMessage().contains("processing FreeMarker template"));
    }

}
