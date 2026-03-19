package com.weareadaptive.chatServer.task3.web;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

public class WebGatewayVerticle extends VerticleBase
{
    private final int configuredPort;
    private final WebGatewayService webGatewayService;

    public WebGatewayVerticle(final int configuredPort, final OneToOneRingBuffer innerRingBuffer, final OneToOneRingBuffer outerRingBuffer)
    {
        this.configuredPort = configuredPort;
        this.webGatewayService = new WebGatewayService(innerRingBuffer, outerRingBuffer);
    }

    @Override
    public Future<?> start()
    {
        final HttpServer httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webGatewayService::registerWebSocket);

        vertx.setPeriodic(1, id -> webGatewayService.pollMessages());

        return httpServer
                .listen(configuredPort)
                .onSuccess(server -> System.out.println("HTTP server started on port " + server.actualPort()))
                .onFailure(throwable -> System.out.println("HTTP server error"));
    }
}
