package publisher;

import common.Globals_Task3;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class PublisherAppMain_Task3
{
    public static void main(final String[] args)
    {
        System.out.println("Publisher starting up");

        final String message = "Hello World";

        final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        try (final Aeron aeron = Aeron.connect();
             final Publication publication = aeron.addPublication(Globals_Task3.CHANNEL, Globals_Task3.STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            unsafeBuffer.putStringUtf8(Long.BYTES, message);
            System.out.println("Sending message: " + message);

            for (int i = 0; i < Globals_Task3.MESSAGES_COUNT; ++i)
            {
                unsafeBuffer.putLong(0, System.nanoTime());
                while (publication.offer(unsafeBuffer) < 0)
                {
                    //System.out.println("Failed to send message. Retrying...");
                    idleStrategy.idle();
                }
                //System.out.println("Sent message " + i);
            }

            System.out.println("Reached end of publisher main method");
        }
    }
}
