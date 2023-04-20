package com.zx.tggptbot.bot;

import com.zx.tggptbot.service.MyGptService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * @author zx
 * @date 2023/4/19 15:56
 */
@Slf4j
public class GPTChatBot extends TelegramLongPollingBot {
    private MyGptService myGptService;

    public GPTChatBot(MyGptService myGptService) {
        super(myGptService.getBotToken());
        this.myGptService = myGptService;
    }

    public GPTChatBot(DefaultBotOptions options, MyGptService myGptService) {
        super(options, myGptService.getBotToken());
        this.myGptService = myGptService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        myGptService.chat(update);
    }

    @Override
    public String getBotUsername() {
        return this.myGptService.getBotUsername();
    }


    @Override
    public void onRegister() {
        log.info("onRegister");
        super.onRegister();
    }


}
