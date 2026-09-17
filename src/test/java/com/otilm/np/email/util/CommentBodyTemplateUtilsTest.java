package com.otilm.np.email.util;

import com.otilm.api.model.common.events.data.CommentEventData;
import com.otilm.np.email.exception.NotificationException;
import com.otilm.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.otilm.api.model.connector.notification.NotificationRecipientDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.other.ResourceEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommentBodyTemplateUtilsTest {

    private static final String HOSTILE_BODY = "<b>bold</b> and <img src=x onerror=alert(1)> & \"quotes\"";

    private NotificationProviderNotifyRequestDto request;

    @BeforeEach
    void setUpCommentRequest() {
        NotificationRecipientDto recipient = new NotificationRecipientDto();
        recipient.setEmail("test@ilm.com");
        recipient.setName("Test User");

        CommentEventData data = new CommentEventData();
        data.setCommentUuid(UUID.randomUUID());
        data.setResource(Resource.RA_PROFILE);
        data.setObjectUuid(UUID.randomUUID());
        data.setObjectName("tst-ra-profile");
        data.setAuthorUuid(UUID.randomUUID());
        data.setAuthorUsername("tst-author");
        data.setBody(HOSTILE_BODY);

        request = new NotificationProviderNotifyRequestDto();
        request.setEvent(ResourceEvent.COMMENT_CREATED);
        request.setResource(Resource.COMMENT);
        request.setRecipients(List.of(recipient));
        request.setNotificationData(data);
    }

    @Test
    void commentBodyArrivesAsTextInTheHtmlContent() {
        String html = TemplateUtils.renderHtml("email content", "<div>${notificationData.body}</div>", request);

        Assertions.assertFalse(html.contains("<b>"), html);
        Assertions.assertFalse(html.contains("<img"), html);
        Assertions.assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt;"), html);
        Assertions.assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"), html);
        Assertions.assertTrue(html.contains("&amp;"), html);
    }

    @Test
    void aTemplateEscapingByHandIsRefusedWhereverItDoesSo() {
        for (String template : List
                .of("<div>${notificationData.body?html}</div>",
                        "<div>${notificationData.body ? html}</div>",
                        "<div>\t${notificationData.body?html}</div>",
                        "<#assign escaped = notificationData.body?html><div>${escaped}</div>",
                        "<#if notificationData.body?html == \"x\">y</#if>",
                        "<#macro show v><div>${v}</div></#macro><@show v=notificationData.body?html/>",
                        "<div>${notificationData.body?html?upper_case}</div>")) {
            NotificationException refused = Assertions
                    .assertThrows(NotificationException.class,
                            () -> TemplateUtils.renderHtml("email content", template, request), template);

            Assertions.assertTrue(refused.getMessage().contains("remove ?html"), template + " -> " + refused.getMessage());
            Assertions.assertTrue(refused.getMessage().contains("?no_esc"), template + " -> " + refused.getMessage());
        }
    }

    @Test
    void aBrokenTemplateIsNotSentToRemoveAnUnrelatedQueryString() {
        NotificationException refused = Assertions
                .assertThrows(NotificationException.class, () -> TemplateUtils
                        .renderHtml("email content",
                                "<a href=\"https://example.test/view?html=true\">${unclosed</a>", request));

        Assertions.assertFalse(refused.getMessage().contains("?esc?markup_string"), refused.getMessage());
        Assertions.assertFalse(refused.getMessage().contains("remove"), refused.getMessage());
    }

    @Test
    void templateTextIsDeliveredAsWritten() {
        String html = TemplateUtils
                .renderHtml("email content",
                        "<a href=\"https://example.test/view?html=true\">${notificationData.objectName}</a>", request);

        Assertions.assertTrue(html.contains("view?html=true"), html);
        Assertions.assertTrue(html.contains("tst-ra-profile"), html);
    }

    @Test
    void aTemplateCanStillInsertTrustedMarkupDeliberately() {
        String html = TemplateUtils.renderHtml("email content", "<div>${notificationData.body?no_esc}</div>", request);

        Assertions.assertTrue(html.contains("<b>bold</b>"), html);
    }

    @Test
    void theSubjectIsPlainTextAndNotEscaped() {
        String subject = TemplateUtils.renderPlainText("email subject", "Comment on ${notificationData.objectName} by ${notificationData.authorUsername}: ${notificationData.body}", request);

        Assertions.assertTrue(subject.contains(HOSTILE_BODY), subject);
        Assertions.assertFalse(subject.contains("&lt;"), subject);
    }
}
