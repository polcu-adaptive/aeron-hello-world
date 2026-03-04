package subscriber;

import common.Globals_Task2;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public class SubscriberAppMain_Task2
{
    static long totalLatency = 0;

    public static void main(final String[] args)
    {
        System.out.println("Subscriber starting up");

        final IdleStrategy idleStrategy = new BusySpinIdleStrategy();

        try (
             final Aeron aeron = Aeron.connect();
             final Subscription subscription = aeron.addSubscription(Globals_Task2.CHANNEL, Globals_Task2.STREAM_ID))
        {

            final FragmentHandler handler = (buffer, offset, length, header) ->
            {
                final long latency = System.nanoTime() - buffer.getLong(offset);
                totalLatency += latency;
                //System.out.println("Latency: " + latency);
            };

            for (int i = 0; i < Globals_Task2.MESSAGES_COUNT; ++i)
            {
                while (subscription.poll(handler, 1) <= 0)
                {
                    //System.out.println("Failed to receive message. Retrying...");
                    idleStrategy.idle();
                }
                //System.out.println("Successfully received message " + i);
            }

            System.out.println("Average latency for " + Globals_Task2.MESSAGES_COUNT + " messages: " + totalLatency / Globals_Task2.MESSAGES_COUNT);
            System.out.println("Reached end of subscriber main method");
        }
    }
}
