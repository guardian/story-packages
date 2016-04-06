package test

import org.scalatest.Suites

class FaciaToolTestSuite extends Suites (
  new metrics.DurationMetricTest,
  new util.EnumeratorsTest,
  new util.RichFutureTest,
  new util.SanitizeInputTest) {}
