package publisher;

import common.Globals_Task4;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;

public class PublisherAppMain_Task4
{
    public static void main(final String[] args)
    {
        System.out.println("Publisher starting up");

        final String message = "Hello World";

        final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();

        final Aeron.Context context = new Aeron.Context().aeronDirectoryName(Globals_Task4.AERON_DIR_PATH);
        try (final Aeron aeron = Aeron.connect(context);
             final Publication publication = aeron.addPublication(Globals_Task4.CHANNEL, Globals_Task4.STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
            messageEncoder.message(message);

            final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();

            System.out.println("Sending message: " + message);

            for (int i = 0; i < Globals_Task4.MESSAGES_COUNT; ++i)
            {
                messageEncoder.timestamp(System.nanoTime());
                while (publication.offer(unsafeBuffer, 0, length) < 0)
                {
                    idleStrategy.idle();
                }
            }

            System.out.println("Reached end of publisher main method");
        }
    }
}
