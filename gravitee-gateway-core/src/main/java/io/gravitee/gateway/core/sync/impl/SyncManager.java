/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.core.sync.impl;

import io.gravitee.gateway.core.model.Api;
import io.gravitee.gateway.core.model.ApiLifecycleState;
import io.gravitee.repository.api.ApiRepository;
import io.gravitee.repository.exceptions.TechnicalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
public class SyncManager {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    @Autowired
    private ApiRepository apiRepository;

    private final Map<String, Api> cache = new HashMap<>();

    public void refresh() {
        try {
            Set<io.gravitee.repository.model.Api> apis = apiRepository.findAll();

            Map<String, Api> apisMap = apis.stream()
                    .map(api -> convert(api))
                    .collect(Collectors.toMap(Api::getName, api -> api));

            // Determine APIs to remove
            Set<String> apiToRemove = cache.keySet().stream()
                    .filter(apiName -> !apisMap.containsKey(apiName))
                    .collect(Collectors.toSet());

            apiToRemove.stream().forEach(apiName -> remove(cache.get(apiName)));

            // Determine APIs to update
            apisMap.keySet().stream()
                    .filter(apiName -> cache.containsKey(apiName))
                    .forEach(apiName -> {
                        // Get local cached API
                        Api cachedApi = cache.get(apiName);

                        // Get API from store
                        Api remoteApi = apisMap.get(apiName);

                        if (cachedApi.getUpdatedAt().before(remoteApi.getUpdatedAt())) {
                            update(remoteApi);
                        }
                    });

            // Determine APIs to create
            apisMap.keySet().stream()
                    .filter(apiName -> !cache.containsKey(apiName))
                    .forEach(apiName -> add(apisMap.get(apiName)));

        } catch (TechnicalException te) {
            logger.error("Unable to sync instance", te);
        }
    }

    public void remove(Api api) {
        logger.info("{} has been removed.", api);

        cache.remove(api.getName());
    }

    public void add(Api api) {
        logger.info("{} has been added.", api);

        cache.put(api.getName(), api);
    }

    public void update(Api api) {
        logger.info("{} has been updated.", api);

        Api cachedApi = cache.get(api.getName());

        // Update only if certain fields has been updated:
        // - Lifecycle
        // - TargetURL
        // - PublicURL

    }

    private Api convert(io.gravitee.repository.model.Api remoteApi) {
        Api api = new Api();

        api.setName(remoteApi.getName());
        api.setPublicURI(remoteApi.getPublicURI());
        api.setTargetURI(remoteApi.getTargetURI());
        api.setCreatedAt(remoteApi.getCreatedAt());
        api.setUpdatedAt(remoteApi.getUpdatedAt());
        api.setState(ApiLifecycleState.START);

        return api;
    }

    public void setApiRepository(ApiRepository apiRepository) {
        this.apiRepository = apiRepository;
    }
}