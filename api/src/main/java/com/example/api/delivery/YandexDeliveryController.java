package com.example.api.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deliveries/yandex")
public class YandexDeliveryController {
    private final YandexDeliveryService deliveryService;

    @Autowired
    public YandexDeliveryController(YandexDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/offers")
    public ResponseEntity<YandexDeliveryService.DeliveryOffersResponse> getOffers(
            @RequestBody YandexDeliveryService.DeliveryOffersRequest request) {
        validateOffersRequest(request);
        return ResponseEntity.ok(deliveryService.getOffers(request));
    }

    @PostMapping("/pickup-points")
    public ResponseEntity<YandexDeliveryService.PickupPointsResult> getPickupPoints(
            @RequestBody YandexDeliveryService.PickupPointsQuery request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        return ResponseEntity.ok(deliveryService.getPickupPoints(request));
    }

    private void validateOffersRequest(YandexDeliveryService.DeliveryOffersRequest request) {
        if (request == null || request.cartId == null) {
            throw new IllegalArgumentException("Cart is required");
        }
        if (request.deliveryType == null) {
            throw new IllegalArgumentException("Delivery type is required");
        }
        if (!StringUtils.hasText(request.firstName)) {
            throw new IllegalArgumentException("Recipient first name is required");
        }
        if (!StringUtils.hasText(request.phone)) {
            throw new IllegalArgumentException("Recipient phone is required");
        }
        if (request.deliveryType == YandexDeliveryService.DeliveryType.COURIER) {
            if (!StringUtils.hasText(request.address)) {
                throw new IllegalArgumentException("Delivery address is required");
            }
        } else if (request.deliveryType == YandexDeliveryService.DeliveryType.PICKUP) {
            if (!StringUtils.hasText(request.pickupPointId)) {
                throw new IllegalArgumentException("Pickup point is required");
            }
        }
    }
}
