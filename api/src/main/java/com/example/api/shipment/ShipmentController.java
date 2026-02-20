package com.example.api.shipment;

import com.example.api.notification.EmailService;
import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import com.example.shipment.domain.Shipment;
import com.example.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final OrderService orderService;
    private final EmailService emailService;

    @Autowired
    public ShipmentController(ShipmentService shipmentService,
                              OrderService orderService,
                              EmailService emailService) {
        this.shipmentService = shipmentService;
        this.orderService = orderService;
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<Shipment> shipOrder(@Valid @RequestBody ShipmentRequest request) {
        Order beforeUpdate = orderService.findById(request.getOrderId());
        Shipment shipment = shipmentService.shipOrder(
                request.getOrderId(), request.getCarrier(), request.getTrackingNumber());
        Order updated = orderService.findById(request.getOrderId());
        notifyStatusChangeIfNeeded(updated, beforeUpdate.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(shipment);
    }

    @PutMapping("/{shipmentId}/delivered")
    public ResponseEntity<Void> markShipmentDelivered(@PathVariable UUID shipmentId) {
        ShipmentService.DeliveryTransition transition = shipmentService.markDelivered(shipmentId);
        Order updated = orderService.findById(transition.orderId());
        notifyStatusChangeIfNeeded(updated, transition.previousStatus());
        return ResponseEntity.noContent().build();
    }

    private void notifyStatusChangeIfNeeded(Order order, String previousStatus) {
        if (order == null || order.getReceiptEmail() == null || order.getReceiptEmail().isBlank()) {
            return;
        }
        if (previousStatus != null && order.getStatus() != null && previousStatus.equalsIgnoreCase(order.getStatus())) {
            return;
        }
        emailService.sendOrderStatusUpdatedEmail(order, order.getReceiptEmail(), previousStatus);
    }

    public static class ShipmentRequest {
        @NotNull
        private UUID orderId;
        @NotBlank
        private String carrier;
        @NotBlank
        private String trackingNumber;

        public UUID getOrderId() {
            return orderId;
        }

        public void setOrderId(UUID orderId) {
            this.orderId = orderId;
        }

        public String getCarrier() {
            return carrier;
        }

        public void setCarrier(String carrier) {
            this.carrier = carrier;
        }

        public String getTrackingNumber() {
            return trackingNumber;
        }

        public void setTrackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
        }
    }
}
