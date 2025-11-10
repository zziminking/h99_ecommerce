package h99.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import h99.ecommerce.service.ProductService;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("상품 목록 조회 API 성공")
    void getProducts_Success() throws Exception {
        // given
        List<Product> products = Arrays.asList(
                new Product(1, "상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now()),
                new Product(2, "상품2", "설명2", new BigDecimal("20000"), new Stock(20), 0, LocalDateTime.now(), LocalDateTime.now())
        );
        given(productService.getAllProducts()).willReturn(products);

        // when & then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].name").value("상품1"))
                .andExpect(jsonPath("$[0].price").value(10000))
                .andExpect(jsonPath("$[1].productId").value(2))
                .andExpect(jsonPath("$[1].name").value("상품2"));
    }

    @Test
    @DisplayName("상품 상세 조회 API 성공")
    void getProduct_Success() throws Exception {
        // given
        Product product = new Product(1, "상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now());
        given(productService.getProduct(1)).willReturn(product);

        // when & then
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.name").value("상품1"))
                .andExpect(jsonPath("$.price").value(10000));
    }

    @Test
    @DisplayName("상품 상세 조회 API - 잘못된 ID로 실패")
    void getProduct_InvalidId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 재고 확인 API 성공")
    void checkStockAvailable_Success() throws Exception {
        // given
        given(productService.checkStockAvailable(1)).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/products/1/stock"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("상품 재고 확인 API - 재고 없음")
    void checkStockAvailable_NoStock() throws Exception {
        // given
        given(productService.checkStockAvailable(1)).willReturn(false);

        // when & then
        mockMvc.perform(get("/api/products/1/stock"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("상품 재고 충분 여부 확인 API 성공")
    void checkStockEnough_Success() throws Exception {
        // given
        given(productService.checkStockEnough(1, 5)).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/products/1/stock/check")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("상품 재고 충분 여부 확인 API - 재고 부족")
    void checkStockEnough_NotEnough() throws Exception {
        // given
        given(productService.checkStockEnough(1, 100)).willReturn(false);

        // when & then
        mockMvc.perform(get("/api/products/1/stock/check")
                        .param("quantity", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("상품 재고 충분 여부 확인 API - 잘못된 파라미터")
    void checkStockEnough_InvalidParameters() throws Exception {
        // when & then - 잘못된 productId
        mockMvc.perform(get("/api/products/0/stock/check")
                        .param("quantity", "5"))
                .andExpect(status().isBadRequest());

        // 잘못된 quantity
        mockMvc.perform(get("/api/products/1/stock/check")
                        .param("quantity", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인기 상품 조회 (조회수 기준) API 성공")
    void getPopularProductsByViewCount_Success() throws Exception {
        // given
        List<Product> popularProducts = Arrays.asList(
                new Product(1, "인기상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now()),
                new Product(2, "인기상품2", "설명2", new BigDecimal("20000"), new Stock(20), 0, LocalDateTime.now(), LocalDateTime.now())
        );
        given(productService.getPopularProductsByViewCount(5)).willReturn(popularProducts);

        // when & then
        mockMvc.perform(get("/api/products/popular/views")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("인기상품1"))
                .andExpect(jsonPath("$[1].name").value("인기상품2"));
    }

    @Test
    @DisplayName("인기 상품 조회 (조회수 기준) API - 기본 limit 값")
    void getPopularProductsByViewCount_DefaultLimit() throws Exception {
        // given
        List<Product> popularProducts = Arrays.asList(
                new Product(1, "인기상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now())
        );
        given(productService.getPopularProductsByViewCount(5)).willReturn(popularProducts);

        // when & then - limit 파라미터 없이 요청
        mockMvc.perform(get("/api/products/popular/views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("인기 상품 조회 (주문 수량 기준) API 성공")
    void getPopularProductsByOrderCount_Success() throws Exception {
        // given
        List<Product> popularProducts = Arrays.asList(
                new Product(1, "베스트상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now()),
                new Product(2, "베스트상품2", "설명2", new BigDecimal("20000"), new Stock(20), 0, LocalDateTime.now(), LocalDateTime.now())
        );
        given(productService.getPopularProductsByOrderCount(5)).willReturn(popularProducts);

        // when & then
        mockMvc.perform(get("/api/products/popular/orders")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("베스트상품1"))
                .andExpect(jsonPath("$[1].name").value("베스트상품2"));
    }

    @Test
    @DisplayName("인기 상품 조회 (주문 수량 기준) API - 기본 limit 값")
    void getPopularProductsByOrderCount_DefaultLimit() throws Exception {
        // given
        List<Product> popularProducts = Arrays.asList(
                new Product(1, "베스트상품1", "설명1", new BigDecimal("10000"), new Stock(10), 0, LocalDateTime.now(), LocalDateTime.now())
        );
        given(productService.getPopularProductsByOrderCount(5)).willReturn(popularProducts);

        // when & then - limit 파라미터 없이 요청
        mockMvc.perform(get("/api/products/popular/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
