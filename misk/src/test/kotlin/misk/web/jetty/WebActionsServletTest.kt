package misk.web.jetty

import misk.Action
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.ResponseContentType
import misk.web.SocketAddress
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebUnixDomainSocketConfig
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.client.UnixDomainSocketFactory
import java.io.File
import java.util.UUID
import jakarta.inject.Inject

@MiskTest(startService = true)
class WebActionsServletTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  internal lateinit var jettyService: JettyService

  private var socketName: String = "@udstest" + UUID.randomUUID().toString()

  @Test
  fun networkSocketSuccess() {
    val response = get("/potato", false)
    assertThat(response.header("ActualSocketName")).isEqualTo(
      with(jettyService.httpServerUrl) { "$host:$port" }
    )
  }

  @Test
  fun parseNonAsciiHeaders() {
    val response = get(
      "/potato", false,
      Headers.Builder()
        .addUnsafeNonAscii("X-device-name", "Walé Iphone")
        .build()
    )

    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun udsSocketSuccess() {
    val response = get("/potato", true)
    assertThat(response.header("ActualSocketName")).isEqualTo(socketName)
  }

  @Test
  fun testPatch404() {
    val response = call(
      Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath("/fooasdf/").build())
        .patch("bar".toRequestBody())
    )
    assertThat(response.body?.string()).contains("Nothing found at PATCH", "fooasdf")
  }

  internal class WebActionsServletNetworkInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      chain.httpCall.addResponseHeaders(
        Headers.Builder()
          .set(
            "ActualSocketName",
            with(chain.httpCall.linkLayerLocalAddress) {
              when (this) {
                is SocketAddress.Network -> "${this.ipAddress}:${this.port}"
                is SocketAddress.Unix -> this.path
                else -> "null"
              }
            }
          )
          .build()
      )
    }

    class Factory : NetworkInterceptor.Factory {
      override fun create(action: Action): NetworkInterceptor? =
        WebActionsServletNetworkInterceptor()
    }
  }

  internal class TestAction @Inject constructor() : WebAction {
    @Get("/potato")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(): TestActionResponse {
      return TestActionResponse("foo")
    }
  }

  internal data class TestActionResponse(val text: String)

  private fun get(
    path: String,
    viaUDS: Boolean,
    headers: Headers = Headers.headersOf()
  ): okhttp3.Response =
    with(
      Request.Builder()
        .headers(headers)
        .url(
          jettyService.httpServerUrl.newBuilder().encodedPath(path)
            .build()
        )
    ) {
      when {
        viaUDS -> {
          udsCall(get())
        }

        else -> {
          call(get())
        }
      }
    }

  private fun call(request: Request.Builder): okhttp3.Response {
    return OkHttpClient().newCall(request.build()).execute()
  }

  private fun udsCall(request: Request.Builder): okhttp3.Response {
    return OkHttpClient().newBuilder()
      .socketFactory(UnixDomainSocketFactory(File(socketName)))
      .build()
      .newCall(request.build())
      .execute()
  }

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            unix_domain_socket = WebUnixDomainSocketConfig(path = socketName)
          )
        )
      )
      install(MiskTestingServiceModule())

      multibind<NetworkInterceptor.Factory>().toInstance(
        WebActionsServletNetworkInterceptor.Factory()
      )

      install(WebActionModule.create<TestAction>())
    }
  }
}
