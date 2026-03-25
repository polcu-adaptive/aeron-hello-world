package com.weareadaptive.chatServer.task3.web;

import com.weareadaptive.chatServer.task3.cluster.ServerClusterClient;
import io.aeron.cluster.client.AeronCluster;
import io.vertx.core.Vertx;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;
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
        System.out.println("Deployed Auction House Web Gateway");

        System.out.println("Setup Cluster Client");
        final ServerClusterClient clusterClient = new ServerClusterClient(innerRingBuffer, outerRingBuffer);

        final String ingressEndpoints = ingressEndpoints(ENDPOINTS);
        final AeronCluster.Context aeronClusterContext = new AeronCluster.Context()
                .egressListener(clusterClient)
                .egressChannel(EGRESS_CHANNEL)
                .aeronDirectoryName(AERON_DIR_PATH)
                .ingressChannel("aeron:udp")
                .ingressEndpoints(ingressEndpoints);

        try (final AeronCluster aeronCluster = AeronCluster.connect(aeronClusterContext))
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

    public static String ingressEndpoints(final List<String> hostnames)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++)
        {
            sb.append(i).append('=');
            sb.append(hostnames.get(i)).append(':').append(9010);
            sb.append(',');
        }

        sb.setLength(sb.length() - 1);

        return sb.toString();
    }
}
