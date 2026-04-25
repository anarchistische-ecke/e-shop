package com.example.api.promotion;

import com.example.api.admincms.DirectusAdminModels.PromoCodeView;
import com.example.api.admincms.DirectusAdminModels.PromotionView;
import com.example.api.admincms.DirectusAdminService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionControllerTest {

    @Test
    void activePromotionsExposeOnlySafePublicFieldsAndActivePromoCodes() {
        DirectusAdminService adminService = mock(DirectusAdminService.class);
        UUID promotionId = UUID.randomUUID();
        UUID activeCodeId = UUID.randomUUID();
        when(adminService.activePromotions()).thenReturn(List.of(new PromotionView(
                promotionId,
                "Весенняя акция",
                "CART_DISCOUNT",
                "ACTIVE",
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(1),
                15,
                50000L,
                null,
                "RUB",
                300000L,
                "Скидка на корзину",
                List.of(),
                true
        )));
        when(adminService.listPromoCodes()).thenReturn(List.of(
                new PromoCodeView(activeCodeId, "SPRING", "ACTIVE", 10, null, 100000L, null, null, 100, 2, "Для корзины", true),
                new PromoCodeView(UUID.randomUUID(), "OLD", "INACTIVE", 5, null, null, null, null, null, 0, "Скрыт", false)
        ));
        PromotionController controller = new PromotionController(adminService);

        PromotionController.ActivePromotionsResponse response = controller.activePromotions();

        assertThat(response.promotions()).hasSize(1);
        assertThat(response.promotions().getFirst().id()).isEqualTo(promotionId);
        assertThat(response.promotions().getFirst().discountPercent()).isEqualTo(15);
        assertThat(response.promotions().getFirst().discountAmount()).isEqualTo(50000L);
        assertThat(response.promoCodes()).hasSize(1);
        assertThat(response.promoCodes().getFirst().id()).isEqualTo(activeCodeId);
        assertThat(response.promoCodes().getFirst().code()).isEqualTo("SPRING");
    }
}
