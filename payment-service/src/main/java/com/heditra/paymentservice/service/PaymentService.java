package com.heditra.paymentservice.service;

import com.heditra.paymentservice.model.Payment;
import com.heditra.paymentservice.model.PaymentStatus;

import java.util.List;

public interface PaymentService {

    Payment createPayment(Payment payment);

    Payment getPaymentById(Long id);

    Payment getPaymentByTicketId(Long ticketId);

    Payment getPaymentByTransactionId(String transactionId);

    List<Payment> getAllPayments();

    List<Payment> getPaymentsByUserId(Long userId);

    List<Payment> getPaymentsByStatus(PaymentStatus status);

    Payment processPayment(Long paymentId);

    Payment refundPayment(Long paymentId);

    void deletePayment(Long id);
}

