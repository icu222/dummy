package com.kt.dummy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 일반 TCP Socket 핸들러 (확장용)
 * @author 고재원
 */
public class TcpSocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(TcpSocketHandler.class);
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // 확장 가능한 일반 TCP 핸들러
        logger.debug("TCP 데이터 수신: {} bytes", msg.readableBytes());
        
        // 향후 추가 프로토콜 지원을 위한 기본 구조
        // 현재는 KeyValue나 XML 핸들러로 위임
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("TCP 핸들러 예외", cause);
        ctx.close();
    }
}