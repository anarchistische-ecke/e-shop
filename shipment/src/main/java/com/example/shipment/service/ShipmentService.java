package com.example.shipment.service;

import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.shipment.domain.Shipment;
import com.example.shipment.repository.ShipmentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class ShipmentService {
    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;

    @Autowired
    public ShipmentService(ShipmentRepository shipmentRepository, OrderRepository orderRepository) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
    }

    public Shipment shipOrder(UUID orderId, String carrier, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        Shipment shipment = new Shipment(orderId, carrier, trackingNumber);
        shipment = shipmentRepository.save(shipment);
        // update order
        order.setShipmentId(shipment.getId());
        order.setStatus("SHIPPED");
        orderRepository.save(order);
        return shipment;
    }

    public DeliveryTransition markDelivered(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        shipment.setDeliveredAt(OffsetDateTime.now());
        shipmentRepository.save(shipment);
        Order order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + shipment.getOrderId()));
        String previousStatus = order.getStatus();
        order.setStatus("DELIVERED");
        orderRepository.save(order);
        return new DeliveryTransition(order.getId(), previousStatus, order.getStatus());
    }

    public record DeliveryTransition(UUID orderId, String previousStatus, String currentStatus) {}
}
