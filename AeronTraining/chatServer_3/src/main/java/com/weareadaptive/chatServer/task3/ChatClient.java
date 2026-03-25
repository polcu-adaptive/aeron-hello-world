package com.weareadaptive.chatServer.task3;

import com.weareadaptive.chatServer.task3.agent.AgentErrorHandler;
import com.weareadaptive.chatServer.task3.agent.CliAgent;
import com.weareadaptive.chatServer.task3.cluster.ServerClusterClient;
import io.aeron.cluster.client.AeronCluster;
import org.agrona.CloseHelper;
import org.agrona.concurrent.*;
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

        final String ingressEndpoints = ingressEndpoints(ENDPOINTS);
        final AeronCluster.Context aeronClusterContext = new AeronCluster.Context()
                .egressListener(clusterClient)
                .egressChannel(EGRESS_CHANNEL)
                .aeronDirectoryName(AERON_DIR_PATH)
                .ingressChannel("aeron:udp")
                .ingressEndpoints(ingressEndpoints);

        System.out.println("Ingress endpoints: " + ingressEndpoints);

        try (final ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier();
            AeronCluster aeronCluster = AeronCluster.connect(aeronClusterContext))
        {
            AgentRunner.startOnThread(cliAgentRunner);

            clusterClient.setAeronCluster(aeronCluster);
            clusterClient.start();

            shutdownSignalBarrier.await();

            CloseHelper.close(cliAgentRunner);
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
