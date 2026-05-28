package com.czertainly.np.email.api;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.connector.NotificationInstanceController;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceDto;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceRequestDto;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.np.email.service.AttributeService;
import com.czertainly.np.email.service.NotificationInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationInstanceControllerImpl implements NotificationInstanceController {

    private NotificationInstanceService notificationInstanceService;
    private AttributeService attributeService;

    @Autowired
    public void setNotificationInstanceService(NotificationInstanceService notificationInstanceService) {
        this.notificationInstanceService = notificationInstanceService;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    //////// NotificationInstanceController implementation ////////

    @Override
    public List<NotificationProviderInstanceDto> listNotificationInstances() {
        return notificationInstanceService.listNotificationInstances();
    }

    @Override
    public NotificationProviderInstanceDto getNotificationInstance(String uuid) throws NotFoundException {
        return notificationInstanceService.getNotificationInstance(UUID.fromString(uuid));
    }

    @Override
    public NotificationProviderInstanceDto createNotificationInstance(NotificationProviderInstanceRequestDto request) throws AlreadyExistException {
        if (!attributeService.validateAttributes(
                request.getKind(), request.getAttributes())) {
            throw new ValidationException("Notification instance attributes validation failed.");
        }
        return notificationInstanceService.createNotificationInstance(request);
    }

    @Override
    public NotificationProviderInstanceDto updateNotificationInstance(String uuid, NotificationProviderInstanceRequestDto request) throws NotFoundException {
        if (!attributeService.validateAttributes(
                request.getKind(), request.getAttributes())) {
            throw new ValidationException("Notification instance attributes validation failed.");
        }
        return notificationInstanceService.updateNotificationInstance(UUID.fromString(uuid), request);
    }

    @Override
    public void removeNotificationInstance(String uuid) throws NotFoundException {
        notificationInstanceService.removeNotificationInstance(UUID.fromString(uuid));
    }

    @Override
    public void sendNotification(String uuid, NotificationProviderNotifyRequestDto request) throws NotFoundException {
        notificationInstanceService.sendNotification(UUID.fromString(uuid), request);
    }

    @Override
    public List<DataAttribute> listMappingAttributes(String kind) {
        return attributeService.listMappingAttributes(kind);
    }
}
