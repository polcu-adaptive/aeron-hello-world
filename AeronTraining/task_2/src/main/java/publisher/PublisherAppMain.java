package publisher;

import common.Globals;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class PublisherAppMain
{
    public static void main(final String[] args)
    {
        System.out.println("Publisher starting up");

        final String message = "Hello World";

        final IdleStrategy idleStrategy = new SleepingIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        try (final Aeron aeron = Aeron.connect();
             final Publication publication = aeron.addPublication(Globals.CHANNEL, Globals.STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            unsafeBuffer.putStringUtf8(Long.BYTES, message);

            for (int i = 0; i < Globals.MESSAGES_COUNT; ++i)
            {
                unsafeBuffer.putLong(0, System.nanoTime());
                while (publication.offer(unsafeBuffer) < 0)
                {
                    idleStrategy.idle();
                }
            }

            System.out.println("Reached end of publisher main method");
        }
    }
}
