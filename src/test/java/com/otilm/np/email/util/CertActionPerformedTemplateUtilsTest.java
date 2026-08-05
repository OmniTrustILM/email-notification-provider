package com.otilm.np.email.util;

import com.otilm.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CertActionPerformedTemplateUtilsTest extends BaseSpringBootTest {

    private static final String HTML_TEMPLATE = """
<h3>Certificate status change!</h3>

<p>
  The certificate identified as:
  <ul>
    <li>Subject: ${notificationData.subjectDn}</li>
    <li>Serial Number: ${notificationData.serialNumber}</li>
    <li>Issuer: ${notificationData.issuerDn}</li>
    <#if notificationData.errorMessage?has_content>
    <li>Error: ${notificationData.errorMessage}</li>
    </#if>
  </ul>
</p>

<button onclick="location.href='https://yourdomain.com/administrator/#/certificates/detail/${notificationData.certificateUuid}'"
        type="button">
  Go To Certificate
</button>
""";

    private NotificationProviderNotifyRequestDto request;

    @BeforeEach
    public void setUp() {
        request = createCertActionPerformedNotificationRequest();
    }

    @Test
    public void testTemplateUtils() {
        String processedHtmlTemplate = TemplateUtils.processFreeMarkerTemplate("email content", HTML_TEMPLATE, request);

        Assertions.assertTrue(processedHtmlTemplate.contains("Subject: " + SUBJECT_DN));
        Assertions.assertTrue(processedHtmlTemplate.contains("Serial Number: " + SERIAL_NUMBER));
        Assertions.assertTrue(processedHtmlTemplate.contains("Issuer: " + ISSUER_DN));
        Assertions.assertTrue(processedHtmlTemplate.contains("https://yourdomain.com/administrator/#/certificates/detail/" + CERTIFICATE_UUID));
        Assertions.assertTrue(processedHtmlTemplate.contains("Error: " + ERROR_MESSAGE));
    }

}
