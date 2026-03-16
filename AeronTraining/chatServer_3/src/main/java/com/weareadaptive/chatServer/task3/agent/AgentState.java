package com.weareadaptive.chatServer.task3.agent;

public enum AgentState
{
    INITIAL,
    STARTING,
    CONNECTING,
    REPLAY_CHECK,
    REPLAYING,
    STEADY,
    STOPPED,
    CLOSED
}