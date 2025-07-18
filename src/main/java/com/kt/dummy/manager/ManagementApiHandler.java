package com.kt.dummy.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.dummy.processor.DelayConfigManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 관리 API 핸들러 (9999 포트)
 * @author 고재원
 */
public class ManagementApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(ManagementApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResponseMapManager responseManager = ResponseMapManager.getInstance();
    private final FileResponseLoader fileLoader = new FileResponseLoader();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        try {
            String uri = request.uri();
            HttpMethod method = request.method();

            logger.info("관리 API 요청: {} {}", method, uri);

            if (uri.startsWith("/api/response")) {
                handleResponseApi(ctx, request);
            } else if (uri.startsWith("/api/status")) {
                handleStatusApi(ctx, request);
            } else if (uri.startsWith("/api/stats")) {
                handleStatsApi(ctx, request);
            } else if (uri.startsWith("/api/delay")) {
                handleDelayApi(ctx, request);
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        "{\"error\":\"API not found\",\"path\":\"" + uri + "\"}");
            }

        } catch (Exception e) {
            logger.error("관리 API 처리 중 오류", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"error\":\"Internal server error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleDelayApi(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            Map<String, List<String>> params = decoder.parameters();
            String content = request.content().toString(CharsetUtil.UTF_8);

            // 조회 요청 (파라미터와 본문 모두 없을 때)
            if (params.isEmpty() && content.isEmpty()) {
                Map<String, Object> currentConfig = DelayConfigManager.getInstance().getCurrentConfig();
                sendJsonResponse(ctx, HttpResponseStatus.OK, objectMapper.writeValueAsString(currentConfig));
                return;
            }

            // 쿼리 파라미터로 간단 설정
            if (!params.isEmpty()) {
                // 전역 설정
                if (params.containsKey("enable")) {
                    boolean enabled = Boolean.parseBoolean(params.get("enable").get(0));
                    DelayConfigManager.getInstance().setGlobalEnabled(enabled);
                }

                if (params.containsKey("min") && params.containsKey("max")) {
                    long min = Long.parseLong(params.get("min").get(0));
                    long max = Long.parseLong(params.get("max").get(0));
                    DelayConfigManager.getInstance().setGlobalDelay(min, max);
                }

                // 포트별 설정
                if (params.containsKey("port")) {
                    int port = Integer.parseInt(params.get("port").get(0));
                    boolean enabled = params.containsKey("enable") ?
                            Boolean.parseBoolean(params.get("enable").get(0)) : true;

                    if (params.containsKey("min") && params.containsKey("max")) {
                        long min = Long.parseLong(params.get("min").get(0));
                        long max = Long.parseLong(params.get("max").get(0));
                        DelayConfigManager.getInstance().setPortDelay(port, enabled, min, max);
                    }
                }
            }

            // JSON 본문으로 복잡한 설정 (있을 경우)
            if (!content.isEmpty()) {
                JsonNode json = objectMapper.readTree(content);
                DelayConfigManager.getInstance().applyJsonConfig(json);
            }

            // 변경 사항 로깅
            logger.info("지연 설정 변경 - 파라미터: {}, 본문: {}", params, content);

            // 업데이트된 설정 반환
            Map<String, Object> updatedConfig = DelayConfigManager.getInstance().getCurrentConfig();
            sendJsonResponse(ctx, HttpResponseStatus.OK, objectMapper.writeValueAsString(updatedConfig));

        } catch (Exception e) {
            logger.error("지연 설정 처리 중 오류", e);
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleResponseApi(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpMethod method = request.method();

        if (method == HttpMethod.POST) {
            // 새 응답 전문 등록
            createOrUpdateResponse(ctx, request);
        } else if (method == HttpMethod.GET) {
            // 응답 전문 조회
            getResponse(ctx, request);
        } else {
            sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,
                    "{\"error\":\"Method not allowed\",\"allowed\":[\"GET\",\"POST\"]}");
        }
    }

    private void createOrUpdateResponse(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String content = request.content().toString(CharsetUtil.UTF_8);
        JsonNode requestJson = objectMapper.readTree(content);

        String protocol = getJsonString(requestJson, "protocol");
        String apiName = getJsonString(requestJson, "apiName");
        String responseContent = getJsonString(requestJson, "responseContent");

        if (protocol == null || apiName == null || responseContent == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    "{\"error\":\"Missing required fields\",\"required\":[\"protocol\",\"apiName\",\"responseContent\"]}");
            return;
        }

        // 유효성 검증
        if (!isValidProtocol(protocol)) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    "{\"error\":\"Invalid protocol\",\"validProtocols\":[\"json\",\"xml\",\"soap\",\"keyValue\"]}");
            return;
        }

        try {
            // 메모리 맵 업데이트
            responseManager.putResponse(protocol, apiName, responseContent);

            // 응답
            String responseJson = String.format(
                    "{\"success\":true,\"message\":\"Response updated successfully\",\"protocol\":\"%s\",\"apiName\":\"%s\"}",
                    protocol, apiName
            );

            sendJsonResponse(ctx, HttpResponseStatus.OK, responseJson);

        } catch (Exception e) {
            logger.error("응답 전문 등록/업데이트 실패", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"error\":\"Failed to update response\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void getResponse(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();

        // 쿼리 파라미터 파싱: /api/response?protocol=json&apiName=test
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, String> params = decoder.parameters().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().isEmpty() ? null : entry.getValue().get(0)
                ));

        String protocol = params.get("protocol");
        String apiName = params.get("apiName");

        if (protocol != null && apiName != null) {
            // 특정 응답 전문 조회
            String responseContent = responseManager.getResponse(protocol, apiName);
            if (responseContent != null) {
                String responseJson = String.format(
                        "{\"success\":true,\"protocol\":\"%s\",\"apiName\":\"%s\",\"responseContent\":%s}",
                        protocol, apiName, objectMapper.writeValueAsString(responseContent)
                );
                sendJsonResponse(ctx, HttpResponseStatus.OK, responseJson);
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        "{\"error\":\"Response not found\",\"protocol\":\"" + protocol + "\",\"apiName\":\"" + apiName + "\"}");
            }
        } else if (protocol != null) {
            // 특정 프로토콜의 모든 응답 조회
            Map<String, String> responses = responseManager.getAllResponses(protocol);
            String responseJson = objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "protocol", protocol,
                    "responses", responses,
                    "count", responses.size()
            ));
            sendJsonResponse(ctx, HttpResponseStatus.OK, responseJson);
        } else {
            // 전체 응답 맵 조회
            Map<String, Map<String, String>> allResponses = responseManager.getAllResponseMaps();
            String responseJson = objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "responses", allResponses,
                    "statistics", responseManager.getStatistics()
            ));
            sendJsonResponse(ctx, HttpResponseStatus.OK, responseJson);
        }
    }

    private void handleStatusApi(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String statusJson = objectMapper.writeValueAsString(Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis(),
                "uptime", ManagementUtils.getUptimeMs(),
                "memoryUsage", ManagementUtils.getMemoryUsage()
        ));

        sendJsonResponse(ctx, HttpResponseStatus.OK, statusJson);
    }

    private void handleStatsApi(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Map<String, Object> stats = responseManager.getStatistics();
        stats.put("timestamp", System.currentTimeMillis());

        String statsJson = objectMapper.writeValueAsString(stats);
        sendJsonResponse(ctx, HttpResponseStatus.OK, statsJson);
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String jsonContent) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(jsonContent, CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

        ctx.writeAndFlush(response);
    }

    private String getJsonString(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }

    private boolean isValidStage(String stage) {
        return stage != null && stage.matches("stage[1-4]");
    }

    private boolean isValidProtocol(String protocol) {
        return protocol != null &&
                ("json".equals(protocol) || "xml".equals(protocol) ||
                        "soap".equals(protocol) || "keyValue".equals(protocol));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("관리 API 핸들러 예외", cause);
        ctx.close();
    }

    // 내부 유틸리티 클래스
    private static class ManagementUtils {
        private static final long startTime = System.currentTimeMillis();

        public static long getUptimeMs() {
            return System.currentTimeMillis() - startTime;
        }

        public static Map<String, Object> getMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            return Map.of(
                    "used", usedMemory,
                    "free", freeMemory,
                    "total", totalMemory,
                    "max", maxMemory,
                    "usagePercentage", (usedMemory * 100.0) / maxMemory
            );
        }
    }
}