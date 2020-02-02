package org.zalando.logbook.netty;

import com.github.restdriver.clientdriver.ClientDriver;
import com.github.restdriver.clientdriver.ClientDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.DefaultHttpLogFormatter;
import org.zalando.logbook.DefaultSink;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.TestStrategy;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;

import static com.github.restdriver.clientdriver.ClientDriverRequest.Method.GET;
import static com.github.restdriver.clientdriver.ClientDriverRequest.Method.HEAD;
import static com.github.restdriver.clientdriver.ClientDriverRequest.Method.POST;
import static com.github.restdriver.clientdriver.RestClientDriver.giveEmptyResponse;
import static com.github.restdriver.clientdriver.RestClientDriver.giveResponse;
import static com.github.restdriver.clientdriver.RestClientDriver.onRequestTo;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class LogbookClientHandlerSpringWebClientTest {

    private final HttpLogWriter writer = mock(HttpLogWriter.class);
    private final Logbook logbook = Logbook.builder()
            .strategy(new TestStrategy())
            .sink(new DefaultSink(new DefaultHttpLogFormatter(), writer))
            .build();

    private final ClientDriver driver = new ClientDriverFactory().createClientDriver();

    private final WebClient client = WebClient.builder()
            .baseUrl(driver.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                    .tcpConfiguration(tcpClient ->
                            tcpClient.doOnConnected(connection ->
                                    connection.addHandlerLast(new LogbookClientHandler(logbook))))))
            .build();

    @BeforeEach
    void defaultBehaviour() {
        when(writer.isActive()).thenCallRealMethod();
    }

    @Test
    void shouldLogRequestWithoutBody() throws IOException {
        driver.addExpectation(onRequestTo("/").withMethod(GET), giveEmptyResponse());

        sendAndReceive();

        final String message = captureRequest();

        assertThat(message, startsWith("Outgoing Request:"));
        assertThat(message, containsString(format("GET http://localhost:%d/ HTTP/1.1", driver.getPort())));
        assertThat(message, not(containsStringIgnoringCase("Content-Type")));
        assertThat(message, not(containsString("Hello, world!")));
    }

    @Test
    void shouldLogRequestWithBody() throws IOException {
        driver.addExpectation(onRequestTo("/").withMethod(POST)
                .withBody("Hello, world!", "text/plain"), giveEmptyResponse());

        client.post()
                .bodyValue("Hello, world!")
                .exchange()
                .block();

        final String message = captureRequest();

        assertThat(message, startsWith("Outgoing Request:"));
        assertThat(message, containsString(format("POST http://localhost:%d/ HTTP/1.1", driver.getPort())));
        assertThat(message, containsStringIgnoringCase("Content-Type: text/plain"));
        assertThat(message, containsString("Hello, world!"));
    }

    private String captureRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(writer).write(any(Precorrelation.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldNotLogRequestIfInactive() throws IOException {
        when(writer.isActive()).thenReturn(false);

        driver.addExpectation(onRequestTo("/").withMethod(GET), giveEmptyResponse());

        sendAndReceive();

        verify(writer, never()).write(any(Precorrelation.class), any());
    }

    @Test
    void shouldLogResponseForNotModified() throws IOException {
        driver.addExpectation(onRequestTo("/").withMethod(GET),
                giveEmptyResponse().withStatus(304));

        sendAndReceive();

        final String message = captureResponse();

        assertThat(message, startsWith("Incoming Response:"));
        assertThat(message, containsString("HTTP/1.1 304 Not Modified"));
        assertThat(message, not(containsStringIgnoringCase("Content-Type")));
        assertThat(message, not(containsString("Hello, world!")));
    }

    @Test
    void shouldLogResponseForHeadRequest() throws IOException {
        driver.addExpectation(onRequestTo("/").withMethod(HEAD), giveEmptyResponse());

        client.head()
                .retrieve()
                .bodyToMono(Void.class)
                .block();

        final String message = captureResponse();

        assertThat(message, startsWith("Incoming Response:"));
        assertThat(message, containsString("HTTP/1.1 204 No Content"));
        assertThat(message, not(containsStringIgnoringCase("Content-Type")));
        assertThat(message, not(containsString("Hello, world!")));
    }

    @Test
    void shouldLogResponseWithoutBody() throws IOException {
        driver.addExpectation(onRequestTo("/").withMethod(GET), giveEmptyResponse());

        sendAndReceive();

        final String message = captureResponse();

        assertThat(message, startsWith("Incoming Response:"));
        assertThat(message, containsString("HTTP/1.1 204 No Content"));
        assertThat(message, not(containsStringIgnoringCase("Content-Type")));
        assertThat(message, not(containsString("Hello, world!")));
    }

    @Test
    void shouldLogResponseWithBody() throws IOException {
        driver.addExpectation(onRequestTo("/").withMethod(GET),
                giveResponse("Hello, world!", "text/plain"));

        final String response = client.get()
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(response, is("Hello, world!"));

        final String message = captureResponse();

        assertThat(message, startsWith("Incoming Response:"));
        assertThat(message, containsString("HTTP/1.1 200 OK"));
        assertThat(message, containsStringIgnoringCase("Content-Type: text/plain"));
        assertThat(message, containsString("Hello, world!"));
    }

    private String captureResponse() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(writer).write(any(Correlation.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldNotLogResponseIfInactive() throws IOException {
        when(writer.isActive()).thenReturn(false);

        driver.addExpectation(onRequestTo("/").withMethod(GET), giveEmptyResponse());

        sendAndReceive();

        verify(writer, never()).write(any(Correlation.class), any());
    }

    @Test
    void shouldIgnoreBodies() throws IOException {
        driver.addExpectation(
                onRequestTo("/")
                        .withMethod(POST)
                        .withBody("Hello, world!", "text/plain"),
                giveResponse("Hello, world!", "text/plain"));

        final String response = client.post()
                .header("Ignore", "true")
                .bodyValue("Hello, world!")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(response, is("Hello, world!"));

        {
            final String message = captureRequest();

            assertThat(message, startsWith("Outgoing Request:"));
            assertThat(message, containsString(format("POST http://localhost:%d/ HTTP/1.1", driver.getPort())));
            assertThat(message, containsStringIgnoringCase("Content-Type: text/plain"));
            assertThat(message, not(containsString("Hello, world!")));
        }

        {
            final String message = captureResponse();

            assertThat(message, startsWith("Incoming Response:"));
            assertThat(message, containsString("HTTP/1.1 200 OK"));
            assertThat(message, containsStringIgnoringCase("Content-Type: text/plain"));
            assertThat(message, not(containsString("Hello, world!")));
        }
    }

    private void sendAndReceive() {
        client.get()
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

}
