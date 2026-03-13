package agent;

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
}
