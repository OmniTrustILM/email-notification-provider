package com.otilm.np.email.util;

import com.otilm.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.exception.ValidationError;
import com.otilm.np.email.exception.NotificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.OutputFormat;
import freemarker.core.ParseException;
import freemarker.core.PlainTextOutputFormat;
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
import java.util.Optional;

public class TemplateUtils {

    private static final Logger logger = LoggerFactory.getLogger(TemplateUtils.class);

    /**
     * Shared mapper: constructing one per call is expensive, and the registered modules keep
     * types such as {@code java.time} serializable instead of degrading DEBUG output to the
     * unserializable placeholder.
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final String LEGACY_ESCAPE = "html";
    private static final String LEGACY_ESCAPE_EDIT = " accepted now that values are escaped on their way in. Remove"
            + " the ?html. A value that has to stay markup takes ?no_esc instead, and one whose escaped text is read"
            + " further on, compared or measured, takes ?esc?markup_string with ?no_esc closing the expression.";

    private TemplateUtils() {
    }

    /**
     * Payload-free summary of a notification request: identifiers and counts only. This is what
     * DEBUG logging reports unless payload logging is explicitly switched on.
     */
    public static String summarizeRequest(NotificationProviderNotifyRequestDto request) {
        return "event=%s, resource=%s, recipients=%d, notificationData=%s".formatted(
                request.getEvent(), request.getResource(),
                request.getRecipients() == null ? 0 : request.getRecipients().size(),
                request.getNotificationData() == null ? "absent" : "present");
    }

