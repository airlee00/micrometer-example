/*
 * Copyright (C) Hanwha S&C Ltd., 2018. All rights reserved.
 *
 * This software is covered by the license agreement between
 * the end user and Hanwha S&C Ltd., and may be
 * used and copied only in accordance with the terms of the
 * said agreement.
 *
 * Hanwha S&C Ltd., assumes no responsibility or
 * liability for any errors or inaccuracies in this software,
 * or any consequential, incidental or indirect damage arising
 * out of the use of the software.
 */

package io.micrometer.boot2.samples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;


public class JobLoggingListener implements JobExecutionListener {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private static final String JOB_NAME_KEY = "JOB_NAME";
	Timer.Sample sample;
	private long startTimeMillis;

	@Override
	public void beforeJob(JobExecution jobExecution) {
		String jobName = jobExecution.getJobInstance().getJobName();
		MDC.put(JOB_NAME_KEY, jobName);
	
		sample = Timer.start(Metrics.globalRegistry);
		
		this.startTimeMillis = System.currentTimeMillis();
		logger.info("");
		logger.info("######################################################################################");
		logger.info("## Start Job");
		logger.info("##  - ID   : " + jobName);
		logger.info("######################################################################################");
		logger.info("");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		String jobName = jobExecution.getJobInstance().getJobName();
		logger.info("");
		logger.info("######################################################################################");
		logger.info("## End Job - Completed");
		logger.info("##  - ID   : " + jobName);
		logger.info("##  - Status : " + jobExecution.getStatus().toString());
		logger.info("##  - Elapsed Time : "
				+ (System.currentTimeMillis() - this.startTimeMillis) + " ms");
		logger.info("######################################################################################");
		logger.info("");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MDC.remove(JOB_NAME_KEY);
		sample.stop(Timer.builder("my.tasklet.timer")
				.description("Duration of MyTimedTasklet")
				.tag("status", jobExecution.getStatus().toString())
				.register(Metrics.globalRegistry));
		
	}
}
