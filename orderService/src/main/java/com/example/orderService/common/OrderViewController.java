package com.example.orderService.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 고객 주문 화면 MVC 컨트롤러.
 * QR 링크(/order) 접근 시 order.html을 반환한다.
 *
 * 현재: 목 데이터로 동작
 * 향후: ProductClient (→ Redis or Server B) 에서 상품 목록을 받아 Model에 주입
 */
@Controller
@RequiredArgsConstructor
public class OrderViewController {

    // 향후 주입할 서비스 레이어
    // private final ProductQueryService productQueryService;

    /**
     * GET /order
     * 상품 목록을 Model에 담아 order.html을 렌더링.
     */
    @GetMapping("/orderView")
    public String orderPage(Model model) {

        // ── 임시 목 데이터 (추후 Redis 조회로 교체) ──────────────────
        List<ProductDto> products = List.of(
                new ProductDto(1L, "짜장면",  8_000, 5,  true),
                new ProductDto(2L, "짬뽕",   10_000, 5,  true),
                new ProductDto(3L, "탕수육", 22_000, 0,  true)
        );
        // ─────────────────────────────────────────────────────────────

        model.addAttribute("products", products);
        return "order";   // → resources/templates/order.html
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        // 추후 재고·제조현황 데이터 주입
        return "admin";  // → resources/templates/admin.html
    }

    // ── 내부 DTO (향후 별도 파일로 분리) ─────────────────────────────
    public record ProductDto(
            Long    id,
            String  name,
            int     price,
            int     stock,
            boolean displayed
    ) {
        /** 템플릿에서 재고 여부를 간단히 체크하기 위한 헬퍼 */
        public boolean isSoldOut() {
            return stock <= 0;
        }
    }
}