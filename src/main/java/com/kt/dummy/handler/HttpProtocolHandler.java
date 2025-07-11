package com.kt.dummy.handler;

import com.kt.dummy.manager.ResponseMapManager;
import com.kt.dummy.processor.DelayResponseProcessor;
import com.kt.dummy.processor.DelayConfigManager;
import com.kt.dummy.util.ProtocolUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP/HTTPS 프로토콜 핸들러
 * @author 고재원
 */
public class HttpProtocolHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(HttpProtocolHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        try {
            String uri = request.uri();
            String method = request.method().name();

            logger.debug("HTTP 요청: {} {}", method, uri);

            // API명 추출 (URI에서)
            String apiName = extractApiNameFromUri(uri);

            // Content-Type으로 프로토콜 판별
            String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
            String protocol = determineProtocolFromContentType(contentType);

            // 포트 로깅 (디버깅용)
            int port = ProtocolUtil.getPortFromChannel(ctx.channel());
            logger.debug("요청 수신 - 포트: {}, 프로토콜: {}, API: {}", port, protocol, apiName);

            // 응답 전문 조회 (stage 무관)
            String responseContent = ResponseMapManager.getInstance()
                    .getResponse(protocol, apiName);

            if (responseContent == null) {
                logger.warn("응답 전문 없음: protocol={}, api={}", protocol, apiName);
                sendHttpErrorResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        "No response template found for API: " + apiName);
                return;
            }

            // 지연 응답 처리 (동적 지연 적용)
            long delay = DelayConfigManager.getInstance().getDelayForPort(port);
            DelayResponseProcessor.processWithDelay(ctx, responseContent, (context, content) -> {
                sendHttpResponse(context, content, protocol);
            }, delay);

        } catch (Exception e) {
            logger.error("HTTP 처리 중 오류", e);
            sendHttpErrorResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private String extractApiNameFromUri(String uri) {
        // URI에서 API명 추출 (예: /api/getUserInfo -> getUserInfo)
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }

        String[] segments = uri.split("/");
        if (segments.length > 0) {
            String lastSegment = segments[segments.length - 1];
            // 쿼리 파라미터 제거
            int queryIndex = lastSegment.indexOf('?');
            if (queryIndex > 0) {
                lastSegment = lastSegment.substring(0, queryIndex);
            }
            return lastSegment;
        }

        return "default";
    }

    private String determineProtocolFromContentType(String contentType) {
        if (contentType == null) return "json";

        contentType = contentType.toLowerCase();
        if (contentType.contains("xml")) {
            if (contentType.contains("soap")) {
                return "soap";
            }
            return "xml";
        } else if (contentType.contains("json")) {
            return "json";
        } else if (contentType.contains("multipart")) {
            return "json"; // multipart는 json으로 처리
        }

        return "json"; // 기본값
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, String content, String protocol) {
        String contentType = getContentTypeForProtocol(protocol);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType + "; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        ctx.writeAndFlush(response);
    }

    private void sendHttpErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        String errorJson = "{\"error\":\"" + message + "\",\"status\":" + status.code() + "}";

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(errorJson, CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        ctx.writeAndFlush(response);
    }

    private String getContentTypeForProtocol(String protocol) {
        switch (protocol) {
            case "json": return "application/json";
            case "xml": return "application/xml";
            case "soap": return "text/xml";
            default: return "application/json";
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("HTTP 핸들러 예외", cause);
        ctx.close();
    }
}