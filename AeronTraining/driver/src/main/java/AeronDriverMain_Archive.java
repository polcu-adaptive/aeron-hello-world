import io.aeron.driver.MediaDriver;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class AeronDriverMain_Archive
{
    private static final String AERON_DIR_PATH = "/Volumes/DevShm/aeron-training";

    public static void main(final String[] args)
    {
        System.out.println("Start Media Driver");

        final MediaDriver.Context mediaContext = new MediaDriver.Context()
                .aeronDirectoryName(AERON_DIR_PATH)
                .spiesSimulateConnection(true)
                .dirDeleteOnStart(true);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier();
             final MediaDriver mediaDriver = MediaDriver.launch(mediaContext))
        {
            shutdownSignalBarrier.await();
            CloseHelper.close(mediaDriver);
        }
    }
}
