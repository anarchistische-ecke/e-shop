package com.example.order.domain;

import com.example.common.domain.BaseEntity;
import com.example.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class Order extends BaseEntity {
    @NotNull
    @Column(name = "customer_id", nullable = false, columnDefinition = "uuid")
    private UUID customerId;

    @Column(name = "order_date", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime orderDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "total_currency", nullable = false, length = 3))
    })
    @NotNull
    private Money totalAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "original_subtotal_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "original_subtotal_currency", length = 3))
    })
    private Money originalSubtotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "sale_subtotal_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "sale_subtotal_currency", length = 3))
    })
    private Money saleSubtotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "eligible_discount_subtotal_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "eligible_discount_subtotal_currency", length = 3))
    })
    private Money eligibleDiscountSubtotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "product_sale_discount_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "product_sale_discount_currency", length = 3))
    })
    private Money productSaleDiscount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "cart_discount_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "cart_discount_currency", length = 3))
    })
    private Money cartDiscount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "promo_code_discount_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "promo_code_discount_currency", length = 3))
    })
    private Money promoCodeDiscount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_discount_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "total_discount_currency", length = 3))
    })
    private Money totalDiscount;

    @Column(name = "promo_code")
    private String promoCode;

    @Column(name = "promo_code_redemption_recorded", nullable = false)
    private boolean promoCodeRedemptionRecorded;

    @Column(name = "applied_cart_discount_type")
    private String appliedCartDiscountType;

    @Column(name = "applied_cart_discount_label")
    private String appliedCartDiscountLabel;

    @Column(name = "discount_summary", columnDefinition = "TEXT")
    private String discountSummary;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "delivery_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "delivery_currency", length = 3))
    })
    private Money deliveryAmount;

    @Column(name = "payment_id", columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "shipment_id", columnDefinition = "uuid")
    private UUID shipmentId;

    @Column(name = "public_token", unique = true)
    private String publicToken;

    @Column(name = "receipt_email")
    private String receiptEmail;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "home_address")
    private String homeAddress;

    @Column(name = "delivery_provider")
    private String deliveryProvider;

    @Column(name = "delivery_method")
    private String deliveryMethod;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_pickup_point_id")
    private String deliveryPickupPointId;

    @Column(name = "delivery_pickup_point_name")
    private String deliveryPickupPointName;

    @Column(name = "delivery_interval_from", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deliveryIntervalFrom;

    @Column(name = "delivery_interval_to", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deliveryIntervalTo;

    @Column(name = "delivery_offer_id")
    private String deliveryOfferId;

    @Column(name = "delivery_request_id")
    private String deliveryRequestId;

    @Column(name = "delivery_status")
    private String deliveryStatus;

    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId;

    @Column(name = "manager_subject")
    private String managerSubject;

    @Column(name = "manager_directus_user_id")
    private String managerDirectusUserId;

    @Column(name = "manager_email")
    private String managerEmail;

    @Column(name = "manager_claimed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime managerClaimedAt;

    @OneToMany(mappedBy = "order", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<OrderItem> items = new HashSet<>();

    @Transient
    private Object paymentSummary;

    public Order() {
    }

    public Order(UUID customerId, String status, Money totalAmount) {
        this.customerId = customerId;
        this.orderDate = OffsetDateTime.now();
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public OffsetDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(OffsetDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Money totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Money getOriginalSubtotal() {
        return originalSubtotal;
    }

    public void setOriginalSubtotal(Money originalSubtotal) {
        this.originalSubtotal = originalSubtotal;
    }

    public Money getSaleSubtotal() {
        return saleSubtotal;
    }

    public void setSaleSubtotal(Money saleSubtotal) {
        this.saleSubtotal = saleSubtotal;
    }

    public Money getEligibleDiscountSubtotal() {
        return eligibleDiscountSubtotal;
    }

    public void setEligibleDiscountSubtotal(Money eligibleDiscountSubtotal) {
        this.eligibleDiscountSubtotal = eligibleDiscountSubtotal;
    }

    public Money getProductSaleDiscount() {
        return productSaleDiscount;
    }

    public void setProductSaleDiscount(Money productSaleDiscount) {
        this.productSaleDiscount = productSaleDiscount;
    }

    public Money getCartDiscount() {
        return cartDiscount;
    }

    public void setCartDiscount(Money cartDiscount) {
        this.cartDiscount = cartDiscount;
    }

    public Money getPromoCodeDiscount() {
        return promoCodeDiscount;
    }

    public void setPromoCodeDiscount(Money promoCodeDiscount) {
        this.promoCodeDiscount = promoCodeDiscount;
    }

    public Money getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(Money totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public boolean isPromoCodeRedemptionRecorded() {
        return promoCodeRedemptionRecorded;
    }

    public void setPromoCodeRedemptionRecorded(boolean promoCodeRedemptionRecorded) {
        this.promoCodeRedemptionRecorded = promoCodeRedemptionRecorded;
    }

    public String getAppliedCartDiscountType() {
        return appliedCartDiscountType;
    }

    public void setAppliedCartDiscountType(String appliedCartDiscountType) {
        this.appliedCartDiscountType = appliedCartDiscountType;
    }

    public String getAppliedCartDiscountLabel() {
        return appliedCartDiscountLabel;
    }

    public void setAppliedCartDiscountLabel(String appliedCartDiscountLabel) {
        this.appliedCartDiscountLabel = appliedCartDiscountLabel;
    }

    public String getDiscountSummary() {
        return discountSummary;
    }

    public void setDiscountSummary(String discountSummary) {
        this.discountSummary = discountSummary;
    }

    public Money getDeliveryAmount() {
        return deliveryAmount;
    }

    public void setDeliveryAmount(Money deliveryAmount) {
        this.deliveryAmount = deliveryAmount;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public String getReceiptEmail() {
        return receiptEmail;
    }

    public void setReceiptEmail(String receiptEmail) {
        this.receiptEmail = receiptEmail;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(String homeAddress) {
        this.homeAddress = homeAddress;
    }

    public String getDeliveryProvider() {
        return deliveryProvider;
    }

    public void setDeliveryProvider(String deliveryProvider) {
        this.deliveryProvider = deliveryProvider;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethod = deliveryMethod;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getDeliveryPickupPointId() {
        return deliveryPickupPointId;
    }

    public void setDeliveryPickupPointId(String deliveryPickupPointId) {
        this.deliveryPickupPointId = deliveryPickupPointId;
    }

    public String getDeliveryPickupPointName() {
        return deliveryPickupPointName;
    }

    public void setDeliveryPickupPointName(String deliveryPickupPointName) {
        this.deliveryPickupPointName = deliveryPickupPointName;
    }

    public OffsetDateTime getDeliveryIntervalFrom() {
        return deliveryIntervalFrom;
    }

    public void setDeliveryIntervalFrom(OffsetDateTime deliveryIntervalFrom) {
        this.deliveryIntervalFrom = deliveryIntervalFrom;
    }

    public OffsetDateTime getDeliveryIntervalTo() {
        return deliveryIntervalTo;
    }

    public void setDeliveryIntervalTo(OffsetDateTime deliveryIntervalTo) {
        this.deliveryIntervalTo = deliveryIntervalTo;
    }

    public String getDeliveryOfferId() {
        return deliveryOfferId;
    }

    public void setDeliveryOfferId(String deliveryOfferId) {
        this.deliveryOfferId = deliveryOfferId;
    }

    public String getDeliveryRequestId() {
        return deliveryRequestId;
    }

    public void setDeliveryRequestId(String deliveryRequestId) {
        this.deliveryRequestId = deliveryRequestId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public String getManagerSubject() {
        return managerSubject;
    }

    public void setManagerSubject(String managerSubject) {
        this.managerSubject = managerSubject;
    }

    public String getManagerDirectusUserId() {
        return managerDirectusUserId;
    }

    public void setManagerDirectusUserId(String managerDirectusUserId) {
        this.managerDirectusUserId = managerDirectusUserId;
    }

    public String getManagerEmail() {
        return managerEmail;
    }

    public void setManagerEmail(String managerEmail) {
        this.managerEmail = managerEmail;
    }

    public OffsetDateTime getManagerClaimedAt() {
        return managerClaimedAt;
    }

    public void setManagerClaimedAt(OffsetDateTime managerClaimedAt) {
        this.managerClaimedAt = managerClaimedAt;
    }

    public Object getPaymentSummary() {
        return paymentSummary;
    }

    public void setPaymentSummary(Object paymentSummary) {
        this.paymentSummary = paymentSummary;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Set<OrderItem> getItems() {
        return items;
    }

    public void setItems(Set<OrderItem> items) {
        this.items = items;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}
