package org.warp.commonutils.metrics;

import java.util.Arrays;

public class AtomicTimeAbsoluteSamples implements AtomicTimeAbsoluteSamplesSnapshot {

	protected final boolean isSnapshot;
	protected long startTime;
	protected final long[] samples;
	protected final int sampleTime;
	protected long currentSampleStartTime;
	protected long totalSamplesSum = 0;
	protected long totalSamplesCount = 1;

	/**
	 *
	 * @param sampleTime in milliseconds
	 * @param samplesCount
	 */
	public AtomicTimeAbsoluteSamples(int sampleTime, int samplesCount) {
		this.samples = new long[samplesCount];
		this.sampleTime = sampleTime;
		startTime = -1;
		if (samplesCount < 1) throw new IndexOutOfBoundsException();
		if (sampleTime < 1) throw new IndexOutOfBoundsException();
		this.isSnapshot = false;
	}

	public AtomicTimeAbsoluteSamples(long startTime, long[] samples, int sampleTime, long currentSampleStartTime, long totalSamplesSum, long totalSamplesCount, boolean isSnapshot) {
		this.startTime = startTime;
		this.samples = samples;
		this.sampleTime = sampleTime;
		this.currentSampleStartTime = currentSampleStartTime;
		this.totalSamplesSum = totalSamplesSum;
		this.totalSamplesCount = totalSamplesCount;
		this.isSnapshot = isSnapshot;
	}

	protected synchronized void updateSamples() {
		checkStarted();

		if (isSnapshot) {
			return;
		}

		long currentTime = System.nanoTime() / 1000000L;
		long timeDiff = currentTime - currentSampleStartTime;
		long timeToShift = timeDiff - (timeDiff % sampleTime);
		int shiftCount = (int) (timeToShift / sampleTime);
		if (currentTime - (currentSampleStartTime + timeToShift) > sampleTime) {
			throw new IndexOutOfBoundsException("Time sample bigger than " + sampleTime + "! It's " + (currentTime - (currentSampleStartTime + timeToShift)));
		}
		if (shiftCount > 0) {
			shiftSamples(shiftCount);
			currentSampleStartTime += timeToShift;
			totalSamplesCount += shiftCount;
			long lastSample = samples[0];
			totalSamplesSum += lastSample * shiftCount;
		}
	}

	protected synchronized void checkStarted() {
		if (startTime == -1) {
			this.startTime = System.nanoTime() / 1000000L;
			this.currentSampleStartTime = startTime;
		}
	}

	protected void shiftSamples(int shiftCount) {
		checkStarted();
		long lastSample = samples[0];
		if (samples.length - shiftCount > 0) {
			System.arraycopy(samples, 0, samples, shiftCount, samples.length - shiftCount);
			Arrays.fill(samples, 0, shiftCount, lastSample);
		} else {
			Arrays.fill(samples, lastSample);
		}
	}

	public synchronized void set(long count) {
		updateSamples();
		long oldValue = samples[0];
		samples[0]=count;
		totalSamplesSum += count - oldValue;
	}

	@Override
	public synchronized double getAveragePerSecond(long timeRange) {
		updateSamples();

		double preciseTimeRange = timeRange;
		// Fix if the time range is bigger than the collected data since start
		if (currentSampleStartTime - preciseTimeRange < startTime) {
			preciseTimeRange = currentSampleStartTime - startTime;
		}

		double samplesCount = Math.min(Math.max(preciseTimeRange / sampleTime, 1d), samples.length - 1);
		if (samplesCount < 0) {
			return 0;
		}
		double value = 0;
		for (int i = 1; i <= samplesCount; i++) {
			value += samples[i];
		}
		return value / samplesCount;
	}

	@Override
	public synchronized long getCurrentCount() {
		updateSamples();
		return samples[0];
	}

	@Override
	public synchronized double getTotalAveragePerSecond() {
		updateSamples();
		return (double) totalSamplesSum / (double) totalSamplesCount;
	}

	public synchronized AtomicTimeAbsoluteSamplesSnapshot snapshot() {
		return new AtomicTimeAbsoluteSamples(startTime, Arrays.copyOf(this.samples, this.samples.length), sampleTime, currentSampleStartTime, totalSamplesSum, totalSamplesCount, true);
	}
}
