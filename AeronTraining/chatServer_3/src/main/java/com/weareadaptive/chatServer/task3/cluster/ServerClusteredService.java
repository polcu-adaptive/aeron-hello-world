package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.ClusterClientSession;
import io.aeron.cluster.ClusterTool;
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
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.*;

import java.nio.ByteBuffer;
import java.util.*;

public class ServerClusteredService implements ClusteredService
{
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final TextMessageEncoder textMessageEncoder = new TextMessageEncoder();
    private final TextMessageDecoder textMessageDecoder = new TextMessageDecoder();

    private final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    private final MutableDirectBuffer egressBuffer = new ExpandableArrayBuffer(256);
    private final UnsafeBuffer snapshotBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    private final List<ClientSession> clientSessions = new ArrayList<>();

    private final List<TextMessage> messages = new ArrayList<>();
    private final MessageSnapshotEncoder messageSnapshotEncoder = new MessageSnapshotEncoder();
    private final MessageSnapshotDecoder messageSnapshotDecoder = new MessageSnapshotDecoder();
    private final FragmentHandler snapshotLoader;

    private final Queue<ReplayingSession> replayingSessions = new ArrayDeque<>();
    private final IdleStrategy replayIdleStrategy = new SleepingMillisIdleStrategy(2000);
    private static final int REPLAY_PER_TICK = 50;
    private final MutableDirectBuffer replayBuffer = new ExpandableArrayBuffer(256);

    private Cluster cluster;
    private final int replayCorrelationId = 1;

    public ServerClusteredService()
    {
        this.snapshotLoader = (final DirectBuffer buffer, final int offset, final int length, final Header header) ->
        {
            messageSnapshotDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
            final TextMessage textMessage = new TextMessage(messageSnapshotDecoder.message(), messageSnapshotDecoder.timestamp());
            messages.add(textMessage);
        };
    }

    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        System.out.println("[Server Clustered Service] On start");

        this.cluster = cluster;
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
        clientSessions.add(session);

        replayingSessions.add(new ReplayingSession(session));
        cluster.scheduleTimer(replayCorrelationId, timestamp + 1);
    }

    @Override
    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
    {
        System.out.println("[Server Clustered Service] Client session closed");
        clientSessions.remove(session);
    }

    @Override
    public void onSessionMessage(final ClientSession session, final long timestamp, final DirectBuffer buffer, final int offset, final int length, Header header)
    {
        if (session == null)
        {
            System.err.println("[Server Clustered Service] Client session is null");
            return;
        }

        // Decode and store new message
        textMessageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        final long correlationId = textMessageDecoder.correlationId();
        final TextMessage textMessage = new TextMessage(textMessageDecoder.message(), timestamp);
        messages.add(textMessage);

        System.out.println("[Server Clustered Service] Message received: " + textMessage.message());

        // Encode and send the message through the egress log
        textMessageEncoder.wrapAndApplyHeader(egressBuffer, 0, headerEncoder);
        textMessageEncoder.correlationId(correlationId);
        textMessageEncoder.message(textMessage.message());
        textMessageEncoder.timestamp(timestamp);

        final int egressLength = textMessageEncoder.encodedLength() + headerEncoder.encodedLength();
        System.out.println("[Server Clustered Service] Egress length: " + egressLength);

        clientSessions.forEach(clientSession ->
        {
            while (clientSession.offer(egressBuffer, 0, egressLength) < 0)
            {
                idleStrategy.idle();
            }
        });

        System.out.println("[Server Clustered Service] Message sent back to clients" + System.lineSeparator());
    }

    @Override
    public void onTimerEvent(final long correlationId, final long timestamp)
    {
        System.out.println("[Server Clustered Service] Node timer event firing ");

        if (correlationId != replayCorrelationId)
        {
            System.err.println("[Server Clustered Service] Unknown timer correlationId");
            return;
        }

        replayingSessions.forEach(this::sendReplayStream);
        if (!replayingSessions.isEmpty())
        {
            cluster.scheduleTimer(replayCorrelationId, timestamp + 1);
        }
    }

    private void sendReplayStream(final ReplayingSession replayingSession)
    {
        final int currentIndex = replayingSession.currentIndex();
        System.out.println("Replaying from index: " + currentIndex);

        for (int i = currentIndex; i < currentIndex + REPLAY_PER_TICK; ++i)
        {
            if (i >= messages.size())
            {
                replayingSessions.remove();
                return;
            }

            final TextMessage message = messages.get(i);
            textMessageEncoder.wrapAndApplyHeader(replayBuffer, 0, headerEncoder);
            textMessageEncoder.message(message.message());
            textMessageEncoder.timestamp(message.timestamp());

            final int length = headerEncoder.encodedLength() + textMessageEncoder.encodedLength();
            if (replayingSession.clientSession().offer(replayBuffer, 0, length) < 0)
            {
                idleStrategy.idle();
            }
        }

        replayingSession.currentIndex(currentIndex + REPLAY_PER_TICK);
        if (replayingSession.currentIndex() >= messages.size())
        {
            replayingSessions.remove();
        }
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
