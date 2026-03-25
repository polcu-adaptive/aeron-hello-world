package com.weareadaptive.chatServer.task3.web;

import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.MessageHeaderDecoder;
import task3.src.main.resources.MessageHeaderEncoder;
import task3.src.main.resources.TextMessageDecoder;
import task3.src.main.resources.TextMessageEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WebGatewayService
{
    private final OneToOneRingBuffer innerRingBuffer;
    private final OneToOneRingBuffer outerRingBuffer;

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final TextMessageEncoder messageEncoder = new TextMessageEncoder();
    private final TextMessageDecoder messageDecoder = new TextMessageDecoder();

    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));

    private final List<WebSocket> webSocketList = new ArrayList<>();
    private final List<String> messagesLog = new ArrayList<>();

    public WebGatewayService(final OneToOneRingBuffer innerRingBuffer, final OneToOneRingBuffer outerRingBuffer)
    {
        this.innerRingBuffer = innerRingBuffer;
        this.outerRingBuffer = outerRingBuffer;
    }

    public void registerWebSocket(final WebSocket newWebSocket)
    {
        System.out.println("Register web socket");
        webSocketList.add(newWebSocket);

        // Replay past messages. This is a blocking operation which is probably not a good idea for large logs
        messagesLog.forEach(newWebSocket::writeTextMessage);

        // On message event
        newWebSocket.textMessageHandler(this::onWebSocketMessage);

        // On close event
        newWebSocket.closeHandler(v -> onWebSocketClose(newWebSocket));
    }

    public void pollMessages()
    {
        outerRingBuffer.read(this::decodeAndSendMessages);
    }

    private void onWebSocketMessage(final String message)
    {
        System.out.println("Websocket message received: " + message);

        messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
        messageEncoder.message(message);

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        innerRingBuffer.write(1, unsafeBuffer, 0, length);
    }

    private void onWebSocketClose(final WebSocket closingWebSocket)
    {
        webSocketList.remove(closingWebSocket);
        System.out.println("Websocket has been closed");
    }

    private void decodeAndSendMessages(final int msgTypeId, final MutableDirectBuffer buffer, final int offset, final int length)
    {
        //Decode SBE message
        headerDecoder.wrap(buffer, offset);

        final int actingBlockLength = headerDecoder.blockLength();
        final int actingVersion = headerDecoder.version();

        final int totalOffset = headerDecoder.encodedLength() + offset;
        messageDecoder.wrap(buffer, totalOffset, actingBlockLength, actingVersion);

        final String message = messageDecoder.message();
        messagesLog.add(message);

        webSocketList.forEach(webSocket ->
        {
            if (!webSocket.isClosed())
            {
                webSocket.writeTextMessage(message);
            }
        });
    }
}
