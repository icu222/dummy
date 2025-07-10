package com.kt.dummy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 성능 측정 및 모니터링 유틸리티
 * @author 고재원
 */
public class PerformanceUtil {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceUtil.class);
    
    // 성능 카운터
    private static final AtomicLong totalRequestCount = new AtomicLong(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final Map<String, AtomicLong> protocolCounters = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> portCounters = new ConcurrentHashMap<>();
    
    // 시작 시간
    private static final long startTime = System.currentTimeMillis();
    
    /**
     * 요청 처리 시작
     * @return 시작 시간 (나노초)
     */
    public static long startRequest() {
        totalRequestCount.incrementAndGet();
        return System.nanoTime();
    }
    
    /**
     * 요청 처리 완료
     * @param startTimeNanos 시작 시간 (나노초)
     * @param protocol 프로토콜
     * @param port 포트
     */
    public static void endRequest(long startTimeNanos, String protocol, int port) {
        long endTimeNanos = System.nanoTime();
        long elapsedNanos = endTimeNanos - startTimeNanos;
        long elapsedMillis = elapsedNanos / 1_000_000;
        
        // 전체 응답 시간 누적
        totalResponseTime.addAndGet(elapsedMillis);
        
        // 프로토콜별 카운터
        if (protocol != null) {
            protocolCounters.computeIfAbsent(protocol, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        // 포트별 카운터
        if (port > 0) {
            String portKey = String.valueOf(port);
            portCounters.computeIfAbsent(portKey, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        // 느린 요청 로깅 (100ms 이상)
        if (elapsedMillis > 100) {
            logger.warn("느린 요청 감지: {}ms (프로토콜: {}, 포트: {})", elapsedMillis, protocol, port);
        }
    }
    
    /**
     * 현재 성능 통계 조회
     * @return 성능 통계 맵
     */
    public static Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        long requestCount = totalRequestCount.get();
        long totalTime = totalResponseTime.get();
        
        stats.put("totalRequests", requestCount);
        stats.put("totalResponseTimeMs", totalTime);
        stats.put("averageResponseTimeMs", requestCount > 0 ? (double) totalTime / requestCount : 0.0);
        stats.put("requestsPerSecond", calculateTPS());
        stats.put("uptimeMs", System.currentTimeMillis() - startTime);
        
        // 프로토콜별 통계
        Map<String, Long> protocolStats = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : protocolCounters.entrySet()) {
            protocolStats.put(entry.getKey(), entry.getValue().get());
        }
        stats.put("protocolStats", protocolStats);
        
        // 포트별 통계
        Map<String, Long> portStats = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : portCounters.entrySet()) {
            portStats.put(entry.getKey(), entry.getValue().get());
        }
        stats.put("portStats", portStats);
        
        // 메모리 사용률
        stats.put("memoryUsage", getMemoryUsage());
        
        // 스레드 정보
        stats.put("threadInfo", getThreadInfo());
        
        return stats;
    }
    
    /**
     * TPS (초당 트랜잭션 수) 계산
     * @return 현재 TPS
     */
    public static double calculateTPS() {
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;
        if (uptimeSeconds <= 0) {
            return 0.0;
        }
        
        return (double) totalRequestCount.get() / uptimeSeconds;
    }
    
    /**
     * 메모리 사용 정보 조회
     * @return 메모리 사용 맵
     */
    public static Map<String, Object> getMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        Map<String, Object> memoryStats = new ConcurrentHashMap<>();
        memoryStats.put("heapUsed", heapUsage.getUsed());
        memoryStats.put("heapMax", heapUsage.getMax());
        memoryStats.put("heapCommitted", heapUsage.getCommitted());
        memoryStats.put("heapUsagePercentage", calculateUsagePercentage(heapUsage));
        
        memoryStats.put("nonHeapUsed", nonHeapUsage.getUsed());
        memoryStats.put("nonHeapMax", nonHeapUsage.getMax());
        memoryStats.put("nonHeapCommitted", nonHeapUsage.getCommitted());
        
        return memoryStats;
    }
    
    /**
     * 스레드 정보 조회
     * @return 스레드 정보 맵
     */
    public static Map<String, Object> getThreadInfo() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        Map<String, Object> threadStats = new ConcurrentHashMap<>();
        threadStats.put("threadCount", threadBean.getThreadCount());
        threadStats.put("peakThreadCount", threadBean.getPeakThreadCount());
        threadStats.put("daemonThreadCount", threadBean.getDaemonThreadCount());
        threadStats.put("totalStartedThreadCount", threadBean.getTotalStartedThreadCount());
        
        return threadStats;
    }
    
    /**
     * 성능 경고 확인
     * @return 경고 메시지 리스트
     */
    public static java.util.List<String> checkPerformanceWarnings() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        
        // 메모리 사용률 확인
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double heapUsagePercentage = calculateUsagePercentage(heapUsage);
        
        if (heapUsagePercentage > 90) {
            warnings.add("힙 메모리 사용률 위험: " + String.format("%.1f%%", heapUsagePercentage));
        } else if (heapUsagePercentage > 80) {
            warnings.add("힙 메모리 사용률 경고: " + String.format("%.1f%%", heapUsagePercentage));
        }
        
        // TPS 확인
        double currentTPS = calculateTPS();
        if (currentTPS > 2000) {
            warnings.add("높은 TPS 감지: " + String.format("%.1f TPS", currentTPS));
        }
        
        // 평균 응답 시간 확인
        long requestCount = totalRequestCount.get();
        if (requestCount > 0) {
            double avgResponseTime = (double) totalResponseTime.get() / requestCount;
            if (avgResponseTime > 1000) {
                warnings.add("평균 응답시간 지연: " + String.format("%.1fms", avgResponseTime));
            }
        }
        
        return warnings;
    }
    
    /**
     * 성능 통계 초기화
     */
    public static void resetStats() {
        totalRequestCount.set(0);
        totalResponseTime.set(0);
        protocolCounters.clear();
        portCounters.clear();
        
        logger.info("성능 통계 초기화 완료");
    }
    
    private static double calculateUsagePercentage(MemoryUsage usage) {
        if (usage.getMax() <= 0) {
            return 0.0;
        }
        return (double) usage.getUsed() / usage.getMax() * 100.0;
    }
    
    /**
     * 성능 로그 출력 (주기적 호출용)
     */
    public static void logPerformanceStats() {
        if (logger.isInfoEnabled()) {
            Map<String, Object> stats = getPerformanceStats();
            logger.info("성능 통계 - TPS: {}, 평균응답시간: {}ms, 총요청: {}, 메모리사용률: {}%",
                String.format("%.1f", stats.get("requestsPerSecond")),
                String.format("%.1f", stats.get("averageResponseTimeMs")),
                stats.get("totalRequests"),
                String.format("%.1f", ((Map<String, Object>) stats.get("memoryUsage")).get("heapUsagePercentage"))
            );
        }
    }
}