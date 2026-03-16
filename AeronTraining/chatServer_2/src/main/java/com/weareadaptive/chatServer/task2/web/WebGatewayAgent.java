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
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.Json;
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
import java.util.*;

import static com.weareadaptive.chatServer.task2.Globals.*;
import static com.weareadaptive.chatServer.task2.agent.RecordingInfo.getRecordingId;

public class WebGatewayAgent implements Agent
{
    private final Queue<String> messagesToPublish;
    private final List<WebSocketState> webSockets;
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
                if (subscription == null)
                {
                    subscription = aeron.addSubscription(CHAT_OUTBOUND_CHANNEL, STREAM_ID);
                }

                if (publication.isConnected())
                {
                    agentState = AgentState.STEADY;
                }
            }
            case STEADY ->
            {
                replayToWebSockets();

                if (publication.isConnected() && !messagesToPublish.isEmpty())
                {
                    workCount += (int)publishMessage();
                }

                if (subscription.isConnected())
                {
                    subscription.poll(this::liveSubscriptionHandleFragment, 10);
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
        Subscription replaySubscription = null;
        boolean isReplaying = false;

        final RecordingInfo recordingInfo = RecordingInfo.getRecordingId(archiveClient, CHAT_OUTBOUND_CHANNEL, STREAM_ID);
        if (recordingInfo.getRecordingId() != Long.MIN_VALUE)
        {
            System.out.println("Websocket has missed messages. Replaying");

            final int replayStreamId = new java.util.Random().nextInt(10000);

            replaySubscription = aeron.addSubscription(REPLAY_CHANNEL, replayStreamId);
            archiveClient.startReplay(recordingInfo.getRecordingId(), 0L, Long.MAX_VALUE, REPLAY_CHANNEL, replayStreamId);
            isReplaying = true;
        }

        webSockets.add(new WebSocketState(webSocket, replaySubscription, isReplaying));


        webSocket.textMessageHandler(text ->
        {
            System.out.println("Websocket message received: " + text);
            messagesToPublish.add(text);
        });

        webSocket.closeHandler(v ->
        {
            final Optional<WebSocketState> webSocketState = webSockets.stream().filter(ws -> ws.webSocket().equals(webSocket)).findFirst();
            if (webSocketState.isPresent())
            {
                CloseHelper.close(webSocketState.get().replaySubscription());
                webSockets.remove(webSocketState.get());
                System.out.println("Websocket has been closed");
            }
            else
            {
                System.out.println("Error closing a webSocket");
            }
        });
    }

    private void broadcastToWebSockets(final String message)
    {
        vertx.runOnContext(v ->
        {
            for (final WebSocketState webSocketState : webSockets)
            {
                final WebSocket webSocket = webSocketState.webSocket();;
                if (!webSocket.isClosed() && !webSocketState.isReplaying())
                {
                    webSocket.writeTextMessage(message);
                }
            }
        });
    }

    private void replayToWebSockets()
    {
        vertx.runOnContext(v ->
        {
            for (final WebSocketState webSocketState : webSockets)
            {
                if (webSocketState.isReplaying() && webSocketState.replaySubscription().isConnected())
                {
                    int fragments = webSocketState.replaySubscription().poll((buffer, offset, length, header) ->
                    {
                        final JsonObject jsonObject = handleFragment(buffer, offset, length, header);
                        System.out.println("Replayed message: " + jsonObject.encode());
                        webSocketState.webSocket.writeTextMessage(jsonObject.encode());
                    }, 10);

                    if (fragments == 0)
                    {
                        System.out.println("Finished replaying");
                        webSocketState.isReplaying(false);
                    }
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

    private JsonObject handleFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
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

        return new JsonObject()
                .put("message", message)
                .put("inputLatencyMs", inputLatencyMs)
                .put("netLatencyMs", netLatencyMs)
                .put("serverLatencyMs", serverLatencyMs);
    }

    private void liveSubscriptionHandleFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final JsonObject jsonObject = handleFragment(buffer, offset, length, header);
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
