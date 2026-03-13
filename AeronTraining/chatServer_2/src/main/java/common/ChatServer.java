package common;

import agent.AgentErrorHandler;
import agent.ServerAgent;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingIdleStrategy;

public class ChatServer
{
    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategy = new SleepingIdleStrategy();

        final ServerAgent serverAgent = new ServerAgent();
        final AgentRunner serverAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, serverAgent);

        AgentRunner.startOnThread(serverAgentRunner);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier())
        {
            System.out.println("Server agent is running");
            shutdownSignalBarrier.await();
            CloseHelper.close(serverAgentRunner);
        }
    }
}
