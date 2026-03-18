package com.weareadaptive.chatServer.task3.web;

import com.weareadaptive.chatServer.task3.agent.AgentErrorHandler;
import com.weareadaptive.chatServer.task3.agent.PublishingAgent;
import com.weareadaptive.chatServer.task3.agent.SubscriptionAgent;
import io.vertx.core.Vertx;
import org.agrona.CloseHelper;
import org.agrona.concurrent.*;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;

public class WebGateway
{
    private static final int CONFIGURED_PORT = 8080;

    public static void main(final String[] args)
    {
        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final OneToOneRingBuffer innerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));
        final OneToOneRingBuffer outerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));

        final Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new WebGatewayVerticle(CONFIGURED_PORT, innerRingBuffer, outerRingBuffer));
        System.out.println("Deployed Auction House Web Gateway");

        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        System.out.println("Setup Publishing Agent");
        final PublishingAgent publishingAgent = new PublishingAgent(innerRingBuffer);
        final AgentRunner publishingAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, publishingAgent);

        System.out.println("Setup Subscription Agent");
        final SubscriptionAgent subscriptionAgent = new SubscriptionAgent(outerRingBuffer);
        final AgentRunner subscriptionAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, subscriptionAgent);

        System.out.println("Start agent runners");
        AgentRunner.startOnThread(publishingAgentRunner);
        AgentRunner.startOnThread(subscriptionAgentRunner);

        final ShutdownSignalBarrier signalBarrier = new ShutdownSignalBarrier();
        signalBarrier.await();

        System.out.println("Closing Auction House Web Gateway");
        CloseHelper.close(vertx::close);
    }
}
