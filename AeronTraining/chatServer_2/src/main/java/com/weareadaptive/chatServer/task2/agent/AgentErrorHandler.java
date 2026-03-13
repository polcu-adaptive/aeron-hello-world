package com.weareadaptive.chatServer.task2.agent;

import org.agrona.ErrorHandler;

public class AgentErrorHandler implements ErrorHandler
{
    @Override
    public void onError(final Throwable throwable)
    {
        System.out.println("Error in agent: " + throwable.getMessage());
    }
}
