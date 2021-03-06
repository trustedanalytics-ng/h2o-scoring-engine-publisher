/**
 * Copyright (c) 2015 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.trustedanalytics.h2oscoringengine.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.client.RestTemplate;
import org.trustedanalytics.h2oscoringengine.publisher.enginename.EngineNameSupplier;
import org.trustedanalytics.h2oscoringengine.publisher.filesystem.FsDirectoryOperations;
import org.trustedanalytics.h2oscoringengine.publisher.filesystem.PublisherWorkingDirectory;
import org.trustedanalytics.h2oscoringengine.publisher.http.BasicAuthServerCredentials;
import org.trustedanalytics.h2oscoringengine.publisher.http.FilesDownloader;
import org.trustedanalytics.h2oscoringengine.publisher.restapi.ScoringEngineData;
import org.trustedanalytics.h2oscoringengine.publisher.steps.AssureOfferingPresenceStep;
import org.trustedanalytics.h2oscoringengine.publisher.steps.H2oResourcesDownloadingStep;
import org.trustedanalytics.h2oscoringengine.publisher.tapapi.OfferingCreator;
import org.trustedanalytics.h2oscoringengine.publisher.tapapi.OfferingsFetcher;
import org.trustedanalytics.h2oscoringengine.publisher.tapapi.ServiceCreator;
import org.trustedanalytics.modelcatalog.rest.client.ModelCatalogReaderClient;

public class Publisher {

  private final RestTemplate h2oServerRestTemplate;
  private final RestTemplate tapApiServiceRestTemplate;
  private final String tapApiServiceUrl;
  private final String engineBaseResourcePath;
  private final ModelCatalogReaderClient modelCatalogClient;
  private final EngineNameSupplier engineNameSupplier;

  public Publisher(RestTemplate h2oServerRestTemplate, RestTemplate tapApiServiceRestTemplate,
      String tapApiServiceUrl, String engineBaseJar, ModelCatalogReaderClient modelCatalogClient,
      EngineNameSupplier engineNameSupplier) {
    this.engineBaseResourcePath = engineBaseJar;
    this.h2oServerRestTemplate = h2oServerRestTemplate;
    this.tapApiServiceRestTemplate = tapApiServiceRestTemplate;
    this.tapApiServiceUrl = tapApiServiceUrl;
    this.modelCatalogClient = modelCatalogClient;
    this.engineNameSupplier = engineNameSupplier;
  }

  public Path getScoringEngineJar(BasicAuthServerCredentials h2oCredentials, String modelName)
      throws EngineBuildingException {
    return buildScoringEngineJar(new FilesDownloader(h2oCredentials, h2oServerRestTemplate),
        modelName);
  }

  public void publishScoringEngine(ScoringEngineData scoringEngineData)
      throws EnginePublishingException {
    ObjectMapper jsonMapper = new ObjectMapper();
    AssureOfferingPresenceStep assureOfferingPresenceStep = new AssureOfferingPresenceStep(
        new OfferingsFetcher(tapApiServiceRestTemplate, tapApiServiceUrl, jsonMapper),
        new OfferingCreator(tapApiServiceRestTemplate, tapApiServiceUrl, jsonMapper),
        modelCatalogClient);
    assureOfferingPresenceStep.ensureOfferingExists(scoringEngineData).createOfferingInstance(
        new ServiceCreator(tapApiServiceRestTemplate, tapApiServiceUrl, jsonMapper),
        scoringEngineData, engineNameSupplier);
  }

  private Path buildScoringEngineJar(FilesDownloader h2oFilesDownloader, String modelName)
      throws EngineBuildingException {

    try {
      PublisherWorkingDirectory workingDir =
          new PublisherWorkingDirectory(modelName, new FsDirectoryOperations());

      H2oResourcesDownloadingStep h2oResourcesDownloadingStep = new H2oResourcesDownloadingStep();
      return h2oResourcesDownloadingStep
          .downloadResources(h2oFilesDownloader, modelName, workingDir.getH2oResourcesPath())
          .compileModel(workingDir.getCompiledModelPath())
          .packageModel(workingDir.getModelJarPath())
          .buildScoringEngine(workingDir.getScoringEngineJarDir(), engineBaseResourcePath);

    } catch (IOException e) {
      throw new EngineBuildingException("Unable to create dir for publisher: ", e);
    }
  }
}
