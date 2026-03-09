package common;

import agent.AgentErrorHandler;
import agent.CliAgent;
import agent.PublishingAgent;
import agent.SubscriptionAgent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;

public class ChatAppMain
{
    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final UnsafeBuffer internalBuffer
                = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength));
        final OneToOneRingBuffer ringBuffer
                = new OneToOneRingBuffer(internalBuffer);

        System.out.println("Setup CliAgent");
        final CliAgent cliAgent = new CliAgent(ringBuffer);
        final AgentRunner cliAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, cliAgent);

        System.out.println("Setup PublishingAgent");
        final PublishingAgent publishingAgent = new PublishingAgent(ringBuffer);
        final AgentRunner publishingAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, publishingAgent);

        System.out.println("Setup SubscriptionAgent");
        final SubscriptionAgent subscriptionAgent = new SubscriptionAgent();
        final AgentRunner subscriptionAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, subscriptionAgent);

        System.out.println("Start agent runners");
        AgentRunner.startOnThread(cliAgentRunner);
        AgentRunner.startOnThread(publishingAgentRunner);
        AgentRunner.startOnThread(subscriptionAgentRunner);
    }
}
