package com.kt.dummy.handler;

import io.netty.channel.ChannelInboundHandler;

/**
 * 프로토콜별 핸들러 팩토리
 * @author 고재원
 */
public class ProtocolHandlerFactory {
    
    public ChannelInboundHandler createXmlHandler() {
        return new XmlProtocolHandler();
    }
    
    public ChannelInboundHandler createKeyValueHandler() {
        return new KeyValueHandler();
    }
    
    public ChannelInboundHandler createHttpHandler() {
        return new HttpProtocolHandler();
    }
    
    public ChannelInboundHandler createTcpSocketHandler() {
        return new TcpSocketHandler();
    }
}