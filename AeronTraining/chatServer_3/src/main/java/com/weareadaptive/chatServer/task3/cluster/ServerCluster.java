package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.ShutdownSignalBarrier;

import static com.weareadaptive.chatServer.task3.Globals.*;

public class ServerCluster
{
    private static final String INGRESS_CHANNEL = "aeron:udp?endpoint=localhost:9010|term-length=64k";

    public static void main(final String[] args)
    {
        final ClusterConfig clusterConfig = ClusterConfig.create(0, ENDPOINTS, ENDPOINTS, PORT_BASE, new ServerClusteredService());

        clusterConfig.mediaDriverContext().errorHandler(errorHandler("Media Driver"));
        clusterConfig.archiveContext().errorHandler(errorHandler("Archive"));
        clusterConfig.aeronArchiveContext().errorHandler(errorHandler("Aeron Archive"));
        clusterConfig.consensusModuleContext().errorHandler(errorHandler("Consensus Module"));
        clusterConfig.clusteredServiceContext().errorHandler(errorHandler("Clustered Service"));

        clusterConfig.consensusModuleContext().ingressChannel(INGRESS_CHANNEL);
        clusterConfig.consensusModuleContext().egressChannel(EGRESS_CHANNEL);
        clusterConfig.consensusModuleContext().deleteDirOnStart(false); //true to always start fresh

        clusterConfig.aeronDirectoryName(AERON_DIR_PATH);

        try (final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
             final ClusteredMediaDriver clusteredMediaDriver = ClusteredMediaDriver.launch(
                     clusterConfig.mediaDriverContext().terminationHook(barrier::signalAll),
                     clusterConfig.archiveContext(),
                     clusterConfig.consensusModuleContext().terminationHook(barrier::signalAll));
             final ClusteredServiceContainer clusteredServiceContainer = ClusteredServiceContainer.launch(
                clusterConfig.clusteredServiceContext().terminationHook(barrier::signalAll)))
        {
            System.out.println("Starting Cluster Node...");
            barrier.await();
            System.out.println("Cluster node terminated");
        }
    }

    private static ErrorHandler errorHandler(final String context)
    {
        return (Throwable throwable) ->
        {
            System.err.println(context);
            throwable.printStackTrace(System.err);
        };
    }
}
