package com.example.vintage.controller;

import com.example.vintage.entity.Order;
import com.example.vintage.entity.OrderStatus;
import com.example.vintage.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/account/orders")
public class OrderController {
    @Autowired
    private OrderRepository orderRepository;

    @PostMapping("/{id}/cancel")
    @Transactional
    public String cancelOrder(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Order> optionalOrder = orderRepository.findById(id);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                redirectAttributes.addFlashAttribute("success", "Đơn hàng đã được hủy thành công.");
            } else {
                redirectAttributes.addFlashAttribute("error", "Chỉ có thể hủy đơn hàng ở trạng thái Chờ xác nhận.");
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
        }
        return "redirect:/account/orders";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        return orderRepository.findByIdWithItems(id)
                .map(order -> {
                    model.addAttribute("order", order);
                    return "account/order-detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
                    return "redirect:/account/orders";
                });
    }
}
