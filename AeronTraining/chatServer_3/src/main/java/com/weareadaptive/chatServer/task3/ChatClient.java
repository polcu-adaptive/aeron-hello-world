package com.weareadaptive.chatServer.task3;

import com.weareadaptive.chatServer.task3.agent.AgentErrorHandler;
import com.weareadaptive.chatServer.task3.agent.CliAgent;
import com.weareadaptive.chatServer.task3.agent.PublishingAgent;
import com.weareadaptive.chatServer.task3.agent.SubscriptionAgent;
import org.agrona.CloseHelper;
import org.agrona.concurrent.*;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;

public class ChatClient
{
    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final OneToOneRingBuffer innerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));
        final OneToOneRingBuffer outerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));

        System.out.println("Setup Cli Agent");
        final CliAgent cliAgent = new CliAgent(innerRingBuffer, outerRingBuffer);
        final AgentRunner cliAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, cliAgent);

        System.out.println("Setup Publishing Agent");
        final PublishingAgent publishingAgent = new PublishingAgent(innerRingBuffer);
        final AgentRunner publishingAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, publishingAgent);

        System.out.println("Setup Subscription Agent");
        final SubscriptionAgent subscriptionAgent = new SubscriptionAgent(outerRingBuffer);
        final AgentRunner subscriptionAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, subscriptionAgent);

        System.out.println("Start agent runners");
        AgentRunner.startOnThread(cliAgentRunner);
        AgentRunner.startOnThread(publishingAgentRunner);
        AgentRunner.startOnThread(subscriptionAgentRunner);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier())
        {
            shutdownSignalBarrier.await();
            CloseHelper.closeAll(cliAgentRunner, publishingAgentRunner, subscriptionAgentRunner);
        }
    }
}
