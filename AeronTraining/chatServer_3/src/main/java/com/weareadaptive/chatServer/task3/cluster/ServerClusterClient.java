package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.MessageHeaderEncoder;
import task3.src.main.resources.TextMessageEncoder;

public class ServerClusterClient implements EgressListener
{
    private AeronCluster clusterClient;

    private final OneToOneRingBuffer innerRingBuffer;
    private final OneToOneRingBuffer outerRingBuffer;
    private final IdleStrategy idleStrategy = new BackoffIdleStrategy();

    public ServerClusterClient(final OneToOneRingBuffer innerRingBuffer, final OneToOneRingBuffer outerRingBuffer)
    {
        this.innerRingBuffer = innerRingBuffer;
        this.outerRingBuffer = outerRingBuffer;
    }

    public void start()
    {
        System.out.println("[Server Cluster Client] Starting...");

        while (!Thread.currentThread().isInterrupted())
        {
            final int workCount = innerRingBuffer.read(this::readAndOfferMessage);
            idleStrategy.idle(workCount);
        }
    }

    @Override
    public void onMessage(long clusterSessionId, long timestamp, DirectBuffer buffer, int offset, int length, Header header)
    {
        System.out.println("[Server Cluster Client] Receiving message");

        if (!outerRingBuffer.write(1, buffer, offset, length))
        {
            System.err.println("[Subscription Agent] Error writing in outer ringBuffer");
        }
    }

    private void readAndOfferMessage(final int msgTypeId, final MutableDirectBuffer buffer, final int index, final int length)
    {
        System.out.println("[Server Cluster Client] Publishing message");

        final long offer = clusterClient.offer(buffer, index, length);
        if (offer < 0)
        {
            System.err.println("[Server Cluster Client] Publishing failed - Response Code: " + offer);
        }
    }
}
