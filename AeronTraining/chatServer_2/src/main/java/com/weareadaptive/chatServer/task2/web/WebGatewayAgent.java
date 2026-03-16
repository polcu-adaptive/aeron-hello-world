package com.weareadaptive.chatServer.task2.web;

import com.weareadaptive.chatServer.task2.agent.AgentState;
import com.weareadaptive.chatServer.task2.agent.RecordingInfo;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.logbuffer.Header;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.AeronMessageDecoder;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderDecoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.weareadaptive.chatServer.task2.Globals.*;
import static com.weareadaptive.chatServer.task2.agent.RecordingInfo.getRecordingId;

public class WebGatewayAgent implements Agent
{
    private final Queue<String> messagesToPublish;
    private final List<ServerWebSocket> webSockets;
    private final Vertx vertx;

    private Aeron aeron;
    private Publication publication;
    private Subscription subscription;
    private AeronArchive archiveClient;

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AeronMessageDecoder messageDecoder = new AeronMessageDecoder();

    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
    private AgentState agentState = AgentState.INITIAL;

    public WebGatewayAgent(final Vertx vertx)
    {
        messagesToPublish = new ArrayDeque<>();
        webSockets = new ArrayList<>();
        this.vertx = vertx;
    }

    @Override
    public void onStart()
    {
        agentState = AgentState.STARTING;
        aeron = connectAeron();
        archiveClient = launchArchiveClient();
        agentState = AgentState.CONNECTING;
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        switch (agentState)
        {
            case CONNECTING ->
            {
                if (publication == null)
                {
                    publication = aeron.addPublication(CHAT_INBOUND_CHANNEL, STREAM_ID);
                }

                if (publication.isConnected())
                {
                    agentState = AgentState.REPLAY_CHECK;
                }
            }
            case REPLAY_CHECK ->
            {
                final RecordingInfo recordingInfo = getRecordingId(archiveClient, CHAT_OUTBOUND_CHANNEL, STREAM_ID);
                if (recordingInfo.getRecordingId() != Long.MIN_VALUE)
                {
                    final int replayStreamId = new java.util.Random().nextInt(1000);

                    subscription = aeron.addSubscription(REPLAY_CHANNEL, replayStreamId);
                    archiveClient.startReplay(recordingInfo.getRecordingId(), 0L, Long.MAX_VALUE, REPLAY_CHANNEL, replayStreamId);
                    agentState = AgentState.STEADY;
                }
            }
            case STEADY ->
            {
                if (publication.isConnected() && !messagesToPublish.isEmpty())
                {
                    workCount += (int)publishMessage();
                }

                if (subscription.isConnected())
                {
                    subscription.poll(this::handleFragment, 10);
                }
            }
            case STOPPED ->
            {
            }
        }

        return workCount;
    }

    public void registerWebSocket(final ServerWebSocket webSocket)
    {
        webSockets.add(webSocket);

        webSocket.textMessageHandler(text ->
        {
            System.out.println("Websocket message received: " + text);
            messagesToPublish.add(text);
        });

        webSocket.closeHandler(v ->
        {
            webSockets.remove(webSocket);
            System.out.println("Websocket has been closed");
        });
    }

    private void broadcastToWebSockets(final String message)
    {
        vertx.runOnContext(v ->
        {
            for (final ServerWebSocket webSocket : webSockets)
            {
                if (!webSocket.isClosed())
                {
                    webSocket.writeTextMessage(message);
                }
            }
        });
    }

    private long publishMessage()
    {
        final String message = messagesToPublish.poll();

        messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
        messageEncoder.message(message);
        messageEncoder.inputTimestamp(System.nanoTime());

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        return publication.offer(unsafeBuffer, 0, length);
    }

    private void handleFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        // Decode SBE message
        headerDecoder.wrap(buffer, offset);

        final int actingBlockLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        final int totalOffset = headerDecoder.encodedLength() + offset;
        messageDecoder.wrap(buffer, totalOffset, actingBlockLength, actingVersion);

        final String message = messageDecoder.message();
        final long netTimestamp = messageDecoder.netTimestamp();
        final long inputTimestamp = messageDecoder.inputTimestamp();
        final long serverTimestamp = messageDecoder.serverTimestamp();

        // Compute latency
        final double inputLatencyMs = (System.nanoTime() - inputTimestamp) / 1_000_000.0;
        final double netLatencyMs = (System.nanoTime() - netTimestamp) / 1_000_000.0;
        final double serverLatencyMs = (System.nanoTime() - serverTimestamp) / 1_000_000.0;

        final JsonObject jsonObject = new JsonObject()
                .put("message", message)
                .put("inputLatencyMs", inputLatencyMs)
                .put("netLatencyMs", netLatencyMs)
                .put("serverLatencyMs", serverLatencyMs);

        broadcastToWebSockets(jsonObject.encode());
    }

    @Override
    public void onClose()
    {
        CloseHelper.closeAll(aeron, publication, subscription, archiveClient);
    }

    @Override
    public String roleName()
    {
        return "web-gateway-agent";
    }

    private Aeron connectAeron()
    {
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(AERON_DIR_PATH);
        return Aeron.connect(aeronContext);
    }

    private AeronArchive launchArchiveClient()
    {
        final AeronArchive.Context archiveClientContext = new AeronArchive.Context()
                .aeron(aeron)
                .controlRequestChannel(ARCHIVE_CONTROL_CHANNEL)
                .controlResponseChannel(ARCHIVE_CONTROL_RESPONSE_CHANNEL);
        return AeronArchive.connect(archiveClientContext);
    }
}
