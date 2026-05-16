package com.miniagoda.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {}