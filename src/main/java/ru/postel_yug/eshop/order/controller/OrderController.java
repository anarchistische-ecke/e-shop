package ru.postel_yug.eshop.order.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.postel_yug.eshop.order.service.OrderService;
import ru.postel_yug.eshop.security.repository.UserRepository;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository userRepo;

    // Получить список заказов текущего пользователя
    @GetMapping
    public List<OrderDto> listMyOrders(Authentication auth) {
        UserDetails ud = (UserDetails) auth.getPrincipal();
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        List<Order> orders = orderService.getOrdersForUser(user.getId());
        // Маппим в DTO (чтобы не включать лишнего)
        return orders.stream().map(order -> OrderDto.from(order)).toList();
    }

    // Получить детали конкретного заказа текущего пользователя
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId, Authentication auth) {
        UserDetails ud = (UserDetails) auth.getPrincipal();
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Order order = orderService.getOrdersForUser(user.getId()).stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst()
                .orElse(null);
        if(order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        }
        // Если хотим, можем проверить права: хотя мы отфильтровали по userId,
        // либо использовать orderRepo.findByIdAndUserId(orderId, user.getId())
        OrderDetailsDto dto = OrderDetailsDto.from(order);
        return ResponseEntity.ok(dto);
    }
}

