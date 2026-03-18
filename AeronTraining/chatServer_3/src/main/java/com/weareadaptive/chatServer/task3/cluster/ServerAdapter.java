package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.cluster.service.ClientSession;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import task3.src.main.resources.AeronMessageDecoder;
import task3.src.main.resources.MessageHeaderDecoder;

public class ServerAdapter implements FragmentHandler
{
    private final ServerStateMachine server;

    private final AeronMessageDecoder messageDecoder;
    private final MessageHeaderDecoder messageHeaderDecoder;

    private ClientSession cLientSession;

    public ServerAdapter(final ServerStateMachine server)
    {
        this.server = server;

        this.messageDecoder = new AeronMessageDecoder();
        this.messageHeaderDecoder = new MessageHeaderDecoder();
    }

    @Override
    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        messageHeaderDecoder.wrap(buffer, offset);
        final int templateId = messageDecoder.sbeTemplateId();
        switch (templateId)
        {
            case AeronMessageDecoder.TEMPLATE_ID ->
            {

            }
        }

    }
}
