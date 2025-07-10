package com.kt.dummy.processor;

import com.kt.dummy.server.ServerConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 지연 응답 처리기
 * @author 고재원
 */
public class DelayResponseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DelayResponseProcessor.class);
    
    // 응답 처리 함수형 인터페이스
    @FunctionalInterface
    public interface ResponseHandler {
        void handle(ChannelHandlerContext ctx, String content);
    }
    
    /**
     * 지연 응답 처리 (기본 지연 시간 사용)
     * @param ctx 채널 컨텍스트
     * @param responseContent 응답 내용
     * @param responseHandler 응답 처리 핸들러
     */
    public static void processWithDelay(ChannelHandlerContext ctx, 
                                       String responseContent, 
                                       ResponseHandler responseHandler) {
        long delayMs = ServerConfig.getInstance().getDefaultDelayMs();
        processWithDelay(ctx, responseContent, responseHandler, delayMs);
    }
    
    /**
     * 지연 응답 처리 (커스텀 지연 시간)
     * @param ctx 채널 컨텍스트
     * @param responseContent 응답 내용
     * @param responseHandler 응답 처리 핸들러
     * @param delayMs 지연 시간 (밀리초)
     */
    public static void processWithDelay(ChannelHandlerContext ctx, 
                                       String responseContent, 
                                       ResponseHandler responseHandler, 
                                       long delayMs) {
        if (delayMs <= 0) {
            // 지연 없이 즉시 응답
            try {
                responseHandler.handle(ctx, responseContent);
            } catch (Exception e) {
                logger.error("즉시 응답 처리 중 오류", e);
            }
            return;
        }
        
        // Netty EventLoop를 사용한 지연 처리 (성능 최적화)
        EventLoop eventLoop = ctx.channel().eventLoop();
        
        eventLoop.schedule(() -> {
            try {
                if (ctx.channel().isActive()) {
                    responseHandler.handle(ctx, responseContent);
                    
                    if (ServerConfig.getInstance().isPerformanceLogEnabled()) {
                        logger.debug("지연 응답 완료: {}ms 후 처리", delayMs);
                    }
                } else {
                    logger.warn("채널 비활성 상태로 응답 생략: {}ms 지연", delayMs);
                }
            } catch (Exception e) {
                logger.error("지연 응답 처리 중 오류: {}ms 지연", delayMs, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        
        if (ServerConfig.getInstance().isPerformanceLogEnabled()) {
            logger.debug("지연 응답 스케줄링: {}ms 후 처리 예정", delayMs);
        }
    }
    
    /**
     * 포트별 지연 시간 처리 (확장 기능)
     * @param ctx 채널 컨텍스트
     * @param responseContent 응답 내용
     * @param responseHandler 응답 처리 핸들러
     */
    public static void processWithPortBasedDelay(ChannelHandlerContext ctx, 
                                                String responseContent, 
                                                ResponseHandler responseHandler) {
        // 포트별 다른 지연 시간 적용 (향후 확장 가능)
        String addressStr = ctx.channel().localAddress().toString();
        int port = extractPortFromAddress(addressStr);
        
        long delayMs = getDelayForPort(port);
        processWithDelay(ctx, responseContent, responseHandler, delayMs);
    }
    
    private static int extractPortFromAddress(String addressStr) {
        try {
            if (addressStr.contains(":")) {
                String[] parts = addressStr.split(":");
                return Integer.parseInt(parts[parts.length - 1]);
            }
        } catch (Exception e) {
            logger.debug("포트 추출 실패: {}", addressStr);
        }
        return 0;
    }
    
    private static long getDelayForPort(int port) {
        // 포트별 지연 시간 매핑 (설정으로 관리 가능)
        if (port >= 8001 && port <= 8004) {
            return 0; // A그룹: 즉시 응답
        } else if (port >= 18000 && port <= 20000 || port == 10120) {
            return 100; // B그룹: 100ms 지연
        } else if (port == 80 || port == 443) {
            return 50; // HTTP/HTTPS: 50ms 지연
        }
        
        return ServerConfig.getInstance().getDefaultDelayMs();
    }
    
    /**
     * 동적 지연 시간 처리 (부하에 따른 조절)
     */
    public static void processWithAdaptiveDelay(ChannelHandlerContext ctx, 
                                               String responseContent, 
                                               ResponseHandler responseHandler) {
        // JVM 메모리 사용률에 따른 동적 지연 (성능 최적화)
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double memoryUsage = (double)(totalMemory - freeMemory) / totalMemory;
        
        long adaptiveDelay = 0;
        if (memoryUsage > 0.8) {
            adaptiveDelay = 200; // 80% 이상 사용 시 200ms 지연
        } else if (memoryUsage > 0.6) {
            adaptiveDelay = 100; // 60% 이상 사용 시 100ms 지연
        }
        
        processWithDelay(ctx, responseContent, responseHandler, adaptiveDelay);
    }
}