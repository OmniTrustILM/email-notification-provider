package com.otilm.np.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.otilm.np.email.dao.entity.NotificationInstance;
import com.otilm.np.email.dao.repository.NotificationInstanceRepository;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Holds the REST JSON exchanged with core, and the e-mail a notification turns into, to goldens recorded on the Spring
 * Boot 3.5 line, so a change of JSON library cannot alter what core reads or how this connector reads what core sends.
 * Record with {@code -Dwire.golden.write=true} on the 3.5 line only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:hsqldb:mem:wireGolden;sql.syntax_pgs=true")
class WireGoldenTest {

    private static final Path GOLDENS = Path.of("src/test/resources/wire");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern
            .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern EPOCH_MILLIS = Pattern.compile("\"timestamp\":[1-9]\\d*");
    /** The parser's own account of a body it could not read, which each parser words differently. */
    private static final Pattern PARSER_MESSAGE = Pattern
            .compile("\"(detail|error)\":\"(?:[^\"\\\\]|\\\\.)*Source: REDACTED(?:[^\"\\\\]|\\\\.)*\"");
    /**
     * FreeMarker's account of where a template broke, which names its token patterns and joins lines with the
     * platform's separator.
     */
    private static final Pattern TEMPLATE_PARSE_MESSAGE = Pattern
            .compile("(line \\d+, column \\d+:)(?:[^\"\\\\]|\\\\.)*\"");
    /** Lists the connector builds from hash maps, so their order differs from run to run. */
    private static final Set<String> UNORDERED = Set.of("endPoints");
    private static final String INSTANCES = "/v1/notificationProvider/notifications";
    private static final String UNKNOWN_UUID = "00000000-0000-4000-8000-000000000000";
    private static final String CERTIFICATE_UUID = "7de49ef9-8244-4e8f-95b8-82205ae0ad48";
    private static final String SENDER_UUID = "3a1aed46-7e45-4e13-b4c0-5d33e5dc73f8";
    private static final String SUBJECT_UUID = "cc56a091-3e99-446b-b366-1820afa75c97";
    private static final String TEMPLATE_UUID = "b31b3d66-a427-41ca-8f3f-7832e381e4c1";
    private static final String RECIPIENT_UUID = "522f2a57-4db2-408e-b7ed-7aee4bd96282";
    private static final String CONTENT_TEMPLATE = """
            <h3>Certificate ${notificationData.newStatus}</h3>
            <ul>
              <li>Subject: ${notificationData.subjectDn}</li>
              <li>Serial number: ${notificationData.serialNumber}</li>
              <li>Event: ${event}, resource: ${resource}</li>
              <li>Recipient: ${recipients[0].name}</li>
              <li>Owner: ${objectData.associations[0].name}</li>
              <li>Markup kept: ${notificationData.note?no_esc}</li>
            </ul>
            <a href="https://ilm.example.com/certificates/detail/${notificationData.certificateUuid}">Open</a>
            """;
    /** A template stored before values were escaped for it, which only an earlier version could have saved. */
    private static final String LEGACY_TEMPLATE = "<p>${notificationData.subjectDn?html}</p>";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Set<String> stableUuids = new HashSet<>(Set.of(UNKNOWN_UUID, CERTIFICATE_UUID));

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private JavaMailSenderImpl mailSender;

    @Autowired
    private NotificationInstanceRepository repository;

