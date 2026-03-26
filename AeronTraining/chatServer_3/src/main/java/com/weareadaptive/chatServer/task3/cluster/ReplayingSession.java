package com.weareadaptive.chatServer.task3.cluster;

import io.aeron.cluster.service.ClientSession;

public class ReplayingSession
{
    private final ClientSession clientSession;
    private int currentIndex;

    public ReplayingSession(final ClientSession clientSession)
    {
        this.clientSession = clientSession;
        this.currentIndex = 0;
    }

    public ClientSession clientSession()
    {
        return clientSession;
    }

    public int currentIndex()
    {
        return currentIndex;
    }

    public void currentIndex(final int currentIndex)
    {
        this.currentIndex = currentIndex;
    }
}
