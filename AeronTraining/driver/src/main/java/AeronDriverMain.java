import io.aeron.driver.MediaDriver;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class AeronDriverMain
{
    public static void main(final String[] args)
    {
        System.out.println("Start Media Driver");

        final String aeronDirPath = "/Volumes/DevShm/aeron-training";

        final MediaDriver.Context context = new MediaDriver.Context()
                .aeronDirectoryName(aeronDirPath)
                .dirDeleteOnStart(true);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier();
             final MediaDriver mediaDriver = MediaDriver.launch(context))
        {
            shutdownSignalBarrier.await();
            CloseHelper.close(mediaDriver);
        }
    }
}
