package agent;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;
import java.util.Scanner;

public class CliAgent implements Agent
{
    private final OneToOneRingBuffer ringBuffer;
    private final Scanner scanner;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();

    private UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
    private AgentState agentState = AgentState.INITIAL;
    private String message;

    public CliAgent(final OneToOneRingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
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
                System.out.println("|Cli Agent| Enter your message: ");
                message = scanner.nextLine();
                sendMessage(message);
            }
        }

        return workCount;
    }

    private void sendMessage(final String message)
    {
        messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
        messageEncoder.message(message);
        messageEncoder.inputTimestamp(System.nanoTime());

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        ringBuffer.write(1, unsafeBuffer, 0, length);
    }

    @Override
    public String roleName()
    {
        return "cli-agent";
    }
}
