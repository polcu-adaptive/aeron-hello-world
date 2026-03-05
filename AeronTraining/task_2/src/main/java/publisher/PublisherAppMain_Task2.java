package publisher;

import common.Globals_Task2;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class PublisherAppMain_Task2
{
    public static void main(final String[] args)
    {
        System.out.println("Publisher starting up");

        final String message = "Hello World";

        final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        final Aeron.Context context = new Aeron.Context().aeronDirectoryName(Globals_Task2.AERON_DIR_PATH);
        try (final Aeron aeron = Aeron.connect(context);
             final Publication publication = aeron.addPublication(Globals_Task2.CHANNEL, Globals_Task2.STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            unsafeBuffer.putStringUtf8(Long.BYTES, message);
            System.out.println("Sending message: " + message);

            for (int i = 0; i < Globals_Task2.MESSAGES_COUNT; ++i)
            {
                unsafeBuffer.putLong(0, System.nanoTime());
                while (publication.offer(unsafeBuffer) < 0)
                {
                    //idleStrategy.idle();
                }
            }

            System.out.println("Reached end of publisher main method");
        }
    }
}
