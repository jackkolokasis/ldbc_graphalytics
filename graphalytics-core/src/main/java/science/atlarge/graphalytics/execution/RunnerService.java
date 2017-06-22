/*
 * Copyright 2015 Delft University of Technology
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
package science.atlarge.graphalytics.execution;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.apache.commons.configuration.Configuration;
import science.atlarge.graphalytics.configuration.ConfigurationUtil;
import science.atlarge.graphalytics.configuration.GraphalyticsExecutionException;
import science.atlarge.graphalytics.domain.benchmark.BenchmarkRun;
import science.atlarge.graphalytics.report.result.BenchmarkMetrics;
import science.atlarge.graphalytics.report.result.BenchmarkRunResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import science.atlarge.graphalytics.util.ProcessUtil;
import science.atlarge.graphalytics.util.TimeUtil;

public class RunnerService extends MircoService {

    private static final Logger LOG = LogManager.getLogger();

    private static final String BENCHMARK_PROPERTIES_FILE = "benchmark.properties";
    private static final String BENCHMARK_RUNNER_PORT = "benchmark.runner.port";
    public static final String SERVICE_NAME = "runner-service";
    public static final String SERVICE_IP = "localhost";
    BenchmarkRunner runner;

    public RunnerService(BenchmarkRunner runner) {
        LOG.info("Benchmark runner service started.");
        this.runner = runner;
        runner.setService(this);
        LOG.info("Benchmark runner service registration started.");
        register();
        LOG.info("Benchmark runner service registration ended.");
    }

    public static void InitService(BenchmarkRunner benchmarkRunner) {
        Config config = defaultConfiguration();
        config = config.withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(getRunnerPort()));
        config = config.withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(SERVICE_IP));
        final ActorSystem system = ActorSystem.create(SERVICE_NAME, config);
        system.actorOf(Props.create(RunnerService.class, benchmarkRunner), SERVICE_NAME);

        LOG.debug("Started Graphalytics Runner Service");
    }

    private void register() {
        String masterAddress = getExecutorAddress();
        LOG.info(String.format("Register %s at %s.", runner.getBenchmarkId(), masterAddress));
        Integer processId = ProcessUtil.getProcessId();
        Notification notification = new Notification(
                runner.getBenchmarkId(),
                processId,
                Notification.Label.REGISTRATION);
        getContext().actorSelection(masterAddress).tell(notification, getSelf());
    }

    private void reportValidation() {
        String masterAddress = getExecutorAddress();
        LOG.info(String.format("Report validation for %s at %s.", runner.getBenchmarkId(), masterAddress));
        Notification notification = new Notification(
                runner.getBenchmarkId(),
                "Validated benchmark result.",
                Notification.Label.VALIDATION);
        getContext().actorSelection(masterAddress).tell(notification, getSelf());
    }

    private void reportExecution() {
        String masterAddress = getExecutorAddress();
        LOG.info(String.format("Report execution %s at %s.", runner.getBenchmarkId(), masterAddress));
        Notification notification = new Notification(
                runner.getBenchmarkId(),
                "Executed benchmark.",
                Notification.Label.EXECUTION);
        getContext().actorSelection(masterAddress).tell(notification, getSelf());
    }


    private void reportFailure(BenchmarkFailure failure) {
        String masterAddress = getExecutorAddress();
        LOG.info(String.format("Report failures (%s) of %s at %s.", failure, runner.getBenchmarkId(), masterAddress));
        Notification notification = new Notification(
                runner.getBenchmarkId(),
                failure,
                Notification.Label.FAILURE);
        getContext().actorSelection(masterAddress).tell(notification, getSelf());
        TimeUtil.waitFor(5);
    }

    private void reportRetrievedResult(BenchmarkRunResult benchmarkRunResult) {
        String executorAddress = getExecutorAddress();
        LOG.info(String.format("Report benchmark result for %s at %s.", runner.getBenchmarkId(), executorAddress));
        getContext().actorSelection(executorAddress).tell(benchmarkRunResult, getSelf());
    }




    private String getExecutorAddress() {
//        return String.format("akka.tcp://%s@%s:%s/user/%s",
//                ExecutorService.SERVICE_NAME, SERVICE_IP, 8099, ExecutorService.SERVICE_NAME);
        return String.format("akka.tcp://%s@%s:%s/user/%s",
                ExecutorService.SERVICE_NAME, SERVICE_IP, ExecutorService.getExecutorPort(), ExecutorService.SERVICE_NAME);
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof BenchmarkRun) {
            BenchmarkRun benchmarkRun = (BenchmarkRun) message;

            LOG.info(String.format("The runner received benchmark specification %s.", benchmarkRun.getId()));
            LOG.info(String.format("The runner is executing benchmark %s.", benchmarkRun.getId()));

            try  {
                runner.startup(benchmarkRun);
            } catch (Exception e) {
                LOG.error("Failed to startup benchmark run.", e);
                reportFailure(BenchmarkFailure.INI);
                terminate();
            }

            try {
                boolean runned = runner.run(benchmarkRun);
                if(!runned) {
                    reportFailure(BenchmarkFailure.EXE);
                }

            } catch (Exception e) {
                LOG.error("Failed to execute benchmark run.", e);
                reportFailure(BenchmarkFailure.EXE);
                terminate();
            }
            reportExecution();

            try {
                boolean counted = runner.count(benchmarkRun);
                if (!counted) {
                    reportFailure(BenchmarkFailure.COM);
                }
            } catch (Exception e) {
                LOG.error("Failed to count benchmark output.", e);
                reportFailure(BenchmarkFailure.COM);
                terminate();
            }

            try {
                boolean validated = runner.validate(benchmarkRun);

                if(!validated) {
                    reportFailure(BenchmarkFailure.VAL);
                }
            } catch (Exception e) {
                LOG.error("Failed to validate benchmark run.", e);
                reportFailure(BenchmarkFailure.VAL);
                terminate();
            }
            reportValidation();

            try {
                BenchmarkMetrics metrics = runner.finalize(benchmarkRun);
                BenchmarkRunResult benchmarkRunResult = runner.summarize(benchmarkRun, metrics);
                reportRetrievedResult(benchmarkRunResult);
            } catch (Exception e) {
                LOG.error(e);
                reportFailure(BenchmarkFailure.MET);
                LOG.error("Failed to finalize benchmark.");
                terminate();
            }
        }

    }


    public static Integer getRunnerPort() {
        Configuration configuration = ConfigurationUtil.loadConfiguration(BENCHMARK_PROPERTIES_FILE);
        return ConfigurationUtil.getInteger(configuration, BENCHMARK_RUNNER_PORT);
    }

}
