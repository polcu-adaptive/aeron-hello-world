package com.weareadaptive.chatServer.task3.web;

import com.weareadaptive.chatServer.task3.cluster.ServerClusterClient;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.samples.cluster.ClusterConfig;
import io.vertx.core.Vertx;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;
import java.util.List;

import static com.weareadaptive.chatServer.task3.Globals.*;

public class WebGateway
{
    private static final int CONFIGURED_PORT = 8080;

    public static void main(final String[] args)
    {
        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final OneToOneRingBuffer innerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));
        final OneToOneRingBuffer outerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));

        final Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new WebGatewayVerticle(CONFIGURED_PORT, innerRingBuffer, outerRingBuffer));

        System.out.println("Setup Cluster Client");
        final ServerClusterClient clusterClient = new ServerClusterClient(innerRingBuffer, outerRingBuffer);

        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                .aeronDirectoryName(AERON_DIR_PATH_CLIENTS)
                .dirDeleteOnStart(true);

        final String ingressEndpoints = ClusterConfig.ingressEndpoints(
                ENDPOINTS, PORT_BASE, ClusterConfig.CLIENT_FACING_PORT_OFFSET);
        final AeronCluster.Context aeronClusterContext = new AeronCluster.Context()
                .egressListener(clusterClient)
                .egressChannel(EGRESS_CHANNEL)
                .egressChannel(EGRESS_CHANNEL)
                .aeronDirectoryName(AERON_DIR_PATH_CLIENTS)
                .ingressChannel("aeron:udp")
                .ingressEndpoints(ingressEndpoints);

        try (final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverContext);
             final AeronCluster aeronCluster = AeronCluster.connect(aeronClusterContext))
        {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Closing Web Gateway");
                clusterClient.stop();
                CloseHelper.close(vertx::close);
            }));

            clusterClient.setAeronCluster(aeronCluster);
            clusterClient.run();
        }
    }
}
