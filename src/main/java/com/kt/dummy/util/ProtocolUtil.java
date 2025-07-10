package com.kt.dummy.util;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프로토콜 유틸리티
 * @author 고재원
 */
public class ProtocolUtil {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolUtil.class);
    
    /**
     * 채널에서 포트 번호 추출
     * @param channel 네티 채널
     * @return 포트 번호
     */
    public static int getPortFromChannel(Channel channel) {
        try {
            SocketAddress localAddress = channel.localAddress();
            if (localAddress instanceof InetSocketAddress) {
                return ((InetSocketAddress) localAddress).getPort();
            }
            
            // 문자열 파싱 백업 방법
            String addressStr = localAddress.toString();
            Pattern portPattern = Pattern.compile(":([0-9]+)");
            Matcher matcher = portPattern.matcher(addressStr);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            
        } catch (Exception e) {
            logger.debug("포트 추출 실패", e);
        }
        
        return 0;
    }
    
    /**
     * &key=value 형식 문자열 파싱 (기존 코드 패턴)
     * @param str 파싱할 문자열
     * @return key-value 맵
     */
    public static Map<String, String> parseKeyValueString(String str) {
        Map<String, String> map = new LinkedHashMap<>();
        
        if (str == null || str.trim().isEmpty()) {
            return map;
        }
        
        try {
            // 첫 번째 구분자 제거
            if (str.startsWith("&") || str.startsWith("/")) {
                str = str.substring(1);
            }
            
            StringTokenizer strToken = new StringTokenizer(str, "&");
            while (strToken.hasMoreTokens()) {
                String token = strToken.nextToken();
                StringTokenizer strTokenSub = new StringTokenizer(token, "=");
                
                if (strTokenSub.hasMoreTokens()) {
                    String key = strTokenSub.nextToken();
                    String value = "";
                    if (strTokenSub.hasMoreTokens()) {
                        value = strTokenSub.nextToken();
                    }
                    map.put(key, value);
                }
            }
            
        } catch (Exception e) {
            logger.error("Key-Value 파싱 실패: {}", str, e);
        }
        
        return map;
    }
    
    /**
     * key-value 맵을 &key=value 형식 문자열로 변환
     * @param map key-value 맵
     * @return &key=value 형식 문자열
     */
    public static String encodeKeyValueMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            sb.append("&").append(key).append("=").append(value != null ? value : "");
        }
        
        return sb.length() > 0 ? sb.substring(1) : "";
    }
    
    /**
     * HTTP URI에서 API명 추출
     * @param uri HTTP URI
     * @return API명
     */
    public static String extractApiNameFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "default";
        }
        
        try {
            // 쿼리 파라미터 제거
            int queryIndex = uri.indexOf('?');
            if (queryIndex > 0) {
                uri = uri.substring(0, queryIndex);
            }
            
            // 앞의 '/' 제거
            if (uri.startsWith("/")) {
                uri = uri.substring(1);
            }
            
            // 마지막 세그먼트 추출
            String[] segments = uri.split("/");
            if (segments.length > 0) {
                String lastSegment = segments[segments.length - 1];
                return lastSegment.isEmpty() ? "default" : lastSegment;
            }
            
        } catch (Exception e) {
            logger.debug("URI에서 API명 추출 실패: {}", uri, e);
        }
        
        return "default";
    }
    
    /**
     * XML에서 루트 엘리먼트명 추출
     * @param xml XML 문자열
     * @return 루트 엘리먼트명
     */
    public static String extractRootElementFromXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return null;
        }
        
        try {
            String trimmed = xml.trim();
            
            // 첫 번째 태그 추출
            int start = trimmed.indexOf('<');
            if (start >= 0) {
                int end = trimmed.indexOf('>', start);
                if (end > start) {
                    String tag = trimmed.substring(start + 1, end);
                    
                    // 속성이 있는 경우 공백 전까지만
                    int spaceIndex = tag.indexOf(' ');
                    if (spaceIndex > 0) {
                        tag = tag.substring(0, spaceIndex);
                    }
                    
                    // 닫는 태그나 특수 태그 제외
                    if (!tag.startsWith("/") && !tag.startsWith("?") && !tag.startsWith("!")) {
                        return tag;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.debug("XML 루트 엘리먼트 추출 실패", e);
        }
        
        return null;
    }
    
    /**
     * 프로토콜별 Content-Type 반환
     * @param protocol 프로토콜 ("json", "xml", "soap", "keyValue")
     * @return Content-Type 헤더 값
     */
    public static String getContentTypeForProtocol(String protocol) {
        if (protocol == null) {
            return "application/json";
        }
        
        switch (protocol.toLowerCase()) {
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "soap":
                return "text/xml";
            case "keyvalue":
                return "text/plain";
            default:
                return "application/json";
        }
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환 (디버깅용)
     * @param bytes 바이트 배열
     * @return 16진수 문자열
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }
}