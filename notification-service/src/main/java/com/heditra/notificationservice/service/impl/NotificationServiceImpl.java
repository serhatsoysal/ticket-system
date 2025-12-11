package com.heditra.notificationservice.service.impl;

import com.heditra.notificationservice.model.Notification;
import com.heditra.notificationservice.model.NotificationStatus;
import com.heditra.notificationservice.model.NotificationType;
import com.heditra.notificationservice.repository.NotificationRepository;
import com.heditra.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public Notification createNotification(Notification notification) {
        log.info("Creating notification for user ID: {}", notification.getUserId());

        notification.setStatus(NotificationStatus.PENDING);
        Notification savedNotification = notificationRepository.save(notification);

        log.info("Notification created successfully with ID: {}", savedNotification.getId());
        return savedNotification;
    }

    @Override
    @Transactional(readOnly = true)
    public Notification getNotificationById(Long id) {
        log.debug("Fetching notification by ID: {}", id);
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getAllNotifications() {
        log.debug("Fetching all notifications");
        return notificationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByUserId(Long userId) {
        log.debug("Fetching notifications for user ID: {}", userId);
        return notificationRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByStatus(NotificationStatus status) {
        log.debug("Fetching notifications by status: {}", status);
        return notificationRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByType(NotificationType type) {
        log.debug("Fetching notifications by type: {}", type);
        return notificationRepository.findByType(type);
    }

    @Override
    @Transactional
    public void sendNotification(Notification notification) {
        log.info("Sending notification ID: {}", notification.getId());

        boolean sent = executeNotificationSending(notification);

        if (sent) {
            notification.setStatus(NotificationStatus.SENT);
            log.info("Notification sent successfully with ID: {}", notification.getId());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            log.warn("Failed to send notification with ID: {}", notification.getId());
        }

        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id) {
        log.info("Deleting notification with ID: {}", id);

        Notification notification = getNotificationById(id);
        notificationRepository.delete(notification);

        log.info("Notification deleted successfully with ID: {}", id);
    }

    @KafkaListener(topics = "user-created", groupId = "notification-service-group")
    public void handleUserCreated(Object userData) {
        log.info("Received user-created event: {}", userData);
        Notification notification = Notification.builder()
                .userId(1L)
                .message("Welcome! Your account has been created successfully.")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .build();
        createNotification(notification);
        sendNotification(notification);
    }

    @KafkaListener(topics = "ticket-created", groupId = "notification-service-group")
    public void handleTicketCreated(Object ticketData) {
        log.info("Received ticket-created event: {}", ticketData);
        Notification notification = Notification.builder()
                .userId(1L)
                .message("Your ticket has been booked successfully.")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .build();
        createNotification(notification);
        sendNotification(notification);
    }

    @KafkaListener(topics = "payment-success", groupId = "notification-service-group")
    public void handlePaymentSuccess(Object paymentData) {
        log.info("Received payment-success event: {}", paymentData);
        Notification notification = Notification.builder()
                .userId(1L)
                .message("Payment processed successfully. Thank you!")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .build();
        createNotification(notification);
        sendNotification(notification);
    }

    private boolean executeNotificationSending(Notification notification) {
        log.debug("Executing notification sending via {}: {}", 
                notification.getType(), notification.getMessage());
        return true;
    }
}

