package com.example.delivery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "yandex.delivery", name = "enabled", havingValue = "true", matchIfMissing = false)
public class YandexDeliveryClient {
    private final RestTemplate restTemplate;

    @Value("${yandex.delivery.base-url:https://b2b.taxi.tst.yandex.net}")
    private String baseUrl;

    @Value("${yandex.delivery.token:}")
    private String token;

    public YandexDeliveryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public OffersResponse createOffers(CreateOfferRequest request) {
        String url = resolveBaseUrl() + "/api/b2b/platform/offers/create";
        HttpEntity<CreateOfferRequest> entity = new HttpEntity<>(request, buildHeaders());
        return restTemplate.postForObject(url, entity, OffersResponse.class);
    }

    public ConfirmOfferResponse confirmOffer(String offerId) {
        String url = resolveBaseUrl() + "/api/b2b/platform/offers/confirm";
        HttpEntity<ConfirmOfferRequest> entity = new HttpEntity<>(new ConfirmOfferRequest(offerId), buildHeaders());
        return restTemplate.postForObject(url, entity, ConfirmOfferResponse.class);
    }

    public RequestInfoResponse getRequestInfo(String requestId) {
        String url = UriComponentsBuilder
                .fromHttpUrl(resolveBaseUrl() + "/api/b2b/platform/request/info")
                .queryParam("request_id", requestId)
                .toUriString();
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        return restTemplate.getForObject(url, RequestInfoResponse.class);
    }

    public CancelRequestResponse cancelRequest(String requestId) {
        String url = resolveBaseUrl() + "/api/b2b/platform/request/cancel";
        HttpEntity<CancelRequest> entity = new HttpEntity<>(new CancelRequest(requestId), buildHeaders());
        return restTemplate.postForObject(url, entity, CancelRequestResponse.class);
    }

    public PricingResponse calculatePricing(PricingRequest request) {
        String url = resolveBaseUrl() + "/api/b2b/platform/pricing-calculator";
        HttpEntity<PricingRequest> entity = new HttpEntity<>(request, buildHeaders());
        return restTemplate.postForObject(url, entity, PricingResponse.class);
    }

    public OffersInfoResponse getOffersInfo(OffersInfoRequest request) {
        String url = resolveBaseUrl() + "/api/b2b/platform/offers/info";
        HttpEntity<OffersInfoRequest> entity = new HttpEntity<>(request, buildHeaders());
        return restTemplate.postForObject(url, entity, OffersInfoResponse.class);
    }

    public PickupPointsResponse listPickupPoints(PickupPointsRequest request) {
        String url = resolveBaseUrl() + "/api/b2b/platform/pickup-points/list";
        HttpEntity<PickupPointsRequest> entity = new HttpEntity<>(request, buildHeaders());
        return restTemplate.postForObject(url, entity, PickupPointsResponse.class);
    }

    public LocationDetectResponse detectLocation(LocationDetectRequest request) {
        String url = resolveBaseUrl() + "/api/b2b/platform/location/detect";
        HttpEntity<LocationDetectRequest> entity = new HttpEntity<>(request, buildHeaders());
        return restTemplate.postForObject(url, entity, LocationDetectResponse.class);
    }

