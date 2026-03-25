package com.weareadaptive.chatServer.task3;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;

import java.util.List;

public class Globals
{
    public static int PORT_BASE = 9000;

    public static List<String> ENDPOINTS = List.of("localhost");

    public static final String EGRESS_CHANNEL = "aeron:udp?endpoint=localhost:0";

    public static final String AERON_DIR_PATH = "/Volumes/DevShm/aeron-training";
}
