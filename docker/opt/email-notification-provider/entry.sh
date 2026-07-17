#!/bin/sh

appHome="/opt/email-notification-provider"
source ${appHome}/static-functions

log "INFO" "Launching the Email Notification Provider"
java $JAVA_OPTS -jar ./app.jar

#exec "$@"