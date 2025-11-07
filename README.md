# MCP
## MCP-Server
### 如何查看其运行逻辑
***
public class ClientSse {
    static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            System.out.println("[REQ] " + req.method() + " " + req.url());
            req.headers().forEach((name, values) ->
                    System.out.println("[REQ] " + name + ": " + String.join(",", values)));
            // 如需在应用层打印请求体，最好在发起请求处自己打印（比如 bodyValue(payload) 前打印 payload），
            // 或使用 wiretap 捕获底层字节。
            return Mono.just(req);
        });
    }
    // 响应日志（状态码、头）+ 缓存并重放响应体，避免被消费后下游拿不到。
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();
    static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            System.out.println("[RESP] status=" + resp.statusCode());
            resp.headers().asHttpHeaders()
                    .forEach((k, v) -> System.out.println("[RESP] " + k + ": " + String.join(",", v)));
            Flux<DataBuffer> replayableBody = resp.body(BodyExtractors.toDataBuffers())
                    .map(db -> {
                        byte[] bytes = new byte[db.readableByteCount()];
                        db.read(bytes);
                        DataBufferUtils.release(db);
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        System.out.println("[RESP-BODY] " + text);
                        return BUFFER_FACTORY.wrap(bytes);
                    });

            // 保留原始响应的状态、头、cookies、编解码策略
            ClientResponse rebuilt = ClientResponse.from(resp)
                    .body(replayableBody)
                    .build();
            return Mono.just(rebuilt);
        });
    }
    public static WebClient.Builder build() {
        // 启用 wiretap：会打印底层 HTTP 报文（包含 body）
        HttpClient httpClient = HttpClient.create()
                .wiretap("reactor.netty.http.client", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);
        return WebClient.builder()
                .baseUrl("http://localhost:8080")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse())
                ;
    }
    public static void main(String[] args) {
        var transport = new WebFluxSseClientTransport(build());
        SampleClient sampleClient = new SampleClient(transport);
        sampleClient.run();
        System.out.println("WebClient with request/response logging created successfully!");
    }
}
***

### 可以查看MCP协议
***
第一步： sse-endpoint  Web 传输的自定义 SSE 端点路径/sse
http://localhost:8080/sse
只要第二步骤一触发，就会收到消息
***
第二步： sse-message-endpoint  客户端用于发送消息的 Web 传输自定义 SSE 消息端点路径 /mcp/message
curl --location --request POST 'http://localhost:8080/mcp/message?sessionId=c8392ff6-79ef-466e-86ef-5a96e93ca695' \
--header 'Content-Type: application/json' \
--data-raw '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "id": "e65613f0-0",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {

        },
        "clientInfo": {
            "name": "JavaSDK MCP Client",
            "version": "1.0.0"
        }
    }
}'
这是触发后，第一步收到的消息
data:{"jsonrpc":"2.0","id":"e65613f0-0","result":{"protocolVersion":"2024-11-05","capabilities":{"logging":{},"tools":{"listChanged":true}},"serverInfo":{"name":"MCP Demo Weather Server","version":"1.0.0"}}}
***
第三步：
curl --location --request POST 'http://localhost:8080/mcp/message?sessionId=c8392ff6-79ef-466e-86ef-5a96e93ca695' \
--header 'Content-Type: application/json' \
--data-raw '{"jsonrpc":"2.0","method":"notifications/initialized"}'

这是触发后，第一步收到的消息
event:message
data:{"jsonrpc":"2.0","id":"e65613f0-1","error":{"code":-32601,"message":"Method not found: notifications/initialized"}}
***
第四步： 方法初始化
curl --location --request POST 'http://localhost:8080/mcp/message?sessionId=c8392ff6-79ef-466e-86ef-5a96e93ca695' \
--header 'Content-Type: application/json' \
--data-raw '{"jsonrpc":"2.0","method":"notifications/initialized"}'

