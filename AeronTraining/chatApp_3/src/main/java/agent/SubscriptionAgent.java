package agent;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import task3.src.main.resources.AeronMessageDecoder;
import task3.src.main.resources.MessageHeaderDecoder;

import static common.Globals.*;

public class SubscriptionAgent implements Agent
{
    private Aeron aeron;
    private Subscription subscription;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AeronMessageDecoder messageDecoder = new AeronMessageDecoder();
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
                if (subscription == null)
                {
                    subscription = aeron.addSubscription(CHAT_OUTBOUND_CHANNEL, STREAM_ID);
                }
                else if (subscription.isConnected())
                {
                    agentState = AgentState.STEADY;
                }
            }
            case STEADY ->
            {
                if (subscription.isConnected())
                {
                    workCount = subscription.poll(this::handleFragment, 10);;
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


    private void handleFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
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

        System.out.println("|Subscription Agent| Message received: " + message + " - Input latency: " + inputLatencyMs + " ms - Net latency: " + netLatencyMs + " ms - Server latency: " + serverLatencyMs + " ms");
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
        return "subscription-agent";
    }

    private Aeron connectAeron()
    {
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(AERON_DIR_PATH);
        return Aeron.connect(aeronContext);
    }
}