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
package org.apache.fineract.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;

@Configuration
public class AwsEventBridgeConfig {

    /**
     * Región AWS (p. ej. "mx-central-1").
     */
    @Value("${spring.cloud.aws.region.static}")
    private String awsRegion;

    /**
     * Endpoint custom, deje vacío para AWS real.
     */
    @Value("${spring.cloud.aws.endpoint:}")
    private String awsEndpoint;

    @Bean
    public EventBridgeClient eventBridgeClient() {
        EventBridgeClientBuilder builder = EventBridgeClient.builder().region(Region.of(awsRegion));

        if (awsEndpoint != null && !awsEndpoint.isBlank()) {
            // Si estás apuntando a LocalStack u otro endpoint HTTP
            builder.endpointOverride(URI.create(awsEndpoint));
        }

        return builder.build();
    }
}
