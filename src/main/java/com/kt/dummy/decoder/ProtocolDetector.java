package com.kt.dummy.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 프로토콜 자동 감지기 (확장 기능)
 * @author 고재원
 */
public class ProtocolDetector {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolDetector.class);
    
    public enum ProtocolType {
        XML, KEY_VALUE, JSON, SOAP, UNKNOWN
    }
    
    /**
     * 수신 데이터에서 프로토콜 타입 감지
     * @param data 수신 데이터
     * @return 감지된 프로토콜 타입
     */
    public static ProtocolType detectProtocol(ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return ProtocolType.UNKNOWN;
        }
        
        try {
            // 데이터를 문자열로 변환 (일부만)
            int readableBytes = Math.min(data.readableBytes(), 1024);
            String content = data.toString(data.readerIndex(), readableBytes, CharsetUtil.UTF_8);
            
            return detectProtocolFromString(content);
            
        } catch (Exception e) {
            logger.debug("프로토콜 감지 중 오류", e);
            return ProtocolType.UNKNOWN;
        }
    }
    
    /**
     * 문자열에서 프로토콜 타입 감지
     * @param content 문자열 내용
     * @return 감지된 프로토콜 타입
     */
    public static ProtocolType detectProtocolFromString(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ProtocolType.UNKNOWN;
        }
        
        content = content.trim();
        
        // XML 프로토콜 감지
        if (content.startsWith("<") && content.contains(">")) {
            if (content.contains("soap:Envelope") || content.contains("soapenv:Envelope")) {
                logger.debug("SOAP 프로토콜 감지");
                return ProtocolType.SOAP;
            } else {
                logger.debug("XML 프로토콜 감지");
                return ProtocolType.XML;
            }
        }
        
        // JSON 프로토콜 감지
        else if ((content.startsWith("{") && content.contains("}")) ||
            (content.startsWith("[") && content.contains("]"))) {
            logger.debug("JSON 프로토콜 감지");
            return ProtocolType.JSON;
        }
        
        // &key=value 프로토콜 감지
        else if (content.contains("=") && (content.contains("&") || content.startsWith("/"))) {
            logger.debug("KEY_VALUE 프로토콜 감지");
            return ProtocolType.KEY_VALUE;
        }
        
        logger.debug("알 수 없는 프로토콜: {}", content.substring(0, Math.min(50, content.length())));
        return ProtocolType.UNKNOWN;
    }
    
    /**
     * 프로토콜 타입에 따른 문자열 반환
     * @param protocolType 프로토콜 타입
     * @return 프로토콜 문자열
     */
    public static String getProtocolString(ProtocolType protocolType) {
        switch (protocolType) {
            case XML: return "xml";
            case JSON: return "json";
            case SOAP: return "soap";
            case KEY_VALUE: return "keyValue";
            default: return "unknown";
        }
    }
}