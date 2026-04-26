package com.example.api.admincms;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_configuration")
public class TaxConfiguration extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "ACTIVE";

    @Column(name = "tax_system_code", nullable = false)
    private int taxSystemCode;

    @Column(name = "vat_code", nullable = false)
    private int vatCode;

    @Column(name = "vat_rate_percent")
    private BigDecimal vatRatePercent;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTaxSystemCode() {
        return taxSystemCode;
    }

    public void setTaxSystemCode(int taxSystemCode) {
        this.taxSystemCode = taxSystemCode;
    }

    public int getVatCode() {
        return vatCode;
    }

    public void setVatCode(int vatCode) {
        this.vatCode = vatCode;
    }

    public BigDecimal getVatRatePercent() {
        return vatRatePercent;
    }

    public void setVatRatePercent(BigDecimal vatRatePercent) {
        this.vatRatePercent = vatRatePercent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
