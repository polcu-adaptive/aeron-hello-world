package publisher;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.Sender;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.*;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;

import static common.Globals_Task4.*;

public class PublisherAppMain_Task4
{
    public static void main(final String[] args)
    {
        System.out.println("Publisher starting up");

        final String message = "Hello World";

        final IdleStrategy idleStrategy = new BackoffIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();

        final MediaDriver.Context context = new MediaDriver.Context()
                .aeronDirectoryName(AERON_DIR_PATH)
                .dirDeleteOnStart(true);
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(AERON_DIR_PATH);
        try (final Aeron aeron = Aeron.connect(aeronContext);
             final Publication publication = aeron.addPublication(CHANNEL, STREAM_ID))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
            messageEncoder.message(message);

            final Sender sender = null;
            final AgentRunner agentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, sender);

            final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();

            System.out.println("Sending message: " + message);

            for (int i = 0; i < MESSAGES_COUNT; ++i)
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
