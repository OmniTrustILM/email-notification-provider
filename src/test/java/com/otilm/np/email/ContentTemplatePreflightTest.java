package com.otilm.np.email;

import com.otilm.np.email.dao.entity.NotificationInstance;
import com.otilm.np.email.dao.repository.NotificationInstanceRepository;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentTemplatePreflightTest {

    @Mock
    private NotificationInstanceRepository repository;

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
        Assertions.assertTrue(reported.get(0).contains("remove ?html"), reported.toString());
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
}
