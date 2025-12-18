package h99.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 설정
 */
@Configuration
public class KafkaConfig {

    public static final String ORDER_COMPLETED_TOPIC = "order-completed";

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
}
