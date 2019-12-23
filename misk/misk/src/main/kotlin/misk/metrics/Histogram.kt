package misk.metrics

/*
 * Skeleton for the functionality of histograms
 *
 * A histogram samples observations (usually things like request durations or response sizes)
 * and counts them in configurable buckets.
 *
 * A sample implementation can be found in PrometheusHistogram
 */
interface Histogram {
  /** records a new set of labels and accompanying duration */
  fun record(duration: Double, vararg labelValues: String)

  /** returns the number of buckets */
  fun count(vararg labelValues: String): Int

  /** records a new set of labels and the time to execute the work lambda in milliseconds */
  fun <T> timedMills(vararg labelValues: String, work: () -> T): T {
    val (time, result) = misk.time.timed { work.invoke() }
    record(time.toMillis().toDouble(), *labelValues)
    return result
  }
}