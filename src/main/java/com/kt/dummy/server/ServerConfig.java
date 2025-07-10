package com.kt.dummy.server;

import java.io.InputStream;
import java.util.*;

/**
 * 서버 설정 관리 클래스
 * @author 고재원
 */
public class ServerConfig {
    private static final ServerConfig INSTANCE = new ServerConfig();
    private final Properties properties;
    
    // 포트 정의
    private final List<Integer> scapPorts = Arrays.asList(8001, 8002, 8003, 8004);
    private final List<Integer> capriPorts = Arrays.asList(18000, 19000, 20000, 10120);
    private final int httpPort = 80;
    private final int httpsPort = 443;
    private final int managementPort = 9999;
    
    private ServerConfig() {
        properties = new Properties();
        loadProperties();
    }
    
    public static ServerConfig getInstance() {
        return INSTANCE;
    }
    
    private void loadProperties() {
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (Exception e) {
            throw new RuntimeException("설정 파일 로드 실패", e);
        }
    }
    
    public List<Integer> getScapPorts() { return scapPorts; }
    public List<Integer> getCapriPorts() { return capriPorts; }
    public int getHttpPort() { return httpPort; }
    public int getHttpsPort() { return httpsPort; }
    public int getManagementPort() { return managementPort; }
    
    public int getBossThreads() {
        return Integer.parseInt(properties.getProperty("server.boss.threads", "1"));
    }
    
    public int getWorkerThreads() {
        return Integer.parseInt(properties.getProperty("server.worker.threads", 
            String.valueOf(Runtime.getRuntime().availableProcessors() * 2)));
    }
    
    public long getDefaultDelayMs() {
        return Long.parseLong(properties.getProperty("server.default.delay.ms", "0"));
    }
    
    public String getResponseBasePath() {
        return properties.getProperty("server.response.base.path", "response");
    }
    
    public boolean isPerformanceLogEnabled() {
        return Boolean.parseBoolean(properties.getProperty("server.performance.log.enabled", "false"));
    }
}