package com.weareadaptive.chatServer.task2.agent;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import static com.weareadaptive.chatServer.task2.Globals.*;

public class SubscriptionAgent implements Agent
{
    private Aeron aeron;
    private Subscription liveSubscription;
    private Subscription replaySubscription;
    private AeronArchive archiveClient;

    private AgentState agentState = AgentState.INITIAL;

    private final OneToOneRingBuffer ringBuffer;

    public SubscriptionAgent(final OneToOneRingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onStart()
    {
        agentState = AgentState.STARTING;
        aeron = connectAeron();
        archiveClient = launchArchiveClient();
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
                if (liveSubscription == null)
                {
                    liveSubscription = aeron.addSubscription(CHAT_OUTBOUND_CHANNEL, STREAM_ID);
                    agentState = AgentState.REPLAY_CHECK;
                }
            }
            case REPLAY_CHECK ->
            {
                final RecordingInfo recordingInfo = RecordingInfo.getRecordingId(archiveClient, CHAT_OUTBOUND_CHANNEL, STREAM_ID);
                if (recordingInfo.getRecordingId() != Long.MIN_VALUE)
                {
                    final int replayStreamId = new java.util.Random().nextInt(1000);

                    replaySubscription = aeron.addSubscription(REPLAY_CHANNEL, replayStreamId);
                    archiveClient.startReplay(recordingInfo.getRecordingId(), 0L, Long.MAX_VALUE, REPLAY_CHANNEL, replayStreamId);
                    agentState = AgentState.REPLAYING;
                }
            }
            case REPLAYING ->
            {
                if (replaySubscription.isConnected())
                {
                    workCount += replaySubscription.poll(this::handleFragment, 10);
                }
            }
            case STEADY ->
            {
                if (liveSubscription.isConnected())
                {
                    workCount = liveSubscription.poll(this::handleFragment, 10);
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
        if (!ringBuffer.write(1, buffer, offset, length))
        {
            System.err.println("[Subscription Agent] Error writing in outer ringBuffer");
        }
    }

    @Override
    public void onClose()
    {
        CloseHelper.closeAll(aeron, liveSubscription, replaySubscription, archiveClient);
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

    private AeronArchive launchArchiveClient()
    {
        final AeronArchive.Context archiveClientContext = new AeronArchive.Context()
                .aeron(aeron)
                .controlRequestChannel(ARCHIVE_CONTROL_CHANNEL)
                .controlResponseChannel(ARCHIVE_CONTROL_RESPONSE_CHANNEL);
        return AeronArchive.connect(archiveClientContext);
    }
}