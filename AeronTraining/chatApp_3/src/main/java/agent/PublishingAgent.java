package agent;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.CloseHelper;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;

import static common.Globals.*;

public class PublishingAgent implements Agent
{
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    private Aeron aeron;
    private Publication publication;

    private static final int HEADER_LENGTH = new MessageHeaderEncoder().encodedLength();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();
    private AgentState agentState = AgentState.INITIAL;

    private final OneToOneRingBuffer ringBuffer;

    public PublishingAgent(final OneToOneRingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onStart()
    {
        agentState = AgentState.STARTING;
        aeron = connectAeron();
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
                else if (publication.isConnected())
                {
                    agentState = AgentState.STEADY;
                }
            }
            case STEADY ->
            {
                if (publication.isConnected())
                {
                    workCount += ringBuffer.read(this::readAndOfferMessage);
                }
                else
                {
                    onClose();
                }
            }
            case STOPPED ->
            {
            }
        }
        return workCount;
    }

    private void readAndOfferMessage(final int msgTypeId, final MutableDirectBuffer buffer, final int index, final int length)
    {
        messageEncoder.wrap(buffer, index + HEADER_LENGTH);
        messageEncoder.netTimestamp(System.nanoTime());
        final long offer = publication.offer(buffer, index, length);
        if (offer < 0)
        {
            System.err.println("Client publishing failed | Response Code: " + offer);
        }
    }

    @Override
    public void onClose()
    {
        CloseHelper.close(aeron);
        agentState = AgentState.CLOSED;
    }

    @Override
    public String roleName()
    {
        return "publishing-agent";
    }

    private Aeron connectAeron()
    {
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(AERON_DIR_PATH);
        return Aeron.connect(aeronContext);
    }
}