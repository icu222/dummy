package com.kt.dummy;

import com.kt.dummy.manager.ResponseMapManager;
import com.kt.dummy.server.MultiProtocolServer;
import com.kt.dummy.server.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 더미 서버 메인 애플리케이션
 * @author 고재원
 */
public class DummyServerApplication {
    private static final Logger logger = LoggerFactory.getLogger(DummyServerApplication.class);
    
    public static void main(String[] args) {
        try {
            logger.info("=== 더미 서버 시작 ===");
            
            // 1. 설정 로드
            ServerConfig config = ServerConfig.getInstance();
            logger.info("서버 설정 로드 완료");
            
            // 2. 응답 전문 맵 초기화
            ResponseMapManager.getInstance().initialize();
            logger.info("응답 전문 맵 초기화 완료");
            
            // 3. 멀티 프로토콜 서버 시작
            MultiProtocolServer server = new MultiProtocolServer(config);
            server.start();
            
            // 4. Shutdown Hook 등록
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("=== 더미 서버 종료 중 ===");
                server.shutdown();
                logger.info("=== 더미 서버 종료 완료 ===");
            }));
            
            logger.info("=== 더미 서버 시작 완료 ===");
            logger.info("관리 API 포트: {}", config.getManagementPort());
            
        } catch (Exception e) {
            logger.error("더미 서버 시작 실패", e);
            System.exit(1);
        }
    }
}