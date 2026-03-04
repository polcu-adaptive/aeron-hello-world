package common;

import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class AeronDriverMain_Task2
{
    public static void main(final String[] args)
    {
        System.out.println("Start Media Driver");

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier();
             final MediaDriver mediaDriver = MediaDriver.launch())
        {
            shutdownSignalBarrier.await();
        }
    }
}
