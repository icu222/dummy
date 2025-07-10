# Pure Netty 멀티 프로토콜 더미 서버

## 프로젝트 개요

서비스 A의 클라우드 전환을 위한 고성능 더미 서버입니다. 2500 TPS 처리 능력을 목표로 하는 Pure Netty 기반 멀티 프로토콜 서버입니다.

## 주요 변경사항 (최신 버전)

### 라이브러리 업데이트
- **Netty**: 4.1.104.Final (취약점 수정)
- **Jackson**: 2.16.1 (보안 업데이트)
- **SLF4J**: 2.0.12 (최신 안정 버전)
- **Logback**: 1.4.14 (최신 안정 버전)
- **JUnit**: 5.10.2 (최신 테스트 프레임워크)

### 코드 개선사항
- HttpObjectAggregator 추가로 HTTP 요청 완전성 보장
- XML 파싱 시 XXE 공격 방지 설정 추가
- OWASP Dependency Check 플러그인 추가
- Maven 플러그인 최신 버전 업데이트

## 지원 프로토콜 및 포트

### A그룹 (TCP Socket + XML)
- **포트**: 8001, 8002, 8003, 8004
- **프로토콜**: TCP Socket
- **데이터 형식**: XML

### B그룹 (TCP Socket + &key=value)
- **포트**: 18000, 19000, 20000, 10120
- **프로토콜**: TCP Socket
- **데이터 형식**: &key=value

### C그룹 (HTTP)
- **포트**: 80
- **프로토콜**: HTTP
- **데이터 형식**: JSON, XML, SOAP

### D그룹 (HTTPS)
- **포트**: 443
- **프로토콜**: HTTPS, mTLS
- **데이터 형식**: JSON, multipart, SOAP

### 관리 API
- **포트**: 9999
- **프로토콜**: HTTP
- **용도**: 응답 전문 관리, 모니터링

## 빠른 시작

### 1. 보안 스캔 (권장)
```bash
# 의존성 취약점 검사
mvn org.owasp:dependency-check-maven:check
```

### 2. 빌드
```bash
# 기본 빌드
mvn clean package

# 성능 최적화 빌드
mvn clean package -Pperformance

# 운영 환경 빌드
mvn clean package -Pprod
```

### 3. 샘플 응답 전문 생성
```bash
# 실행 권한 부여
chmod +x create_sample_responses.sh

# 샘플 파일 생성
./create_sample_responses.sh
```

### 4. 서버 실행
```bash
# 스크립트로 실행
chmod +x start-server.sh
./start-server.sh

# 상태 확인
./status.sh

# 서버 종료
./stop-server.sh
```

## 성능 최적화 JVM 옵션

```bash
-Xms2g -Xmx4g                          # 힙 메모리 설정
-XX:+UseG1GC                           # G1 GC 사용
-XX:MaxGCPauseMillis=100               # GC 일시정지 시간 목표
-XX:+UseStringDeduplication            # 문자열 중복 제거
-XX:+OptimizeStringConcat              # 문자열 연결 최적화
-Dio.netty.allocator.type=pooled       # Netty 메모리 풀 사용
-Dio.netty.allocator.numDirectArenas=0 # Direct 메모리 아레나 비활성화
```

## 보안 강화 사항

### XML 처리 보안
- XXE(XML External Entity) 공격 방지
- DTD 처리 비활성화
- 외부 엔티티 참조 차단

### 의존성 보안
- OWASP Dependency Check 통합
- 취약점 없는 최신 라이브러리 사용
- 정기적인 보안 스캔 권장

## 관리 API 사용법

### 상태 확인
```bash
curl http://localhost:9999/api/status
```

### 성능 통계 조회
```bash
curl http://localhost:9999/api/stats
```

### 응답 전문 등록/업데이트
```bash
curl -X POST http://localhost:9999/api/response \
  -H "Content-Type: application/json" \
  -d '{
    "stage": "stage1",
    "protocol": "json",
    "apiName": "newApi",
    "responseContent": "{\"result\":\"success\",\"data\":\"test\"}"
  }'
```

## 포트 구성

| 그룹 | 포트 | 프로토콜 | 데이터 형식 |
|------|------|----------|-------------|
| A | 8001-8004 | TCP Socket | XML |
| B | 18000,19000,20000,10120 | TCP Socket | &key=value |
| C | 80 | HTTP | JSON,XML,SOAP |
| D | 443 | HTTPS | JSON,multipart,SOAP |
| 관리 | 9999 | HTTP | JSON (관리 API) |

## 문제 해결

### 포트 충돌
```bash
# 포트 사용 확인
netstat -tlnp | grep -E ':(80|443|8001|8002|8003|8004|9999|18000|19000|20000|10120)'

# 프로세스 종료
kill -9 $(lsof -t -i:8001)
```

### 메모리 부족
```bash
# 메모리 사용률 확인
free -h

# JVM 힙 덤프 생성
jmap -dump:format=b,file=heapdump.hprof $(cat dummy-server.pid)
```

## 개발자 정보

이 프로젝트는 KT 서비스 A 클라우드 전환팀에서 개발되었습니다.

- 목표 성능: 2500 TPS
- 지원 프로토콜: HTTP, HTTPS, TCP Socket, XML, JSON, SOAP, &key=value
- 기술 스택: Java 17, Netty 4.1, Maven
- 보안: OWASP 보안 가이드라인 준수