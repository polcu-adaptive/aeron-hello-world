package publisher;

import common.AgentState;
import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;

import static common.Globals_Task4.*;

public class PublishingAgent implements Agent
{
    private static final int BUFFER_SIZE = MessageHeaderEncoder.ENCODED_LENGTH + AeronMessageEncoder.BLOCK_LENGTH;
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_SIZE));
    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Publication publication;

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();
    private AgentState agentState = AgentState.INITIAL;
    private String message = "";

    @Override
    public void onStart()
    {
        agentState = AgentState.STARTING;
        mediaDriver = launchMediaDriver();
        aeron = connectAeron(mediaDriver);
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
                }
                else
                {
                    onClose();
                }
            }
        }
        return workCount;
    }

    public void setMessage(final String message)
    {
        this.message = message;
        messageEncoder.message(message);
    }

    private void offerMessage()
    {
        if (message == null || message.isEmpty())
        {
            System.err.println("Publishing Agent can't offer an empty message");
            return;
        }

        messageEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        messageEncoder.timestamp(System.nanoTime());

        final long offer = publication.offer(buffer, 0, BUFFER_SIZE);
        if (offer >= 0)
        {
            System.out.println("Publishing - Sent: " + message);
        }
        else
        {
            System.err.println("Publishing - Failed | Response Code: " + offer);
        }
    }

    @Override
    public void onClose()
    {
        CloseHelper.closeAll(mediaDriver, aeron);
        agentState = AgentState.CLOSED;
    }

    @Override
    public String roleName()
    {
        return "publishing-agent";
    }

    private MediaDriver launchMediaDriver()
    {
        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context().aeronDirectoryName(AERON_DIR_PATH).dirDeleteOnStart(true);
        return MediaDriver.launchEmbedded(mediaDriverContext);
    }

    private Aeron connectAeron(final MediaDriver mediaDriver)
    {
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(mediaDriver.context().aeronDirectoryName());
        return Aeron.connect(aeronContext);
    }
}