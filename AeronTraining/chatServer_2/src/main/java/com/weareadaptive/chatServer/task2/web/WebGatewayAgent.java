package com.weareadaptive.chatServer.task2.web;

import com.weareadaptive.chatServer.task2.agent.AgentState;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import task3.src.main.resources.AeronMessageEncoder;
import task3.src.main.resources.MessageHeaderEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import static com.weareadaptive.chatServer.task2.Globals.*;

public class WebGatewayAgent implements Agent
{
    public static final String MESSAGE_PATH = "/message";

    private final Queue<String> messagesToPublish;
    private Aeron aeron;
    private Publication publication;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final AeronMessageEncoder messageEncoder = new AeronMessageEncoder();

    private UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
    private AgentState agentState = AgentState.INITIAL;

    public WebGatewayAgent(final Router router)
    {
        messagesToPublish = new ArrayDeque<>();

        router.post(MESSAGE_PATH).handler(this::handlePublishMessageRequest);
    }

//    public static void registerService(final Router router,
//                                       final AuthenticationService authenticationService,
//                                       final AuctionService auctionService)
//    {
//        final AuctionWebService webService = new AuctionWebService(auctionService);
//
//        router.post(MESSAGE_PATH).handler(webService::handleCreateAuctionRequest);
//    }

    private void handlePublishMessageRequest(final RoutingContext context)
    {
        System.out.println("Received publish message request");

        if (context.body().isEmpty())
        {
            System.err.println("The routing context body is empty");
        }

        final JsonObject jsonObject = context.body().asJsonObject();
        final PublishMessageRequest request = jsonObject.mapTo(PublishMessageRequest.class);

        messagesToPublish.add(request.message());
        context.json(JsonObject.mapFrom(request));
    }

    @Override
    public void onStart()
    {
        agentState = AgentState.STARTING;
        aeron = connectAeron();
        agentState = AgentState.CONNECTING;
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        switch (agentState)
        {
            case CONNECTING ->
            {
                if (publication == null)
                {
                    publication = aeron.addPublication(WEB_CHANNEL, STREAM_ID);
                }
                else
                {
                    agentState = AgentState.STEADY;
                }
            }
            case STEADY ->
            {
                if (publication.isConnected() && !messagesToPublish.isEmpty())
                {
                    workCount += (int)publishMessage();
                }
            }
            case STOPPED ->
            {
            }
        }

        return workCount;
    }

    private long publishMessage()
    {
        final String message = messagesToPublish.poll();

        messageEncoder.wrapAndApplyHeader(unsafeBuffer, 0, headerEncoder);
        messageEncoder.message(message);
        messageEncoder.inputTimestamp(System.nanoTime());

        final int length = headerEncoder.encodedLength() + messageEncoder.encodedLength();
        return publication.offer(unsafeBuffer, 0, length);
    }

    @Override
    public void onClose()
    {

    }

    @Override
    public String roleName()
    {
        return "web-gateway-agent";
    }

    private Aeron connectAeron()
    {
        final Aeron.Context aeronContext = new Aeron.Context().aeronDirectoryName(AERON_DIR_PATH);
        return Aeron.connect(aeronContext);
    }
}
