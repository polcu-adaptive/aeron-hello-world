package com.weareadaptive.chatServer.task3.agent;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.MessageHeaderDecoder;
import task3.src.main.resources.MessageHeaderEncoder;
import task3.src.main.resources.TextMessageDecoder;
import task3.src.main.resources.TextMessageEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Scanner;

public class CliAgent implements Agent
{
    private final OneToOneRingBuffer innerRingBuffer;
    private final OneToOneRingBuffer outerRingBuffer;
    private final Scanner scanner;

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final TextMessageEncoder messageEncoder = new TextMessageEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final TextMessageDecoder messageDecoder = new TextMessageDecoder();

    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
    private AgentState agentState = AgentState.INITIAL;
    private final Queue<String> inputQueue = new ArrayDeque<>();

    public CliAgent(final OneToOneRingBuffer innerRingBuffer, final OneToOneRingBuffer outerRingBuffer)
    {
        this.innerRingBuffer = innerRingBuffer;
        this.outerRingBuffer = outerRingBuffer;
        this.scanner = new Scanner(System.in);
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        switch (agentState)
        {
            case INITIAL ->
            {
                Thread inputThread = new Thread(() ->
                {
                    while (true)
                    {
                        System.out.println("[Cli Agent] Enter your message: ");
                        String line = scanner.nextLine();
                        inputQueue.offer(line);
                    }
                });
                inputThread.setDaemon(true);
                inputThread.start();

                agentState = AgentState.STEADY;
            }

            case STEADY ->
            {
                sendMessages();

                outerRingBuffer.read(this::pollMessages);
            }
        }

        return workCount;
    }

    private void sendMessages()
    {
        if (inputQueue.isEmpty())
        {
            return;
        }

        final String message = inputQueue.poll();
        messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
        messageEncoder.message(message);
        messageEncoder.inputTimestamp(System.nanoTime());

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        innerRingBuffer.write(1, unsafeBuffer, 0, length);
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

        System.out.println("[Cli Agent] Message received: " + message + " - Input latency: " + inputLatencyMs + " ms - Net latency: " + netLatencyMs + " ms - Server latency: " + serverLatencyMs + " ms");
    }

    @Override
    public void onClose()
    {
        agentState = AgentState.CLOSED;
    }

    @Override
    public String roleName()
    {
        return "cli-agent";
    }
}
