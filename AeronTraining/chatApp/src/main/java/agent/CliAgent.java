package agent;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

public class CliAgent implements Agent
{
    private OneToOneRingBuffer ringBuffer;
    private AgentState agentState = AgentState.INITIAL;

    public CliAgent(final OneToOneRingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        return workCount;
    }

    @Override
    public String roleName()
    {
        return "cli-agent";
    }
}
