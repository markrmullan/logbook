package org.zalando.logbook.netty;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.zalando.logbook.DefaultHttpLogFormatter;
import org.zalando.logbook.DefaultSink;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.TestStrategy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class LogbookClientHandlerVertxHttpServerTest {

    private final HttpLogWriter writer = mock(HttpLogWriter.class);
    private final Logbook logbook = Logbook.builder()
            .strategy(new TestStrategy())
            .sink(new DefaultSink(new DefaultHttpLogFormatter(), writer))
            .build();

    private final Vertx vertx = Vertx.vertx(
            new VertxOptions()
                    .setWorkerPoolSize(40));

    private final HttpServer server = vertx.createHttpServer(
            new HttpServerOptions()
                    .setLogActivity(true));

    @BeforeEach
    void start() {
        server.listen(8080);
    }

    @BeforeEach
    void defaultBehaviour() {
        when(writer.isActive()).thenCallRealMethod();
    }

    @AfterEach
    void stop() {
        server.close();
    }

}
