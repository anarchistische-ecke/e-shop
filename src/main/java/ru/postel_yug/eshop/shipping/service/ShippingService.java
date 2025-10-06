package ru.postel_yug.eshop.shipping.service;

import org.springframework.stereotype.Service;

@Service
public class ShippingService {

    public double calculateShippingCost(String deliveryType, double orderTotal) {
        if ("EXPRESS".equalsIgnoreCase(deliveryType)) {
            return 10.0;
        }
        if ("STANDARD".equalsIgnoreCase(deliveryType)) {
            return 5.0;
        }
        return 0.0; // free shipping for unknown type
    }
}


