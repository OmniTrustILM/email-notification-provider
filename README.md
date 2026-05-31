# CZERTAINLY-Email-Notification-Provider

> This repository is part of the commercial open-source project CZERTAINLY. You can find more information about the project at [CZERTAINLY](https://github.com/CZERTAINLY/CZERTAINLY) repository, including the contribution guide.

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

Email Notification Provider implements `Notification Provider` interfaces. To learn more about the interfaces and end points, refer to the [CZERTAINLY Interfaces](https://github.com/CZERTAINLY/CZERTAINLY-Interfaces).

For more information, please refer to the [CZERTAINLY documentation](https://docs.czertainly.com).

## Docker container

Email Notification Provider `Connector` is provided as a Docker container. Use the `czertainly/czertainly-email-notification-provider:tagname` to pull the required image from the repository. It can be configured using the following environment variables:

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

## Attributes to configure

Configuring instance of this Email Notification Provider requires to provide the following attributes:

| Attribute            | Description                                            | Content Type |
|----------------------|--------------------------------------------------------|--------------|
| Sender email address | Email address from which the notification will be sent | `STRING`     |
| Subject              | Subject of the email that will be sent                 | `STRING`     |
| Content Template     | HTML template to be used to send information in email  | `CODEBLOCK`  |

Subject and Content Template attributes support variables that are replaced during notification processing. Variables are replaced with the data coming from the request for notification.
The varaibles are written in format `${variable}`.

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

<button onclick="location.href='https://yourdomain.com/administrator/#/certificates/detail/${notificationData.certificateUuid}'"
        type="button">
  Go To Certificate
</button>
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

<button onclick="location.href='https://localhost/administrator/#/certificates/detail/7de49ef9-8244-4e8f-95b8-82205ae0ad48'"
        type="button">
  Go To Certificate
</button>
```

## Recipients

The recipients of a notification are resolved from each entry in the `recipients` array of the notification request. For every recipient the provider collects email addresses from two sources:

- the `email` field — a **single** recipient address (populated by the platform from one user/role/group), and
- the **Recipient email address** mapped attribute (`data_recipientEmailAddress`, content type `STRING`), which may carry **multiple** addresses.

The mapped attribute accepts multiple addresses in two ways:

1. **Delimited string** — a single content value containing several addresses separated by `,` or `;` (surrounding whitespace is ignored), e.g. `alice@example.com, bob@example.com; carol@example.com`.
2. **Multiple content items** — the attribute carries several content items, each holding one address (or itself a delimited string).

The `email` field is treated as a single address and is not split on `,`/`;`.

Each resolved address is validated individually. Invalid addresses are skipped and logged (at `WARN`), so a single malformed entry never aborts delivery to the remaining valid recipients. If no valid address can be resolved for the whole request, the notification is rejected with a validation error.

## How to enable DEBUG logs

To enable DEBUG logs for the implementation of the email notification provider, you need to set the following environment variable:
```shell
LOGGING_LEVEL_COM_CZERTAINLY=DEBUG
```

To enable DEBUG logs for the mail sending process and SMTP related information, you need to set the following environment variable:
```shell
SPRING_MAIL_PROPERTIES_MAIL_DEBUG=true
```
