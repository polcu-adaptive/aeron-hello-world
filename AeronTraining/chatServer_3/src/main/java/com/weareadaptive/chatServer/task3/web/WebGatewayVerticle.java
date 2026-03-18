package com.weareadaptive.chatServer.task3.web;

import com.weareadaptive.chatServer.task3.agent.AgentErrorHandler;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
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
