package com.example.api.shipment;

import com.example.api.notification.NotificationOrchestrator;
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
    private final NotificationOrchestrator notificationOrchestrator;

    @Autowired
    public ShipmentController(ShipmentService shipmentService,
                              OrderService orderService,
                              NotificationOrchestrator notificationOrchestrator) {
        this.shipmentService = shipmentService;
        this.orderService = orderService;
        this.notificationOrchestrator = notificationOrchestrator;
    }

    @PostMapping
    public ResponseEntity<Shipment> shipOrder(@Valid @RequestBody ShipmentRequest request) {
        Shipment shipment = shipmentService.shipOrder(
                request.getOrderId(), request.getCarrier(), request.getTrackingNumber());
        Order updated = orderService.findById(request.getOrderId());
        notificationOrchestrator.orderShipped(updated, shipment);
        return ResponseEntity.status(HttpStatus.CREATED).body(shipment);
    }

    @PutMapping("/{shipmentId}/delivered")
    public ResponseEntity<Void> markShipmentDelivered(@PathVariable UUID shipmentId) {
        ShipmentService.DeliveryTransition transition = shipmentService.markDelivered(shipmentId);
        Order updated = orderService.findById(transition.orderId());
        notificationOrchestrator.orderStatusChanged(updated, transition.previousStatus());
        return ResponseEntity.noContent().build();
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
