package com.miniagoda.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.payment.dto.PaymentIntentRequest;
import com.miniagoda.payment.dto.PaymentIntentResponse;
import com.miniagoda.payment.dto.RefundRequest;
import com.miniagoda.payment.dto.RefundResponse;
import com.miniagoda.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/create-intent")
    public ResponseEntity<PaymentIntentResponse> createPayment(@RequestBody PaymentIntentRequest request) throws Exception {
        return ResponseEntity.ok(paymentService.createPayment(request));
    }

    @PostMapping("/payments/refund")
    public ResponseEntity<RefundResponse> refund(@RequestBody RefundRequest request) throws Exception {
        return ResponseEntity.ok(paymentService.refund(request));
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleWebHook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) throws Exception {
        paymentService.handleWebHook(payload, sigHeader);
        return ResponseEntity.ok("OK");
    }
}