package com.heditra.notificationservice.service.impl;

import com.heditra.events.payment.PaymentCompletedEvent;
import com.heditra.events.ticket.TicketCreatedEvent;
import com.heditra.events.user.UserCreatedEvent;
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
        return notificationRepository.findById(id)
                .orElseThrow(() -> new com.heditra.notificationservice.exception.NotificationNotFoundException("Notification not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByUserId(Long userId) {
        return notificationRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByType(NotificationType type) {
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

    @org.springframework.transaction.annotation.Transactional
    @KafkaListener(topics = "user-created", groupId = "notification-service-group")
    public void handleUserCreated(UserCreatedEvent event) {
        if (event == null || event.getUserId() == null) {
            log.error("Invalid UserCreatedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing userId");
        }
        
        log.info("Received user-created event for user: {}", event.getUserId());
        try {
            Notification notification = Notification.builder()
                    .userId(event.getUserId())
                    .message("Welcome! Your account has been created successfully.")
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .build();
            Notification savedNotification = createNotification(notification);
            sendNotification(savedNotification);
        } catch (Exception e) {
            log.error("Failed to handle user-created event for user: {}", event.getUserId(), e);
            throw e;
        }
    }

    @org.springframework.transaction.annotation.Transactional
    @KafkaListener(topics = "ticket-created", groupId = "notification-service-group")
    public void handleTicketCreated(TicketCreatedEvent event) {
        if (event == null || event.getTicketId() == null || event.getUserId() == null) {
            log.error("Invalid TicketCreatedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing required fields");
        }
        
        log.info("Received ticket-created event for ticket: {}", event.getTicketId());
        try {
            Notification notification = Notification.builder()
                    .userId(event.getUserId())
                    .message("Your ticket has been booked successfully.")
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .build();
            Notification savedNotification = createNotification(notification);
            sendNotification(savedNotification);
        } catch (Exception e) {
            log.error("Failed to handle ticket-created event for ticket: {}", event.getTicketId(), e);
            throw e;
        }
    }

    @org.springframework.transaction.annotation.Transactional
    @KafkaListener(topics = "payment-completed", groupId = "notification-service-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (event == null || event.getPaymentId() == null || event.getUserId() == null) {
            log.error("Invalid PaymentCompletedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing required fields");
        }
        
        log.info("Received payment-completed event for payment: {}", event.getPaymentId());
        try {
            Notification notification = Notification.builder()
                    .userId(event.getUserId())
                    .message("Payment processed successfully. Thank you!")
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .build();
            Notification savedNotification = createNotification(notification);
            sendNotification(savedNotification);
        } catch (Exception e) {
            log.error("Failed to handle payment-completed event for payment: {}", event.getPaymentId(), e);
            throw e;
        }
    }

    private boolean executeNotificationSending(Notification notification) {
        return true;
    }
}