    /**
     * Serializes the whole notification request for opt-in DEBUG logging — the sanctioned way
     * to inspect payload content when debugging. Uses explicit JSON serialization because the
     * request's {@code toString} deliberately excludes the payload-bearing fields, which would
     * make DEBUG output silently incomplete. A request that cannot be serialized — including one
     * whose own accessors fail — yields a payload-free placeholder rather than disrupting the
     * send flow.
     */
    public static String describeRequestForDebug(NotificationProviderNotifyRequestDto request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException | RuntimeException e) {
            return "unserializable notification request (" + e.getClass().getSimpleName() + ")";
        }
    }

    /**
     * Renders the HTML content template. Every interpolated value is HTML-escaped, so text a user authored - a
     * comment body - arrives as text and never as live markup; a template that must insert trusted markup says so with
     * {@code ?no_esc}. A template written before escaping arrived, which escapes with {@code ?html} itself, is refused
     * with the edit it needs: FreeMarker does not accept that built-in where values are escaped for it.
     */
    public static String renderHtml(String templateLabel, String templateSource,
            NotificationProviderNotifyRequestDto request) {
        return render(templateLabel, templateSource, request, HTMLOutputFormat.INSTANCE);
    }

    /** Renders a plain-text template, such as the subject line; values are inserted as they are. */
    public static String renderPlainText(String templateLabel, String templateSource,
            NotificationProviderNotifyRequestDto request) {
        return render(templateLabel, templateSource, request, PlainTextOutputFormat.INSTANCE);
    }

    /**
     * Renders the given FreeMarker template against the notification request in the given output format.
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
    private static String render(String templateLabel, String templateSource,
            NotificationProviderNotifyRequestDto request, OutputFormat outputFormat) {
        // Convert request to a Map instead of using the JSON node directly
        Map<String, Object> dataModel;
        try {
            dataModel = OBJECT_MAPPER.convertValue(request, new TypeReference<>() {});
        } catch (IllegalArgumentException e) {
            // Only the exception type is reported: Jackson conversion messages can embed model
            // paths or content, and this internal failure has no template-author diagnostics value.
            logger.error("Failed to build the {} template data model: event={}, resource={}, error={}",
                    templateLabel, request.getEvent(), request.getResource(), e.getClass().getSimpleName());
            throw new NotificationException("Failed to build the " + templateLabel + " template data model (" + e.getClass().getSimpleName() + ")");
        }

        Configuration cfg = configuration(outputFormat);

        // Create template from the HTML string
        Template template;
        try {
            template = parse(templateLabel, templateSource, cfg);
        } catch (IOException e) {
            // Parsing happens before the data model is bound, so this message describes the
            // operator's own template only and cannot quote payload values.
            logger.error("Failed to parse the {} template: event={}, resource={}, error={}",
                    templateLabel, request.getEvent(), request.getResource(), e.getMessage());
            throw new ValidationException(ValidationError.create(parseFailureDescription(templateLabel, e)));
        }

        // Process the template with the data model
        StringWriter stringWriter = new StringWriter();
        try {
            template.process(dataModel, stringWriter);
        } catch (TemplateException | IOException e) {
            String diagnostics = renderFailureDiagnostics(e);
            logger.error("Failed to render the {} template: event={}, resource={}, error={}",
                    templateLabel, request.getEvent(), request.getResource(), diagnostics);
            throw new ValidationException(ValidationError
                    .create("The " + templateLabel + " template cannot be rendered: " + diagnostics));
        }

        return stringWriter.toString();
    }

    /**
     * Why this content template will not render, for a caller checking one that is already stored: the message the
     * operator would be given when a notification reaches it, or empty when it renders.
     */
    public static Optional<String> contentTemplateFailure(String templateSource) {
        try {
            parse("email content", templateSource, configuration(HTMLOutputFormat.INSTANCE));
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(parseFailureDescription("email content", e));
        }
    }

    private static Configuration configuration(OutputFormat outputFormat) {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setOutputFormat(outputFormat);
        // A parse failure's column is read as an offset into the source, so a tab must count as the one character it
        // is rather than as a jump to the next tab stop
        cfg.setTabSize(1);
        return cfg;
    }

    /**
     * Parses the template, refusing the legacy {@code ?html} built-in in terms the operator can act on. FreeMarker
     * reports where it refused, which is checked against the source so that only that failure is answered with the
     * edit and an unrelated syntax error is reported as it stands.
     */
    private static Template parse(String templateLabel, String templateSource, Configuration cfg) throws IOException {
        try {
            return new Template(templateLabel, new StringReader(templateSource), cfg);
        } catch (ParseException e) {
            throw legacyEscapeAt(templateSource, e) < 0 ? e : new UnsupportedLegacyEscapeException(e);
        }
    }

    /** Where the legacy escaping built-in the parser refused begins, or -1 when that is not what it refused. */
    private static int legacyEscapeAt(String source, ParseException failure) {
        int name = offsetOf(source, failure.getLineNumber(), failure.getColumnNumber());
        if (name < 1 || !source.startsWith(LEGACY_ESCAPE, name)) {
            return -1;
        }
        int question = skipWhitespaceBack(source, name - 1);
        return question >= 0 && source.charAt(question) == '?' ? name : -1;
    }

    private static int skipWhitespaceBack(String source, int from) {
        int at = from;
        while (at >= 0 && Character.isWhitespace(source.charAt(at))) {
            at--;
        }
        return at;
    }

    /** Where the parser's line and column land in the source, or -1 when they name no position in it. */
    private static int offsetOf(String source, int line, int column) {
        int offset = 0;
        for (int passed = 1; passed < line; passed++) {
            int newline = source.indexOf('\n', offset);
            if (newline < 0) {
                return -1;
            }
            offset = newline + 1;
        }
        int at = offset + column - 1;
        return at <= source.length() ? at : -1;
    }

    /**
     * FreeMarker refuses the legacy {@code ?html} once values are escaped for it, and says so in terms of its own
     * built-ins. The edit is named only when that is what failed, so a template that merely carries the text
     * somewhere, in a URL for one, is not sent to alter it.
     */
    /**
     * What the operator is told about a template that will not parse. Their own syntax error is reported in
     * FreeMarker's words, which name the position and the construct; the legacy escape is reported in ours, since its
     * refusal is this connector's and the edit is what matters.
     */
    private static String parseFailureDescription(String templateLabel, IOException failure) {
        if (failure instanceof UnsupportedLegacyEscapeException legacyEscape) {
            return "The %s template cannot be rendered: line %d, column %d escapes a value with ?html, which is not"
                    .formatted(templateLabel, legacyEscape.line(), legacyEscape.column())
                    + LEGACY_ESCAPE_EDIT;
        }
        return "The " + templateLabel + " template cannot be parsed: " + failure.getMessage();
    }

    /** A refused legacy escape, carrying where it was written so the operator is sent to it. */
    private static final class UnsupportedLegacyEscapeException extends IOException {

        private final transient int line;
        private final transient int column;

        private UnsupportedLegacyEscapeException(ParseException refusal) {
            super(refusal.getMessage(), refusal);
            this.line = refusal.getLineNumber();
            this.column = refusal.getColumnNumber();
        }

        private int line() {
            return line;
        }

        private int column() {
            return column;
        }
    }

    /**
     * Payload-free description of a rendering failure. FreeMarker quotes the value that failed to
     * evaluate in its message — {@code ${credential?number}} embeds the credential verbatim — so
     * only the exception type and the position in the template are reported. The template is the
     * operator's own content, so the position identifies the failing expression for them.
     */
    static String renderFailureDiagnostics(Exception e) {
        if (e instanceof TemplateException templateException) {
            return "%s at line %s, column %s".formatted(e.getClass().getSimpleName(),
                    templateException.getLineNumber(), templateException.getColumnNumber());
        }
        return e.getClass().getSimpleName();
    }

}
