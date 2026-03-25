package com.weareadaptive.chatServer.task3;

import java.util.List;

public class Globals
{
    public static int PORT_BASE = 9000;

    public static int PORT_OFFSET = 10;

    public static List<String> ENDPOINTS = List.of("localhost", "localhost", "localhost");

    public static final String EGRESS_CHANNEL = "aeron:udp?endpoint=localhost:0";

    public static final String INGRESS_CHANNEL = "aeron:udp?term-length=64k";

    public static final String AERON_DIR_PATH = "/Volumes/DevShm/aeron-training";

    public static final String AERON_DIR_PATH_CLIENTS = "/Volumes/DevShm/aeron-training/clients";
}
