package com.jyk.feedbackme.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component // 스프링이 켜질 때 이 클래스를 자동으로 빈(Bean)으로 등록합니다.
@Profile("!test")
public class QueueMetrics {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry registry; // 스프링이 미리 만들어둔 모니터링 등록 장부

    private static final String QUEUE_KEY = "feedback:queue";

    // 생성자를 통해 스프링이 만들어둔 RedisTemplate과 MeterRegistry를 주입받습니다.
    public QueueMetrics(StringRedisTemplate redisTemplate, MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.registry = registry;
    }

    @PostConstruct // 의존성 주입이 완료된 후, 딱 한 번 실행되어 메트릭을 등록합니다.
    public void init() {
        /**
         * 게이지(Gauge) 메트릭 등록
         * - 이름: "feedback_queue_size"
         * - 대상 객체: redisTemplate
         * - 측정 방법(람다식): 5초마다 프로메테우스가 노크할 때마다 큐의 size를 실시간으로 측정해서 반환
         */
        registry.gauge("feedback_queue_size", redisTemplate,
                redis -> {
                    Long size = redis.opsForList().size(QUEUE_KEY);
                    return size != null ? size.doubleValue() : 0.0;
                });
    }
}
