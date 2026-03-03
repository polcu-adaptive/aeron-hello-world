package subscriber;

import common.Globals;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.util.ArrayList;

public class SubscriberAppMain
{
    static long totalLatency = 0;

    public static void main(final String[] args)
    {
        System.out.println("Subscriber starting up");

        final IdleStrategy idleStrategy = new SleepingIdleStrategy();

        try (
             final Aeron aeron = Aeron.connect();
             final Subscription subscription = aeron.addSubscription(Globals.CHANNEL, Globals.STREAM_ID))
        {

            final FragmentHandler handler = (buffer, offset, length, header) ->
                    totalLatency += System.nanoTime() - buffer.getLong(offset);

            for (int i = 0; i < Globals.MESSAGES_COUNT; ++i)
            {
                while (subscription.poll(handler, 1) <= 0)
                {
                    idleStrategy.idle();
                }
            }

            System.out.println("Average message latency: " + totalLatency / Globals.MESSAGES_COUNT);
            System.out.println("Reached end of subscriber main method");
        }
    }
}
