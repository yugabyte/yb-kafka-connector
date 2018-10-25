// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.
//

package com.yb.connect.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

/**
 * YugaByte Sink connector.
 */
public class YBSinkConnector extends SinkConnector {
    private static final String VERSION = "1";

    private Map<String, String> properties;

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public void start(final Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public Class<? extends Task> taskClass() {
        return YBSinkTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(final int maximumTasks) {
        final List<Map<String, String>> configs = new ArrayList<Map<String, String>>();
        IntStream.rangeClosed(1, maximumTasks).forEach((i) -> configs.add(properties));
        return configs;
    }

    @Override
    public void stop() {
    }

    @Override
    public ConfigDef config() {
        return new ConfigDef();
    }
}
