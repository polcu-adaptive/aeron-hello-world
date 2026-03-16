package com.weareadaptive.chatServer.task2.web;

import com.weareadaptive.chatServer.task2.agent.AgentErrorHandler;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public class WebGatewayVerticle extends VerticleBase
{
    private final int configuredPort;
    private HttpServer httpServer;

    public WebGatewayVerticle(final int configuredPort)
    {
        this.configuredPort = configuredPort;
    }

    public int boundPort()
    {
        return httpServer.actualPort();
    }

    @Override
    public Future<?> start()
    {
        final Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        final WebGatewayAgent webGatewayAgent = new WebGatewayAgent(router);
        final AgentRunner webGatewayAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, webGatewayAgent);
        AgentRunner.startOnThread(webGatewayAgentRunner);

        httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webSocket ->
        {
            System.out.println("Register web socket");
            webGatewayAgent.registerWebSocket(webSocket);
        });

        return httpServer
                .requestHandler(router)
                .listen(configuredPort)
                .onSuccess(server -> System.out.println("HTTP server started on port " + server.actualPort()))
                .onFailure(throwable -> System.out.println("HTTP server error"));
    }
}
