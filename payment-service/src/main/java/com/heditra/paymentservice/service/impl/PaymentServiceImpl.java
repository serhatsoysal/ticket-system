package com.heditra.paymentservice.service.impl;

import com.heditra.paymentservice.model.Payment;
import com.heditra.paymentservice.model.PaymentStatus;
import com.heditra.paymentservice.repository.PaymentRepository;
import com.heditra.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String PAYMENT_CREATED_TOPIC = "payment-created";
    private static final String PAYMENT_SUCCESS_TOPIC = "payment-success";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";
    private static final String PAYMENT_REFUNDED_TOPIC = "payment-refunded";

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public Payment createPayment(Payment payment) {
        log.info("Creating new payment for ticket ID: {}", payment.getTicketId());

        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(generateTransactionId());

        Payment savedPayment = paymentRepository.save(payment);
        publishPaymentEvent(PAYMENT_CREATED_TOPIC, savedPayment);

        log.info("Payment created successfully with ID: {}", savedPayment.getId());
        return savedPayment;
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {
        log.debug("Fetching payment by ID: {}", id);
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByTicketId(Long ticketId) {
        log.debug("Fetching payment by ticket ID: {}", ticketId);
        return paymentRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new RuntimeException("Payment not found for ticket ID: " + ticketId));
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByTransactionId(String transactionId) {
        log.debug("Fetching payment by transaction ID: {}", transactionId);
        return paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found for transaction ID: " + transactionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        log.debug("Fetching all payments");
        return paymentRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByUserId(Long userId) {
        log.debug("Fetching payments for user ID: {}", userId);
        return paymentRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        log.debug("Fetching payments by status: {}", status);
        return paymentRepository.findByStatus(status);
    }

    @Override
    @Transactional
    public Payment processPayment(Long paymentId) {
        log.info("Processing payment with ID: {}", paymentId);

        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Payment is not in PENDING status");
        }

        boolean paymentSuccess = executePaymentGateway(payment);

        if (paymentSuccess) {
            payment.setStatus(PaymentStatus.SUCCESS);
            publishPaymentEvent(PAYMENT_SUCCESS_TOPIC, payment);
            log.info("Payment processed successfully with ID: {}", paymentId);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            publishPaymentEvent(PAYMENT_FAILED_TOPIC, payment);
            log.warn("Payment processing failed for ID: {}", paymentId);
        }

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment refundPayment(Long paymentId) {
        log.info("Refunding payment with ID: {}", paymentId);

        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new RuntimeException("Only successful payments can be refunded");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment refundedPayment = paymentRepository.save(payment);
        publishPaymentEvent(PAYMENT_REFUNDED_TOPIC, refundedPayment);

        log.info("Payment refunded successfully with ID: {}", paymentId);
        return refundedPayment;
    }

    @Override
    @Transactional
    public void deletePayment(Long id) {
        log.info("Deleting payment with ID: {}", id);

        Payment payment = getPaymentById(id);
        paymentRepository.delete(payment);

        log.info("Payment deleted successfully with ID: {}", id);
    }

    @KafkaListener(topics = "ticket-created", groupId = "payment-service-group")
    public void handleTicketCreated(Object ticketData) {
        log.info("Received ticket-created event: {}", ticketData);
    }

    private String generateTransactionId() {
        return UUID.randomUUID().toString();
    }

    private boolean executePaymentGateway(Payment payment) {
        log.debug("Executing payment gateway for transaction: {}", payment.getTransactionId());
        return true;
    }

    private void publishPaymentEvent(String topic, Payment payment) {
        kafkaTemplate.send(topic, payment.getId().toString(), payment)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Payment event published to topic: {}", topic);
                    } else {
                        log.error("Failed to publish payment event to topic: {}", topic, ex);
                    }
                });
    }
}

