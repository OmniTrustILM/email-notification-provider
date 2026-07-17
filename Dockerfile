# Build stage
FROM maven:3.9.16-eclipse-temurin-21 as build
COPY src /home/app/src
COPY pom.xml /home/app
COPY settings.xml /root/.m2/settings.xml
COPY docker /home/app/docker
RUN mvn -f /home/app/pom.xml clean package

# Package stage
FROM eclipse-temurin:21.0.11_10-jre-alpine

LABEL org.opencontainers.image.authors="ILM <ilm@omnitrust.com>"

# Upgrade OS packages to pick up security fixes not yet in the base image
RUN apk update && apk upgrade --no-cache

# add non root user otilm
RUN addgroup --system --gid 10001 otilm && adduser --system --home /opt/otilm --uid 10001 --ingroup otilm otilm

COPY --from=build /home/app/docker /
COPY --from=build /home/app/target/*.jar /opt/otilm/app.jar

WORKDIR /opt/email-notification-provider

ENV JDBC_URL=
ENV JDBC_USERNAME=
ENV JDBC_PASSWORD=
ENV DB_SCHEMA=emailnp
ENV PORT=8080
ENV SMTP_HOST=
ENV SMTP_PORT=587
ENV SMTP_USERNAME=
ENV SMTP_PASSWORD=
ENV SMTP_AUTH=true
ENV SMTP_TLS=true
ENV JAVA_OPTS=

USER 10001

ENTRYPOINT ["/opt/email-notification-provider/entry.sh"]