    /**
     * The definitions' UUIDs are constants, so they stay in the goldens; every other UUID is generated per run. A sent
     * e-mail is caught for its golden instead of delivered.
     */
    @BeforeEach
    void collectStableUuidsAndCatchMail() throws Exception {
        stableUuids.addAll(uuidsIn(get("/v1/notificationProvider/EMAIL/attributes").body()));
        stableUuids.addAll(uuidsIn(get("/v1/notificationProvider/EMAIL/attributes/mapping").body()));
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    /** The instance list is held to a golden, so no test leaves an instance behind for the next. */
    @AfterEach
    void removeInstances() {
        repository.deleteAll();
    }

    @Test
    void infoAndHealth() throws Exception {
        assertGolden("v1-info", get("/v1"));
        assertGolden("v1-health", get("/v1/health"));
    }

    @Test
    void attributeDefinitions() throws Exception {
        assertGolden("v1-attributes", get("/v1/notificationProvider/EMAIL/attributes"));
        assertGolden("v1-attributes-validate",
                post("/v1/notificationProvider/EMAIL/attributes/validate", attributes(CONTENT_TEMPLATE)));
        assertGolden("v1-attributes-invalid", post("/v1/notificationProvider/EMAIL/attributes/validate", """
                [%s]""".formatted(string(SUBJECT_UUID, "data_emailSubject", "Subject"))));
        assertGolden("v1-attributes-unsupported-kind", get("/v1/notificationProvider/SMS/attributes"));
        assertGolden("v1-mapping-attributes", get("/v1/notificationProvider/EMAIL/attributes/mapping"));
    }

    @Test
    void instanceLifecycle() throws Exception {
        HttpResponse<String> created = post(INSTANCES, instance("wire-golden", CONTENT_TEMPLATE));
        assertGolden("v1-instance", created);
        String instance = INSTANCES + "/" + JSON.readTree(created.body()).get("uuid").asText();
        assertGolden("v1-instance-get", get(instance));
        assertGolden("v1-instances", get(INSTANCES));
        assertGolden("v1-instance-update",
                put(instance, instance("wire-golden", "<p>${notificationData.subjectDn}</p>" + CONTENT_TEMPLATE)));

        assertGolden("v1-notify", post(instance + "/notify", notification()));
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertGolden("v1-notify-email", email(sent.getValue()));

        assertGolden("v1-instance-delete", delete(instance));
        assertGolden("v1-instance-deleted", get(instance));
    }

    @Test
    void templatesThatCannotRender() throws Exception {
        assertGolden("v1-instance-legacy-escape", post(INSTANCES, instance("wire-golden-legacy", LEGACY_TEMPLATE)));
        String instance = INSTANCES + "/"
                + JSON
                        .readTree(post(INSTANCES, instance("wire-golden-broken", CONTENT_TEMPLATE)).body())
                        .get("uuid")
                        .asText();
        assertGolden("v1-instance-update-syntax-error",
                put(instance, instance("wire-golden-broken", "<div>${unclosed</div>")));
        assertGolden("v1-notify-missing-value", post(instance + "/notify", """
                {"recipients":[{"name":"Jane","email":"jane@example.com"}],"event":"certificate_status_changed",
                 "resource":"certificates","notificationData":{"subjectDn":"CN=test"}}"""));

        NotificationInstance stored = new NotificationInstance();
        stored.setName("wire-golden-stored-legacy");
        stored.setEmailFrom("sender@example.com");
        stored.setSubject("Stored before escaping");
        stored.setContentTemplate(base64(LEGACY_TEMPLATE));
        repository.save(stored);
        assertGolden("v1-notify-legacy-escape", post(INSTANCES + "/" + stored.getUuid() + "/notify", notification()));
    }

    @Test
    void errors() throws Exception {
        assertGolden("v1-instance-not-found", get(INSTANCES + "/" + UNKNOWN_UUID));
        assertGolden("v1-instance-malformed-uuid", get(INSTANCES + "/not-a-uuid"));
        assertGolden("v1-instance-invalid", post(INSTANCES, """
                {"name":"wire-golden-invalid","kind":"EMAIL","attributes":[%s]}"""
                .formatted(string(SUBJECT_UUID, "data_emailSubject", "Subject"))));
        assertGolden("v1-instance-attribute-uuid-malformed", post(INSTANCES, """
                {"name":"wire-golden-malformed","kind":"EMAIL","attributes":[%s]}"""
                .formatted(string("not-a-uuid", "data_emailSubject", "Subject"))));
        assertGolden("v1-unreadable-body", post(INSTANCES, "{"));
        assertGolden("v1-notify-not-found", post(INSTANCES + "/" + UNKNOWN_UUID + "/notify", notification()));

        String instance = INSTANCES + "/" + JSON
                .readTree(
                        post(INSTANCES, instance("wire-golden-nobody", "<p>${notificationData.subjectDn}</p>")).body())
                .get("uuid")
                .asText();
        assertGolden("v1-notify-no-recipient", post(instance + "/notify", """
                {"recipients":[{"name":"Nobody","email":"not an address"}],"event":"certificate_status_changed",
                 "resource":"certificates","notificationData":{"subjectDn":"CN=nobody","newStatus":"revoked"}}"""));
        assertGolden("v1-notify-unknown-resource", post(instance + "/notify", """
                {"recipients":[{"name":"Jane","email":"jane@example.com"}],"event":"certificate_status_changed",
                 "resource":"noSuchResource","notificationData":{"subjectDn":"CN=test"}}"""));
    }

    /** A v2 string attribute as core writes it. */
    private static String string(String uuid, String name, String value) {
        return """
                {"uuid":"%s","name":"%s","contentType":"string","content":[{"data":"%s"}],"version":"v2"}"""
                .formatted(uuid, name, value);
    }

    private static String attributes(String contentTemplate) {
        return "[%s,%s,%s]"
                .formatted(string(SENDER_UUID, "data_senderEmailAddress", "sender@example.com"),
                        string(SUBJECT_UUID, "data_emailSubject",
                                "Certificate ${notificationData.subjectDn} is ${notificationData.newStatus}"),
                        """
                                {"uuid":"%s","name":"data_emailContentTemplate","contentType":"codeblock",
                                 "content":[{"data":{"language":"html","code":"%s"}}],"version":"v2"}"""
                                .formatted(TEMPLATE_UUID, base64(contentTemplate)));
    }

    private static String instance(String name, String contentTemplate) {
        return """
                {"name":"%s","kind":"EMAIL","attributes":%s}""".formatted(name, attributes(contentTemplate));
    }

    /**
     * A notification as core sends it, with values that have to be escaped, a recipient addressed through the mapped
     * attribute, and the object data a notification profile adds.
     */
    private static String notification() {
        return """
                {"recipients":[
                   {"name":"Jane <Doe>","email":"jane@example.com"},
                   {"name":"Mapped","mappedAttributes":[
                     {"uuid":"%s","name":"data_recipientEmailAddress","contentType":"string",
                      "content":[{"data":"first@example.com; second@example.com","contentType":"string"}],
                      "version":"v3"}]}],
                 "event":"certificate_status_changed","resource":"certificates",
                 "notificationData":{"oldStatus":"valid","newStatus":"revoked",
                   "subjectDn":"CN=<img src=x onerror=alert(1)>, O=\\"Quoted & Co\\"",
                   "serialNumber":"4a25c46b33ee052d242023f5dfaaafd3694858a4","issuerDn":"CN=issuer",
                   "certificateUuid":"%s","note":"<b>bold</b>"},
                 "objectData":{"subject":{"uuid":"%s","name":"CN=test","resource":"certificates"},
                   "associations":[{"uuid":"%s","name":"Owner & <Admin>","resource":"users"}]}}"""
                .formatted(RECIPIENT_UUID, CERTIFICATE_UUID, CERTIFICATE_UUID, UNKNOWN_UUID);
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest
                .newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> put(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest
                .newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).DELETE());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static Set<String> uuidsIn(String text) {
        Set<String> uuids = new HashSet<>();
        Matcher matcher = UUID_PATTERN.matcher(text);
        while (matcher.find()) {
            uuids.add(matcher.group());
        }
        return uuids;
    }

    private static ObjectNode email(MimeMessage message) throws Exception {
        ObjectNode email = JSON.createObjectNode();
        email.put("from", Arrays.stream(message.getFrom()).map(Address::toString).findFirst().orElse(null));
        email.set("to", JSON.valueToTree(Arrays.stream(message.getAllRecipients()).map(Address::toString).toList()));
        email.put("subject", message.getSubject());
        email.put("contentType", message.getDataHandler().getContentType());
        email.put("body", message.getContent().toString());
        return email;
    }

    /** Masks what differs between runs: generated identifiers and the time. */
    private String normalized(String body) {
        String masked = EPOCH_MILLIS.matcher(body).replaceAll("\"timestamp\":\"<millis>\"");
        masked = PARSER_MESSAGE.matcher(masked).replaceAll("\"$1\":\"<parser message>\"");
        masked = TEMPLATE_PARSE_MESSAGE.matcher(masked).replaceAll("$1 <parser message>\"");
        return UUID_PATTERN
                .matcher(masked)
                .replaceAll(match -> stableUuids.contains(match.group()) ? match.group() : "<uuid>");
    }

    /** Sorts every object's properties, since their order is not part of the contract and Jackson 3 changes it. */
    private static JsonNode canonical(JsonNode node) {
        if (node.isArray()) {
            return JSON.createArrayNode().addAll(node.valueStream().map(WireGoldenTest::canonical).toList());
        }
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            node
                    .properties()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(field -> sorted.set(field.getKey(), canonical(field.getValue())));
            UNORDERED.stream().filter(sorted::has).forEach(name -> {
                List<JsonNode> elements = new ArrayList<>(sorted.get(name).valueStream().toList());
                elements.sort(Comparator.comparing(JsonNode::toString));
                sorted.set(name, JSON.createArrayNode().addAll(elements));
            });
            return sorted;
        }
        return node;
    }

    private void assertGolden(String name, HttpResponse<String> response) throws IOException {
        ObjectNode actual = JSON.createObjectNode();
        actual.put("status", response.statusCode());
        actual.put("contentType", response.headers().firstValue("Content-Type").map(t -> t.split(";")[0]).orElse(""));
        String body = normalized(response.body());
        actual.set("body", body.isEmpty() ? null : canonical(JSON.readTree(body)));
        assertGolden(name, actual);
    }

    private void assertGolden(String name, ObjectNode actual) throws IOException {
        Path golden = GOLDENS.resolve(name + ".json");
        if (Boolean.getBoolean("wire.golden.write")) {
            Files.createDirectories(GOLDENS);
            Files.writeString(golden, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        JsonNode expected = JSON.readTree(Files.readString(golden));
        assertEquals(expected, actual, "Wire output drifted from " + golden + ": " + actual);
    }
}
