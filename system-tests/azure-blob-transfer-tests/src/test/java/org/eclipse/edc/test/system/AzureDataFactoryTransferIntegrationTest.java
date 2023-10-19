/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.test.system;

import org.eclipse.edc.azure.blob.api.BlobStoreApiImpl;
import org.eclipse.edc.azure.testfixtures.TestFunctions;
import org.eclipse.edc.azure.testfixtures.annotations.AzureDataFactoryIntegrationTest;
import org.eclipse.edc.junit.extensions.EdcRuntimeExtension;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_CONNECTOR_MANAGEMENT_URL;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_CONNECTOR_PATH;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_CONNECTOR_PORT;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_MANAGEMENT_PATH;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_MANAGEMENT_PORT;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_PROTOCOL_PORT;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.CONSUMER_PROTOCOL_URL;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROTOCOL_PATH;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_ASSET_FILE;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_CONNECTOR_PATH;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_CONNECTOR_PORT;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_MANAGEMENT_PATH;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_MANAGEMENT_PORT;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_PROTOCOL_PORT;
import static org.eclipse.edc.test.system.TransferRuntimeConfiguration.PROVIDER_PROTOCOL_URL;
import static org.eclipse.edc.test.system.local.BlobTransferConfiguration.BLOB_CONTENT;

@AzureDataFactoryIntegrationTest
class AzureDataFactoryTransferIntegrationTest {

    private static final List<Runnable> CONTAINER_CLEANUP = new ArrayList<>();
    private static final RuntimeAzureSettings RUNTIME_AZURE_SETTINGS = new RuntimeAzureSettings();
    private static final String EDC_FS_CONFIG = "edc.fs.config";
    private static final String EDC_VAULT_NAME = "edc.vault.name";
    private static final String PROVIDER_CONTAINER_NAME = UUID.randomUUID().toString();
    private static final String KEY_VAULT_NAME = RUNTIME_AZURE_SETTINGS.getProperty("test.key.vault.name");

    @RegisterExtension
    public static final EdcRuntimeExtension CONSUMER = new EdcRuntimeExtension(
            ":system-tests:runtimes:azure-storage-transfer-consumer",
            "consumer",
            Map.ofEntries(
                    Map.entry("web.http.port", valueOf(CONSUMER_CONNECTOR_PORT)),
                    Map.entry("web.http.path", CONSUMER_CONNECTOR_PATH),
                    Map.entry("web.http.management.port", valueOf(CONSUMER_MANAGEMENT_PORT)),
                    Map.entry("web.http.management.path", CONSUMER_MANAGEMENT_PATH),
                    Map.entry("web.http.protocol.port", valueOf(CONSUMER_PROTOCOL_PORT)),
                    Map.entry("web.http.protocol.path", PROTOCOL_PATH),
                    Map.entry("edc.dsp.callback.address", CONSUMER_PROTOCOL_URL),
                    Map.entry(EDC_FS_CONFIG, RuntimeAzureSettings.ABSOLUTE_PATH),
                    Map.entry(EDC_VAULT_NAME, KEY_VAULT_NAME)
            )
    );

    @RegisterExtension
    public static final EdcRuntimeExtension PROVIDER = new EdcRuntimeExtension(
            ":system-tests:runtimes:azure-data-factory-transfer-provider",
            "provider",
            Map.ofEntries(
                    Map.entry("web.http.port", valueOf(PROVIDER_CONNECTOR_PORT)),
                    Map.entry("web.http.path", PROVIDER_CONNECTOR_PATH),
                    Map.entry("web.http.management.port", valueOf(PROVIDER_MANAGEMENT_PORT)),
                    Map.entry("web.http.management.path", PROVIDER_MANAGEMENT_PATH),
                    Map.entry("web.http.protocol.port", valueOf(PROVIDER_PROTOCOL_PORT)),
                    Map.entry("web.http.protocol.path", PROTOCOL_PATH),
                    Map.entry("edc.dsp.callback.address", PROVIDER_PROTOCOL_URL),
                    Map.entry(EDC_FS_CONFIG, RuntimeAzureSettings.ABSOLUTE_PATH),
                    Map.entry(EDC_VAULT_NAME, KEY_VAULT_NAME)
            )
    );
    private static final String PROVIDER_STORAGE_ACCOUNT_NAME = RUNTIME_AZURE_SETTINGS.getProperty("test.provider.storage.name");
    private static final String CONSUMER_STORAGE_ACCOUNT_NAME = RUNTIME_AZURE_SETTINGS.getProperty("test.consumer.storage.name");
    private static final String BLOB_STORE_ENDPOINT_TEMPLATE = "https://%s.blob.core.windows.net";

    @Test
    void transferBlob_success() {
        // Arrange
        var vault = PROVIDER.getContext().getService(Vault.class);
        var account2Key = Objects.requireNonNull(vault.resolveSecret(format("%s-key1", CONSUMER_STORAGE_ACCOUNT_NAME)));
        var blobStoreApi = new BlobStoreApiImpl(vault, BLOB_STORE_ENDPOINT_TEMPLATE);

        // Upload a blob with test data on provider blob container
        blobStoreApi.createContainer(PROVIDER_STORAGE_ACCOUNT_NAME, PROVIDER_CONTAINER_NAME);
        blobStoreApi.putBlob(PROVIDER_STORAGE_ACCOUNT_NAME, PROVIDER_CONTAINER_NAME, PROVIDER_ASSET_FILE, BLOB_CONTENT.getBytes(UTF_8));
        // Add for cleanup
        CONTAINER_CLEANUP.add(() -> blobStoreApi.deleteContainer(PROVIDER_STORAGE_ACCOUNT_NAME, PROVIDER_CONTAINER_NAME));

        // Seed data to provider
        createAsset(PROVIDER_STORAGE_ACCOUNT_NAME, PROVIDER_CONTAINER_NAME);
        var policyId = createPolicy();
        createContractDefinition(policyId);


        var blobServiceClient = TestFunctions.getBlobServiceClient(CONSUMER_STORAGE_ACCOUNT_NAME, account2Key, TestFunctions.getBlobServiceTestEndpoint(format("https://%s.blob.core.windows.net", CONSUMER_STORAGE_ACCOUNT_NAME)));

        var runner = new TransferTestRunner(new BlobTransferConfiguration(CONSUMER_CONNECTOR_MANAGEMENT_URL, PROVIDER_PROTOCOL_URL, blobServiceClient));

        runner.executeTransfer();
    }

    @AfterAll
    static void cleanUp() {
        CONTAINER_CLEANUP.parallelStream().forEach(Runnable::run);
    }
}
