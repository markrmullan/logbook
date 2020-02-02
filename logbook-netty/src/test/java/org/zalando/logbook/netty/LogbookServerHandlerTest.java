package org.zalando.logbook.netty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.DefaultHttpLogFormatter;
import org.zalando.logbook.DefaultSink;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.StreamHttpLogWriter;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import static reactor.core.publisher.Mono.just;
import static reactor.netty.ByteBufFlux.fromString;

@Disabled
final class LogbookServerHandlerTest {

    private final Logbook logbook = Logbook.builder()
            .sink(new DefaultSink(
                    new DefaultHttpLogFormatter(),
                    new StreamHttpLogWriter(System.err)))
            .build();

    private final DisposableServer server = HttpServer.create()
            .route(routes -> routes
                    .route(request -> request.path().equals("/echo"), (request, response) ->
                            response.sendObject(request.receiveContent())))
            .tcpConfiguration(tcpServer ->
                    tcpServer.doOnConnection(connection ->
                            connection.addHandlerLast(new LogbookServerHandler(logbook))))
            .bindNow();


    @AfterEach
    void stop() {
        server.disposeNow();
    }

    @Test
    void logs() {
        final HttpClient client = HttpClient.create()
                .baseUrl("http://localhost:" + server.port());

        client.post()
                .uri("/echo")
                .send(fromString(just("Hello, world!")))
                .response()
                .block();


    }

}
