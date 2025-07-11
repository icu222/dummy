package com.kt.dummy.handler;

import com.kt.dummy.manager.ResponseMapManager;
import com.kt.dummy.processor.DelayResponseProcessor;
import com.kt.dummy.util.ProtocolUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * &key=value 프로토콜 핸들러 (CAPRI용)
 * @author 고재원
 */
public class KeyValueHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(KeyValueHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        try {
            String receivedData = msg.toString(CharsetUtil.UTF_8);
            logger.debug("수신 KeyValue: {}", receivedData);

            // &key=value 파싱
            Map<String, String> requestMap = parseKeyValueString(receivedData);

            // API명 추출 (opcode 필드에서)
            String apiName = requestMap.get("opcode");
            if (apiName == null) {
                logger.warn("opcode 없음: {}", receivedData);
                sendErrorResponse(ctx, "Missing opcode");
                return;
            }

            // 포트 번호 로깅 (디버깅용)
            int port = ProtocolUtil.getPortFromChannel(ctx.channel());
            logger.debug("요청 수신 - 포트: {}, opcode: {}", port, apiName);

            // 특별한 경우 처리 (기존 코드 패턴 참조)
            String responseKey = buildResponseKey(requestMap, apiName);

            // 응답 전문 조회 (stage 무관, keyValue 프로토콜로 고정)
            String responseContent = ResponseMapManager.getInstance()
                    .getResponse("keyValue", responseKey);

            if (responseContent == null) {
                logger.warn("응답 전문 없음: protocol=keyValue, api={}", responseKey);
                sendErrorResponse(ctx, "No response template found for opcode: " + apiName);
                return;
            }

            // 지연 응답 처리
            DelayResponseProcessor.processWithDelay(ctx, responseContent, (context, content) -> {
                sendKeyValueResponse(context, content, requestMap.get("transaction_id"));
            });

        } catch (Exception e) {
            logger.error("KeyValue 처리 중 오류", e);
            sendErrorResponse(ctx, "Internal server error");
        }
    }

    private Map<String, String> parseKeyValueString(String str) {
        Map<String, String> map = new LinkedHashMap<>();

        // 첫 번째 문자가 구분자면 제거
        if (str.startsWith("&") || str.startsWith("/")) {
            str = str.substring(1);
        }

        StringTokenizer strToken = new StringTokenizer(str, "&");
        while (strToken.hasMoreTokens()) {
            StringTokenizer strTokenSub = new StringTokenizer(strToken.nextToken(), "=");
            if (strTokenSub.hasMoreTokens()) {
                String key = strTokenSub.nextToken();
                String value = "";
                if (strTokenSub.hasMoreTokens()) {
                    value = strTokenSub.nextToken();
                }
                map.put(key, value);
            }
        }
        return map;
    }

    private String buildResponseKey(Map<String, String> requestMap, String opcode) {
        // 기존 코드 패턴에 따른 특별 처리
        String ctn = requestMap.get("ctn");
        if (("406".equals(opcode) || "435".equals(opcode)) && ctn != null) {
            return "OPCODE_" + opcode + "_" + ctn;
        }
        return "OPCODE_" + opcode;
    }

    private void sendKeyValueResponse(ChannelHandlerContext ctx, String content, String transactionId) {
        // transaction_id 추가
        String finalContent = "transaction_id=" + (transactionId != null ? transactionId : "1") + "&" + content;

        // 헤더 추가
        String headerLength = String.format("%05d", finalContent.getBytes(CharsetUtil.UTF_8).length + 1);
        String response = "data_length=" + headerLength + "/" + finalContent;

        ByteBuf responseBuf = Unpooled.copiedBuffer(response, CharsetUtil.UTF_8);
        ctx.writeAndFlush(responseBuf);
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, String errorMsg) {
        sendKeyValueResponse(ctx, "response=f&code=999&RT=1&RT_MSG=" + errorMsg, "1");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("KeyValue 핸들러 예외", cause);
        ctx.close();
    }
}