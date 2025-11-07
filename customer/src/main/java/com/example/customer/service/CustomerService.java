package com.example.customer.service;

import com.example.customer.domain.Customer;
import com.example.customer.repository.CustomerRepository;
import com.example.common.domain.Address;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public Customer registerCustomer(String firstName, String lastName, String email, Address address) {
        // Legacy method (without password) – not used in new flow
        Customer customer = new Customer(firstName, lastName, email, address);
        return customerRepository.save(customer);
    }

    public Customer registerCustomer(String firstName, String lastName, String email, String rawPassword, Address address) {
        Customer customer = new Customer(firstName, lastName, email, address, passwordEncoder.encode(rawPassword));
        return customerRepository.save(customer);
    }

    public Optional<Customer> authenticate(String email, String rawPassword) {
        return customerRepository.findByEmail(email)
                .filter(customer -> passwordEncoder.matches(rawPassword, customer.getPassword()));
    }

    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer);
    }
}
