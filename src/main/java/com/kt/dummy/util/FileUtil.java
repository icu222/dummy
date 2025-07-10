package com.kt.dummy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 파일 유틸리티
 * @author 고재원
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    
    /**
     * 파일 내용 읽기
     * @param filePath 파일 경로
     * @return 파일 내용
     */
    public static String readFileContent(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                logger.warn("파일이 존재하지 않음: {}", filePath);
                return null;
            }
            
            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            logger.debug("파일 읽기 성공: {} ({} bytes)", filePath.getFileName(), bytes.length);
            return content;
            
        } catch (IOException e) {
            logger.error("파일 읽기 실패: {}", filePath, e);
            return null;
        }
    }
    
    /**
     * 파일 내용 쓰기
     * @param filePath 파일 경로
     * @param content 내용
     * @return 성공 여부
     */
    public static boolean writeFileContent(Path filePath, String content) {
        try {
            // 상위 디렉토리 생성
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.debug("디렉토리 생성: {}", parentDir);
            }
            
            // 파일 쓰기
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(filePath, bytes, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.TRUNCATE_EXISTING);
            
            logger.debug("파일 쓰기 성공: {} ({} bytes)", filePath.getFileName(), bytes.length);
            return true;
            
        } catch (IOException e) {
            logger.error("파일 쓰기 실패: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 파일 존재 여부 확인
     * @param filePath 파일 경로
     * @return 존재 여부
     */
    public static boolean exists(Path filePath) {
        return Files.exists(filePath);
    }
    
    /**
     * 디렉토리 생성
     * @param dirPath 디렉토리 경로
     * @return 성공 여부
     */
    public static boolean createDirectories(Path dirPath) {
        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                logger.info("디렉토리 생성: {}", dirPath);
            }
            return true;
            
        } catch (IOException e) {
            logger.error("디렉토리 생성 실패: {}", dirPath, e);
            return false;
        }
    }
    
    /**
     * 파일 삭제
     * @param filePath 파일 경로
     * @return 성공 여부
     */
    public static boolean deleteFile(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.debug("파일 삭제: {}", filePath);
                return true;
            }
            return false;
            
        } catch (IOException e) {
            logger.error("파일 삭제 실패: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 파일 크기 조회
     * @param filePath 파일 경로
     * @return 파일 크기 (bytes), 실패시 -1
     */
    public static long getFileSize(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
            return -1;
            
        } catch (IOException e) {
            logger.error("파일 크기 조회 실패: {}", filePath, e);
            return -1;
        }
    }
    
    /**
     * 안전한 파일명 생성 (특수문자 제거)
     * @param fileName 원본 파일명
     * @return 안전한 파일명
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        
        // 특수문자를 언더스코어로 치환
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // 연속된 언더스코어 제거
        sanitized = sanitized.replaceAll("_{2,}", "_");
        
        // 앞뒤 언더스코어 제거
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // 빈 문자열이면 기본값 반환
        if (sanitized.isEmpty()) {
            return "unknown";
        }
        
        return sanitized;
    }
}