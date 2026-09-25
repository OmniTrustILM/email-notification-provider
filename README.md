# Email Notification Provider

> This repository is part of the commercial open-source project ILM. You can find more information about the project at the [ILM](https://github.com/OmniTrustILM/ilm) repository, including the contribution guide.

Email Notification Provider `Connector` is the implementation of the following `Function Groups` and `Kinds`:

| Function Group          | Kind    |
|-------------------------|---------|
| `Notification Provider` | `EMAIL` |

Software Cryptography Provider implements cryptographic key management function based on the software keystore managed data. Therefore, it is not recommended to use this provider for the production environment, where you require higher protection of the cryptographic keys. The Software Cryptography Provider is intended for the development and testing purposes.

It is compatible with the `Notification Provider` interface. This connector provides the following features:
- Send email notifications

## Database requirements

Email Notification Provider `Connector` requires the PostgreSQL database to store the data.

## Interfaces

Email Notification Provider implements `Notification Provider` interfaces. To learn more about the interfaces and end points, refer to the [Interfaces](https://github.com/OmniTrustILM/interfaces).

For more information, please refer to the [documentation](https://docs.otilm.com).

## Docker container

Email Notification Provider `Connector` is provided as a Docker container. Use the `hub.omnitrustregistry.com/ilm/email-notification-provider:tagname` to pull the required image from the repository. It can be configured using the following environment variables:

| Variable        | Description                                              | Required                                           | Default value |
|-----------------|----------------------------------------------------------|----------------------------------------------------|---------------|
| `JDBC_URL`      | JDBC URL for database access                             | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`         |
| `JDBC_USERNAME` | Username to access the database                          | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`         |
| `JDBC_PASSWORD` | Password to access the database                          | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`         |
| `DB_SCHEMA`     | Database schema to use                                   | ![](https://img.shields.io/badge/-NO-red.svg)      | `emailnp`     |
| `PORT`          | Port where the service is exposed                        | ![](https://img.shields.io/badge/-NO-red.svg)      | `8080`        |
| `JAVA_OPTS`     | Customize Java system properties for running application | ![](https://img.shields.io/badge/-NO-red.svg)      | `N/A`         |
| `SMTP_HOST`     | SMTP host                                                | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`         |
| `SMTP_PORT`     | SMTP port                                                | ![](https://img.shields.io/badge/-NO-red.svg)      | `587`         |
| `SMTP_USERNAME` | SMTP username                                            | ![](https://img.shields.io/badge/-NO-red.svg)      | `N/A`         |
| `SMTP_PASSWORD` | SMTP password                                            | ![](https://img.shields.io/badge/-NO-red.svg)      | `N/A`         |
| `SMTP_AUTH`     | SMTP authentication                                      | ![](https://img.shields.io/badge/-NO-red.svg)      | `true`        |
| `SMTP_TLS`      | SMTP TLS                                                 | ![](https://img.shields.io/badge/-NO-red.svg)      | `true`        |
| `NOTIFICATION_LOG_REQUEST_PAYLOAD` | Include the notification request in DEBUG logs. See [How to enable DEBUG logs](#how-to-enable-debug-logs) | ![](https://img.shields.io/badge/-NO-red.svg) | `false` |

## Attributes to configure

Configuring instance of this Email Notification Provider requires to provide the following attributes:

| Attribute            | Description                                            | Content Type |
|----------------------|--------------------------------------------------------|--------------|
| Sender email address | Email address from which the notification will be sent | `STRING`     |
| Subject              | Subject of the email that will be sent                 | `STRING`     |
| Content Template     | HTML template to be used to send information in email  | `CODEBLOCK`  |

Subject and Content Template attributes support variables that are replaced during notification processing. Variables are replaced with the data coming from the request for notification.
The variables are written in format `${variable}`.

Values inserted into the Content Template are HTML-escaped, so text written by a user - a comment body, for instance - is delivered as text and never as live markup.

Put a value in element text, or in a quoted value of an inert attribute such as `alt`, `title` or `class`. Those are the two positions HTML escaping makes safe, and it makes a value safe in no other:

| Position | Why escaping is not enough |
|----------|----------------------------|
| An unquoted attribute | A space in the value starts a new attribute |
| An event handler, `onclick` and the like | The browser decodes the entities, then runs what is left as JavaScript |
| Inside `<script>` or `<style>` | The content is script or style, not HTML, so entities are not decoded and the value is read as code |
| A URL-bearing attribute, `href` or `src` | Escaping does not restrict the scheme, so a value may supply `javascript:` or point anywhere |

A value belongs in those positions only after it has been checked for that position: a URL accepted only with a scheme you allow, and script or style never assembled from a value at all. Where a template must build a link, write the scheme and host itself and interpolate only the part that cannot change the target, as the example below does with a UUID.

A value that has to be delivered as markup opts out with `${variable?no_esc}`, and what it carries is then the template author's responsibility.

A template written before escaping arrived may still carry `?html`, which FreeMarker does not accept where values are escaped for it. Such a template is refused rather than adjusted, because what its escaped value is used for cannot be seen where the built-in is written: a template that compares or measures the escaped text would quietly behave differently if the built-in were simply removed.

The refusal happens where it can be acted on, and reads the same in each place: saving a notification instance whose content template carries `?html` is rejected with the edit, every stored template that will not render is named in the log at startup, and a notification that reaches one is answered with the same message and an unprocessable-entity status, since the template is configuration rather than a fault of the connector. The edit is to remove `?html` and let the value be escaped on its way out, `?no_esc` where a value has to stay markup, and `?esc?markup_string` with `?no_esc` closing the expression where the escaped text is read further on.

The following is an example of the Content Template with variables:
```htlm
<h3>Certificate status change!</h3>

<p>
  The certificate identified as:
  <ul>
    <li>Subject: ${notificationData.subjectDn}</li>
    <li>Serial Number: ${notificationData.serialNumber}</li>
    <li>Issuer: ${notificationData.issuerDn}</li>
  </ul>
</p>

<p>
  <a href="https://yourdomain.com/administrator/#/certificates/detail/${notificationData.certificateUuid}">Go To Certificate</a>
</p>
```

The variables will be replaced with values in the notification request, for example:
```json
{
    "recipients": [
        {
            "name": "John Doe",
            "email": "john.doe@example.com",
            "mappedAttributes": [
            ]
        }
    ],
    "eventType": "certificate_status_changed",
    "resource": "certificates",
    "notificationData": {
        "oldStatus": "valid",
        "newStatus": "expiring",
        "subjectDn": "CN=test",
        "serialNumber": "4a25c46b33ee052d242023f5dfaaafd3694858a4",
        "issuerDn": "CN=issuer",
        "certificateUuid": "7de49ef9-8244-4e8f-95b8-82205ae0ad48"
    }
}
```

Will parse the final notification Content Template to be:
```html
<h3>Certificate status change!</h3>

<p>
  The certificate identified as:
  <ul>
    <li>Subject: CN=test</li>
    <li>Serial Number: 4a25c46b33ee052d242023f5dfaaafd3694858a4</li>
    <li>Issuer: CN=issuer</li>
  </ul>
</p>

<p>
  <a href="https://localhost/administrator/#/certificates/detail/7de49ef9-8244-4e8f-95b8-82205ae0ad48">Go To Certificate</a>
</p>
```

## Recipients

The recipients of a notification are resolved from each entry in the `recipients` array of the notification request. For every recipient the provider collects email addresses from two sources:

- the `email` field — a **single** recipient address (populated by the platform from one user/role/group), and
- the **Recipient email address** mapped attribute (`data_recipientEmailAddress`, content type `STRING`), which may carry **multiple** addresses.

The mapped attribute accepts multiple addresses in two ways:

1. **Delimited string** — a single content value containing several addresses separated by `,` or `;` (surrounding whitespace is ignored), e.g. `alice@example.com, bob@example.com; carol@example.com`.
2. **Multiple content items** — the attribute carries several content items, each holding one address (or itself a delimited string).

The `email` field is treated as a single address and is not split on `,`/`;`.

Each resolved address is validated individually. Invalid addresses, and recipients that supply no address at all, are skipped and logged (at `WARN`), so a single malformed or empty entry never aborts delivery to the remaining valid recipients. Only when no valid address can be resolved for the whole request is the notification rejected with a validation error.

## How to enable DEBUG logs

To enable DEBUG logs for the implementation of the email notification provider, you need to set the following environment variable:
```shell
LOGGING_LEVEL_COM_OTILM=DEBUG
```

DEBUG logs describe each notification by its identifiers only — event, resource, recipient count, and whether notification data is present. The notification request itself is never written to the logs at any level unless payload logging is switched on explicitly:

```shell
NOTIFICATION_LOG_REQUEST_PAYLOAD=true
```

> **Warning**
> With payload logging enabled, DEBUG logs contain the complete notification request, including data that can be sensitive — for example the one-time credential of certificate registration events, or object data enabled on the notification profile. Enable it only while troubleshooting, and treat the logs accordingly.

Template failures do not need it: they are reported at ERROR level with the template identifier, the event and resource, and the position of the failing expression in the template, without exposing any payload.

To enable DEBUG logs for the mail sending process and SMTP related information, you need to set the following environment variable:
```shell
SPRING_MAIL_PROPERTIES_MAIL_DEBUG=true
```