这是触发后，第一步收到的消息
event:message
data:{"jsonrpc":"2.0","id":"e65613f0-1","result":{}}
***
第五步： 获取工具
curl --location --request POST 'http://localhost:8080/mcp/message?sessionId=c8392ff6-79ef-466e-86ef-5a96e93ca695' \
--header 'Content-Type: application/json' \
--data-raw '{"jsonrpc":"2.0","method":"tools/list","id":"e65613f0-1"}'
这是触发后，第一步收到的消息
event:message
data:{"jsonrpc":"2.0","id":"e65613f0-1","result":{"tools":[{"name":"getAlerts","description":"Get weather alerts for a US state. Input is Two-letter US state code (e.g. CA, NY)","inputSchema":{"type":"object","properties":{"state":{"type":"string"}},"required":["state"],"additionalProperties":false}},{"name":"getWeatherForecastByLocation","description":"Get weather forecast for a specific latitude/longitude","inputSchema":{"type":"object","properties":{"latitude":{"type":"number","format":"double"},"longitude":{"type":"number","format":"double"}},"required":["latitude","longitude"],"additionalProperties":false}}]}}

***
第六步： 获取工具远程调用
curl --location --request POST 'http://localhost:8080/mcp/message?sessionId=c8392ff6-79ef-466e-86ef-5a96e93ca695' \
--header 'Content-Type: application/json' \
--data-raw '{"jsonrpc":"2.0","method":"tools/call","id":"e65613f0-3",
"params":{"name":"getWeatherForecastByLocation","arguments":{"longitude":"-122.3321","latitude":"47.6062"}}}'

这是触发后，第一步收到的消息
event:message
data:{"jsonrpc":"2.0","id":"e65613f0-3","result":{"content":[{"type":"text","text":"\"Tonight:\\nTemperature: 47 F\\nWind: 12 to 16 mph SSW\\nForecast: Rain likely. Mostly cloudy. Low around 47, with temperatures rising to around 49 overnight. South southwest wind 12 to 16 mph, with gusts as high as 25 mph. Chance of precipitation is 70%. New rainfall amounts between a tenth and quarter of an inch possible.\\nFriday:\\nTemperature: 54 F\\nWind: 5 to 10 mph SSW\\nForecast: Rain likely. Partly sunny. High near 54, with temperatures falling to around 52 in the afternoon. South southwest wind 5 to 10 mph. Chance of precipitation is 60%. New rainfall amounts less than a tenth of an inch possible.\\nFriday Night:\\nTemperature: 42 F\\nWind: 5 mph SE\\nForecast: A chance of rain before 10pm. Mostly cloudy, with a low around 42. Southeast wind around 5 mph. Chance of precipitation is 30%.\\nSaturday:\\nTemperature: 56 F\\nWind: 5 mph NE\\nForecast: Partly sunny, with a high near 56. Northeast wind around 5 mph.\\nSaturday Night:\\nTemperature: 46 F\\nWind: 5 mph ENE\\nForecast: Mostly cloudy, with a low around 46. East northeast wind around 5 mph.\\nSunday:\\nTemperature: 61 F\\nWind: 5 mph SE\\nForecast: Partly sunny, with a high near 61.\\nSunday Night:\\nTemperature: 50 F\\nWind: 7 mph S\\nForecast: Rain likely after 10pm. Mostly cloudy, with a low around 50.\\nMonday:\\nTemperature: 57 F\\nWind: 6 mph S\\nForecast: Rain likely. Mostly cloudy, with a high near 57.\\nMonday Night:\\nTemperature: 47 F\\nWind: 6 mph S\\nForecast: Rain likely. Mostly cloudy, with a low around 47.\\nVeterans Day:\\nTemperature: 54 F\\nWind: 5 mph WSW\\nForecast: A chance of rain. Mostly cloudy, with a high near 54.\\nTuesday Night:\\nTemperature: 45 F\\nWind: 3 mph NNE\\nForecast: A chance of rain. Mostly cloudy, with a low around 45.\\nWednesday:\\nTemperature: 55 F\\nWind: 5 mph NNE\\nForecast: Rain likely. Mostly cloudy, with a high near 55.\\nWednesday Night:\\nTemperature: 48 F\\nWind: 6 mph ENE\\nForecast: Rain. Mostly cloudy, with a low around 48.\\nThursday:\\nTemperature: 56 F\\nWind: 6 mph S\\nForecast: Rain before 5pm. Cloudy, with a high near 56.\\n\""}],"isError":false}}

***
第其步： 结束
 client.closeGracefully();


***
