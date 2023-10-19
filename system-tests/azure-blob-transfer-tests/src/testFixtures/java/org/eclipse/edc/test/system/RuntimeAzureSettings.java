/*
 *  Copyright (c) 2023 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.edc.test.system;

import org.eclipse.edc.junit.testfixtures.TestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class RuntimeAzureSettings {

    private static final String RELATIVE_PATH = "resources/azure/testing/runtime_settings.properties";
    public static final String ABSOLUTE_PATH = new File(TestUtils.findBuildRoot(), RELATIVE_PATH).getAbsolutePath();

    private final Properties properties;

    public RuntimeAzureSettings() {
        try (var input = new FileInputStream(ABSOLUTE_PATH)) {
            properties = new Properties();
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error in loading runtime settings properties", e);
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}
