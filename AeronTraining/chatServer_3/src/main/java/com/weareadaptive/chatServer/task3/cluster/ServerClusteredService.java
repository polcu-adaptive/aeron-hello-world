package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ServerClusteredService implements ClusteredService
{
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final TextMessageEncoder textMessageEncoder = new TextMessageEncoder();
    private final TextMessageDecoder textMessageDecoder = new TextMessageDecoder();

    private final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    private final MutableDirectBuffer egressBuffer = new ExpandableArrayBuffer(256);
    private final UnsafeBuffer snapshotBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

    private final List<TextMessage> messages = new ArrayList<>();
    private final MessageSnapshotEncoder messageSnapshotEncoder = new MessageSnapshotEncoder();
    private final FragmentHandler snapshotLoader;

    public ServerClusteredService()
    {
        this.snapshotLoader = (final DirectBuffer buffer, final int offset, final int length, final Header header) ->
        {

        };
    }

    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        if (snapshotImage != null)
        {
            System.out.println("[Server Clustered Service] Snapshot found on start. Loading...");
            loadSnapshot(snapshotImage);
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
        System.out.println("On session message!");

        if (session == null)
        {
            System.err.println("[Server Clustered Service] Client session is null");
            return;
        }

        textMessageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        textMessageEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder);

        textMessageEncoder.correlationId(textMessageDecoder.correlationId());
        textMessageEncoder.message(textMessageDecoder.message());

        final TextMessage newTextMessage = new TextMessage(textMessageDecoder.message(), timestamp);
        messages.add(newTextMessage);

        System.out.println(textMessageDecoder.message() + System.lineSeparator());

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

        messages.forEach(message ->
        {
            messageSnapshotEncoder.wrapAndApplyHeader(snapshotBuffer, 0, headerEncoder);
            messageSnapshotEncoder.message(message.message());
            messageSnapshotEncoder.timestamp(message.timestamp());

            final int length = headerDecoder.encodedLength() + messageSnapshotEncoder.encodedLength();
            while (snapshotPublication.offer(snapshotBuffer, 0, length) < 0)
            {
                idleStrategy.idle();
            }
        });
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

    private void loadSnapshot(final Image snapshotImage)
    {
        System.out.println("[Server Clustered Service] Load snapshot");

        idleStrategy.reset();
        while (!snapshotImage.isEndOfStream())
        {
            final int workCount = snapshotImage.poll(snapshotLoader, 10);
            idleStrategy.idle(workCount);
        }
    }
}
