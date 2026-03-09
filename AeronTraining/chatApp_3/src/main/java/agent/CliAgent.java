package agent;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;
import java.util.Scanner;

public class CliAgent implements Agent
{
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
    private final OneToOneRingBuffer ringBuffer;
    private final Scanner scanner;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();

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
        messageEncoder.wrapAndApplyHeader(ringBuffer.buffer(), 0, headerEncoder);
        messageEncoder.message(message);
        messageEncoder.netTimestamp(0);;
        messageEncoder.serverTimestamp(0);
        messageEncoder.inputTimestamp(0);

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        final int claimIndex = ringBuffer.tryClaim(1, length);
        if (claimIndex > 0)
        {
            messageEncoder.wrapAndApplyHeader(ringBuffer.buffer(), claimIndex, headerEncoder);
            messageEncoder.message(message);
            messageEncoder.inputTimestamp(System.nanoTime());

            ringBuffer.commit(claimIndex);
        }
        else
        {
            System.err.println("Ring Buffer Claim Index is not greater than 0.");
        }
    }

    @Override
    public String roleName()
    {
        return "cli-agent";
    }
}
