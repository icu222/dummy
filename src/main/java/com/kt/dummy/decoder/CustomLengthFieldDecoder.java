package com.kt.dummy.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 커스텀 길이 필드 디코더
 *
 * scap/capri, 즉 tcp 소켓 연동 시에 데이터가 다음과 같이 인입이 된다.
 *
 *  ex)
 *  data_length=00202/<getVasOfAllSubscpn><TRANSACTIONID>5b7b38e2-e94d-4f6....
 *
 * 구성)
 * 헤더 + / + 바디
 *
 * 헤더 = data_length=5자리길이 = 고정 17자리
 * 본문 = xml or &key=value
 * 따라서 헤더로부터 바디 길이를 찾아서 바디를 읽는다.
 *
 * @author 고재원
 * @date   2025.07.10
 */
public class CustomLengthFieldDecoder extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(CustomLengthFieldDecoder.class);

    // 기존 코드에서 헤더 길이는 17바이트
    private static final int HEADER_LENGTH = 17;
    private static final int LENGTH_FIELD_OFFSET = 12; // "data_length=" 다음 위치
    private static final int LENGTH_FIELD_LENGTH = 5;  // "00000" 형식

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            // 헤더 + 구분자까지 읽을 수 있는지 확인
            if (in.readableBytes() < HEADER_LENGTH + 1) {
                return; // 더 많은 데이터 필요
            }

            // 현재 위치 마킹
            in.markReaderIndex();

            // 헤더만 읽기 (17바이트)
            byte[] headerBytes = new byte[HEADER_LENGTH];
            in.readBytes(headerBytes);
            String header = new String(headerBytes, StandardCharsets.UTF_8);

            // 구분자 읽기
            byte separator = in.readByte();

            logger.debug("수신 헤더: {}", header);
            logger.debug("구분자: {}", (char)separator);

            // 구분자 검증
            if (separator != '/') {
                in.resetReaderIndex();
                throw new CorruptedFrameException("잘못된 구분자: " + (char)separator + " (예상값: '/')");
            }

            // 길이 필드 추출
            if (!header.startsWith("data_length=")) {
                in.resetReaderIndex();
                throw new CorruptedFrameException("잘못된 헤더 형식: " + header);
            }

            String lengthStr = header.substring(LENGTH_FIELD_OFFSET, LENGTH_FIELD_OFFSET + LENGTH_FIELD_LENGTH);
            int bodyLength;

            try {
                bodyLength = Integer.parseInt(lengthStr);
            } catch (NumberFormatException e) {
                in.resetReaderIndex();
                throw new CorruptedFrameException("잘못된 길이 필드: " + lengthStr);
            }

            logger.debug("본문 길이: {}", bodyLength);

            // 본문 길이가 0이면 빈 프레임 반환
            if (bodyLength == 0) {
                ByteBuf frame = ctx.alloc().buffer(0);
                out.add(frame);
                return;
            }

            // 실제 본문 길이 (구분자 제외)
            int actualBodyLength = bodyLength - 1;

            // 본문 데이터가 충분한지 확인
            if (in.readableBytes() < actualBodyLength) {
                in.resetReaderIndex();
                return; // 더 많은 데이터 필요
            }

            // 본문 읽기
            ByteBuf frame = in.readRetainedSlice(actualBodyLength);
            out.add(frame);

            logger.debug("프레임 디코딩 완료: 헤더 {}바이트, 구분자 1바이트, 본문 {}바이트",
                    HEADER_LENGTH, actualBodyLength);

        } catch (Exception e) {
            logger.error("프레임 디코딩 중 오류", e);
            in.resetReaderIndex();
            throw e;
        }
    }
}