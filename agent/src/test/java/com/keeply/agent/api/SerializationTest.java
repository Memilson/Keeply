package com.keeply.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.model.StartedSnapshot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SerializationTest {

    @Test
    void testParseStartSnapshotResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).findAndRegisterModules();

        String json = """
        {
          "snapshot": {
            "id": "8f806509-3224-4f05-862d-0585255474d2",
            "deviceId": "550e8400-e29b-41d4-a716-446655440000",
            "status": "IN_PROGRESS",
            "sourcePath": "/home/user/data",
            "totalFiles": 0,
            "totalOriginalSize": 0,
            "totalCompressedSize": 0,
            "startedAt": "2023-10-27T10:00:00Z",
            "completedAt": null,
            "errorMessage": null
          },
          "transfer": {
            "transferSessionId": "2d14849a-e1a5-4f3b-a2c6-946765790757",
            "type": "BACKUP",
            "bucket": "keeply",
            "minioEndpoint": "http://localhost:9000",
            "accessKey": "minioadmin",
            "secretKey": "minioadmin",
            "sessionToken": "token",
            "expiresAt": "2023-10-27T11:00:00Z",
            "renewAfter": "2023-10-27T10:45:00Z",
            "stagingPrefix": "staging/"
          }
        }
        """;

        try {
            StartedSnapshot startedSnapshot = mapper.readValue(json, StartedSnapshot.class);
            assertNotNull(startedSnapshot);
            assertNotNull(startedSnapshot.snapshot().id());
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException e) {
            System.err.println("Caught expected exception: " + e.getMessage());
            throw e;
        }
    }
}
