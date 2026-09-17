package com.otilm.np.email;

import com.otilm.np.email.dao.entity.NotificationInstance;
import com.otilm.np.email.dao.repository.NotificationInstanceRepository;
import com.otilm.np.email.util.TemplateUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports the stored content templates that will not render, once, at startup. A template written before values were
 * escaped for it may keep a legacy escaping built-in this connector cannot drop, and without this an operator would
 * learn of it from the first notification that failed to arrive.
 */
@Component
public class ContentTemplatePreflight {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentTemplatePreflight.class);

    private final NotificationInstanceRepository notificationInstanceRepository;

    public ContentTemplatePreflight(NotificationInstanceRepository notificationInstanceRepository) {
        this.notificationInstanceRepository = notificationInstanceRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportUnrenderableTemplates() {
        for (String instanceAndReason : unrenderableTemplates()) {
            LOGGER.warn("Notification instance {}", instanceAndReason);
        }
    }

    /** One entry per instance whose content template will not render, naming the instance and why. */
    List<String> unrenderableTemplates() {
        List<String> reported = new ArrayList<>();
        for (NotificationInstance instance : notificationInstanceRepository.findAll()) {
            failureOf(instance).ifPresent(reason -> reported.add("'%s': %s".formatted(instance.getName(), reason)));
        }
        return reported;
    }

    private Optional<String> failureOf(NotificationInstance instance) {
        try {
            return TemplateUtils.contentTemplateFailure(instance.getContentTemplate());
        } catch (RuntimeException e) {
            // A template that cannot even be read is worth naming too, and must not stop the startup of the rest
            return Optional.of(e.getClass().getSimpleName());
        }
    }
}
