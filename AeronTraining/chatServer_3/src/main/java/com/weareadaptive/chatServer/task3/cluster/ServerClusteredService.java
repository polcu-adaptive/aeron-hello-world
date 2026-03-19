package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import task3.src.main.resources.MessageHeaderDecoder;
import task3.src.main.resources.MessageHeaderEncoder;
import task3.src.main.resources.TextMessageDecoder;
import task3.src.main.resources.TextMessageEncoder;

public class ServerClusteredService implements ClusteredService
{
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final TextMessageEncoder textMessageEncoder = new TextMessageEncoder();
    final TextMessageDecoder textMessageDecoder = new TextMessageDecoder();

    final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    final MutableDirectBuffer egressBuffer = new ExpandableArrayBuffer(256);

    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        if (snapshotImage != null)
        {
            System.out.println("[Server Clustered Service] Snapshot found on start. Loading...");
            loadSnapshot();
        }
    }

    @Override
    public void onSessionOpen(final ClientSession session, final long timestamp)
    {
        System.out.println("[Server Clustered Service] Client session opened");
    }

    @Override
    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
    {
        System.out.println("[Server Clustered Service] Client session closed");
    }

    @Override
    public void onSessionMessage(final ClientSession session, final long timestamp, final DirectBuffer buffer, final int offset, final int length, Header header)
    {
        if (session == null)
        {
            System.err.println("[Server Clustered Service] Client session is null");
            return;
        }

        textMessageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        textMessageEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder);

        textMessageEncoder.correlationId(textMessageDecoder.correlationId());
        textMessageEncoder.message(textMessageDecoder.message());

        final int egressLength = textMessageDecoder.encodedLength() + headerDecoder.encodedLength();
        while (session.offer(egressBuffer, 0, egressLength) < 0)
        {
            idleStrategy.idle();
        }
    }

    @Override
    public void onTimerEvent(final long correlationId, final long timestamp)
    {
        System.out.println("[Server Clustered Service] Node timer event firing ");
    }

    @Override
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication)
    {
        System.out.println("[Server Clustered Service] Take snapshot");
    }

    @Override
    public void onRoleChange(final Cluster.Role newRole)
    {
        System.out.println("[Server Clustered Service] New node role: " + newRole.name());
    }

    @Override
    public void onTerminate(final Cluster cluster)
    {
        System.out.println("[Server Clustered Service] Node is terminating");
    }

    private void loadSnapshot()
    {
        // TODO
    }
}
