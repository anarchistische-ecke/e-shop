package com.example.api.delivery;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartService;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import com.example.delivery.YandexDeliveryClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class YandexDeliveryService {
    private final CartService cartService;
    private final ProductVariantRepository variantRepository;
    private final YandexDeliveryClient client;

    @Value("${yandex.delivery.platform-station-id:}")
    private String platformStationId;

    @Value("${yandex.delivery.defaults.weight-g:1000}")
    private int defaultWeightG;

    @Value("${yandex.delivery.defaults.length-cm:30}")
    private int defaultLengthCm;

    @Value("${yandex.delivery.defaults.width-cm:20}")
    private int defaultWidthCm;

    @Value("${yandex.delivery.defaults.height-cm:10}")
    private int defaultHeightCm;

    public YandexDeliveryService(CartService cartService,
                                 ProductVariantRepository variantRepository,
                                 ObjectProvider<YandexDeliveryClient> clientProvider) {
        this.cartService = cartService;
        this.variantRepository = variantRepository;
        this.client = clientProvider.getIfAvailable();
    }

    public DeliveryOffersResponse getOffers(DeliveryOffersRequest request) {
        assertEnabled();
        if (request == null || request.cartId == null) {
            throw new IllegalArgumentException("Cart is required");
        }
        Cart cart = cartService.getCartById(request.cartId);
        YandexDeliveryClient.CreateOfferRequest payload = buildOfferRequest(cart, request, null);
        YandexDeliveryClient.OffersResponse response = client.createOffers(payload);
        List<DeliveryOffer> offers = mapOffers(response);
        return new DeliveryOffersResponse(offers);
    }

    public DeliveryOffer resolveOffer(DeliveryOffersRequest request, String offerId) {
        assertEnabled();
        if (!StringUtils.hasText(offerId)) {
            throw new IllegalArgumentException("Offer id is required");
        }
        Cart cart = cartService.getCartById(request.cartId);
        YandexDeliveryClient.CreateOfferRequest payload = buildOfferRequest(cart, request, offerId);
        YandexDeliveryClient.OffersResponse response = client.createOffers(payload);
        return mapOffers(response).stream()
                .filter(offer -> offer.offerId().equals(offerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected delivery offer is unavailable"));
    }

    public DeliveryConfirmResult confirmOffer(String offerId) {
        assertEnabled();
        YandexDeliveryClient.ConfirmOfferResponse response = client.confirmOffer(offerId);
        if (response == null || !StringUtils.hasText(response.requestId())) {
            throw new IllegalStateException("Failed to confirm Yandex delivery offer");
        }
        return new DeliveryConfirmResult(response.requestId());
    }

    public DeliveryRequestInfo getRequestInfo(String requestId) {
        assertEnabled();
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("Request id is required");
        }
        YandexDeliveryClient.RequestInfoResponse response = client.getRequestInfo(requestId);
        if (response == null) {
            throw new IllegalStateException("Failed to fetch Yandex delivery request");
        }
        String status = StringUtils.hasText(response.status) ? response.status : response.statusCode;
        OffsetDateTime from = null;
        OffsetDateTime to = null;
        String destinationType = null;
        if (response.request != null && response.request.destination != null) {
            destinationType = response.request.destination.type;
            if (response.request.destination.intervalUtc != null) {
                from = parseDate(response.request.destination.intervalUtc.from);
                to = parseDate(response.request.destination.intervalUtc.to);
            }
        }
        return new DeliveryRequestInfo(response.requestId, status, response.statusCode, destinationType, from, to);
    }

    public void cancelRequest(String requestId) {
        if (!StringUtils.hasText(requestId) || client == null) {
            return;
        }
        client.cancelRequest(requestId);
    }

    public PickupPointsResult getPickupPoints(PickupPointsQuery query) {
        assertEnabled();
        if (query == null) {
            throw new IllegalArgumentException("Request is required");
        }
        Integer geoId = query.geoId;
        boolean hasViewportBounds = hasViewportBounds(query);
        if (!StringUtils.hasText(query.location) && geoId == null && !hasViewportBounds) {
            throw new IllegalArgumentException("Location, geoId, or viewport bounds are required");
        }
        if (geoId == null && !hasViewportBounds) {
            geoId = detectGeoId(query.location);
        }
        YandexDeliveryClient.PickupPointsRequest request = new YandexDeliveryClient.PickupPointsRequest();
        request.geoId = geoId;
        applyViewportBounds(query, request);
        request.type = "pickup_point";
        request.paymentMethod = "already_paid";
        YandexDeliveryClient.PickupPointsResponse response = client.listPickupPoints(request);
        List<PickupPoint> points = new ArrayList<>();
        if (response != null && response.points != null) {
            for (YandexDeliveryClient.PickupPoint point : response.points) {
                String pointId = StringUtils.hasText(point.id) ? point.id : point.operatorStationId;
                points.add(new PickupPoint(
                        pointId,
                        point.name,
                        point.type,
                        point.address != null ? point.address.fullAddress : null,
                        point.position != null ? point.position.latitude : null,
                        point.position != null ? point.position.longitude : null,
                        point.address != null ? point.address.geoId : null
                ));
            }
        }
        return new PickupPointsResult(geoId, points);
    }

    private boolean hasViewportBounds(PickupPointsQuery query) {
        return query != null
                && query.latitudeFrom != null
                && query.latitudeTo != null
                && query.longitudeFrom != null
                && query.longitudeTo != null;
    }

    private void applyViewportBounds(PickupPointsQuery query, YandexDeliveryClient.PickupPointsRequest request) {
        if (!hasViewportBounds(query)) {
            return;
        }

        YandexDeliveryClient.Range latitude = new YandexDeliveryClient.Range();
        latitude.from = Math.min(query.latitudeFrom, query.latitudeTo);
        latitude.to = Math.max(query.latitudeFrom, query.latitudeTo);

        YandexDeliveryClient.Range longitude = new YandexDeliveryClient.Range();
        longitude.from = Math.min(query.longitudeFrom, query.longitudeTo);
        longitude.to = Math.max(query.longitudeFrom, query.longitudeTo);

        request.latitude = latitude;
        request.longitude = longitude;
    }

    public Integer detectGeoId(String location) {
        assertEnabled();
        if (!StringUtils.hasText(location)) {
            return null;
        }
        YandexDeliveryClient.LocationDetectRequest request = new YandexDeliveryClient.LocationDetectRequest();
        request.location = location;
        YandexDeliveryClient.LocationDetectResponse response = client.detectLocation(request);
        if (response == null || response.variants == null || response.variants.isEmpty()) {
            return null;
        }
        return response.variants.stream()
                .filter(variant -> variant.geoId != null)
                .findFirst()
                .map(variant -> variant.geoId)
                .orElse(null);
    }

    private void assertEnabled() {
        if (client == null) {
            throw new IllegalStateException("Yandex Delivery integration is disabled");
        }
        if (!StringUtils.hasText(platformStationId)) {
            throw new IllegalStateException("Yandex Delivery platform station id is not configured");
        }
    }

    private YandexDeliveryClient.CreateOfferRequest buildOfferRequest(Cart cart,
                                                                      DeliveryOffersRequest request,
                                                                      String selectedOfferId) {
        YandexDeliveryClient.CreateOfferRequest payload = new YandexDeliveryClient.CreateOfferRequest();
        payload.info = new YandexDeliveryClient.Info();
        payload.info.operatorRequestId = "cart-" + cart.getId() + "-" + UUID.randomUUID();
        if (StringUtils.hasText(request.comment)) {
            payload.info.comment = request.comment;
        }

        payload.source = new YandexDeliveryClient.Source();
        payload.source.platformStation = new YandexDeliveryClient.PlatformStation();
        payload.source.platformStation.platformId = platformStationId;

        payload.destination = new YandexDeliveryClient.Destination();
        payload.destination.type = request.deliveryType == DeliveryType.PICKUP ? "platform_station" : "custom_location";
        if (request.deliveryType == DeliveryType.PICKUP) {
            payload.destination.platformStation = new YandexDeliveryClient.PlatformStation();
            payload.destination.platformStation.platformId = request.pickupPointId;
        } else {
            payload.destination.customLocation = new YandexDeliveryClient.CustomLocation();
            payload.destination.customLocation.details = new YandexDeliveryClient.LocationDetails();
            payload.destination.customLocation.details.fullAddress = request.address;
        }
        if (request.intervalFrom != null || request.intervalTo != null) {
            payload.destination.intervalUtc = new YandexDeliveryClient.Interval();
            payload.destination.intervalUtc.from = request.intervalFrom != null ? request.intervalFrom.toString() : null;
            payload.destination.intervalUtc.to = request.intervalTo != null ? request.intervalTo.toString() : null;
        }

        payload.lastMilePolicy = request.deliveryType == DeliveryType.PICKUP ? "self_pickup" : "time_interval";
        payload.billingInfo = new YandexDeliveryClient.BillingInfo();
        payload.billingInfo.paymentMethod = "already_paid";
        payload.billingInfo.deliveryCost = 0L;
        payload.recipientInfo = new YandexDeliveryClient.RecipientInfo();
        payload.recipientInfo.firstName = request.firstName;
        payload.recipientInfo.lastName = request.lastName;
        payload.recipientInfo.phone = request.phone;
        payload.recipientInfo.email = request.email;

        PackageDims packageDims = buildPackageDims(cart);
        String placeBarcode = "place-" + cart.getId();

        payload.places = List.of(buildPlace(packageDims, placeBarcode));
        payload.items = buildItems(cart, placeBarcode, packageDims);
        payload.particularItemsRefuse = false;
        payload.forbidUnboxing = false;
        return payload;
    }

    private List<YandexDeliveryClient.Item> buildItems(Cart cart, String placeBarcode, PackageDims packageDims) {
        List<YandexDeliveryClient.Item> items = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = variantRepository.findWithProductById(item.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + item.getVariantId()));
            YandexDeliveryClient.Item ydItem = new YandexDeliveryClient.Item();
            ydItem.count = item.getQuantity();
            ydItem.name = resolveItemName(variant);
            ydItem.article = variant.getSku();
            ydItem.barcode = variant.getSku();
            ydItem.placeBarcode = placeBarcode;
            ydItem.billingDetails = new YandexDeliveryClient.BillingDetails();
            ydItem.billingDetails.unitPrice = item.getUnitPrice().getAmount();
            ydItem.billingDetails.assessedUnitPrice = item.getUnitPrice().getAmount();
            ydItem.physicalDims = new YandexDeliveryClient.PhysicalDims();
            ydItem.physicalDims.weightGross = resolveWeight(variant);
            ydItem.physicalDims.dx = resolveLengthCm(variant);
            ydItem.physicalDims.dy = resolveHeightCm(variant);
            ydItem.physicalDims.dz = resolveWidthCm(variant);
            items.add(ydItem);
        }
        return items;
    }

    private YandexDeliveryClient.Place buildPlace(PackageDims dims, String barcode) {
        YandexDeliveryClient.Place place = new YandexDeliveryClient.Place();
        place.barcode = barcode;
        place.description = "Order package";
        place.physicalDims = new YandexDeliveryClient.PhysicalDims();
        place.physicalDims.weightGross = dims.weightG;
        place.physicalDims.dx = dims.lengthCm;
        place.physicalDims.dy = dims.heightCm;
        place.physicalDims.dz = dims.widthCm;
        return place;
    }

    private PackageDims buildPackageDims(Cart cart) {
        int totalWeight = 0;
        int maxLength = 0;
        int maxWidth = 0;
        int maxHeight = 0;
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = variantRepository.findById(item.getVariantId())
                    .orElse(null);
            int weight = resolveWeight(variant);
            int length = resolveLengthCm(variant);
            int width = resolveWidthCm(variant);
            int height = resolveHeightCm(variant);
            totalWeight += weight * item.getQuantity();
            maxLength = Math.max(maxLength, length);
            maxWidth = Math.max(maxWidth, width);
            maxHeight = Math.max(maxHeight, height);
        }
        if (totalWeight <= 0) {
            totalWeight = defaultWeightG;
        }
        return new PackageDims(totalWeight, maxLength, maxWidth, maxHeight);
    }

    private int resolveWeight(ProductVariant variant) {
        if (variant != null && variant.getWeightGrossG() != null && variant.getWeightGrossG() > 0) {
            return variant.getWeightGrossG();
        }
        return defaultWeightG;
    }

    private int resolveLengthCm(ProductVariant variant) {
        return resolveDimensionCm(variant != null ? variant.getLengthMm() : null, defaultLengthCm);
    }

    private int resolveWidthCm(ProductVariant variant) {
        return resolveDimensionCm(variant != null ? variant.getWidthMm() : null, defaultWidthCm);
    }

    private int resolveHeightCm(ProductVariant variant) {
        return resolveDimensionCm(variant != null ? variant.getHeightMm() : null, defaultHeightCm);
    }

    private int resolveDimensionCm(Integer mmValue, int fallbackCm) {
        if (mmValue == null || mmValue <= 0) {
            return fallbackCm;
        }
        return Math.max(1, (int) Math.ceil(mmValue / 10.0));
    }

    private String resolveItemName(ProductVariant variant) {
        if (variant == null) {
            return "Товар";
        }
        if (variant.getProduct() != null && StringUtils.hasText(variant.getProduct().getName())) {
            return variant.getProduct().getName() + (StringUtils.hasText(variant.getName()) ? " (" + variant.getName() + ")" : "");
        }
        return StringUtils.hasText(variant.getName()) ? variant.getName() : "Товар";
    }

    private List<DeliveryOffer> mapOffers(YandexDeliveryClient.OffersResponse response) {
        if (response == null || response.offers == null) {
            return List.of();
        }
        return response.offers.stream()
                .filter(offer -> offer != null && StringUtils.hasText(offer.offerId))
                .map(this::mapOffer)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DeliveryOffer::intervalFrom, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private DeliveryOffer mapOffer(YandexDeliveryClient.Offer offer) {
        YandexDeliveryClient.OfferDetails details = offer.offerDetails;
        if (details == null) {
            return null;
        }
        Money pricing = parsePrice(details.pricing);
        Money pricingTotal = parsePrice(details.pricingTotal);
        OffsetDateTime from = parseDate(details.deliveryInterval != null ? details.deliveryInterval.from : null);
        OffsetDateTime to = parseDate(details.deliveryInterval != null ? details.deliveryInterval.to : null);
        OffsetDateTime expiresAt = parseDate(offer.expiresAt);
        return new DeliveryOffer(offer.offerId, expiresAt, from, to, pricing, pricingTotal);
    }

    private Money parsePrice(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.trim().split("\\s+");
        String amountPart = parts[0].replace(",", ".");
        String currency = parts.length > 1 ? parts[1] : "RUB";
        BigDecimal amount = new BigDecimal(amountPart);
        long minor = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        return Money.of(minor, currency);
    }

    private OffsetDateTime parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    public enum DeliveryType {
        COURIER,
        PICKUP
    }

    public static class DeliveryOffersRequest {
        public UUID cartId;
        public DeliveryType deliveryType;
        public String address;
        public String pickupPointId;
        public String pickupPointName;
        public OffsetDateTime intervalFrom;
        public OffsetDateTime intervalTo;
        public String firstName;
        public String lastName;
        public String phone;
        public String email;
        public String comment;
    }

    public record DeliveryOffersResponse(List<DeliveryOffer> offers) {}

    public record DeliveryOffer(String offerId,
                                OffsetDateTime expiresAt,
                                OffsetDateTime intervalFrom,
                                OffsetDateTime intervalTo,
                                Money pricing,
                                Money pricingTotal) {}

    public record DeliveryConfirmResult(String requestId) {}

    public record DeliveryRequestInfo(String requestId,
                                      String status,
                                      String statusCode,
                                      String destinationType,
                                      OffsetDateTime intervalFrom,
                                      OffsetDateTime intervalTo) {}

    public static class PickupPointsQuery {
        public String location;
        public Integer geoId;
        public Double latitudeFrom;
        public Double latitudeTo;
        public Double longitudeFrom;
        public Double longitudeTo;
    }

    public record PickupPointsResult(Integer geoId, List<PickupPoint> points) {}

    public record PickupPoint(String id,
                               String name,
                               String type,
                               String address,
                               Double latitude,
                               Double longitude,
                               Integer geoId) {}

    private record PackageDims(int weightG, int lengthCm, int widthCm, int heightCm) {}
}
