package subscriber;

import common.Globals_Task3;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import task3.src.main.resources.AeronMessageDecoder;
import task3.src.main.resources.MessageHeaderDecoder;

public class SubscriberAppMain_Task3
{
    static long totalLatency = 0;

    public static void main(final String[] args)
    {
        System.out.println("Subscriber starting up");

        final IdleStrategy idleStrategy = new BusySpinIdleStrategy();

        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final AeronMessageDecoder messageDecoder = new AeronMessageDecoder();

        final Aeron.Context context = new Aeron.Context().aeronDirectoryName(Globals_Task3.AERON_DIR_PATH);
        try (
             final Aeron aeron = Aeron.connect(context);
             final Subscription subscription = aeron.addSubscription(Globals_Task3.CHANNEL, Globals_Task3.STREAM_ID))
        {
            final FragmentHandler handler = (buffer, offset, length, header) ->
            {
                // Decode SBE message
                headerDecoder.wrap(buffer, offset);

                final int actingBlockLength = headerDecoder.blockLength();
                final int actingVersion = headerDecoder.version();

                offset += headerDecoder.encodedLength();
                messageDecoder.wrap(buffer, offset, actingBlockLength, actingVersion);

                final long timestamp = messageDecoder.timestamp();

                // Compute latency
                final long latency = System.nanoTime() - timestamp;
                totalLatency += latency;
            };

            for (int i = 0; i < Globals_Task3.MESSAGES_COUNT; ++i)
            {
                while (subscription.poll(handler, 1) <= 0)
                {
                    idleStrategy.idle();
                }
            }

            final long averageLatencyNs = totalLatency / Globals_Task3.MESSAGES_COUNT;
            final double averageLatencyMs = averageLatencyNs / 1_000_000.0;
            System.out.println("Average latency for " + Globals_Task3.MESSAGES_COUNT + " messages: " + averageLatencyNs + " ns | " + averageLatencyMs + " ms");
            System.out.println("Reached end of subscriber main method");
        }
    }
}
