import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class AeronDriverMain_Archive
{
    public static void main(final String[] args)
    {
        System.out.println("Start Media Driver");

        final String aeronDirPath = "/Volumes/DevShm/aeron-training";

        final MediaDriver.Context context = new MediaDriver.Context()
                .aeronDirectoryName(aeronDirPath)
                .spiesSimulateConnection(true)
                .dirDeleteOnStart(true);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier();
             final MediaDriver mediaDriver = MediaDriver.launch(context))
        {
            shutdownSignalBarrier.await();
        }
    }
}
