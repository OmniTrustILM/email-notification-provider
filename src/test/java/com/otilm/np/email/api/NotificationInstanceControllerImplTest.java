package com.otilm.np.email.api;

import com.otilm.api.exception.ValidationError;
import com.otilm.api.exception.ValidationException;
import com.otilm.np.email.exception.NotificationException;
import com.otilm.np.email.service.AttributeService;
import com.otilm.np.email.service.NotificationInstanceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The status a template failure answers with is what the platform records it by: a 422 is kept as the connector's
 * sentence at WARN, anything else as an error. These tests pin that contract at the HTTP boundary.
 */
@WebMvcTest(NotificationInstanceControllerImpl.class)
class NotificationInstanceControllerImplTest {

    private static final String TEMPLATE_FAILURE = "The email content template cannot be rendered: line 1";
    private static final String NOTIFY_REQUEST = """
            {"event": "certificate_status_changed", "resource": "certificates"}""";
    private static final String INSTANCE_REQUEST = """
            {"name": "email", "kind": "EMAIL", "attributes": []}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationInstanceService notificationInstanceService;

    @MockitoBean
    private AttributeService attributeService;

    @Test
    void notifyAnswersATemplateThatWillNotRenderWith422() throws Exception {
        UUID uuid = UUID.randomUUID();
        doThrow(new ValidationException(ValidationError.create(TEMPLATE_FAILURE)))
                .when(notificationInstanceService)
                .sendNotification(eq(uuid), any());

        mvc
                .perform(post("/v1/notificationProvider/notifications/{uuid}/notify", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTIFY_REQUEST))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$[0]").value(TEMPLATE_FAILURE));
    }

    /** Building the data model is the connector's own work, so its failure stays an internal error. */
    @Test
    void notifyAnswersAnInternalFailureWith500() throws Exception {
        UUID uuid = UUID.randomUUID();
        doThrow(new NotificationException("Failed to build the email content template data model"))
                .when(notificationInstanceService)
                .sendNotification(eq(uuid), any());

        mvc
                .perform(post("/v1/notificationProvider/notifications/{uuid}/notify", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTIFY_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(101));
    }

    @Test
    void createAnswersATemplateThatWillNotParseWith422() throws Exception {
        when(attributeService.validateAttributes(any(), any())).thenReturn(true);
        when(notificationInstanceService.createNotificationInstance(any()))
                .thenThrow(new ValidationException(ValidationError.create(TEMPLATE_FAILURE)));

        mvc
                .perform(post("/v1/notificationProvider/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INSTANCE_REQUEST))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$[0]").value(TEMPLATE_FAILURE));
    }
}
