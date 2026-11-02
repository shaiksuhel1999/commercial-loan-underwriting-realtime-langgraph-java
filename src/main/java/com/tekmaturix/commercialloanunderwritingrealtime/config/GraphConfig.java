package com.tekmaturix.commercialloanunderwritingrealtime.config;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tekmaturix.commercialloanunderwritingrealtime.model.UnderwritingState;
import com.tekmaturix.commercialloanunderwritingrealtime.service.UnderwritingStateGraph;

/**
 * Wires the graph into Spring as singletons.
 *
 * Why beans instead of building a graph per request?
 *
 *  - The checkpoint saver MUST be one shared instance. It is the memory that
 *    holds a paused application between the "start" HTTP call and the later
 *    "resume" HTTP call. A per-request saver would be empty on resume.
 *
 *  - The CompiledGraph is immutable and safe to reuse, so we compile it once at
 *    startup and inject the ChatClient the LLM nodes need.
 */
@Configuration
public class GraphConfig {

    /**
     * In-memory checkpoint store. Perfect for learning / a single instance.
     * For production swap in a durable saver (FileSystemSaver, or a Redis/JDBC
     * backed one) so paused applications survive restarts and scale out.
     */
    @Bean
    public BaseCheckpointSaver checkpointSaver() {
        return new MemorySaver();
    }

    @Bean
    public CompiledGraph<UnderwritingState> underwritingGraph(
            ChatClient chatClient,
            BaseCheckpointSaver checkpointSaver) throws Exception {

        return UnderwritingStateGraph.build(chatClient, checkpointSaver);
    }
}
