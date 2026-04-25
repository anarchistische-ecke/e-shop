package com.example.api.admincms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, UUID> {
    Optional<TaxConfiguration> findFirstByActiveTrueAndStatusIgnoreCase(String status);
}
