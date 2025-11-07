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
