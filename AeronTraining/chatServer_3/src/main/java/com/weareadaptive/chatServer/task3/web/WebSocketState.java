package com.weareadaptive.chatServer.task3.web;

import io.aeron.Subscription;
import io.vertx.core.http.WebSocket;

public class WebSocketState
{
    final WebSocket webSocket;
    final Subscription replaySubscription;
    boolean isReplaying;

    public WebSocketState(final WebSocket webSocket, final Subscription replaySubscription, final boolean isReplaying)
    {
        this.webSocket = webSocket;
        this.replaySubscription = replaySubscription;
        this.isReplaying = isReplaying;
    }

    public WebSocket webSocket()
    {
        return webSocket;
    }

    public Subscription replaySubscription()
    {
        return replaySubscription;
    }

    public boolean isReplaying()
    {
        return isReplaying;
    }

    public void isReplaying(final boolean isReplaying)
    {
        this.isReplaying = isReplaying;
    }
}
