/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.monitoring;

import org.apache.hadoop.hbase.metrics.Gauge;
import org.apache.hadoop.hbase.metrics.MetricRegistries;
import org.apache.hadoop.hbase.metrics.MetricRegistry;
import org.apache.hadoop.hbase.metrics.MetricRegistryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalClientMetricsRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalClientMetrics.class);

    static {
        if (GlobalClientMetrics.isMetricsEnabled()) {
            MetricRegistry metricRegistry = createMetricRegistry();
            registerPhoenixMetricsToRegistry(metricRegistry);
            GlobalMetricRegistriesAdapter.getInstance().registerMetricRegistry(metricRegistry);
        }
    }

    private static void registerPhoenixMetricsToRegistry(MetricRegistry metricRegistry) {
        for (GlobalClientMetrics globalMetric : GlobalClientMetrics.values()) {
            metricRegistry.register(globalMetric.getMetricType().columnName(),
                    new PhoenixGlobalMetricGauge(globalMetric.getMetric()));
        }
    }

    private static MetricRegistry createMetricRegistry() {
        LOGGER.info("Creating Metric Registry for Phoenix Global Metrics");
        MetricRegistryInfo registryInfo = new MetricRegistryInfo("PHOENIX", "Phoenix Client Metrics",
                "phoenix", "Phoenix,sub=CLIENT", true);
        return MetricRegistries.global().create(registryInfo);
    }

    /**
     * Class to convert Phoenix Metric objects into HBase Metric objects (Gauge)
     */
    private static class PhoenixGlobalMetricGauge implements Gauge<Long> {

        private final GlobalMetric metric;

        public PhoenixGlobalMetricGauge(GlobalMetric metric) {
            this.metric = metric;
        }

        @Override
        public Long getValue() {
            return metric.getValue();
        }
    }

    public static void register() {
        //Noop, just ensure that the class is loaded
    }
}
