package com.heditra.notificationservice.service;

import com.heditra.notificationservice.model.Notification;
import com.heditra.notificationservice.model.NotificationStatus;
import com.heditra.notificationservice.model.NotificationType;

import java.util.List;

public interface NotificationService {

    Notification createNotification(Notification notification);

    Notification getNotificationById(Long id);

    List<Notification> getAllNotifications();

    List<Notification> getNotificationsByUserId(Long userId);

    List<Notification> getNotificationsByStatus(NotificationStatus status);

    List<Notification> getNotificationsByType(NotificationType type);

    void sendNotification(Notification notification);

    void deleteNotification(Long id);
}

