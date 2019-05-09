package io.micrometer.boot2.samples;

import java.util.Collection;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;

/**
 * Used in {@link MetricsListener} to define the format of the metrics log. A component implementing this interface may
 * be added to the ApplicationContext to override the default behaviour.
 * 
 * @author Tobias Flohre
 */
public interface MetricsOutputFormatter {

	public String format(Collection<Gauge> gauges, Collection<Timer> timers);

}
