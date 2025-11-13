package h99.ecommerce.infrastructure.repository.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

import h99.ecommerce.domain.Product;
import h99.ecommerce.domain.vo.Stock;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JpaProductRepository 통합 테스트")
class JpaProductRepositoryIntegrationTest extends BaseJpaRepositoryTest {

    @Autowired
    private JpaProductRepository productRepository;

    @Test
    @DisplayName("상품 저장")
    void save_product() {
        // given
        Product product = Product.builder()
                .name("테스트 상품")
                .description("테스트 상품 설명")
                .price(BigDecimal.valueOf(10000))
                .stock(new Stock(100))
                .totalViewCount(0)
                .build();

        // when
        Product saved = productRepository.save(product);
        flushAndClear();

        // then
        assertThat(product.getProductId()).isEqualTo(saved.getProductId());
    }

    @Test
    @DisplayName("상품 조회")
    void findOne_by_id() {
        // given
        Product product = Product.builder()
                .name("노트북")
                .description("고성능 노트북")
                .price(BigDecimal.valueOf(1500000))
                .stock(new Stock(50))
                .totalViewCount(0)
                .build();
        Product saved = productRepository.save(product);
        flushAndClear();

        // when
        Product found = productRepository.findOne(saved.getProductId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("노트북");
        assertThat(found.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1500000));
        assertThat(found.getStock().getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("모든 상품 조회")
    void find_all_products() {
        // given
        Product product1 = Product.builder()
                .name("상품1")
                .description("상품1 설명")
                .price(BigDecimal.valueOf(10000))
                .stock(new Stock(100))
                .totalViewCount(0)
                .build();
        Product product2 = Product.builder()
                .name("상품2")
                .description("상품2 설명")
                .price(BigDecimal.valueOf(20000))
                .stock(new Stock(50))
                .totalViewCount(0)
                .build();
        Product product3 = Product.builder()
                .name("상품3")
                .description("상품3 설명")
                .price(BigDecimal.valueOf(30000))
                .stock(new Stock(30))
                .totalViewCount(0)
                .build();

        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);
        flushAndClear();

        // when
        List<Product> products = productRepository.findAll();

        // then
        assertThat(products).hasSize(3);
        assertThat(products).extracting("name")
                .containsExactlyInAnyOrder("상품1", "상품2", "상품3");
    }

    @Test
    @DisplayName("상품 삭제")
    void delete_product() {
        // given
        Product product = Product.builder()
                .name("삭제할 상품")
                .description("삭제 테스트")
                .price(BigDecimal.valueOf(5000))
                .stock(new Stock(10))
                .totalViewCount(0)
                .build();
        Product saved = productRepository.save(product);
        flushAndClear();

        // when
        productRepository.delete(saved.getProductId());
        flushAndClear();

        // then
        Product deleted = productRepository.findOne(saved.getProductId());
        assertNull(deleted);
    }
}
