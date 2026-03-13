package agent;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
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
    private Subscription liveSubscription;
    private Subscription replaySubscription;
    private AeronArchive archiveClient;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AeronMessageDecoder messageDecoder = new AeronMessageDecoder();
    private AgentState agentState = AgentState.INITIAL;

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
                final RecordingInfo recordingInfo = getRecordingId(archiveClient, CHAT_OUTBOUND_CHANNEL, STREAM_ID);
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

    private RecordingInfo getRecordingId(final AeronArchive archive, final String remoteRecordedChannel, final int remoteRecordedStream)
    {
        final RecordingInfo recordingInfo = new RecordingInfo();
        final RecordingDescriptorConsumer consumer = (controlSessionId, correlationId, recordingId,
                                                      startTimestamp, stopTimestamp, startPosition,
                                                      stopPosition, initialTermId, segmentFileLength,
                                                      termBufferLength, mtuLength, sessionId,
                                                      streamId, strippedChannel, originalChannel,
                                                      sourceIdentity) ->
        {
            recordingInfo.setRecordingId(recordingId);
            recordingInfo.setSessionId(sessionId);
        };

        final var fromRecordingId = 0L;
        final var recordCount = 100;

        final int foundCount = archive.listRecordingsForUri(fromRecordingId, recordCount, remoteRecordedChannel,
                remoteRecordedStream, consumer);

        if (0 == foundCount)
        {
            recordingInfo.setRecordingId(Long.MIN_VALUE);
        }

        return recordingInfo;
    }
}