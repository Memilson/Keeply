package com.keeply.agent.core;

@FunctionalInterface
public interface ChunkConsumer {
    void accept(ChunkData chunk) throws Exception;
}
