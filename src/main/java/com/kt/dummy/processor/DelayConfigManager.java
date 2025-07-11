package com.kt.dummy.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.kt.dummy.server.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 지연 설정 관리자 - 런타임 동적 지연 제어
 * @author 고재원
 */
public class DelayConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(DelayConfigManager.class);
    private static final DelayConfigManager INSTANCE = new DelayConfigManager();

    // 전역 설정
    private volatile boolean globalEnabled = false;
    private volatile long globalMinDelay = 0;
    private volatile long globalMaxDelay = 0;

    // 포트별 설정
    private final ConcurrentHashMap<Integer, PortDelayConfig> portConfigs = new ConcurrentHashMap<>();

    public static class PortDelayConfig {
        public volatile boolean enabled;
        public volatile long minDelay;
        public volatile long maxDelay;

        public PortDelayConfig(boolean enabled, long minDelay, long maxDelay) {
            this.enabled = enabled;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }

        public long getRandomDelay() {
            if (minDelay == maxDelay) return minDelay;
            return ThreadLocalRandom.current().nextLong(minDelay, maxDelay + 1);
        }
    }

    private DelayConfigManager() {
        // 초기값은 ServerConfig에서 가져옴
        long defaultDelay = ServerConfig.getInstance().getDefaultDelayMs();
        if (defaultDelay > 0) {
            this.globalEnabled = true;
            this.globalMinDelay = defaultDelay;
            this.globalMaxDelay = defaultDelay;
        }
    }

    public static DelayConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 특정 포트의 지연 시간 계산
     * @param port 포트 번호
     * @return 지연 시간 (밀리초)
     */
    public long getDelayForPort(int port) {
        // 포트별 설정 우선
        PortDelayConfig portConfig = portConfigs.get(port);
        if (portConfig != null && portConfig.enabled) {
            long delay = portConfig.getRandomDelay();
            logger.debug("포트 {} 지연: {}ms (포트별 설정)", port, delay);
            return delay;
        }

        // 전역 설정 적용
        if (globalEnabled) {
            long delay = getGlobalRandomDelay();
            logger.debug("포트 {} 지연: {}ms (전역 설정)", port, delay);
            return delay;
        }

        logger.debug("포트 {} 지연: 0ms (비활성화)", port);
        return 0;
    }

    private long getGlobalRandomDelay() {
        if (globalMinDelay == globalMaxDelay) return globalMinDelay;
        return ThreadLocalRandom.current().nextLong(globalMinDelay, globalMaxDelay + 1);
    }

    // 전역 설정 메서드
    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
        logger.info("전역 지연 설정: {}", enabled ? "활성화" : "비활성화");
    }

    public void setGlobalDelay(long minDelay, long maxDelay) {
        if (minDelay < 0 || maxDelay < 0 || minDelay > maxDelay) {
            throw new IllegalArgumentException("잘못된 지연 시간 범위: " + minDelay + "-" + maxDelay);
        }
        this.globalMinDelay = minDelay;
        this.globalMaxDelay = maxDelay;
        logger.info("전역 지연 시간 설정: {}-{}ms", minDelay, maxDelay);
    }

    // 포트별 설정 메서드
    public void setPortDelay(int port, boolean enabled, long minDelay, long maxDelay) {
        if (minDelay < 0 || maxDelay < 0 || minDelay > maxDelay) {
            throw new IllegalArgumentException("잘못된 지연 시간 범위: " + minDelay + "-" + maxDelay);
        }
        portConfigs.put(port, new PortDelayConfig(enabled, minDelay, maxDelay));
        logger.info("포트 {} 지연 설정: {} ({}-{}ms)", port, enabled ? "활성화" : "비활성화", minDelay, maxDelay);
    }

    // JSON 설정 적용
    public void applyJsonConfig(JsonNode json) {
        // 전역 설정
        if (json.has("global")) {
            JsonNode global = json.get("global");
            if (global.has("enabled")) {
                globalEnabled = global.get("enabled").asBoolean();
            }
            if (global.has("min") && global.has("max")) {
                globalMinDelay = global.get("min").asLong();
                globalMaxDelay = global.get("max").asLong();
            }
        }

        // 포트별 설정
        if (json.has("ports")) {
            JsonNode ports = json.get("ports");
            ports.fields().forEachRemaining(entry -> {
                try {
                    int port = Integer.parseInt(entry.getKey());
                    JsonNode config = entry.getValue();
                    boolean enabled = config.has("enabled") ? config.get("enabled").asBoolean() : true;
                    long min = config.has("min") ? config.get("min").asLong() : 0;
                    long max = config.has("max") ? config.get("max").asLong() : 0;

                    if (enabled && min >= 0 && max >= min) {
                        setPortDelay(port, enabled, min, max);
                    } else if (!enabled) {
                        portConfigs.remove(port);
                        logger.info("포트 {} 지연 설정 제거", port);
                    }
                } catch (Exception e) {
                    logger.warn("포트 설정 파싱 실패: {}", entry.getKey(), e);
                }
            });
        }
    }

    // 현재 설정 조회
    public Map<String, Object> getCurrentConfig() {
        Map<String, Object> config = new ConcurrentHashMap<>();

        // 전역 설정
        config.put("global", Map.of(
                "enabled", globalEnabled,
                "minDelay", globalMinDelay,
                "maxDelay", globalMaxDelay
        ));

        // 포트별 설정
        Map<String, Object> ports = new ConcurrentHashMap<>();
        portConfigs.forEach((port, delayConfig) -> {
            ports.put(String.valueOf(port), Map.of(
                    "enabled", delayConfig.enabled,
                    "minDelay", delayConfig.minDelay,
                    "maxDelay", delayConfig.maxDelay
            ));
        });
        config.put("ports", ports);

        return config;
    }

    // Getter 메서드들
    public boolean isGlobalEnabled() { return globalEnabled; }
    public long getGlobalMinDelay() { return globalMinDelay; }
    public long getGlobalMaxDelay() { return globalMaxDelay; }
    public Map<Integer, PortDelayConfig> getAllPortConfigs() { return new ConcurrentHashMap<>(portConfigs); }
}