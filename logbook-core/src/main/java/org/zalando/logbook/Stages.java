package org.zalando.logbook;

import org.zalando.logbook.Logbook.RequestWritingStage;

import static org.zalando.logbook.Logbook.ResponseProcessingStage;
import static org.zalando.logbook.Logbook.ResponseWritingStage;

final class Stages {

    private static final ResponseWritingStage WRITE_RESPONSE = () -> {};
    private static final ResponseProcessingStage PROCESS_RESPONSE = response -> WRITE_RESPONSE;
    private static final RequestWritingStage WRITE_REQUEST = new RequestWritingStage() {
        @Override
        public ResponseProcessingStage write() {
            return PROCESS_RESPONSE;
        }

        @Override
        public ResponseWritingStage process(final HttpResponse response) {
            return WRITE_RESPONSE;
        }
    };

    private Stages() {

    }

    static RequestWritingStage noop() {
        return WRITE_REQUEST;
    }

}
