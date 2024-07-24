package test

import org.scalatest.Suites

class FaciaToolTestSuite extends Suites (
  new story_packages.metrics.DurationMetricTest,
  new story_packages.util.RichFutureTest,
  new story_packages.util.SanitizeInputTest) {}
