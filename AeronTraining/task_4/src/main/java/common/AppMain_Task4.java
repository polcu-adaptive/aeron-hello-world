package common;

import agents.AgentErrorHandler;
import agents.PublishingAgent;
import agents.SubscriptionAgent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import static common.Globals_Task4.MESSAGES_COUNT;

public class AppMain_Task4
{
    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        System.out.println("Setup PublishingAgent");
        final PublishingAgent publishingAgent = new PublishingAgent();
        publishingAgent.setMessage("Hello World!");
        final AgentRunner publishingAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, publishingAgent);

        System.out.println("Setup SubscriptionAgent");
        final SubscriptionAgent subscriptionAgent = new SubscriptionAgent();
        final AgentRunner subscriptionAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, subscriptionAgent);

        System.out.println("Start agent runners");
        AgentRunner.startOnThread(publishingAgentRunner);
        AgentRunner.startOnThread(subscriptionAgentRunner);

        while (publishingAgent.getMessageCounter() < MESSAGES_COUNT)
        {

        }
        publishingAgentRunner.close();

        while (subscriptionAgent.getMessageCounter() < MESSAGES_COUNT)
        {

        }
        subscriptionAgentRunner.close();
    }
}
