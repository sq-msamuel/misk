plugins {
  `gradle-enterprise`
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}

include(":misk:misk")
include(":misk:misk-action-scopes")
include(":misk:misk-actions")
include(":misk:misk-admin")
include(":misk:misk-aws")
include(":misk:misk-aws-dynamodb")
include(":misk:misk-aws-dynamodb-testing")
include(":misk:misk-aws2-dynamodb")
include(":misk:misk-aws2-dynamodb-testing")
include(":misk:misk-bom")
include(":misk:misk-clustering")
include(":misk:misk-config")
include(":misk:misk-core")
include(":misk:misk-cron")
include(":misk:misk-crypto")
include(":misk:misk-datadog")
include(":misk:misk-events")
include(":misk:misk-events-core")
include(":misk:misk-events-testing")
include(":misk:misk-exceptions-dynamodb")
include(":misk:misk-feature")
include(":misk:misk-feature-testing")
include(":misk:misk-gcp")
include(":misk:misk-gcp-testing")
include(":misk:misk-grpc-reflect")
include(":misk:misk-grpc-tests")
include(":misk:misk-hibernate")
include(":misk:misk-hibernate-testing")
include(":misk:misk-hotwire")
include(":misk:misk-inject")
include(":misk:misk-jdbc")
include(":misk:misk-jdbc-testing")
include(":misk:misk-jobqueue")
include(":misk:misk-jobqueue-testing")
include(":misk:misk-jooq")
include(":misk:misk-launchdarkly")
include(":misk:misk-launchdarkly-core")
include(":misk:misk-lease")
include(":misk:misk-metrics")
include(":misk:misk-metrics-digester")
include(":misk:misk-metrics-testing")
include(":misk:misk-policy")
include(":misk:misk-policy-testing")
include(":misk:misk-prometheus")
include(":misk:misk-proto")
include(":misk:misk-redis")
include(":misk:misk-redis-testing")
include(":misk:misk-service")
include(":misk:misk-slack")
include(":misk:misk-tailwind")
include(":misk:misk-testing")
include(":misk:misk-transactional-jobqueue")
include(":misk:misk-warmup")
include(":samples:exemplar")
include(":samples:exemplarchat")
