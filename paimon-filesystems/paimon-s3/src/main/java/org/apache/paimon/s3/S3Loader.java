/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.s3;

import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.fs.FileIO;
import org.apache.paimon.fs.FileIOLoader;
import org.apache.paimon.fs.Path;
import org.apache.paimon.fs.PluginFileIO;
import org.apache.paimon.plugin.PluginLoader;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A {@link PluginLoader} to load oss. */
public class S3Loader implements FileIOLoader {

    private static final String S3_CLASSES_DIR = "paimon-plugin-s3";

    private static final String S3_CLASS = "org.apache.paimon.s3.S3FileIO";

    private static final String S3_ACCESS_KEY = "s3.access-key";

    private static final String S3_SECRET_KEY = "s3.secret-key";

    // Singleton lazy initialization

    private static PluginLoader loader;

    private static synchronized PluginLoader getLoader() {
        if (loader == null) {
            // Avoid NoClassDefFoundError without cause by exception
            loader = new PluginLoader(S3_CLASSES_DIR);
        }
        return loader;
    }

    @Override
    public String getScheme() {
        return "s3";
    }

    @Override
    public List<String[]> requiredOptions() {
        List<String[]> options = new ArrayList<>();
        Optional<AWSCredentials> awsCredentials = getAWSCredentials();
        if (!awsCredentials.isPresent()) {
            options.add(new String[] {S3_ACCESS_KEY, S3_ACCESS_KEY});
            options.add(new String[] {S3_SECRET_KEY, S3_SECRET_KEY});
        }
        return options;
    }

    // try to load AWS credentials via default providers
    private static Optional<AWSCredentials> getAWSCredentials() {
        try {
            return Optional.of(DefaultAWSCredentialsProviderChain.getInstance().getCredentials());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public FileIO load(Path path) {
        return new S3PluginFileIO();
    }

    private static class S3PluginFileIO extends PluginFileIO {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean isObjectStore() {
            return true;
        }

        @Override
        protected FileIO createFileIO(Path path) {
            FileIO fileIO = getLoader().newInstance(S3_CLASS);
            // inject aws credentials into options for s3a
            getAWSCredentials()
                .ifPresent(credentials -> {
                options.set(S3_ACCESS_KEY, credentials.getAWSAccessKeyId());
                options.set(S3_SECRET_KEY, credentials.getAWSSecretKey());
            });
            fileIO.configure(CatalogContext.create(options));
            return fileIO;
        }

        @Override
        protected ClassLoader pluginClassLoader() {
            return getLoader().submoduleClassLoader();
        }
    }
}
