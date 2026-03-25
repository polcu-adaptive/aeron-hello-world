package com.weareadaptive.chatServer.task3;

import com.weareadaptive.chatServer.task3.agent.AgentErrorHandler;
import com.weareadaptive.chatServer.task3.agent.CliAgent;
import com.weareadaptive.chatServer.task3.cluster.ServerClusterClient;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.samples.cluster.ClusterConfig;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;
import java.util.List;

import static com.weareadaptive.chatServer.task3.Globals.*;

public class ChatClient
{
    public static void main(final String[] args)
    {
        final IdleStrategy idleStrategy = new BackoffIdleStrategy();

        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final OneToOneRingBuffer innerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));
        final OneToOneRingBuffer outerRingBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength)));

        System.out.println("Setup Cli Agent");
        final CliAgent cliAgent = new CliAgent(innerRingBuffer, outerRingBuffer);
        final AgentRunner cliAgentRunner = new AgentRunner(idleStrategy, new AgentErrorHandler(), null, cliAgent);

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
                .aeronDirectoryName(AERON_DIR_PATH_CLIENTS)
                .ingressChannel(INGRESS_CHANNEL)
                .ingressEndpoints(ingressEndpoints);

        System.out.println("Ingress endpoints: " + ingressEndpoints);

        try (final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverContext);
             final AeronCluster aeronCluster = AeronCluster.connect(aeronClusterContext))
        {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Closing CLI Chat Client");
                clusterClient.stop();
                CloseHelper.close(cliAgentRunner);
            }));

            AgentRunner.startOnThread(cliAgentRunner);

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
            sb.append(hostnames.get(i)).append(':').append(PORT_BASE + (PORT_OFFSET * i));
            sb.append(',');
        }

        sb.setLength(sb.length() - 1);

        return sb.toString();
    }
}
