package com.example.api.order;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderControllerValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void checkoutRequestRequiresManualContactFieldsAndReceiptEmail() {
        OrderController.CheckoutRequest request = new OrderController.CheckoutRequest(
                UUID.randomUUID(),
                "",
                "",
                "",
                "",
                "https://example.test/order/{token}",
                "https://example.test/order/{token}",
                "EMBEDDED",
                false,
                null
        );

        Set<String> invalidFields = validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertTrue(invalidFields.contains("receiptEmail"));
        assertTrue(invalidFields.contains("customerName"));
        assertTrue(invalidFields.contains("phone"));
        assertTrue(invalidFields.contains("homeAddress"));
    }
}
