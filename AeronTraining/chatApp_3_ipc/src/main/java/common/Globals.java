package common;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;

public class Globals
{
    public static int STREAM_ID = 10;
    public static String AERON_DIR_PATH = "/Volumes/DevShm/aeron-training";

    public static String IPC_CHANNEL =  new ChannelUriStringBuilder().media(CommonContext.IPC_MEDIA).build();

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
}
