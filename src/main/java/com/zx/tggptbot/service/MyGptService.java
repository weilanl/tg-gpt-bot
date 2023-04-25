package com.zx.tggptbot.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * @author zx
 * @date 2023/4/19 16:13
 */
public interface MyGptService {

    /**
     * handle message
     * @param update
     */
    void onMessageReceive(Update update);

    /**
     * creaete a text message by openai
     * @param username
     * @param chatId
     * @param prompt
     * @return
     */
    SendMessage createSendMessageByOpenai(String username, Long chatId, String prompt);

    /**
     * create a photo message by openai
     * @param chatId
     * @param prompt
     * @return
     */
    SendPhoto createSendPhotoByOpenai(Long chatId, String prompt);

    /**
     * send text message to user
     * @param sendMessage
     * @return
     */
    SendMessage sendTextMessage(SendMessage sendMessage);

    /**
     * send photo message to user
     * @param sendPhoto
     * @return
     */
    SendPhoto sendPhotoMessage(SendPhoto sendPhoto);

    String getBotUsername();
    String getBotToken();
}
