package h99.ecommerce.config;

import h99.ecommerce.event.CouponIssuedEvent;
import h99.ecommerce.event.OrderCompletedEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka Consumer 설정
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:ecommerce-consumer-group}")
    private String groupId;

    /**
     * CouponIssuedEvent Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, CouponIssuedEvent> couponEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponIssuedEvent.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "h99.ecommerce.event");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * DLQ 전송용 Producer Factory
     */
    @Bean
    public ProducerFactory<String, CouponIssuedEvent> dlqProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * DLQ 전송용 KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, CouponIssuedEvent> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }

    /**
     * 공통 에러 핸들러 (재시도 + DLQ 전송)
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, CouponIssuedEvent> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            dlqKafkaTemplate,
            (record, ex) -> {
                log.error("[DLQ] 메시지를 DLQ로 전송 - topic: {}, partition: {}, offset: {}, error: {}",
                    record.topic(), record.partition(), record.offset(), ex.getMessage());
                return new org.apache.kafka.common.TopicPartition(
                    KafkaConfig.COUPON_ISSUED_DLQ_TOPIC,
                    record.partition()
                );
            }
        );

        // 3번 재시도, 2초 간격
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(2000L, 3L)
        );

        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }

    /**
     * CouponIssuedEvent Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssuedEvent> couponEventListenerContainerFactory(
        ConsumerFactory<String, CouponIssuedEvent> couponEventConsumerFactory,
        CommonErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssuedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(couponEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );

        log.info("CouponEvent ListenerContainerFactory initialized - concurrency: 3");
        return factory;
    }

    /**
     * OrderCompletedEvent Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, OrderCompletedEvent> orderEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCompletedEvent.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "h99.ecommerce.event");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * OrderCompletedEvent Listener Container Factory (기본 이름으로 등록)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> kafkaListenerContainerFactory(
        ConsumerFactory<String, OrderCompletedEvent> orderEventConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderEventConsumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );

        log.info("OrderEvent ListenerContainerFactory initialized - concurrency: 3");
        return factory;
    }
}