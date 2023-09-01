package org.example;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.*;
import io.reactivex.Flowable;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;


class ZoeAuthenticationInterceptor implements Interceptor {
    private final String token;

    ZoeAuthenticationInterceptor(String token) {
        Objects.requireNonNull(token, "Zoe token required");
        this.token = token;
    }

    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request().newBuilder().header("X-API-KEY", this.token).build();
        return chain.proceed(request);
    }
}

public class StreamExample {
    private static OpenAiService getZoeService() {
        String baseUrl = "https://openapi.zuoshouyisheng.com/gpt/v1/openai-compatible/";
        String ZoeToken = "ZOE-xxxxxxxxx";
        Duration timeout = Duration.ofSeconds(60);
        OkHttpClient client = (new OkHttpClient.Builder()).addInterceptor(new ZoeAuthenticationInterceptor(ZoeToken)).connectionPool(new ConnectionPool(5, 1L, TimeUnit.SECONDS)).readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        mapper.addMixIn(ChatFunction.class, ChatFunctionMixIn.class);
        mapper.addMixIn(ChatCompletionRequest.class, ChatCompletionRequestMixIn.class);
        mapper.addMixIn(ChatFunctionCall.class, ChatFunctionCallMixIn.class);

        Retrofit retrofit = (new Retrofit.Builder()).baseUrl(baseUrl).client(client).addConverterFactory(JacksonConverterFactory.create(mapper)).addCallAdapterFactory(RxJava2CallAdapterFactory.create()).build();

        OpenAiApi api = (OpenAiApi) retrofit.create(ZoeOpenAiApi.class);
        // ZoeOpenAiApi 将API PATH 全部改成了相对路径
        // 这是因为左医 openai-compatiable 的API PATH 包含前缀 /gpt/v1/openai-compatible/
        // Retrofit 拼接URL时会将前缀和相对路径拼接成完整路径
        // 如果 Api.class 中的方法路径是完整路径，那么最终拼接的URL会自动去掉前缀, 会导致无法请求到
        return new OpenAiService(api);
    }

    public static void main(String... args) {

        OpenAiService service = getZoeService();

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "你是一名全科医生");
        messages.add(systemMessage);

        ChatMessage firstMsg = new ChatMessage(ChatMessageRole.USER.value(), "肚子疼应该挂什么科室");
        messages.add(firstMsg);


        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("zoe-gpt")
                .messages(messages)
                .n(1)
                .maxTokens(256)
                .stream(true)
                .build();

        Flowable<ChatCompletionChunk> flowable = service.streamChatCompletion(chatCompletionRequest);

        ChatMessage chatMessage = service.mapStreamToAccumulator(flowable)
                .doOnNext(accumulator -> {
                    if (accumulator.getMessageChunk().getContent() != null) {
                        System.out.print(accumulator.getMessageChunk().getContent());
                    }
                })
                .doOnComplete(System.out::println)
                .lastElement()
                .blockingGet()
                .getAccumulatedMessage();

        System.out.println("[DONE]: 输出完整句子" + chatMessage.getContent());
        System.exit(0);
    }

}