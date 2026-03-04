package publisher;

import common.Globals_Task1;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class PublisherAppMain_Task1
{
    public static void main(final String[] args)
    {
        System.out.println("Publisher starting up");

        final String message = "Hello World";

        final IdleStrategy idleStrategy = new SleepingIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        try (final Aeron aeron = Aeron.connect();
             final Publication publication = aeron.addPublication(Globals_Task1.CHANNEL, Globals_Task1.STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            unsafeBuffer.putStringUtf8(0, message);
            System.out.println("Sending message: " + message);

            while (publication.offer(unsafeBuffer) < 0)
            {
                idleStrategy.idle();
            }

            System.out.println("Reached end of publisher main method");
        }
    }
}
