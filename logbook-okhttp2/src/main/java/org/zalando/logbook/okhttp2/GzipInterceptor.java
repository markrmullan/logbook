package org.zalando.logbook.okhttp2;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.http.RealResponseBody;
import okio.GzipSource;
import org.apiguardian.api.API;

import java.io.IOException;

import static java.util.Objects.requireNonNull;
import static okio.Okio.buffer;
import static org.apiguardian.api.API.Status.EXPERIMENTAL;

@API(status = EXPERIMENTAL)
public final class GzipInterceptor implements Interceptor {

    @Override
    public Response intercept(final Chain chain) throws IOException {
        final Response response = chain.proceed(chain.request());

        if (isContentEncodingGzip(response)) {
            final Headers headers = response.headers()
                    .newBuilder()
                    .removeAll("Content-Encoding")
                    .removeAll("Content-Length")
                    .build();

            return response.newBuilder()
                    .headers(headers)
                    .body(new RealResponseBody(
                            headers,
                            buffer(new GzipSource(requireNonNull(response.body(), "body").source()))))
                    .build();
        }

        return response;
    }

    private boolean isContentEncodingGzip(final Response response) {
        return "gzip".equalsIgnoreCase(response.header("Content-Encoding"));
    }

}
