package misk.hibernate

import io.opentracing.Tracer
import io.opentracing.tag.Tags
import misk.backoff.ExponentialBackoff
import misk.hibernate.Shard.Companion.SINGLE_SHARD_SET
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.map
import misk.jdbc.uniqueString
import misk.logging.getLogger
import misk.tracing.traceWithSpan
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.LockAcquisitionException
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLRecoverableException
import java.time.Duration
import java.util.EnumSet
import javax.inject.Provider
import javax.persistence.OptimisticLockException
import kotlin.reflect.KClass

private val logger = getLogger<RealTransacter>()

internal class RealTransacter private constructor(
  private val qualifier: KClass<out Annotation>,
  private val sessionFactoryProvider: Provider<SessionFactory>,
  private val config: DataSourceConfig,
  private val threadLocalSession: ThreadLocal<RealSession>,
  private val options: TransacterOptions,
  private val queryTracingListener: QueryTracingListener,
  private val tracer: Tracer?
) : Transacter {

  constructor(
    qualifier: KClass<out Annotation>,
    sessionFactoryProvider: Provider<SessionFactory>,
    config: DataSourceConfig,
    queryTracingListener: QueryTracingListener,
    tracer: Tracer?
  ) : this(
      qualifier,
      sessionFactoryProvider,
      config,
      ThreadLocal(),
      TransacterOptions(),
      queryTracingListener,
      tracer
  )

  private val sessionFactory
    get() = sessionFactoryProvider.get()

  override val inTransaction: Boolean
    get() = threadLocalSession.get() != null

  override fun isCheckEnabled(check: Check): Boolean {
    val session = threadLocalSession.get()
    return session == null || !session.disabledChecks.contains(check)
  }

  override fun <T> transaction(block: (session: Session) -> T): T {
    return maybeWithTracing(APPLICATION_TRANSACTION_SPAN_NAME) {
      transactionWithRetriesInternal(block)
    }
  }

  private fun <T> transactionWithRetriesInternal(block: (session: Session) -> T): T {
    require(options.maxAttempts > 0)

    val backoff = ExponentialBackoff(
        Duration.ofMillis(options.minRetryDelayMillis),
        Duration.ofMillis(options.maxRetryDelayMillis),
        Duration.ofMillis(options.retryJitterMillis)
    )
    var attempt = 0

    while (true) {
      try {
        attempt++
        return transactionInternal(block)
      } catch (e: Exception) {
        if (!isRetryable(e)) throw e

        if (attempt >= options.maxAttempts) {
          logger.info {
            "${qualifier.simpleName} recoverable transaction exception " +
                "(attempt: $attempt), no more attempts"
          }
          throw e
        }

        val sleepDuration = backoff.nextRetry()
        logger.info(e) {
          "${qualifier.simpleName} recoverable transaction exception " +
              "(attempt: $attempt), will retry after a $sleepDuration delay"
        }

        if (!sleepDuration.isZero) {
          Thread.sleep(sleepDuration.toMillis())
        }
      }
    }
  }

  private fun <T> transactionInternal(block: (session: Session) -> T): T {
    return maybeWithTracing(DB_TRANSACTION_SPAN_NAME) { transactionInternalSession(block) }
  }

  private fun <T> transactionInternalSession(block: (session: Session) -> T): T {
    return withSession { session ->
      val transaction = maybeWithTracing(DB_BEGIN_SPAN_NAME) {
        session.hibernateSession.beginTransaction()!!
      }
      try {
        val result = block(session)

        // Flush any changes to the databased before commit
        session.hibernateSession.flush()
        session.preCommit()
        maybeWithTracing(DB_COMMIT_SPAN_NAME) { transaction.commit() }
        session.postCommit()
        result
      } catch (e: Throwable) {
        if (transaction.isActive) {
          try {
            maybeWithTracing(DB_ROLLBACK_SPAN_NAME) {
              transaction.rollback()
            }
          } catch (suppressed: Exception) {
            e.addSuppressed(suppressed)
          }
        }
        throw e
      } finally {
        // For any reason if tracing was left open, end it.
        queryTracingListener.endLastSpan()
      }
    }
  }

  override fun retries(maxAttempts: Int): Transacter = withOptions(
      options.copy(maxAttempts = maxAttempts))

  override fun allowCowrites(): Transacter {
    val disableChecks = options.disabledChecks.clone()
    disableChecks.add(Check.COWRITE)
    return withOptions(
        options.copy(disabledChecks = disableChecks))
  }

  override fun noRetries(): Transacter = withOptions(options.copy(maxAttempts = 1))

  override fun readOnly(): Transacter = withOptions(options.copy(readOnly = true))

  private fun withOptions(options: TransacterOptions): Transacter =
      RealTransacter(
          qualifier,
          sessionFactoryProvider,
          config,
          threadLocalSession,
          options,
          queryTracingListener,
          tracer
      )

  private fun <T> withSession(block: (session: RealSession) -> T): T {
    val hibernateSession = sessionFactory.openSession()
    val realSession = RealSession(
        hibernateSession = hibernateSession,
        config = config,
        readOnly = options.readOnly,
        disabledChecks = options.disabledChecks
    )

    // Note that the RealSession is closed last so that close hooks run after the thread locals and
    // Hibernate Session have been released. This way close hooks can start their own transactions.
    realSession.use {
      hibernateSession.use {
        threadLocalSession.withValue(realSession) {
          return block(realSession)
        }
      }
    }
  }

  private fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is RetryTransactionException,
      is StaleObjectStateException,
      is LockAcquisitionException,
      is SQLRecoverableException,
      is OptimisticLockException -> true
      else -> th.cause?.let { isRetryable(it) } ?: false
    }
  }

  // NB: all options should be immutable types as copy() is shallow.
  internal data class TransacterOptions(
    val maxAttempts: Int = 2,
    val disabledChecks: EnumSet<Check> = EnumSet.noneOf(Check::class.java),
    val minRetryDelayMillis: Long = 100,
    val maxRetryDelayMillis: Long = 200,
    val retryJitterMillis: Long = 400,
    val readOnly: Boolean = false
  )

  companion object {
    const val APPLICATION_TRANSACTION_SPAN_NAME = "app-db-transaction"
    const val DB_TRANSACTION_SPAN_NAME = "db-session"
    const val DB_BEGIN_SPAN_NAME = "db-begin"
    const val DB_COMMIT_SPAN_NAME = "db-commit"
    const val DB_ROLLBACK_SPAN_NAME = "db-rollback"
    const val TRANSACTER_SPAN_TAG = "hibernate-transacter"
  }

  internal class RealSession(
    override val hibernateSession: org.hibernate.Session,
    val config: DataSourceConfig,
    private val readOnly: Boolean,
    var disabledChecks: EnumSet<Check>
  ) : Session, Closeable {
    private val preCommitHooks = mutableListOf<() -> Unit>()
    private val postCommitHooks = mutableListOf<() -> Unit>()
    private val sessionCloseHooks = mutableListOf<() -> Unit>()

    init {
      if (readOnly) {
        hibernateSession.isDefaultReadOnly = true
        hibernateSession.hibernateFlushMode = FlushMode.MANUAL
      }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : DbEntity<T>> save(entity: T): Id<T> {
      check(!readOnly) { "Saving isn't permitted in a read only session." }
      return when (entity) {
        is DbChild<*, *> -> (hibernateSession.save(entity) as Gid<*, *>).id
        is DbRoot<*> -> hibernateSession.save(entity)
        is DbUnsharded<*> -> hibernateSession.save(entity)
        else -> throw IllegalArgumentException(
            "You need to sub-class one of [DbChild, DbRoot, DbUnsharded]")
      } as Id<T>
    }

    override fun <T : DbEntity<T>> delete(entity: T) {
      check(!readOnly) {
        "Deleting isn't permitted in a read only session."
      }
      return hibernateSession.delete(entity)
    }

    override fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T {
      return hibernateSession.get(type.java, id)
    }

    override fun <R : DbRoot<R>, T : DbSharded<R, T>> loadSharded(
      gid: Gid<R, T>,
      type: KClass<T>
    ): T {
      return hibernateSession.get(type.java, gid)
    }

    override fun <T : DbEntity<T>> loadOrNull(id: Id<T>, type: KClass<T>): T? {
      return hibernateSession.get(type.java, id)
    }

    override fun shards(): Set<Shard> {
      return if (config.type == DataSourceType.VITESS) {
        useConnection { connection ->
          connection.createStatement().use {
            it.executeQuery("SHOW VITESS_SHARDS")
                .map { parseShard(it.getString(1)) }
                .toSet()
          }
        }
      } else SINGLE_SHARD_SET
    }

    private fun parseShard(string: String): Shard {
      val (keyspace, shard) = string.split('/', limit = 2)
      return Shard(Keyspace(keyspace), shard)
    }

    override fun <T> target(shard: Shard, function: () -> T): T {
      if (config.type == DataSourceType.VITESS) {
        return useConnection { connection ->
          val previousTarget = withoutChecks {
            // TODO we need to parse out the tablet type (replica or master) from the current target and keep that when we target the new shard
            // We should only change the shard we're targeting, not the tablet type
            val previousTarget =
                connection.createStatement().use { statement ->
                  statement.executeQuery("SHOW VITESS_TARGET").uniqueString()
                }
            connection.createStatement().use { statement ->
              statement.execute("USE `$shard`")
            }

            previousTarget
          }
          try {
            function()
          } finally {
            withoutChecks {
              val sql = if (previousTarget.isBlank()) {
                "USE"
              } else {
                "USE `$previousTarget`"
              }
              connection.createStatement().use { it.execute(sql) }
            }
          }
        }
      } else {
        return function()
      }
    }

    override fun <T> useConnection(work: (Connection) -> T): T {
      return hibernateSession.doReturningWork(work)
    }

    internal fun preCommit() {
      preCommitHooks.forEach { preCommitHook ->
        // Propagate hook exceptions up to the transacter so that the the transaction is rolled
        // back and the error gets returned to the application.
        preCommitHook()
      }
    }

    override fun onPreCommit(work: () -> Unit) {
      preCommitHooks.add(work)
    }

    internal fun postCommit() {
      postCommitHooks.forEach { postCommitHook ->
        try {
          postCommitHook()
        } catch (th: Throwable) {
          throw PostCommitHookFailedException(th)
        }
      }
    }

    override fun onPostCommit(work: () -> Unit) {
      postCommitHooks.add(work)
    }

    override fun close() {
      sessionCloseHooks.forEach { sessionCloseHook ->
        sessionCloseHook()
      }
    }

    override fun onSessionClose(work: () -> Unit) {
      sessionCloseHooks.add(work)
    }

    override fun <T> withoutChecks(vararg checks: Check, body: () -> T): T {
      val previous = disabledChecks
      val actualChecks = if (checks.isEmpty()) {
        EnumSet.allOf(Check::class.java)
      } else {
        EnumSet.of(checks[0], *checks)
      }
      disabledChecks = actualChecks
      return try {
        body()
      } finally {
        disabledChecks = previous
      }
    }
  }

  private fun <T> maybeWithTracing(spanName: String, block: () -> T): T {
    return if (tracer != null) tracer.traceWithSpan(spanName) { span ->
      Tags.COMPONENT.set(span, TRANSACTER_SPAN_TAG)
      block()
    } else {
      block()
    }
  }

  private inline fun <T, R> ThreadLocal<T>.withValue(value: T, block: () -> R): R {
    check(get() == null) { "Attempted to start a nested session" }

    set(value)
    try {
      return block()
    } finally {
      remove()
    }
  }
}
