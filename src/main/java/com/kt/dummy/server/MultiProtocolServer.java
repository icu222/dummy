package com.kt.dummy.server;

import com.kt.dummy.handler.ProtocolHandlerFactory;
import com.kt.dummy.manager.ManagementApiHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 멀티 프로토콜 서버 메인 클래스
 * @author 고재원
 */
public class MultiProtocolServer {
    private static final Logger logger = LoggerFactory.getLogger(MultiProtocolServer.class);
    
    private final ServerConfig config;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final List<Channel> serverChannels;
    private final ProtocolHandlerFactory handlerFactory;
    
    public MultiProtocolServer(ServerConfig config) {
        this.config = config;
        this.bossGroup = new NioEventLoopGroup(config.getBossThreads());
        this.workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        this.serverChannels = new ArrayList<>();
        this.handlerFactory = new ProtocolHandlerFactory();
    }
    
    public void start() throws Exception {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // scap 포트 시작 (TCP Socket + XML)
        for (int port : config.getScapPorts()) {
            futures.add(startTcpXmlServer(port));
        }
        
        // capri 포트 시작 (TCP Socket + &key=value)
        for (int port : config.getCapriPorts()) {
            futures.add(startTcpKeyValueServer(port));
        }
        
        // HTTP 서버 시작
        futures.add(startHttpServer(config.getHttpPort(), false));
        
        // HTTPS 서버 시작
        futures.add(startHttpServer(config.getHttpsPort(), true));
        
        // 관리 API 서버 시작
        futures.add(startManagementServer(config.getManagementPort()));
        
        // 모든 서버 시작 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        logger.info("모든 서버 시작 완료. 활성 포트: A그룹{}, B그룹{}, HTTP{}, HTTPS{}, 관리{}", 
            config.getScapPorts(), config.getCapriPorts(),
            config.getHttpPort(), config.getHttpsPort(), config.getManagementPort());
    }
    
    private CompletableFuture<Void> startTcpXmlServer(int port) {
        return CompletableFuture.runAsync(() -> {
            try {
                ServerBootstrap bootstrap = ServerBootstrapFactory.createTcpBootstrap(
                    bossGroup, workerGroup, handlerFactory.createXmlHandler());
                
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                logger.info("TCP XML 서버 시작: 포트 {}", port);
                
            } catch (Exception e) {
                logger.error("TCP XML 서버 시작 실패: 포트 " + port, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    private CompletableFuture<Void> startTcpKeyValueServer(int port) {
        return CompletableFuture.runAsync(() -> {
            try {
                ServerBootstrap bootstrap = ServerBootstrapFactory.createTcpBootstrap(
                    bossGroup, workerGroup, handlerFactory.createKeyValueHandler());
                
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                logger.info("TCP KeyValue 서버 시작: 포트 {}", port);
                
            } catch (Exception e) {
                logger.error("TCP KeyValue 서버 시작 실패: 포트 " + port, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    private CompletableFuture<Void> startHttpServer(int port, boolean ssl) {
        return CompletableFuture.runAsync(() -> {
            try {
                ServerBootstrap bootstrap = ServerBootstrapFactory.createHttpBootstrap(
                    bossGroup, workerGroup, handlerFactory.createHttpHandler(), ssl);
                
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                logger.info("{} 서버 시작: 포트 {}", ssl ? "HTTPS" : "HTTP", port);
                
            } catch (Exception e) {
                logger.error((ssl ? "HTTPS" : "HTTP") + " 서버 시작 실패: 포트 " + port, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    private CompletableFuture<Void> startManagementServer(int port) {
        return CompletableFuture.runAsync(() -> {
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline()
                                  .addLast(new HttpServerCodec())
                                  .addLast(new HttpObjectAggregator(65536))
                                  .addLast(new ManagementApiHandler());
                            }
                        });
                
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                logger.info("관리 API 서버 시작: 포트 {}", port);
                
            } catch (Exception e) {
                logger.error("관리 API 서버 시작 실패: 포트 " + port, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    public void shutdown() {
        logger.info("서버 종료 시작...");
        
        // 모든 서버 채널 닫기
        for (Channel channel : serverChannels) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                logger.warn("서버 채널 종료 중 인터럽트", e);
                Thread.currentThread().interrupt();
            }
        }
        
        // EventLoopGroup 종료
        workerGroup.shutdownGracefully().awaitUninterruptibly();
        bossGroup.shutdownGracefully().awaitUninterruptibly();
        
        logger.info("서버 종료 완료");
    }
}