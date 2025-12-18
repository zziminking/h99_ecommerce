package h99.ecommerce.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka 설정
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public static final String ORDER_COMPLETED_TOPIC = "order-completed";
    public static final String COUPON_ISSUED_TOPIC = "coupon-issued";
    public static final String COUPON_ISSUED_DLQ_TOPIC = "coupon-issued-dlq";

    /**
     * 기본 Producer Factory
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * 기본 KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 주문 완료 토픽
     */
    @Bean
    public NewTopic orderCompletedTopic() {
        return TopicBuilder.name(ORDER_COMPLETED_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    /**
     * 쿠폰 발급 완료 토픽
     */
    @Bean
    public NewTopic couponIssuedTopic() {
        return TopicBuilder.name(COUPON_ISSUED_TOPIC)
            .partitions(3)
            .replicas(2)
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "lz4")
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7일
            .build();
    }

    /**
     * 쿠폰 발급 DLQ 토픽
     */
    @Bean
    public NewTopic couponIssuedDLQTopic() {
        return TopicBuilder.name(COUPON_ISSUED_DLQ_TOPIC)
            .partitions(1)
            .replicas(3)
            .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일
            .build();
    }
}
