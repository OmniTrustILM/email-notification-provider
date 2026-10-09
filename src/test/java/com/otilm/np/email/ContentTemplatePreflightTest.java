package com.otilm.np.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otilm.np.email.dao.entity.NotificationInstance;
import com.otilm.np.email.dao.repository.NotificationInstanceRepository;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentTemplatePreflightTest {

    @Mock
    private NotificationInstanceRepository repository;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureLogs() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(ContentTemplatePreflight.class)).addAppender(logAppender);
    }

    @AfterEach
    void releaseLogs() {
        ((Logger) LoggerFactory.getLogger(ContentTemplatePreflight.class)).detachAppender(logAppender);
        logAppender.stop();
    }

    private static NotificationInstance instance(String name, String contentTemplate) {
        NotificationInstance instance = new NotificationInstance();
        instance.setName(name);
        instance.setContentTemplate(Base64.getEncoder().encodeToString(contentTemplate.getBytes()));
        return instance;
    }

    @Test
    void namesOnlyTheInstancesWhoseTemplateWillNotRender() {
        when(repository.findAll())
                .thenReturn(List
                        .of(instance("plain", "<div>${notificationData.body}</div>"),
                                instance("escaped by hand", "<div>${notificationData.body?html}</div>"),
                                instance("escape read on", "<div>${notificationData.body?html?upper_case}</div>"),
                                instance("malformed", "<div>${unclosed</div>")));

        List<String> reported = new ContentTemplatePreflight(repository).unrenderableTemplates();

        Assertions.assertEquals(3, reported.size(), reported.toString());
        Assertions.assertTrue(reported.get(0).startsWith("'escaped by hand'"), reported.toString());
        Assertions.assertTrue(reported.get(0).contains("Remove the ?html"), reported.toString());
        Assertions.assertTrue(reported.get(1).startsWith("'escape read on'"), reported.toString());
        Assertions.assertTrue(reported.get(2).startsWith("'malformed'"), reported.toString());
    }

    @Test
    void anInstanceWhoseTemplateCannotBeReadIsNamedRatherThanThrown() {
        NotificationInstance unreadable = new NotificationInstance();
        unreadable.setName("not base64");
        unreadable.setContentTemplate("<div>not base64</div>");
        when(repository.findAll()).thenReturn(List.of(unreadable));

        List<String> reported = new ContentTemplatePreflight(repository).unrenderableTemplates();

        Assertions.assertEquals(1, reported.size());
        Assertions.assertTrue(reported.getFirst().startsWith("'not base64'"), reported.toString());
    }

    @Test
    void reportsEachInstanceWhoseTemplateWillNotRenderAtWarn() {
        when(repository.findAll())
                .thenReturn(List
                        .of(instance("plain", "<div>${notificationData.body}</div>"),
                                instance("malformed", "<div>${unclosed</div>")));

        new ContentTemplatePreflight(repository).reportUnrenderableTemplates();

        Assertions.assertEquals(1, logAppender.list.size(), logAppender.list.toString());
        ILoggingEvent event = logAppender.list.getFirst();
        Assertions.assertEquals(Level.WARN, event.getLevel());
        Assertions
                .assertTrue(event
                        .getFormattedMessage()
                        .startsWith("Notification instance 'malformed': The email content template cannot be rendered"),
                        event.getFormattedMessage());
    }

    /** The check only reports; a store it cannot read must not keep the connector from starting. */
    @Test
    void anUnreadableStoreIsReportedWithoutStoppingStartup() {
        when(repository.findAll()).thenThrow(new DataAccessResourceFailureException("connection reset"));
        ContentTemplatePreflight preflight = new ContentTemplatePreflight(repository);

        Assertions.assertDoesNotThrow(preflight::reportUnrenderableTemplates);

        Assertions.assertEquals(1, logAppender.list.size(), logAppender.list.toString());
        Assertions.assertEquals(Level.WARN, logAppender.list.getFirst().getLevel());
        Assertions
                .assertEquals("Stored content templates could not be checked: DataAccessResourceFailureException",
                        logAppender.list.getFirst().getFormattedMessage());
    }
}
