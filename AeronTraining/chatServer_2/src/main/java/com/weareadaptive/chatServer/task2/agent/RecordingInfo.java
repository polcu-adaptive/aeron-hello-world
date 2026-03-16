package com.weareadaptive.chatServer.task2.agent;

import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;

public class RecordingInfo
{
    private long recordingId;
    private int sessionId;

    public long getRecordingId()
    {
        return recordingId;
    }

    public RecordingInfo setRecordingId(final long recordingId)
    {
        this.recordingId = recordingId;
        return this;
    }

    public int getSessionId()
    {
        return sessionId;
    }

    public RecordingInfo setSessionId(final int sessionId)
    {
        this.sessionId = sessionId;
        return this;
    }

    public static RecordingInfo getRecordingId(final AeronArchive archive, final String remoteRecordedChannel, final int remoteRecordedStream)
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
