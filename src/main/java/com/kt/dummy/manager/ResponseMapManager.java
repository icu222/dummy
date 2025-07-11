package com.kt.dummy.manager;

import com.kt.dummy.server.ServerConfig;
import com.kt.dummy.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 응답 전문 메모리 맵 관리자
 * protocol(KEY) -> apiName -> response 구조
 * @author 고재원
 */
public class ResponseMapManager {
    private static final Logger logger = LoggerFactory.getLogger(ResponseMapManager.class);
    private static final ResponseMapManager INSTANCE = new ResponseMapManager();

    // protocol -> apiName -> responseContent
    private final Map<String, Map<String, String>> responseMaps;
    private final FileResponseLoader fileLoader;

    private ResponseMapManager() {
        this.responseMaps = new ConcurrentHashMap<>();
        this.fileLoader = new FileResponseLoader();
        initializeProtocolMaps();
    }

    public static ResponseMapManager getInstance() {
        return INSTANCE;
    }

    private void initializeProtocolMaps() {
        String[] protocols = {"json", "xml", "soap", "keyValue"};

        for (String protocol : protocols) {
            responseMaps.put(protocol, new ConcurrentHashMap<>());
        }
    }

    public void initialize() {
        logger.info("응답 전문 맵 초기화 시작...");

        try {
            String[] stages = {"stage1", "stage2", "stage3", "stage4"};
            String[] protocols = {"json", "xml", "soap", "keyValue"};

            int totalLoaded = 0;
            Map<String, Integer> duplicateCount = new ConcurrentHashMap<>();

            // stage1부터 stage4까지 순차적으로 로드 (나중 stage가 이전 것을 덮어씀)
            for (String stage : stages) {
                logger.info("{} 단계 파일 로드 시작", stage);

                for (String protocol : protocols) {
                    Map<String, String> apiResponses = fileLoader.loadResponsesForStageAndProtocol(stage, protocol);

                    // 기존 맵에 병합 (중복 시 덮어쓰기)
                    for (Map.Entry<String, String> entry : apiResponses.entrySet()) {
                        String apiName = entry.getKey();
                        String responseContent = entry.getValue();

                        // 중복 체크
                        if (responseMaps.get(protocol).containsKey(apiName)) {
                            String dupKey = protocol + ":" + apiName;
                            duplicateCount.merge(dupKey, 1, Integer::sum);
                            logger.info("응답 전문 덮어쓰기: {}/{} ({}에서 로드)",
                                    protocol, apiName, stage);
                        }

                        responseMaps.get(protocol).put(apiName, responseContent);
                    }

                    totalLoaded += apiResponses.size();
                    logger.debug("{}/{} 프로토콜: {} 개 API 로드", stage, protocol, apiResponses.size());
                }
            }

            // 중복 통계 로깅
            if (!duplicateCount.isEmpty()) {
                logger.info("=== 중복 API 통계 ===");
                duplicateCount.forEach((key, count) -> {
                    logger.info("{}: {} 번 중복 (최종적으로 가장 높은 stage 값 사용)", key, count);
                });
            }

            // 최종 통계
            logger.info("=== 최종 로드 통계 ===");
            for (String protocol : protocols) {
                int protocolCount = responseMaps.get(protocol).size();
                logger.info("{} 프로토콜: {} 개 고유 API", protocol, protocolCount);
            }

            logger.info("응답 전문 맵 초기화 완료: 총 {} 개 파일 처리", totalLoaded);

        } catch (Exception e) {
            logger.error("응답 전문 맵 초기화 실패", e);
            throw new RuntimeException("응답 전문 맵 초기화 실패", e);
        }
    }

    /**
     * 응답 전문 조회 (stage 무관)
     * @param protocol 프로토콜 (json, xml, soap, keyValue)
     * @param apiName API명
     * @return 응답 전문 내용
     */
    public String getResponse(String protocol, String apiName) {
        try {
            Map<String, String> protocolMap = responseMaps.get(protocol);
            if (protocolMap == null) {
                logger.warn("존재하지 않는 프로토콜: {}", protocol);
                return null;
            }

            String response = protocolMap.get(apiName);
            if (response != null) {
                logger.debug("응답 전문 조회 성공: {}/{}", protocol, apiName);
            } else {
                logger.debug("응답 전문 없음: {}/{}", protocol, apiName);
            }

            return response;

        } catch (Exception e) {
            logger.error("응답 전문 조회 중 오류: {}/{}", protocol, apiName, e);
            return null;
        }
    }

    /**
     * 응답 전문 추가/업데이트
     * @param protocol 프로토콜
     * @param apiName API명
     * @param responseContent 응답 전문 내용
     */
    public void putResponse(String protocol, String apiName, String responseContent) {
        try {
            Map<String, String> protocolMap = responseMaps.get(protocol);
            if (protocolMap == null) {
                logger.warn("존재하지 않는 프로토콜: {}", protocol);
                return;
            }

            protocolMap.put(apiName, responseContent);
            logger.info("응답 전문 업데이트: {}/{}", protocol, apiName);

        } catch (Exception e) {
            logger.error("응답 전문 업데이트 중 오류: {}/{}", protocol, apiName, e);
        }
    }

    /**
     * 특정 프로토콜의 모든 API 목록 조회
     */
    public Map<String, String> getAllResponses(String protocol) {
        try {
            Map<String, String> protocolMap = responseMaps.get(protocol);
            return protocolMap != null ? new ConcurrentHashMap<>(protocolMap) : new ConcurrentHashMap<>();

        } catch (Exception e) {
            logger.error("전체 응답 전문 조회 중 오류: {}", protocol, e);
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
            Map<String, Integer> protocolStats = new ConcurrentHashMap<>();

            for (Map.Entry<String, Map<String, String>> protocolEntry : responseMaps.entrySet()) {
                String protocol = protocolEntry.getKey();
                int protocolCount = protocolEntry.getValue().size();

                protocolStats.put(protocol, protocolCount);
                totalApis += protocolCount;
            }

            stats.put("totalApis", totalApis);
            stats.put("protocolStats", protocolStats);

        } catch (Exception e) {
            logger.error("통계 정보 조회 중 오류", e);
        }

        return stats;
    }

    /**
     * 모든 프로토콜의 API 목록 조회 (디버깅용)
     */
    public Map<String, Map<String, String>> getAllResponseMaps() {
        return new ConcurrentHashMap<>(responseMaps);
    }
}