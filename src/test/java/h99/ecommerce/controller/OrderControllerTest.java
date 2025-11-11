package h99.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.domain.Order;
import h99.ecommerce.domain.User;
import h99.ecommerce.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("주문 생성 API 성공 - 쿠폰 없이")
    void createOrder_Success_WithoutCoupon() throws Exception {
        // given
        Order order = Order.builder()
                .orderId(1L)
                .user(User.builder().userId(1L).build())
                .totalQuantity(2)
                .totalPrice(new BigDecimal("10000"))
                .orderAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(orderService.createOrder(eq(1L), isNull())).willReturn(order);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.totalPrice").value(10000));
    }

    @Test
    @DisplayName("주문 생성 API 성공 - 쿠폰 사용")
    void createOrder_Success_WithCoupon() throws Exception {
        // given
        Order order = Order.builder()
                .orderId(1L)
                .user(User.builder().userId(1L).build())
                .totalQuantity(2)
                .totalPrice(new BigDecimal("9000"))
                .orderAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(orderService.createOrder(1L, 10L)).willReturn(order);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", "1")
                        .param("userCouponId", "10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.totalPrice").value(9000));
    }

    @Test
    @DisplayName("주문 생성 API - 잘못된 userId")
    void createOrder_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders")
                        .param("userId", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 API - 잘못된 userCouponId")
    void createOrder_InvalidUserCouponId() throws Exception {
        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", "1")
                        .param("userCouponId", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders")
                        .param("userId", "1")
                        .param("userCouponId", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 상세 조회 API 성공")
    void getOrder_Success() throws Exception {
        // given
        Order order = Order.builder()
                .orderId(1L)
                .user(User.builder().userId(1L).build())
                .totalQuantity(2)
                .totalPrice(new BigDecimal("10000"))
                .orderAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(orderService.getOrder(1L)).willReturn(order);

        // when & then
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.totalPrice").value(10000));
    }

    @Test
    @DisplayName("주문 상세 조회 API - 잘못된 orderId")
    void getOrder_InvalidOrderId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/orders/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/orders/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사용자별 주문 목록 조회 API 성공")
    void getOrdersByUserId_Success() throws Exception {
        // given
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1L)
                        .user(User.builder().userId(1L).build())
                        .totalQuantity(2)
                        .totalPrice(new BigDecimal("10000"))
                        .orderAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                Order.builder()
                        .orderId(2L)
                        .user(User.builder().userId(1L).build())
                        .totalQuantity(3)
                        .totalPrice(new BigDecimal("20000"))
                        .orderAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        given(orderService.getOrdersByUserId(1L)).willReturn(orders);

        // when & then
        mockMvc.perform(get("/api/orders/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].totalPrice").value(10000))
                .andExpect(jsonPath("$[1].orderId").value(2))
                .andExpect(jsonPath("$[1].totalPrice").value(20000));
    }

    @Test
    @DisplayName("사용자별 주문 목록 조회 API - 잘못된 userId")
    void getOrdersByUserId_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/orders/users/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/orders/users/-1"))
                .andExpect(status().isBadRequest());
    }
}
