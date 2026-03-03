package publisher;

import common.AeronDriverMain;
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
             final Publication publication = aeron.addPublication(AeronDriverMain.CHANNEL, AeronDriverMain.STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            unsafeBuffer.putStringAscii(0, message);
            System.out.println("Sending message: " + message);

            while (publication.offer(unsafeBuffer) < 0)
            {
                idleStrategy.idle();
            }

            System.out.println("Reached end of publisher main method");
        }
    }
}
