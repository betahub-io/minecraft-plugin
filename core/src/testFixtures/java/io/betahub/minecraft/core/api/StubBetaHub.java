package io.betahub.minecraft.core.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal fake BetaHub: replies with queued responses and records what it received. */
public final class StubBetaHub implements AutoCloseable {

    public record Reply(int status, String body, long delayMillis) {
        public static Reply of(int status, String body) {
            return new Reply(status, body, 0);
        }
    }

    public record Received(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().get(0))
                    .findFirst().orElse(null);
        }
    }

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public StubBetaHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public StubBetaHub reply(int status, String body) {
        replies.add(Reply.of(status, body));
        return this;
    }

    public StubBetaHub reply(Reply reply) {
        replies.add(reply);
        return this;
    }

    public List<Received> received() {
        return received;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                Map.copyOf(exchange.getRequestHeaders()), body));
        Reply reply = replies.poll();
        if (reply == null) {
            reply = Reply.of(500, "{\"error\":\"stub has no reply queued\"}");
        }
        try {
            Thread.sleep(reply.delayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            // client gave up (timeout test)
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
