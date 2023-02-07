package story_packages.metrics

import com.amazonaws.services.cloudwatch.model.StandardUnit
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DurationMetricTest extends AnyFlatSpec with Matchers {

  "DurationMetric" should "start off empty" in {
    val durationMetric: DurationMetric = DurationMetric("TestMetric", StandardUnit.Count)

    durationMetric.getAndResetDataPoints should be (List())
  }

  it should "record some metrics" in {
    val durationMetric: DurationMetric = DurationMetric("TestMetric", StandardUnit.Count)

    durationMetric.recordDuration(1000)
    durationMetric.recordDuration(1000)
    durationMetric.recordDuration(1000)

    durationMetric.getDataPoints.length should be (3)
    durationMetric.getAndResetDataPoints.forall(_.value == 1000) should be (true)

    durationMetric.getDataPoints.length should be(0)
  }

  it should "add datapoints to the head of the list" in {
    val durationMetric: DurationMetric = DurationMetric("TestMetric", StandardUnit.Count)

    val metricOne = DurationDataPoint(1000, None)
    val metricTwo = DurationDataPoint(1000, None)
    val metricThree = DurationDataPoint(1000, None)
    val metricFour = DurationDataPoint(1000, None)
    val allMetrics = List(metricOne, metricTwo, metricThree, metricFour)

    durationMetric.recordDuration(1000)
    durationMetric.recordDuration(1000)
    durationMetric.recordDuration(1000)
    durationMetric.putDataPoints(List(metricOne, metricTwo, metricThree, metricFour))

    val dataPoints = durationMetric.getDataPoints
    dataPoints.length should be (7)
    dataPoints.splitAt(4)._1 should be (allMetrics)
  }
}
