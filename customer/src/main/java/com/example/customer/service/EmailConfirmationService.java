package com.example.customer.service;

import com.example.customer.domain.Customer;
import com.example.customer.domain.EmailConfirmation;
import com.example.customer.repository.CustomerRepository;
import com.example.customer.repository.EmailConfirmationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class EmailConfirmationService {
    private static final int CODE_LENGTH = 6;
    private final EmailConfirmationRepository confirmationRepository;
    private final CustomerRepository customerRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${email.confirmation.ttl-minutes:60}")
    private long ttlMinutes;

    public EmailConfirmationService(EmailConfirmationRepository confirmationRepository,
                                    CustomerRepository customerRepository) {
        this.confirmationRepository = confirmationRepository;
        this.customerRepository = customerRepository;
    }

    public EmailConfirmation createCode(String email, UUID customerId) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("Email is required");
        }
        EmailConfirmation confirmation = new EmailConfirmation();
        confirmation.setEmail(normalizedEmail);
        confirmation.setCustomerId(customerId);
        confirmation.setCode(generateCode());
        confirmation.setExpiresAt(OffsetDateTime.now().plusMinutes(ttlMinutes));
        return confirmationRepository.save(confirmation);
    }

    public boolean confirmCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code != null ? code.trim() : null;
        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(normalizedCode)) {
            return false;
        }
        Optional<EmailConfirmation> confirmationOpt =
                confirmationRepository.findFirstByEmailIgnoreCaseAndCodeAndExpiresAtAfterAndVerifiedAtIsNullOrderByCreatedAtDesc(
                        normalizedEmail,
                        normalizedCode,
                        OffsetDateTime.now()
                );
        if (confirmationOpt.isEmpty()) {
            return false;
        }
        EmailConfirmation confirmation = confirmationOpt.get();
        confirmation.setVerifiedAt(OffsetDateTime.now());
        confirmationRepository.save(confirmation);

        UUID customerId = confirmation.getCustomerId();
        Optional<Customer> customerOpt = customerId != null
                ? customerRepository.findById(customerId)
                : customerRepository.findByEmail(email);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setEmailVerified(true);
            customer.setEmailVerifiedAt(OffsetDateTime.now());
            customerRepository.save(customer);
        }
        return true;
    }

    private String generateCode() {
        int max = (int) Math.pow(10, CODE_LENGTH);
        int min = max / 10;
        int value = random.nextInt(max - min) + min;
        return Integer.toString(value);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
