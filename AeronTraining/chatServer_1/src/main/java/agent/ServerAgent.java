package agent;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;

import static common.Globals.*;

public class ServerAgent implements Agent
{
    private AeronArchive archive;
    private Aeron aeron;
    private Subscription subscription;
    private Publication publication;
    private final UnsafeBuffer outBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

    private static final int HEADER_LENGTH = new MessageHeaderEncoder().encodedLength();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();
    private AgentState agentState = AgentState.INITIAL;

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
                    publication = aeron.addPublication(CHAT_OUTBOUND_CHANNEL, STREAM_ID);
                }

                if (subscription == null)
                {
                    subscription = aeron.addSubscription(CHAT_INBOUND_CHANNEL, STREAM_ID);
                }

                if (publication.isConnected() && subscription.isConnected())
                {
                    agentState = AgentState.STEADY;
                }
            }
            case STEADY ->
            {
                if (subscription.isConnected())
                {
                    workCount += subscription.poll(this::readAndBroadcastMessage, 10);
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

    private void readAndBroadcastMessage(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        System.out.println("|Server Agent| Received message");

        outBuffer.putBytes(0, buffer, offset, length);
        messageEncoder.wrap(outBuffer, HEADER_LENGTH);
        messageEncoder.serverTimestamp(System.nanoTime());

        final long offerResult = publication.offer(outBuffer, 0, length);
        if (offerResult < 0)
        {
            System.err.println("Server publishing failed | Response Code: " + offerResult);
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
        return "server-agent";
    }

    private Aeron connectAeron()
    {
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(AERON_DIR_PATH);
        return Aeron.connect(aeronContext);
    }
}