package com.kt.dummy.manager;

import com.kt.dummy.server.ServerConfig;
import com.kt.dummy.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 파일 기반 응답 전문 로더
 * @author 고재원
 */
public class FileResponseLoader {
    private static final Logger logger = LoggerFactory.getLogger(FileResponseLoader.class);
    private final String basePath;
    
    public FileResponseLoader() {
        this.basePath = ServerConfig.getInstance().getResponseBasePath();
    }
    
    /**
     * 특정 단계/프로토콜의 모든 응답 전문 파일 로드
     * @param stage 단계 (stage1~stage4)
     * @param protocol 프로토콜 (json, xml, soap, keyValue)
     * @return API명 -> 응답전문 맵
     */
    public Map<String, String> loadResponsesForStageAndProtocol(String stage, String protocol) {
        Map<String, String> responses = new ConcurrentHashMap<>();
        
        try {
            // 디렉토리 경로 구성
            Path directoryPath = getDirectoryPath(stage, protocol);
            
            if (!Files.exists(directoryPath)) {
                logger.debug("디렉토리 없음: {}", directoryPath);
                // 디렉토리 생성
                Files.createDirectories(directoryPath);
                logger.info("디렉토리 생성: {}", directoryPath);
                return responses;
            }
            
            // 파일 확장자 결정
            String fileExtension = getFileExtension(protocol);
            
            // 디렉토리 내 모든 파일 스캔
            try (Stream<Path> files = Files.walk(directoryPath, 1)) {
                files.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(fileExtension))
                     .forEach(filePath -> {
                         try {
                             String apiName = getApiNameFromFilePath(filePath, fileExtension);
                             String content = FileUtil.readFileContent(filePath);
                             
                             if (content != null && !content.trim().isEmpty()) {
                                 responses.put(apiName, content);
                                 logger.debug("파일 로드: {} -> {}", filePath.getFileName(), apiName);
                             }
                             
                         } catch (Exception e) {
                             logger.warn("파일 로드 실패: {}", filePath, e);
                         }
                     });
            }
            
            logger.info("{}/{} 디렉토리에서 {} 개 파일 로드", stage, protocol, responses.size());
            
        } catch (Exception e) {
            logger.error("{}/{} 응답 전문 로드 중 오류", stage, protocol, e);
        }
        
        return responses;
    }
    
    /**
     * 단일 응답 전문 파일 저장
     * @param stage 단계
     * @param protocol 프로토콜
     * @param apiName API명
     * @param content 응답 전문 내용
     * @return 저장 성공 여부
     */
    public boolean saveResponseFile(String stage, String protocol, String apiName, String content) {
        try {
            Path directoryPath = getDirectoryPath(stage, protocol);
            
            // 디렉토리 생성 (존재하지 않으면)
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }
            
            // 파일 경로 생성
            String fileExtension = getFileExtension(protocol);
            Path filePath = directoryPath.resolve(apiName + fileExtension);
            
            // 파일 저장
            FileUtil.writeFileContent(filePath, content);
            
            logger.info("응답 전문 파일 저장: {}", filePath);
            return true;
            
        } catch (Exception e) {
            logger.error("응답 전문 파일 저장 실패: {}/{}/{}", stage, protocol, apiName, e);
            return false;
        }
    }
    
    private Path getDirectoryPath(String stage, String protocol) {
        // resources 디렉토리에서 파일 로드 시도
        try {
            String resourcePath = "/" + basePath + "/" + stage + "/" + protocol;
            java.net.URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl != null) {
                Path resourceDir = Paths.get(resourceUrl.toURI());
                if (Files.exists(resourceDir)) {
                    return resourceDir;
                }
            }
        } catch (Exception e) {
            // resources에 없으면 현재 디렉토리 사용
        }
        
        // 현재 작업 디렉토리 기준
        return Paths.get(basePath, stage, protocol);
    }
    
    private String getFileExtension(String protocol) {
        switch (protocol) {
            case "json": return ".json";
            case "xml": return ".xml";
            case "soap": return ".xml";  // SOAP도 XML 파일
            case "keyValue": return ".txt";
            default: return ".txt";
        }
    }
    
    private String getApiNameFromFilePath(Path filePath, String extension) {
        String fileName = filePath.getFileName().toString();
        
        // 확장자 제거
        if (fileName.endsWith(extension)) {
            return fileName.substring(0, fileName.length() - extension.length());
        }
        
        return fileName;
    }
}