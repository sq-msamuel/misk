plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.awsS3)
  implementation(Dependencies.awsSqs)
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.openTracingDatadog)
  implementation(Dependencies.prometheusClient)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-hibernate"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-jobqueue"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":misk-transactional-jobqueue"))
  api(Dependencies.wispAwsEnvironment)
  api(Dependencies.wispConfig)
  api(Dependencies.wispContainersTesting)
  api(Dependencies.wispLease)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.dockerCore)
  testImplementation(Dependencies.dockerTransport)
  testImplementation(Dependencies.awaitility)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-feature-testing"))
}
