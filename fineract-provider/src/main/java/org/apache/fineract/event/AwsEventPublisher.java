/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Service
public class AwsEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AwsEventPublisher.class);
    private final EventBridgeClient client;
    private final String eventBusName;
    private final ObjectMapper mapper = new ObjectMapper();

    public AwsEventPublisher(EventBridgeClient client, @Value("${aws.eventbus.name}") String eventBusName) {
        this.client = client;
        this.eventBusName = eventBusName;
    }

    /**
     * Publica un evento en EventBridge.
     *
     * @param source
     *            Identificador de la fuente (p. ej. "my-service")
     * @param detailType
     *            Tipo de detalle
     * @param detailPayload
     *            Payload que se serializa a JSON
     */
    public void publish(String source, String detailType, Map<String, Object> detailPayload) {
        try {
            String detailJson = mapper.writeValueAsString(detailPayload);
            PutEventsRequestEntry entry = PutEventsRequestEntry.builder().eventBusName(eventBusName).source(source).detailType(detailType)
                    .detail(detailJson).build();

            client.putEvents(PutEventsRequest.builder().entries(entry).build());
        } catch (JsonProcessingException e) {
            LOG.error("Error serializando payload del evento", e);
        } catch (Exception e) {
            LOG.error("Error al publicar en EventBridge", e);
        }
    }
}
