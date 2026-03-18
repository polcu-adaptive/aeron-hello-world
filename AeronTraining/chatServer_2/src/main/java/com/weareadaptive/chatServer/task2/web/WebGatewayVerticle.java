package com.weareadaptive.chatServer.task2.web;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.AeronMessageDecoder;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderDecoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WebGatewayVerticle extends VerticleBase
{
    private final int configuredPort;

    private final OneToOneRingBuffer innerRingBuffer;
    private final OneToOneRingBuffer outerRingBuffer;

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();
    private final AeronMessageDecoder messageDecoder = new AeronMessageDecoder();

    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));

    private final List<WebSocket> webSocketList = new ArrayList<>();

    public WebGatewayVerticle(final int configuredPort, final OneToOneRingBuffer innerRingBuffer, final OneToOneRingBuffer outerRingBuffer)
    {
        this.configuredPort = configuredPort;
        this.innerRingBuffer = innerRingBuffer;
        this.outerRingBuffer = outerRingBuffer;
    }

    @Override
    public Future<?> start()
    {
        final HttpServer httpServer = vertx.createHttpServer();

        httpServer.webSocketHandler(webSocket ->
        {
            System.out.println("Register web socket");
            webSocketList.add(webSocket);

            // On message event
            webSocket.textMessageHandler(message ->
            {
                System.out.println("Websocket message received: " + message);

                messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
                messageEncoder.message(message);
                messageEncoder.inputTimestamp(System.nanoTime());

                final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
                innerRingBuffer.write(1, unsafeBuffer, 0, length);
            });

            // On close event
            webSocket.closeHandler(v ->
            {
                webSocketList.remove(webSocket);
                System.out.println("Websocket has been closed");
            });
        });

        vertx.setPeriodic(1, id -> outerRingBuffer.read(this::pollMessages));

        return httpServer
                .listen(configuredPort)
                .onSuccess(server -> System.out.println("HTTP server started on port " + server.actualPort()))
                .onFailure(throwable -> System.out.println("HTTP server error"));
    }

    private void pollMessages(final int msgTypeId, final MutableDirectBuffer buffer, final int offset, final int length)
    {
        //Decode SBE message
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
                .put("Message", message)
                .put("InputLatency", inputLatencyMs)
                .put("NetLatency", netLatencyMs)
                .put("ServerLatency", serverLatencyMs);

        webSocketList.forEach(webSocket ->
        {
            if (!webSocket.isClosed())
            {
                webSocket.writeTextMessage(jsonObject.encode());
            }
        });
    }
}
