package com.weareadaptive.chatServer.task3.web;

import io.vertx.core.Vertx;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class WebGateway
{
    private static final int CONFIGURED_PORT = 8080;

    public static void main(final String[] args)
    {
        final Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new WebGatewayVerticle(CONFIGURED_PORT));
        System.out.println("Deployed Auction House Web Gateway");

        final ShutdownSignalBarrier signalBarrier = new ShutdownSignalBarrier();
        signalBarrier.await();

        System.out.println("Closing Auction House Web Gateway");
        CloseHelper.close(vertx::close);
    }
}
