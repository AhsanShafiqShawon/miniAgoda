package com.miniagoda.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.payment.entity.Payment;
import com.miniagoda.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Payment findByGatewayPaymentId(String id);
    void updateStatus(UUID id, PaymentStatus status);
}