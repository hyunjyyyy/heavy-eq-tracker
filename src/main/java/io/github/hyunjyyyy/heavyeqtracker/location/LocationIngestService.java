package io.github.hyunjyyyy.heavyeqtracker.location;

import org.springframework.stereotype.Service;

@Service
public class LocationIngestService {

    private final IdempotencyStore store;
    private final LocationEventWriter writer;

    public LocationIngestService(IdempotencyStore store, LocationEventWriter writer) {
        this.store = store;
        this.writer = writer;
    }

    public IngestResult ingest(LocationReport report, String idempotencyKey) {
        if (!store.tryAcquire(idempotencyKey)) {
            return store.savedEventId(idempotencyKey)
                    .map(id -> new IngestResult(new LocationAck(id), false))
                    .orElseThrow(() -> new DuplicateInProgressException(idempotencyKey));
        }

        IngestResult result;
        try {
            result = writer.save(report, idempotencyKey);
        } catch (RuntimeException e) {
            store.release(idempotencyKey);
            throw e;
        }

        store.complete(idempotencyKey, result.ack().eventId());
        return result;
    }
}
