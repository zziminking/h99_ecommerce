package h99.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.dto.CartItemDto;
import h99.ecommerce.request.CartAddRequest;
import h99.ecommerce.request.CartUpdateRequest;
import h99.ecommerce.service.CartItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartItemController.class)
class CartItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartItemService cartItemService;

    @Test
    @DisplayName("장바구니 조회 API 성공")
    void getCart_Success() throws Exception {
        // given
        Product product1 = new Product(1, "상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now());
        Product product2 = new Product(2, "상품2", "설명2", new BigDecimal("20000"), new Stock(20), 0, LocalDateTime.now(), LocalDateTime.now());

        List<CartItemDto> cartItems = Arrays.asList(
                CartItemDto.builder()
                        .cartItemId(1)
                        .userId(1)
                        .product(product1)
                        .productName("상품1")
                        .price(new BigDecimal("10000"))
                        .quantity(2)
                        .totalAmount(new BigDecimal("20000"))
                        .build(),
                CartItemDto.builder()
                        .cartItemId(2)
                        .userId(1)
                        .product(product2)
                        .productName("상품2")
                        .price(new BigDecimal("20000"))
                        .quantity(1)
                        .totalAmount(new BigDecimal("20000"))
                        .build()
        );
        given(cartItemService.getCart(1)).willReturn(cartItems);

        // when & then
        mockMvc.perform(get("/api/cart/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cartItemId").value(1))
                .andExpect(jsonPath("$[0].productName").value("상품1"))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].totalAmount").value(20000))
                .andExpect(jsonPath("$[1].cartItemId").value(2))
                .andExpect(jsonPath("$[1].productName").value("상품2"));
    }

    @Test
    @DisplayName("장바구니 조회 API - 잘못된 userId")
    void getCart_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/cart/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/cart/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 총액 조회 API 성공")
    void getCartTotal_Success() throws Exception {
        // given
        BigDecimal total = new BigDecimal("40000");
        given(cartItemService.getCartTotal(1)).willReturn(total);

        // when & then
        mockMvc.perform(get("/api/cart/1/total"))
                .andExpect(status().isOk())
                .andExpect(content().string("40000"));
    }

    @Test
    @DisplayName("장바구니 총액 조회 API - 잘못된 userId")
    void getCartTotal_InvalidUserId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/cart/0/total"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 추가 API 성공")
    void addToCart_Success() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(1, 1, 2);

        // when & then
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(cartItemService).addCartItem(any(CartAddRequest.class));
    }

    @Test
    @DisplayName("장바구니 추가 API - 잘못된 userId")
    void addToCart_InvalidUserId() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(0, 1, 2);

        // when & then
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 추가 API - 잘못된 productId")
    void addToCart_InvalidProductId() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(1, 0, 2);

        // when & then
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 추가 API - 잘못된 quantity")
    void addToCart_InvalidQuantity() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(1, 1, 0);

        // when & then
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 수량 변경 API 성공")
    void updateCartItemQuantity_Success() throws Exception {
        // given
        CartUpdateRequest request = new CartUpdateRequest(5);

        // when & then
        mockMvc.perform(patch("/api/cart/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(cartItemService).updateCartItemQuantity(eq(1), eq(5));
    }

    @Test
    @DisplayName("장바구니 수량 변경 API - 잘못된 cartItemId")
    void updateCartItemQuantity_InvalidCartItemId() throws Exception {
        // given
        CartUpdateRequest request = new CartUpdateRequest(5);

        // when & then
        mockMvc.perform(patch("/api/cart/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 수량 변경 API - 잘못된 quantity")
    void updateCartItemQuantity_InvalidQuantity() throws Exception {
        // given
        CartUpdateRequest request = new CartUpdateRequest(0);

        // when & then
        mockMvc.perform(patch("/api/cart/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 상품 삭제 API 성공")
    void removeFromCart_Success() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/cart/1"))
                .andExpect(status().isNoContent());

        verify(cartItemService).removeCartItem(1);
    }

    @Test
    @DisplayName("장바구니 상품 삭제 API - 잘못된 cartItemId")
    void removeFromCart_InvalidCartItemId() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/cart/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/cart/-1"))
                .andExpect(status().isBadRequest());
    }
}
