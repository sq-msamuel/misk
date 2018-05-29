package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.MiskModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.InMemoryHsqlService
import misk.resources.ResourceLoaderModule

/** This module supports our Hibernate tests. */
class HibernateTestModule : KAbstractModule() {
  override fun configure() {
    bind(Environment::class.java).toInstance(Environment.TESTING)
    install(ResourceLoaderModule())
    install(MiskModule())

    val rootConfig = MiskConfig.load<RootConfig>("test_hibernate_app", Environment.TESTING)
    val config: DataSourceConfig = rootConfig.data_source_clusters["exemplar"]!!.writer
    binder().addMultibinderBinding<Service>().toInstance(
        InMemoryHsqlService(config))
    install(HibernateModule(Movies::class, config))
    install(HibernateEntityModule(Movies::class,
        setOf(DbMovie::class)))
  }

  data class RootConfig(val data_source_clusters: DataSourceClustersConfig) : Config
}