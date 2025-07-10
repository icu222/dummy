package com.kt.dummy.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        
        // 필수 필드 검증
        String stage = getJsonString(requestJson, "stage");
        String protocol = getJsonString(requestJson, "protocol");
        String apiName = getJsonString(requestJson, "apiName");
        String responseContent = getJsonString(requestJson, "responseContent");
        
        if (stage == null || protocol == null || apiName == null || responseContent == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                "{\"error\":\"Missing required fields\",\"required\":[\"stage\",\"protocol\",\"apiName\",\"responseContent\"]}");
            return;
        }
        
        // 유효성 검증
        if (!isValidStage(stage)) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                "{\"error\":\"Invalid stage\",\"validStages\":[\"stage1\",\"stage2\",\"stage3\",\"stage4\"]}");
            return;
        }
        
        if (!isValidProtocol(protocol)) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                "{\"error\":\"Invalid protocol\",\"validProtocols\":[\"json\",\"xml\",\"soap\",\"keyValue\"]}");
            return;
        }
        
        try {
            // 1. 파일 저장
            boolean fileSaved = fileLoader.saveResponseFile(stage, protocol, apiName, responseContent);
            
            // 2. 메모리 맵 업데이트
            responseManager.putResponse(stage, protocol, apiName, responseContent);
            
            // 3. 응답
            String responseJson = String.format(
                "{\"success\":true,\"message\":\"Response updated successfully\",\"stage\":\"%s\",\"protocol\":\"%s\",\"apiName\":\"%s\",\"fileSaved\":%s}",
                stage, protocol, apiName, fileSaved
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
        
        // 쿼리 파라미터 파싱: /api/response?stage=stage1&protocol=json&apiName=test
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, String> params = decoder.parameters().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().isEmpty() ? null : entry.getValue().get(0)
            ));
        
        String stage = params.get("stage");
        String protocol = params.get("protocol");
        String apiName = params.get("apiName");
        
        if (stage != null && protocol != null && apiName != null) {
            // 특정 응답 전문 조회
            String responseContent = responseManager.getResponse(stage, protocol, apiName);
            if (responseContent != null) {
                String responseJson = String.format(
                    "{\"success\":true,\"stage\":\"%s\",\"protocol\":\"%s\",\"apiName\":\"%s\",\"responseContent\":%s}",
                    stage, protocol, apiName, objectMapper.writeValueAsString(responseContent)
                );
                sendJsonResponse(ctx, HttpResponseStatus.OK, responseJson);
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"error\":\"Response not found\",\"stage\":\"" + stage + "\",\"protocol\":\"" + protocol + "\",\"apiName\":\"" + apiName + "\"}");
            }
        } else if (stage != null && protocol != null) {
            // 특정 단계/프로토콜의 모든 응답 조회
            Map<String, String> responses = responseManager.getAllResponses(stage, protocol);
            String responseJson = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "stage", stage,
                "protocol", protocol,
                "responses", responses,
                "count", responses.size()
            ));
            sendJsonResponse(ctx, HttpResponseStatus.OK, responseJson);
        } else {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                "{\"error\":\"Missing required parameters\",\"required\":[\"stage\",\"protocol\",\"apiName\"]}");
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