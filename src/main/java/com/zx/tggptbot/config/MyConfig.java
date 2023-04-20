package com.zx.tggptbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.theokanning.openai.service.OpenAiService.*;

/**
 * @author zx
 * @date 2023/4/19 16:17
 */
@Configuration
public class MyConfig {
    @Value("${openai.token}")
    private String openaiToken;
    @Value("${openai.proxy.enable}")
    private Boolean useProxy;
    @Value("${openai.proxy.host}")
    private String proxyHost;
    @Value("${openai.proxy.port}")
    private Integer proxyPort;

    @Bean("openAiService")
    public OpenAiService openAiService() {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient.Builder clientBuilder = defaultClient(this.openaiToken, Duration.ZERO)
                .newBuilder();
        if(useProxy) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(this.proxyHost, this.proxyPort));
            clientBuilder.proxy(proxy);
        }
        OkHttpClient client = clientBuilder.build();
        Retrofit retrofit = defaultRetrofit(client, mapper);
        OpenAiApi api = retrofit.create(OpenAiApi.class);
        return new OpenAiService(api);
    }

    @Bean("executorService")
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(10);
    }

}
