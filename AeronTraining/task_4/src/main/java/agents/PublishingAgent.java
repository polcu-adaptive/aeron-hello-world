package agents;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;

import static common.Globals_Task4.*;

public class PublishingAgent implements Agent
{
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    private Aeron aeron;
    private Publication publication;

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();
    private AgentState agentState = AgentState.INITIAL;
    private String message = "";

    private int messageCounter = 0;

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
                    publication = aeron.addPublication(CHANNEL, STREAM_ID);
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
                    offerMessage();
                    if (messageCounter >= MESSAGES_COUNT)
                    {
                        agentState = AgentState.STOPPED;
                    }
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

    public void setMessage(final String message)
    {
        this.message = message;
    }

    private void offerMessage()
    {
        if (message == null || message.isEmpty())
        {
            System.err.println("Publishing Agent can't offer an empty message");
            return;
        }

        messageEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        messageEncoder.message(message);
        messageEncoder.timestamp(System.nanoTime());

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        final long offer = publication.offer(buffer, 0, length);
        if (offer >= 0)
        {
            //System.out.println("Publishing - Sent: " + message);
            ++messageCounter;
        }
        else
        {
            System.err.println("Publishing - Failed | Response Code: " + offer);
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