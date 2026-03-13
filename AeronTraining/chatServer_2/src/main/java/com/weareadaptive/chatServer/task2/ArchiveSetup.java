package com.weareadaptive.chatServer.task2;

import io.aeron.archive.Archive;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;

import static com.weareadaptive.chatServer.task2.Globals.ARCHIVE_CONTROL_CHANNEL;
import static com.weareadaptive.chatServer.task2.Globals.ARCHIVE_REPLICATION_CHANNEL;

public class ArchiveSetup
{
    private static final String AERON_DIR_PATH = "/Volumes/DevShm/aeron-training";

    public static void main(final String[] args)
    {
        final Archive.Context archiveContext = new Archive.Context()
                .aeronDirectoryName(AERON_DIR_PATH)
                .controlChannel(ARCHIVE_CONTROL_CHANNEL)
                .replicationChannel(ARCHIVE_REPLICATION_CHANNEL)
                .deleteArchiveOnStart(true);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier();
             final Archive archive = Archive.launch(archiveContext))
        {
            System.out.println("Archive running");
            shutdownSignalBarrier.await();
            CloseHelper.close(archive);
        }
    }
}
