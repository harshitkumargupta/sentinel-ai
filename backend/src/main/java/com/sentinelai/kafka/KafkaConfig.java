package com.sentinelai.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

/**
 * Wires Kafka explicitly (producer/consumer factories, listener container factory, admin + topics)
 * only when {@code sentinel.kafka.enabled=true}. Keys and values are plain strings — payloads are
 * serialized to JSON by the publisher so pipeline headers stay simple and no type metadata leaks
 * onto the wire. Listener containers use manual offset commits so an offset only advances after the
 * consumer's DB transaction succeeds (or the message is safely routed to retry/DLQ).
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

    private final KafkaProperties props;

    public KafkaConfig(KafkaProperties props) {
        this.props = props;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.RETRIES_CONFIG, 3);
        cfg.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        cfg.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
        cfg.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        return new DefaultKafkaProducerFactory<>(cfg);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(props.getConcurrency());
        // Manual acks: the offset is committed only after a successful DB transaction (or after the
        // poison message has been moved to retry/DLQ), which is what keeps the pipeline loss-free.
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        KafkaAdmin admin = new KafkaAdmin(cfg);
        // Don't fail startup if the broker is briefly unavailable (graceful degradation).
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    @Bean
    public KafkaAdmin.NewTopics pipelineTopics() {
        KafkaProperties.Topics t = props.getTopics();
        int p = props.getPartitions();
        short rf = props.getReplicationFactor();
        return new KafkaAdmin.NewTopics(
                topic(t.getRaw(), p, rf),
                topic(t.getNormalized(), p, rf),
                topic(t.getAlerts(), p, rf),
                topic(t.getIncidents(), p, rf),
                topic(t.getAiInvestigations(), p, rf),
                // Retry/DLQ: single partition, consumed serially so backoff sleeps there, never on
                // the main partitions.
                topic(t.getRetry(), 1, rf),
                topic(t.getDlq(), 1, rf));
    }

    private static NewTopic topic(String name, int partitions, short rf) {
        return TopicBuilder.name(name).partitions(partitions).replicas(rf).build();
    }
}
