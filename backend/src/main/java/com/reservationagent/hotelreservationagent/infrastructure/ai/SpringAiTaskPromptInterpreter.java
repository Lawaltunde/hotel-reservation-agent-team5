package com.reservationagent.hotelreservationagent.infrastructure.ai;

import com.reservationagent.hotelreservationagent.application.port.out.TaskPromptInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Reads a free-text booking request with Spring AI structured output. The model only labels what
 * the guest wrote; dates come back as unresolved expressions and Java turns them into dates.
 */
@Component
class SpringAiTaskPromptInterpreter implements TaskPromptInterpreter {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTaskPromptInterpreter.class);

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final NullableOutputConverter<InterpretedPrompt> outputConverter = new NullableOutputConverter<>(InterpretedPrompt.class);

    SpringAiTaskPromptInterpreter(ChatClient.Builder builder,
                                  @Value("classpath:prompts/task-intake.st") Resource systemPrompt) {
        this.chatClient = builder.build();
        this.systemPrompt = systemPrompt;
    }

    @Override
    public InterpretedPrompt interpret(String redactedPrompt) {
        InterpretedPrompt interpreted;
        try {
            interpreted = chatClient.prompt()
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .system(systemPrompt)
                    .user(redactedPrompt)
                    .call()
                    .entity(outputConverter);
        } catch (RuntimeException e) {
            throw new InterpreterUnavailableException("Reading the request failed: " + e.getMessage()
                    + " (check that OPENAI_API_KEY is set and OPENAI_CHAT_MODEL is a valid model)", e);
        }
        if (interpreted == null) {
            throw new InterpreterUnavailableException("The AI model returned no structured request", null);
        }
        log.debug("Interpreted prompt as {}", interpreted);
        return interpreted;
    }
}
