package subscriber;

import common.Globals_Task1;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

public class SubscriberAppMain_Task1
{
    public static void main(final String[] args)
    {
        System.out.println("Subscriber starting up");

        final IdleStrategy idleStrategy = new SleepingIdleStrategy();

        try (
             final Aeron aeron = Aeron.connect();
             final Subscription subscription = aeron.addSubscription(Globals_Task1.CHANNEL, Globals_Task1.STREAM_ID))
        {
            final FragmentHandler handler = (buffer, offset, length, header) ->
                    System.out.println("Received message: " + buffer.getStringAscii(offset));

            while (subscription.poll(handler, 1) <= 0)
            {
                idleStrategy.idle();
            }

            System.out.println("Reached end of subscriber main method");
        }
    }
}
