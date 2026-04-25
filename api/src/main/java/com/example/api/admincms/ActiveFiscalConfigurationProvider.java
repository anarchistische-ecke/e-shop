package com.example.api.admincms;

import com.example.payment.service.FiscalConfigurationProvider;
import org.springframework.stereotype.Service;

@Service
public class ActiveFiscalConfigurationProvider implements FiscalConfigurationProvider {

    private final TaxConfigurationRepository taxConfigurationRepository;

    public ActiveFiscalConfigurationProvider(TaxConfigurationRepository taxConfigurationRepository) {
        this.taxConfigurationRepository = taxConfigurationRepository;
    }

    @Override
    public FiscalConfiguration getActiveFiscalConfiguration() {
        return taxConfigurationRepository.findFirstByActiveTrueAndStatusIgnoreCase("ACTIVE")
                .map(config -> new FiscalConfiguration(config.getTaxSystemCode(), config.getVatCode()))
                .orElse(null);
    }
}