    private String resolveBaseUrl() {
        return baseUrl != null ? baseUrl.replaceAll("/$", "") : "";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreateOfferRequest {
        public Info info;
        public Source source;
        public Destination destination;
        public List<Item> items;
        public List<Place> places;
        @JsonProperty("billing_info")
        public BillingInfo billingInfo;
        @JsonProperty("recipient_info")
        public RecipientInfo recipientInfo;
        @JsonProperty("last_mile_policy")
        public String lastMilePolicy;
        @JsonProperty("particular_items_refuse")
        public Boolean particularItemsRefuse;
        @JsonProperty("forbid_unboxing")
        public Boolean forbidUnboxing;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Info {
        @JsonProperty("operator_request_id")
        public String operatorRequestId;
        @JsonProperty("merchant_id")
        public String merchantId;
        public String comment;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Source {
        @JsonProperty("platform_station")
        public PlatformStation platformStation;
        @JsonProperty("interval_utc")
        public Interval intervalUtc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Destination {
        public String type;
        @JsonProperty("platform_station")
        public PlatformStation platformStation;
        @JsonProperty("custom_location")
        public CustomLocation customLocation;
        @JsonProperty("interval_utc")
        public Interval intervalUtc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlatformStation {
        @JsonProperty("platform_id")
        public String platformId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CustomLocation {
        public Double latitude;
        public Double longitude;
        public LocationDetails details;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LocationDetails {
        @JsonProperty("geoId")
        public Integer geoId;
        @JsonProperty("full_address")
        public String fullAddress;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Interval {
        public String from;
        public String to;
        public String policy;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        public Integer count;
        public String name;
        public String article;
        public String barcode;
        @JsonProperty("billing_details")
        public BillingDetails billingDetails;
        @JsonProperty("physical_dims")
        public PhysicalDims physicalDims;
        @JsonProperty("place_barcode")
        public String placeBarcode;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BillingDetails {
        @JsonProperty("unit_price")
        public Long unitPrice;
        @JsonProperty("assessed_unit_price")
        public Long assessedUnitPrice;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhysicalDims {
        @JsonProperty("weight_gross")
        public Integer weightGross;
        public Integer dx;
        public Integer dy;
        public Integer dz;
        @JsonProperty("predefined_volume")
        public Integer predefinedVolume;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Place {
        @JsonProperty("physical_dims")
        public PhysicalDims physicalDims;
        public String barcode;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BillingInfo {
        @JsonProperty("payment_method")
        public String paymentMethod;
        @JsonProperty("delivery_cost")
        public Long deliveryCost;
        @JsonProperty("variable_delivery_cost_for_recipient")
        public List<VariableDeliveryCost> variableDeliveryCostForRecipient;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VariableDeliveryCost {
        @JsonProperty("min_cost_of_accepted_items")
        public Long minCostOfAcceptedItems;
        @JsonProperty("delivery_cost")
        public Long deliveryCost;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecipientInfo {
        @JsonProperty("first_name")
        public String firstName;
        @JsonProperty("last_name")
        public String lastName;
        @JsonProperty("patronymic")
        public String patronymic;
        public String phone;
        public String email;
    }

    public record ConfirmOfferRequest(@JsonProperty("offer_id") String offerId) {}

    public record ConfirmOfferResponse(@JsonProperty("request_id") String requestId) {}

    public record CancelRequest(@JsonProperty("request_id") String requestId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CancelRequestResponse {
        public String code;
        public String message;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffersResponse {
        public List<Offer> offers;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Offer {
        @JsonProperty("offer_id")
        public String offerId;
        @JsonProperty("expires_at")
        public String expiresAt;
        @JsonProperty("offer_details")
        public OfferDetails offerDetails;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OfferDetails {
        @JsonProperty("delivery_interval")
        public Interval deliveryInterval;
        @JsonProperty("pickup_interval")
        public Interval pickupInterval;
        public String pricing;
        @JsonProperty("pricing_total")
        public String pricingTotal;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestInfoResponse {
        @JsonProperty("request_id")
        public String requestId;
        public RequestDetails request;
        public String status;
        @JsonProperty("status_code")
        public String statusCode;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestDetails {
        public Info info;
        public Source source;
        public Destination destination;
        public List<Item> items;
        public List<Place> places;
        @JsonProperty("billing_info")
        public BillingInfo billingInfo;
        @JsonProperty("recipient_info")
        public RecipientInfo recipientInfo;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PricingRequest {
        public PricingNode source;
        public PricingNode destination;
        public String tariff;
        @JsonProperty("total_weight")
        public Integer totalWeight;
        @JsonProperty("total_assessed_price")
        public Long totalAssessedPrice;
        @JsonProperty("client_price")
        public Long clientPrice;
        @JsonProperty("payment_method")
        public String paymentMethod;
        public List<Place> places;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PricingNode {
        @JsonProperty("platform_station_id")
        public String platformStationId;
        public String address;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PricingResponse {
        public String pricing;
        @JsonProperty("pricing_total")
        public String pricingTotal;
        public String currency;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffersInfoRequest {
        public PricingNode source;
        public PricingNode destination;
        public List<Place> places;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffersInfoResponse {
        public List<Interval> offers;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickupPointsRequest {
        @JsonProperty("pickup_point_ids")
        public List<String> pickupPointIds;
        @JsonProperty("geo_id")
        public Integer geoId;
        public Range longitude;
        public Range latitude;
        public String type;
        @JsonProperty("payment_method")
        public String paymentMethod;
        @JsonProperty("available_for_dropoff")
        public Boolean availableForDropoff;
    }

    public static class Range {
        public Double from;
        public Double to;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickupPointsResponse {
        public List<PickupPoint> points;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickupPoint {
        @JsonProperty("ID")
        public String id;
        @JsonProperty("operator_station_id")
        public String operatorStationId;
        public String name;
        public String type;
        public PickupPointPosition position;
        public PickupPointAddress address;
        @JsonProperty("is_yandex_branded")
        public Boolean isYandexBranded;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickupPointPosition {
        public Double latitude;
        public Double longitude;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickupPointAddress {
        @JsonProperty("geoId")
        public Integer geoId;
        @JsonProperty("full_address")
        public String fullAddress;
    }

    public static class LocationDetectRequest {
        public String location;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LocationDetectResponse {
        public List<LocationVariant> variants;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LocationVariant {
        @JsonProperty("geo_id")
        public Integer geoId;
        public String address;
    }
}
