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
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * 각 요청을 통신/데이터 프로토콜로 분류
 * 그래서 XML 프로토콜이라고 함.
 *
 * XML 프로토콜 핸들러 (scap용)
 * @author 고재원
 */
public class XmlProtocolHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(XmlProtocolHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        try {
            String receivedXml = msg.toString(CharsetUtil.UTF_8);
            logger.debug("수신 XML: {}", receivedXml);

            // API명 추출 (XML 루트 엘리먼트에서)
            String apiName = extractApiNameFromXml(receivedXml);
            if (apiName == null) {
                logger.warn("API명 추출 실패: {}", receivedXml);
                sendErrorResponse(ctx, "Invalid XML format");
                return;
            }

            // 포트 번호 로깅 (디버깅용)
            int port = ProtocolUtil.getPortFromChannel(ctx.channel());
            logger.debug("요청 수신 - 포트: {}, API: {}", port, apiName);

            // 응답 전문 조회 (stage 무관, xml 프로토콜로 고정)
            String responseContent = ResponseMapManager.getInstance()
                    .getResponse("xml", apiName);

            if (responseContent == null) {
                logger.warn("응답 전문 없음: protocol=xml, api={}", apiName);
                sendErrorResponse(ctx, "No response template found for API: " + apiName);
                return;
            }

            // 지연 응답 처리
            DelayResponseProcessor.processWithDelay(ctx, responseContent, (context, content) -> {
                sendXmlResponse(context, content);
            });

        } catch (Exception e) {
            logger.error("XML 처리 중 오류", e);
            sendErrorResponse(ctx, "Internal server error");
        }
    }

    private String extractApiNameFromXml(String xml) {
        try {
            // 간단한 정규식으로 루트 엘리먼트 추출
            String rootElement = xml.trim();
            if (rootElement.startsWith("<") && rootElement.contains(">")) {
                int start = rootElement.indexOf('<') + 1;
                int end = rootElement.indexOf('>', start);
                if (end > start) {
                    String tagName = rootElement.substring(start, end);
                    // 속성이 있는 경우 공백 전까지만
                    int spaceIndex = tagName.indexOf(' ');
                    if (spaceIndex > 0) {
                        tagName = tagName.substring(0, spaceIndex);
                    }
                    return tagName;
                }
            }

            // DOM 파싱 백업 방법
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement().getNodeName();

        } catch (Exception e) {
            logger.debug("XML 파싱 실패", e);
            return null;
        }
    }

    private void sendXmlResponse(ChannelHandlerContext ctx, String content) {
        // 헤더 추가 (기존 코드 패턴 참조)
        String headerLength = String.format("%05d", content.getBytes(CharsetUtil.UTF_8).length + 1);
        String response = "data_length=" + headerLength + "/" + content;

        ByteBuf responseBuf = Unpooled.copiedBuffer(response, CharsetUtil.UTF_8);
        ctx.writeAndFlush(responseBuf);
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, String errorMsg) {
        String errorXml = "<error><message>" + errorMsg + "</message></error>";
        sendXmlResponse(ctx, errorXml);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("XML 핸들러 예외", cause);
        ctx.close();
    }
}