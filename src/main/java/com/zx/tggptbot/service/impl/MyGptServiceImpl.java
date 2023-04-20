package com.zx.tggptbot.service.impl;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.service.OpenAiService;
import com.zx.tggptbot.bot.GPTChatBot;
import com.zx.tggptbot.service.MyGptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author zx
 * @date 2023/4/19 17:10
 */
@Service
@Slf4j
public class MyGptServiceImpl implements MyGptService {
    @Value("${telegram.bot.botToken}")
    private String botToken;
    @Value("${telegram.bot.botName}")
    private String botUsername;
    @Value("${telegram.proxy.enable}")
    private Boolean useProxy;
    @Value("${telegram.proxy.host}")
    private String proxyHost;
    @Value("${telegram.proxy.port}")
    private Integer proxyPort;
    @Value("${openai.gpt.model}")
    private String gptModel;
    @Value("${openai.gpt.maxTokens}")
    private Integer maxTokens;

    @Autowired
    private OpenAiService openAiService;
    @Autowired
    private ExecutorService executorService;
    private GPTChatBot gptChatBot;

    /**
     * register a bot
     */
    @PostConstruct
    public void registerBot() {
        try {
            if (this.useProxy) {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                DefaultBotOptions botOptions = new DefaultBotOptions();
                botOptions.setProxyHost(this.proxyHost);
                botOptions.setProxyPort(this.proxyPort);
                botOptions.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
                gptChatBot = new GPTChatBot(botOptions, this);
            } else {
                gptChatBot = new GPTChatBot(this);
            }
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(gptChatBot);
        } catch (TelegramApiException e) {
            log.error("bot register failed", e);
        }
    }

    @Override
    public void chat(Update update) {
        CompletableFuture.supplyAsync(() -> {
            List<ChatMessage> chatMessages = new ArrayList<>();
            //receive message
            Message message = update.getMessage();
            if (message != null) {
                User from = message.getFrom();
                log.info("from {}:{}", from.getUserName(), message.getText());
                String text = message.getText();
                chatMessages.add(new ChatMessage("user", text));
            } else {
                log.info("on update:message is null");
                return sendMessage(update.getMessage().getChatId(), "you cannot send a empty message");
            }
            //build a request
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(chatMessages)
                    .model(this.gptModel)
                    .maxTokens(this.maxTokens)
                    .build();
            List<ChatCompletionChoice> completionChoiceList = openAiService.createChatCompletion(completionRequest).getChoices();
            StringBuilder stringBuffer = new StringBuilder();
            for (ChatCompletionChoice completionChoice : completionChoiceList) {
                String content = completionChoice.getMessage().getContent();
                stringBuffer.append(content);
            }
            //send message
            return sendMessage(update.getMessage().getChatId(), stringBuffer.toString());
        }, executorService)
        .whenComplete((s, e) -> log.info("create chat success:{}", s))
        .exceptionally(e -> {
            log.error("create chat failed", e);
            return sendMessage(update.getMessage().getChatId(), "sorry, something wrong, please try again later");
        });
    }

    /**
     * send message to user
     * @param chatId chat id
     * @param text message
     * @return
     */
    private SendMessage sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        try {
            this.gptChatBot.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            log.error("message send failed:{}", e.getLocalizedMessage());
        }
        return sendMessage;
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }

}
