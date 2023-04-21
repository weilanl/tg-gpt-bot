package com.zx.tggptbot.service.impl;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.image.Image;
import com.theokanning.openai.image.ImageResult;
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
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
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
    public void onMessageReceive(Update update) {
        Message message = update.getMessage();
        String text;
        if (message != null) {
            User from = message.getFrom();
            log.info("from {}:{}", from.getUserName(), message.getText());
            text = message.getText();
        } else {
            log.info("on update:message is null");
            sendTextMessage(createSendMessageByOpenai(update.getMessage().getChatId(), "you cannot send a empty message"));
            return;
        }
        SendMessage defaultErrorMessage = new SendMessage();
        defaultErrorMessage.setChatId(update.getMessage().getChatId());
        defaultErrorMessage.setText("sorry, something wrong, please try again later");
        if (text.startsWith("/image")) {
            CompletableFuture.supplyAsync(() -> createSendPhotoByOpenai(update.getMessage().getChatId(), text.replace("/image", "")), executorService)
                    .thenApply(this::sendPhotoMessage)
                    .whenComplete((s, e) -> {
                        if (e == null) {
                            log.info("create chat success:{}", s);
                        }
                    })
                    .exceptionally(e -> {
                        log.error("create chat failed", e);
                        sendTextMessage(defaultErrorMessage);
                        return null;
                    });
        } else {
            CompletableFuture.supplyAsync(() -> createSendMessageByOpenai(update.getMessage().getChatId(), text), executorService)
                    .thenApply(this::sendTextMessage)
                    .whenComplete((s, e) -> {
                        if (e == null) {
                            log.info("create chat success:{}", s);
                        }
                    })
                    .exceptionally(e -> {
                        log.error("create chat failed", e);
                        sendTextMessage(defaultErrorMessage);
                        return null;
                    });
        }
    }

    public SendMessage createSendMessageByOpenai(Long chatId, String prompt) {
        if(prompt == null || prompt.trim().isEmpty()){
            return null;
        }
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage("user", prompt));
        //create chat completion request
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .messages(chatMessages)
                .model(this.gptModel)
                .maxTokens(this.maxTokens)
                .build();
        //send request
        List<ChatCompletionChoice> completionChoiceList = openAiService.createChatCompletion(completionRequest).getChoices();
        StringBuilder stringBuffer = new StringBuilder();
        for (ChatCompletionChoice completionChoice : completionChoiceList) {
            String content = completionChoice.getMessage().getContent();
            stringBuffer.append(content);
        }
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(stringBuffer.toString());
        return sendMessage;
    }

    public SendPhoto createSendPhotoByOpenai(Long chatId, String prompt) {
        if(prompt == null || prompt.trim().isEmpty()){
            return null;
        }
        //create image request
        CreateImageRequest createImageRequest = CreateImageRequest.builder()
                .prompt(prompt)
                .build();
        //send request
        ImageResult imageResult = openAiService.createImage(createImageRequest);
        if (imageResult != null) {
            String imageUrl = null;
            for (Image image : imageResult.getData()) {
                imageUrl = image.getUrl();
                //only receive the first image TODO support multi image
                break;
            }
            if (imageUrl == null) {
                return null;
            }
            //create sendPhoto
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            InputFile inputFile = new InputFile();
            try {
                //download image
                log.info("download image from {}", imageUrl);
                URL url = new URL(imageUrl);
                URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                InputStream stream = conn.getInputStream();
                inputFile.setMedia(stream, System.currentTimeMillis() + ".png");
                sendPhoto.setPhoto(inputFile);
                log.info("download success");
                return sendPhoto;
            } catch (IOException e) {
                log.error("download failed:{}", e.getLocalizedMessage(), e);
                return null;
            }
        } else {
            return null;
        }
    }

    public SendMessage sendTextMessage(SendMessage sendMessage) {
        if (sendMessage == null) {
            throw new RuntimeException("send message is null");
        }
        try {
            this.gptChatBot.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("text message send failed:{}", e.getLocalizedMessage(), e);
        }
        return sendMessage;
    }

    public SendPhoto sendPhotoMessage(SendPhoto sendPhoto) {
        if (sendPhoto == null) {
            throw new RuntimeException("send photo is null");
        }
        try {
            this.gptChatBot.execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("text message send failed:{}", e.getLocalizedMessage(), e);
        }
        return sendPhoto;
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
