package agents;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import task3.src.main.resources.AeronMessageDecoder;
import task3.src.main.resources.MessageHeaderDecoder;

import static common.Globals_Task4.*;

public class SubscriptionAgent implements Agent
{
    private Aeron aeron;
    private Subscription subscription;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AeronMessageDecoder messageDecoder = new AeronMessageDecoder();
    private FragmentHandler fragmentHandler = null;
    private AgentState agentState = AgentState.INITIAL;

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
                if (subscription == null)
                {
                    subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
                    setupFragmentHandler();
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
                    workCount = pollSubscription();
                }
                else
                {
                    onClose();
                }
            }
        }
        return workCount;
    }

    private int pollSubscription()
    {
        final int fragments = subscription.poll(fragmentHandler, 10);
        messageCounter += fragments;
        return fragments;
    }

    private void setupFragmentHandler()
    {
        fragmentHandler = (buffer, offset, length, header) ->
        {
            // Decode SBE message
            headerDecoder.wrap(buffer, offset);

            final int actingBlockLength = headerDecoder.blockLength();
            final int actingVersion = headerDecoder.version();

            offset += headerDecoder.encodedLength();
            messageDecoder.wrap(buffer, offset, actingBlockLength, actingVersion);

            final String message = messageDecoder.message();
            final long timestamp = messageDecoder.timestamp();

            // Compute latency
            final long latencyNs = System.nanoTime() - timestamp;
            final double latencyMs = latencyNs / 1_000_000.0;
            System.out.println("Message received: " + message + " | Latency: " + latencyNs + "ns - " + latencyMs + "ms");
        };
    }

    public int getMessageCounter()
    {
        return messageCounter;
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