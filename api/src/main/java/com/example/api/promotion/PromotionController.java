package com.example.api.promotion;

import com.example.api.admincms.DirectusAdminModels.PromoCodeView;
import com.example.api.admincms.DirectusAdminModels.PromotionView;
import com.example.api.admincms.DirectusAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/promotions")
public class PromotionController {

    private final DirectusAdminService adminService;

    public PromotionController(DirectusAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/active")
    public ActivePromotionsResponse activePromotions() {
        return new ActivePromotionsResponse(
                adminService.activePromotions().stream()
                        .map(PublicPromotion::from)
                        .toList(),
                adminService.listPromoCodes().stream()
                        .filter(PromoCodeView::activeNow)
                        .map(PublicPromoCode::from)
                        .toList()
        );
    }

    public record ActivePromotionsResponse(
            List<PublicPromotion> promotions,
            List<PublicPromoCode> promoCodes
    ) {
    }

    public record PublicPromotion(
            UUID id,
            String name,
            String type,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer discountPercent,
            Long discountAmount,
            Long salePriceAmount,
            String currency,
            Long thresholdAmount,
            String description
    ) {
        static PublicPromotion from(PromotionView view) {
            return new PublicPromotion(
                    view.id(),
                    view.name(),
                    view.type(),
                    view.startsAt(),
                    view.endsAt(),
                    view.discountPercent(),
                    view.discountAmount(),
                    view.salePriceAmount(),
                    view.currency(),
                    view.thresholdAmount(),
                    view.description()
            );
        }
    }

    public record PublicPromoCode(
            UUID id,
            String code,
            Integer discountPercent,
            Long discountAmount,
            Long thresholdAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String description
    ) {
        static PublicPromoCode from(PromoCodeView view) {
            return new PublicPromoCode(
                    view.id(),
                    view.code(),
                    view.discountPercent(),
                    view.discountAmount(),
                    view.thresholdAmount(),
                    view.startsAt(),
                    view.endsAt(),
                    view.description()
            );
        }
    }
}
