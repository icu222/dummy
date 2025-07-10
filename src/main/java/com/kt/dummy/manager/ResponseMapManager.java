package com.kt.dummy.manager;

import com.kt.dummy.server.ServerConfig;
import com.kt.dummy.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 응답 전문 메모리 맵 관리자
 * @author 고재원
 */
public class ResponseMapManager {
    private static final Logger logger = LoggerFactory.getLogger(ResponseMapManager.class);
    private static final ResponseMapManager INSTANCE = new ResponseMapManager();
    
    // stage -> protocol -> apiName -> responseContent
    private final Map<String, Map<String, Map<String, String>>> responseMaps;
    private final FileResponseLoader fileLoader;
    
    private ResponseMapManager() {
        this.responseMaps = new ConcurrentHashMap<>();
        this.fileLoader = new FileResponseLoader();
        initializeStageAndProtocolMaps();
    }
    
    public static ResponseMapManager getInstance() {
        return INSTANCE;
    }
    
    private void initializeStageAndProtocolMaps() {
        String[] stages = {"stage1", "stage2", "stage3", "stage4"};
        String[] protocols = {"json", "xml", "soap", "keyValue"};
        
        for (String stage : stages) {
            Map<String, Map<String, String>> protocolMap = new ConcurrentHashMap<>();
            for (String protocol : protocols) {
                protocolMap.put(protocol, new ConcurrentHashMap<>());
            }
            responseMaps.put(stage, protocolMap);
        }
    }
    
    public void initialize() {
        logger.info("응답 전문 맵 초기화 시작...");
        
        try {
            // 모든 단계/프로토콜별 파일 로드
            String[] stages = {"stage1", "stage2", "stage3", "stage4"};
            String[] protocols = {"json", "xml", "soap", "keyValue"};
            
            int totalLoaded = 0;
            for (String stage : stages) {
                for (String protocol : protocols) {
                    Map<String, String> apiResponses = fileLoader.loadResponsesForStageAndProtocol(stage, protocol);
                    responseMaps.get(stage).get(protocol).putAll(apiResponses);
                    totalLoaded += apiResponses.size();
                    
                    logger.debug("{}/{} 프로토콜: {} 개 API 로드", stage, protocol, apiResponses.size());
                }
            }
            
            logger.info("응답 전문 맵 초기화 완료: 총 {} 개 API", totalLoaded);
            
        } catch (Exception e) {
            logger.error("응답 전문 맵 초기화 실패", e);
            throw new RuntimeException("응답 전문 맵 초기화 실패", e);
        }
    }
    
    /**
     * 응답 전문 조회
     * @param stage 단계 (stage1~stage4)
     * @param protocol 프로토콜 (json, xml, soap, keyValue)
     * @param apiName API명
     * @return 응답 전문 내용
     */
    public String getResponse(String stage, String protocol, String apiName) {
        try {
            Map<String, Map<String, String>> stageMap = responseMaps.get(stage);
            if (stageMap == null) {
                logger.warn("존재하지 않는 단계: {}", stage);
                return null;
            }
            
            Map<String, String> protocolMap = stageMap.get(protocol);
            if (protocolMap == null) {
                logger.warn("존재하지 않는 프로토콜: {}", protocol);
                return null;
            }
            
            String response = protocolMap.get(apiName);
            if (response != null) {
                logger.debug("응답 전문 조회 성공: {}/{}/{}", stage, protocol, apiName);
            } else {
                logger.debug("응답 전문 없음: {}/{}/{}", stage, protocol, apiName);
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("응답 전문 조회 중 오류: {}/{}/{}", stage, protocol, apiName, e);
            return null;
        }
    }
    
    /**
     * 응답 전문 추가/업데이트
     * @param stage 단계
     * @param protocol 프로토콜
     * @param apiName API명
     * @param responseContent 응답 전문 내용
     */
    public void putResponse(String stage, String protocol, String apiName, String responseContent) {
        try {
            Map<String, Map<String, String>> stageMap = responseMaps.get(stage);
            if (stageMap == null) {
                logger.warn("존재하지 않는 단계: {}", stage);
                return;
            }
            
            Map<String, String> protocolMap = stageMap.get(protocol);
            if (protocolMap == null) {
                logger.warn("존재하지 않는 프로토콜: {}", protocol);
                return;
            }
            
            protocolMap.put(apiName, responseContent);
            logger.info("응답 전문 업데이트: {}/{}/{}", stage, protocol, apiName);
            
        } catch (Exception e) {
            logger.error("응답 전문 업데이트 중 오류: {}/{}/{}", stage, protocol, apiName, e);
        }
    }
    
    /**
     * 특정 단계/프로토콜의 모든 API 목록 조회
     */
    public Map<String, String> getAllResponses(String stage, String protocol) {
        try {
            Map<String, Map<String, String>> stageMap = responseMaps.get(stage);
            if (stageMap == null) {
                return new ConcurrentHashMap<>();
            }
            
            Map<String, String> protocolMap = stageMap.get(protocol);
            return protocolMap != null ? new ConcurrentHashMap<>(protocolMap) : new ConcurrentHashMap<>();
            
        } catch (Exception e) {
            logger.error("전체 응답 전문 조회 중 오류: {}/{}", stage, protocol, e);
            return new ConcurrentHashMap<>();
        }
    }
    
    /**
     * 통계 정보 조회
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        try {
            int totalApis = 0;
            Map<String, Integer> stageStats = new ConcurrentHashMap<>();
            Map<String, Integer> protocolStats = new ConcurrentHashMap<>();
            
            for (Map.Entry<String, Map<String, Map<String, String>>> stageEntry : responseMaps.entrySet()) {
                String stage = stageEntry.getKey();
                int stageTotal = 0;
                
                for (Map.Entry<String, Map<String, String>> protocolEntry : stageEntry.getValue().entrySet()) {
                    String protocol = protocolEntry.getKey();
                    int protocolCount = protocolEntry.getValue().size();
                    
                    stageTotal += protocolCount;
                    protocolStats.merge(protocol, protocolCount, Integer::sum);
                }
                
                stageStats.put(stage, stageTotal);
                totalApis += stageTotal;
            }
            
            stats.put("totalApis", totalApis);
            stats.put("stageStats", stageStats);
            stats.put("protocolStats", protocolStats);
            
        } catch (Exception e) {
            logger.error("통계 정보 조회 중 오류", e);
        }
        
        return stats;
    }
}