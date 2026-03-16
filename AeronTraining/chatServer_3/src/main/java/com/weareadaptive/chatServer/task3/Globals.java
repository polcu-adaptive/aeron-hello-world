package com.weareadaptive.chatServer.task3;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;

public class Globals
{
    public static int STREAM_ID = 10;
    public static String AERON_DIR_PATH = "/Volumes/DevShm/aeron-training";

    public static String CHAT_INBOUND_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .endpoint("localhost:8999")
        .alias("ChatInboundChannel")
        .build();

    public static String CHAT_OUTBOUND_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .controlMode(CommonContext.MDC_CONTROL_MODE_DYNAMIC)
        .controlEndpoint("localhost:9000")
        .sessionId(50)
        .alias("ChatOutboundChannel")
        .build();

    public static final String ARCHIVE_CONTROL_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .endpoint("localhost:8700")
        .alias("ArchiveControlChannel")
        .build();

    public static final String ARCHIVE_REPLICATION_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .endpoint("localhost:8710")
        .alias("ArchiveReplicationChannel")
        .build();

    public static final String ARCHIVE_CONTROL_RESPONSE_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .endpoint("localhost:0")
        .build();

    public static final String REPLAY_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .endpoint("localhost:8005")
        .build();

    public static final String WEB_CHANNEL = new ChannelUriStringBuilder()
        .media(CommonContext.UDP_MEDIA)
        .endpoint("localhost:8666")
        .build();
}
