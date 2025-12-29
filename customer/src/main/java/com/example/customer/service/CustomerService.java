package com.example.customer.service;

import com.example.common.domain.Address;
import com.example.customer.domain.Customer;
import com.example.customer.repository.CustomerRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Customer registerCustomer(String firstName, String lastName, String email, String rawPassword, Address address) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("Email and password must be provided");
        }
        if (customerRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }
        String hashed = passwordEncoder.encode(rawPassword);
        Customer customer = buildCustomer(firstName, lastName, email, null, address);
        customer.setPassword(hashed);
        return customerRepository.save(customer);
    }

    public Customer registerCustomerByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Phone must be provided");
        }
        Optional<Customer> existing = customerRepository.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }
        String fallbackEmail = phone.replaceAll("\\D", "") + "@phone.local";
        String hashed = passwordEncoder.encode(UUID.randomUUID().toString());
        Customer customer = buildCustomer("Guest", "Customer", fallbackEmail, phone, emptyAddress());
        customer.setPassword(hashed);
        return customerRepository.save(customer);
    }

    public Optional<Customer> authenticateByEmail(String email, String rawPassword) {
        return authenticate(email, rawPassword);
    }

    public Optional<Customer> authenticate(String email, String rawPassword) {
        return customerRepository.findByEmail(email)
                .filter(customer -> {
                    String encoded = customer.getPassword();
                    return StringUtils.hasText(encoded) && passwordEncoder.matches(rawPassword, encoded);
                });
    }

    public Customer findOrCreateByYandex(String yandexId, String email, String firstName, String lastName) {
        if (!StringUtils.hasText(yandexId) && !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Yandex ID or email must be provided");
        }
        String resolvedEmail = StringUtils.hasText(email) ? email : yandexId + "@yandex.local";
        String safeFirst = defaultIfBlank(firstName, "Yandex");
        String safeLast = defaultIfBlank(lastName, "User");

        Optional<Customer> existingById = StringUtils.hasText(yandexId)
                ? customerRepository.findByYandexId(yandexId)
                : Optional.empty();
        if (existingById.isPresent()) {
            return updateProfile(existingById.get(), resolvedEmail, safeFirst, safeLast, customer -> {
                customer.setYandexId(yandexId);
            });
        }
        Optional<Customer> existingByEmail = customerRepository.findByEmail(resolvedEmail);
        if (existingByEmail.isPresent()) {
            return updateProfile(existingByEmail.get(), resolvedEmail, safeFirst, safeLast, customer -> {
                customer.setYandexId(yandexId);
            });
        }
        Customer customer = buildCustomer(safeFirst, safeLast, resolvedEmail, null, emptyAddress());
        customer.setYandexId(yandexId);
        customer.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return customerRepository.save(customer);
    }

    public Customer findOrCreateByVk(String vkId, String email, String firstName, String lastName) {
        if (!StringUtils.hasText(vkId) && !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("VK ID or email must be provided");
        }
        String resolvedEmail = StringUtils.hasText(email) ? email : ("vk-" + vkId + "@vk.local");
        String safeFirst = defaultIfBlank(firstName, "VK");
        String safeLast = defaultIfBlank(lastName, "User");

        Optional<Customer> existingById = StringUtils.hasText(vkId)
                ? customerRepository.findByVkId(vkId)
                : Optional.empty();
        if (existingById.isPresent()) {
            return updateProfile(existingById.get(), resolvedEmail, safeFirst, safeLast, customer -> {
                customer.setVkId(vkId);
            });
        }
        Optional<Customer> existingByEmail = customerRepository.findByEmail(resolvedEmail);
        if (existingByEmail.isPresent()) {
            return updateProfile(existingByEmail.get(), resolvedEmail, safeFirst, safeLast, customer -> {
                customer.setVkId(vkId);
            });
        }
        Customer customer = buildCustomer(safeFirst, safeLast, resolvedEmail, null, emptyAddress());
        customer.setVkId(vkId);
        customer.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return customerRepository.save(customer);
    }

    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    public Optional<Customer> findByPhone(String phone) {
        return customerRepository.findByPhone(phone);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    private Customer buildCustomer(String firstName, String lastName, String email, String phone, Address address) {
        Customer customer = new Customer();
        customer.setFirstName(defaultIfBlank(firstName, "Customer"));
        customer.setLastName(defaultIfBlank(lastName, "Online"));
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setAddress(address != null ? address : emptyAddress());
        customer.setRegisteredAt(OffsetDateTime.now());
        return customer;
    }

    private Customer updateProfile(Customer customer, String email, String firstName, String lastName, java.util.function.Consumer<Customer> idSetter) {
        if (StringUtils.hasText(email) && !email.equalsIgnoreCase(customer.getEmail())) {
            customerRepository.findByEmail(email)
                    .filter(existing -> !existing.getId().equals(customer.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Email already in use");
                    });
            customer.setEmail(email);
        }
        customer.setFirstName(defaultIfBlank(firstName, customer.getFirstName()));
        customer.setLastName(defaultIfBlank(lastName, customer.getLastName()));
        idSetter.accept(customer);
        return customerRepository.save(customer);
    }

    private Address emptyAddress() {
        return new Address("Not provided", "Not provided", "", "000000", "Unknown");
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
