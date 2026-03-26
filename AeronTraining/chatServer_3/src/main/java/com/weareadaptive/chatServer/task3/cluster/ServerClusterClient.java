package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.AdminRequestType;
import io.aeron.cluster.codecs.AdminResponseCode;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.MessageHeaderDecoder;
import task3.src.main.resources.TakeSnapshotCommandDecoder;

public class ServerClusterClient implements EgressListener
{
    private AeronCluster clusterClient;

    private final OneToOneRingBuffer innerRingBuffer;
    private final OneToOneRingBuffer outerRingBuffer;
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final IdleStrategy idleStrategy = new BackoffIdleStrategy();

    private boolean running = true;

    public ServerClusterClient(final OneToOneRingBuffer innerRingBuffer, final OneToOneRingBuffer outerRingBuffer)
    {
        this.innerRingBuffer = innerRingBuffer;
        this.outerRingBuffer = outerRingBuffer;
    }

    public void run()
    {
        System.out.println("[Server Cluster Client] Starting...");

        while (running && !Thread.currentThread().isInterrupted())
        {
            int workCount = 0;
            workCount += innerRingBuffer.read(this::readAndOfferMessage);
            workCount += clusterClient.pollEgress();

            if (workCount == 0)
            {
                clusterClient.sendKeepAlive();
            }
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

        headerDecoder.wrap(buffer, index);
        switch (headerDecoder.templateId())
        {
            case TakeSnapshotCommandDecoder.TEMPLATE_ID -> clusterClient.sendAdminRequestToTakeASnapshot(0);
            default ->
            {
                final long offer = clusterClient.offer(buffer, index, length);
                if (offer < 0)
                {
                    System.err.println("[Server Cluster Client] Publishing failed - Response Code: " + offer);
                }
            }
        }
    }

    @Override
    public void onSessionEvent(
            final long correlationId,
            final long clusterSessionId,
            final long leadershipTermId,
            final int leaderMemberId,
            final EventCode code,
            final String detail)
    {
        System.out.println("[Server Cluster Client] On Session Event");
        System.out.println("[Server Cluster Client] Detail: " + detail + System.lineSeparator());
    }

    @Override
    public void onNewLeader(
            final long clusterSessionId,
            final long leadershipTermId,
            final int leaderMemberId,
            final String ingressEndpoints)
    {
        System.out.println("[Server Cluster Client] On New Leader");
        System.out.println("[Server Cluster Client] Cluster session id: " + clusterSessionId);
        System.out.println("[Server Cluster Client] Leadership term id: " + leadershipTermId);
        System.out.println("[Server Cluster Client] Leader member id: " + leaderMemberId);
        System.out.println("[Server Cluster Client] Ingress endpoints: " + ingressEndpoints + System.lineSeparator());
    }

    @Override
    public void onAdminResponse(
            final long clusterSessionId,
            final long correlationId,
            final AdminRequestType requestType,
            final AdminResponseCode responseCode,
            final String message,
            final DirectBuffer payload,
            final int payloadOffset,
            final int payloadLength)
    {
        System.out.println("[Server Cluster Client] On Admin Response");
        System.out.println("[Server Cluster Client] Message: " + message + System.lineSeparator());
    }

    public void setAeronCluster(final AeronCluster clusterClient)
    {
        this.clusterClient = clusterClient;
    }

    public void stop()
    {
        this.running = false;
    }
}
