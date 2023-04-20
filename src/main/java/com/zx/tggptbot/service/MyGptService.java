package com.zx.tggptbot.service;

import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * @author zx
 * @date 2023/4/19 16:13
 */
public interface MyGptService {
    /**
     * chat
     * @param update
     */
    void chat(Update update);

    String getBotUsername();
    String getBotToken();
}
