package com.kt.dummy.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 응답 포맷터 - 응답 전문 동적 처리
 * @author 고재원
 */
public class ResponseFormatter {
    private static final Logger logger = LoggerFactory.getLogger(ResponseFormatter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    /**
     * 응답 전문에서 플레이스홀더 치환
     * @param template 응답 전문 템플릿
     * @param variables 치환할 변수 맵
     * @return 치환된 응답 전문
     */
    public static String formatWithVariables(String template, Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        
        try {
            String result = template;
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
            
            while (matcher.find()) {
                String placeholder = matcher.group(0); // ${변수명}
                String variableName = matcher.group(1); // 변수명
                String value = variables.get(variableName);
                
                if (value != null) {
                    result = result.replace(placeholder, value);
                    logger.debug("플레이스홀더 치환: {} -> {}", placeholder, value);
                } else {
                    logger.debug("플레이스홀더 변수 없음: {}", variableName);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("응답 전문 포맷팅 중 오류", e);
            return template;
        }
    }
    
    /**
     * JSON 응답 전문 포맷팅
     * @param jsonTemplate JSON 템플릿
     * @param variables 치환 변수
     * @return 포맷팅된 JSON
     */
    public static String formatJsonResponse(String jsonTemplate, Map<String, String> variables) {
        try {
            String formattedJson = formatWithVariables(jsonTemplate, variables);
            
            // JSON 유효성 검증 및 포맷팅
            JsonNode jsonNode = objectMapper.readTree(formattedJson);
            return objectMapper.writeValueAsString(jsonNode);
            
        } catch (Exception e) {
            logger.error("JSON 응답 포맷팅 실패", e);
            return jsonTemplate;
        }
    }
    
    /**
     * XML 응답 전문 포맷팅
     * @param xmlTemplate XML 템플릿
     * @param variables 치환 변수
     * @return 포맷팅된 XML
     */
    public static String formatXmlResponse(String xmlTemplate, Map<String, String> variables) {
        try {
            String formattedXml = formatWithVariables(xmlTemplate, variables);
            
            // XML 유효성 검증 및 포맷팅
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            
            Document doc = factory.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new StringReader(formattedXml)));
            
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            
            return writer.toString();
            
        } catch (Exception e) {
            logger.error("XML 응답 포맷팅 실패", e);
            return xmlTemplate;
        }
    }
    
    /**
     * 동적 시간 스탬프 추가
     * @param template 템플릿
     * @return 시간 스탬프가 추가된 응답
     */
    public static String addTimestamp(String template) {
        Map<String, String> timeVariables = Map.of(
            "timestamp", String.valueOf(System.currentTimeMillis()),
            "currentTime", java.time.LocalDateTime.now().toString(),
            "date", java.time.LocalDate.now().toString(),
            "time", java.time.LocalTime.now().toString()
        );
        
        return formatWithVariables(template, timeVariables);
    }
    
    /**
     * 요청 정보 기반 동적 응답 생성
     * @param template 템플릿
     * @param requestInfo 요청 정보
     * @return 동적 응답
     */
    public static String formatWithRequestInfo(String template, Map<String, String> requestInfo) {
        Map<String, String> responseVariables = Map.of(
            "transactionId", requestInfo.getOrDefault("transaction_id", generateTransactionId()),
            "sequenceNo", requestInfo.getOrDefault("sequence_no", "1"),
            "requestId", requestInfo.getOrDefault("request_id", generateRequestId()),
            "sessionId", requestInfo.getOrDefault("session_id", generateSessionId())
        );
        
        return formatWithVariables(template, responseVariables);
    }
    
    private static String generateTransactionId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    private static String generateRequestId() {
        return String.valueOf(System.currentTimeMillis());
    }
    
    private static String generateSessionId() {
        return "SES" + System.currentTimeMillis();
    }
}