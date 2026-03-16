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

public class WebGatewayVerticle extends VerticleBase
{
    private final int configuredPort;

    public WebGatewayVerticle(final int configuredPort)
    {
        this.configuredPort = configuredPort;
    }

    @Override
    public Future<?> start()
    {
        final Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        final WebGatewayAgent webGatewayAgent = new WebGatewayAgent(vertx);
        final AgentRunner webGatewayAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, webGatewayAgent);
        AgentRunner.startOnThread(webGatewayAgentRunner);

        final HttpServer httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webSocket ->
        {
            System.out.println("Register web socket");
            webGatewayAgent.registerWebSocket(webSocket);
        });

        return httpServer
                .listen(configuredPort)
                .onSuccess(server -> System.out.println("HTTP server started on port " + server.actualPort()))
                .onFailure(throwable -> System.out.println("HTTP server error"));
    }
}
