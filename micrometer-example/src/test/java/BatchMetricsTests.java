/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;

import io.micrometer.boot2.samples.JobLoggingListener;
import io.micrometer.boot2.samples.MetricsListener;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class BatchMetricsTests {

	private static final int EXPECTED_SPRING_BATCH_METRICS = 6;

	static MeterRegistry consoleLoggingRegistry(MetricRegistry dropwizardRegistry) {
		DropwizardConfig consoleConfig = new DropwizardConfig() {

			@Override
			public String prefix() {
				return "console";
			}

			@Override
			public String get(String key) {
				return null;
			}
		};

		return new DropwizardMeterRegistry(consoleConfig, dropwizardRegistry, HierarchicalNameMapper.DEFAULT,
			Clock.SYSTEM) {
			@Override
			protected Double nullGaugeValue() {
				return null;
			}
		};
	}

	static ConsoleReporter consoleReporter(MetricRegistry dropwizardRegistry) {
		ConsoleReporter reporter = ConsoleReporter.forRegistry(dropwizardRegistry)
			.convertRatesTo(TimeUnit.SECONDS)
			.convertDurationsTo(TimeUnit.MILLISECONDS)
			.build();
		return reporter;
	}

	@Test
	public void testBatchMetrics() throws Exception {
		// given
		ApplicationContext context = new AnnotationConfigApplicationContext(MyJobConfiguration.class);
		JobLauncher jobLauncher = context.getBean(JobLauncher.class);
		Job job = context.getBean(Job.class);

		// when
		JobExecution jobExecution = jobLauncher.run(job, new JobParameters());

		// then
		assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());

		//Metrics.addRegistry(new SimpleMeterRegistry());
		MetricRegistry dropwizardsMetricRegistry = new MetricRegistry();
		Metrics.addRegistry(consoleLoggingRegistry(dropwizardsMetricRegistry));
		ConsoleReporter consoleReporter = consoleReporter(dropwizardsMetricRegistry);

		
		CompositeMeterRegistry meterRegistry = Metrics.globalRegistry;
		Counter ehemCounter = Metrics.globalRegistry.counter("presentation.filler.words", "word", "ehem");
		ehemCounter.increment();


		consoleReporter.report();

		// Or via the builder
		Counter sooooCounter = Counter.builder("presentation.filler.words")
			.tag("word", "soooo…")
			.register(meterRegistry);
		sooooCounter.increment(2);

		System.out.println(ehemCounter.count());
		System.out.println(sooooCounter.count());

		// A timer
		Timer slideTimer = Timer.builder("presentation.slide.timer")
			.description("This is a timer.")
			.tags(
				"conference", "Spring I/O",
				"place", "Barcelona"
			)
			.register(meterRegistry);

		Supplier<String> slideSupplier = () -> {
			try {
				Thread.sleep((long) (1_000 * ThreadLocalRandom.current().nextDouble()));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return "Next slide";
		};

		Stream.iterate(0, n -> n + 1)
        .limit(2)
        .forEach(x -> System.out.println(slideTimer.record(slideSupplier)));
		

		System.out.println(slideTimer.max(TimeUnit.SECONDS));
		System.out.println(slideTimer.count());

		// And last but not least the Gauge
		List<String> audience = meterRegistry.gauge("presentation.audience", new ArrayList<String>(), List::size);

		// What is audience?
		// It's the actual list
		audience.add("Mark");
		audience.add("Oliver");

		System.out.println(meterRegistry.find("presentation.audience").gauge().value());

		audience.add("Alice");
		audience.add("Tina");
		audience.remove("Mark");

		System.out.println(meterRegistry.find("presentation.audience").gauge().value());

		// One hardly interacts with a gauge direclty, but it's possible
		Gauge usedMemory = Gauge.builder("jvm.memory.used", Runtime.getRuntime(), r -> r.totalMemory() - r.freeMemory())
			.baseUnit("bytes")
			.tag("host", "chronos")
			.tag("region", "my-desk")
			.register(meterRegistry);

		System.out.println(usedMemory.value());
		consoleReporter.report();

		
		//meterRegistry.find("my.tasklet.timer").timer().
		
		List<Meter> meters = Metrics.globalRegistry.getMeters();
		System.out.println("======>" + meters.size());
		assertTrue(meters.size() >= EXPECTED_SPRING_BATCH_METRICS);

		try {
			Metrics.globalRegistry.get("spring.batch.job")
					.tag("name", "job")
					.tag("status", "COMPLETED")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.job " +
					"registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.job.active")
					.longTaskTimer();
		} catch (Exception e) {
			fail("There should be a meter of type LONG_TASK_TIMER named spring.batch.job.active" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.step")
					.tag("name", "step1")
					.tag("job.name", "job")
					.tag("status", "COMPLETED")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.step" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.step")
					.tag("name", "step2")
					.tag("job.name", "job")
					.tag("status", "COMPLETED")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.step" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.item.read")
					.tag("job.name", "job")
					.tag("step.name", "step2")
					.tag("status", "SUCCESS")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.item.read" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.item.process")
					.tag("job.name", "job")
					.tag("step.name", "step2")
					.tag("status", "SUCCESS")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.item.process" +
					" registered in the global registry: " + e.getMessage());
		}

		try {
			Metrics.globalRegistry.get("spring.batch.chunk.write")
					.tag("job.name", "job")
					.tag("step.name", "step2")
					.tag("status", "SUCCESS")
					.timer();
		} catch (Exception e) {
			fail("There should be a meter of type TIMER named spring.batch.chunk.write" +
					" registered in the global registry: " + e.getMessage());
		}
	}

	@Configuration
	@EnableBatchProcessing
	static class MyJobConfiguration {

		private JobBuilderFactory jobBuilderFactory;
		private StepBuilderFactory stepBuilderFactory;

		public MyJobConfiguration(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory) {
			this.jobBuilderFactory = jobBuilderFactory;
			this.stepBuilderFactory = stepBuilderFactory;
		}

		@Bean
		public MetricsListener jobLoggingListener() {
			return new MetricsListener(Metrics.globalRegistry);
		}
		
		@Bean
		public Step step1() {
			return stepBuilderFactory.get("step1")
					.tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED)
					.build();
		}

		@Bean
		public ItemReader<Integer> itemReader() {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return new ListItemReader<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
		}

		@Bean
		public ItemWriter<Integer> itemWriter() {
			return items -> {
				for (Integer item : items) {
					System.out.println("item = " + item);
				}
			};
		}

		@Bean
		public Step step2() {
			return stepBuilderFactory.get("step2")
					.<Integer, Integer>chunk(5)
					.reader(itemReader())
					.writer(itemWriter())
					.build();
		}

		@Bean
		public Job job() {
			return jobBuilderFactory.get("job").listener(jobLoggingListener())
					.start(step1())
					.next(step2())
					.build();
		}
	}
}
