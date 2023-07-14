import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(Dependencies.guava)
  api(Dependencies.wispFeature)
  implementation(Dependencies.kotlinStdLibJdk8)

  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.wispFeature)
  testFixturesApi(Dependencies.wispFeatureTesting)
  testFixturesApi(project(":misk:misk-feature"))
  testFixturesApi(project(":misk:misk-inject"))
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinStdLibJdk8)
  testFixturesImplementation(Dependencies.moshi)
  testFixturesImplementation(project(":misk:misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.guice)
  testImplementation(Dependencies.javaxInject)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.moshi)
  testImplementation(Dependencies.wispFeatureTesting)
  testImplementation(Dependencies.wispMoshi)
  testImplementation(project(":misk:misk-feature"))
  testImplementation(project(":misk:misk-inject"))
  testImplementation(project(":misk:misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
