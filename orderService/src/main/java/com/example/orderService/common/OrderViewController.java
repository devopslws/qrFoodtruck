package com.example.orderService.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 고객 주문 화면 컨트롤러
 *
 * GET /orderView
 *   1. 쿠키에서 session uuid 확인
 *   2. 없거나 Redis에 만료됐으면 신규 발급
 *   3. 있으면 TTL 갱신
 *   4. order.html 반환 (session uuid를 html에 주입)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class OrderViewController {

    @GetMapping("/orderView")
    public String orderPage(HttpServletRequest request,
                            HttpServletResponse response,
                            Model model) {
        return "order";
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        // 추후 재고·제조현황 데이터 주입
        return "admin";  // → resources/templates/admin.html
    }
}