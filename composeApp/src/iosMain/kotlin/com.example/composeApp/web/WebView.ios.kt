package com.example.composeApp.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.example.composeApp.extensions.addProgressObservers
import com.example.composeApp.extensions.removeProgressObservers
import com.example.composeApp.jsbridge.WebViewJsBridge
import com.example.composeApp.util.toUIColor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.setValue
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled

@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    factory: (WebViewFactoryParam) -> NativeWebView,
) {
    IOSWebView(
        state = state,
        modifier = modifier,
        captureBackPresses = captureBackPresses,
        navigator = navigator,
        webViewJsBridge = webViewJsBridge,
        onCreated = onCreated,
        onDispose = onDispose,
        factory = factory,
    )
}


actual data class WebViewFactoryParam(val config: WKWebViewConfiguration)

@OptIn(ExperimentalForeignApi::class)
actual fun defaultWebViewFactory(param: WebViewFactoryParam) = WKWebView(frame = CGRectZero.readValue(), configuration = param.config)

@OptIn(ExperimentalForeignApi::class)
@Composable
fun IOSWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    factory: (WebViewFactoryParam) -> NativeWebView,
) {
    val observer = remember {
        WKWebViewObserver(
            state = state,
            navigator = navigator,
        )
    }

    val navigationDelegate = remember { WKNavigationDelegate(state, navigator) }
    val scope = rememberCoroutineScope()

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
                defaultWebpagePreferences.allowsContentJavaScript = state.webSettings.isJavaScriptEnabled
                preferences.apply {
                    setValue(state.webSettings.allowFileAccessFromFileURLs, forKey = "allowFileAccessFromFileURLs")
                    javaScriptEnabled = state.webSettings.isJavaScriptEnabled
                }
                setValue(state.webSettings.allowUniversalAccessFromFileURLs, forKey = "allowUniversalAccessFromFileURLs")
            }

            factory(WebViewFactoryParam(config)).apply {
                onCreated(this)
                state.viewState?.let {
                    this.interactionState = it
                }
                allowsBackForwardNavigationGestures = captureBackPresses
                customUserAgent = state.webSettings.customUserAgentString
                this.addProgressObservers(
                    observer = observer,
                )

                this.navigationDelegate = navigationDelegate

                state.webSettings.let {
                    val backgroundColor = (it.iOSWebSettings.backgroundColor ?: it.backgroundColor).toUIColor()
                    val scrollViewColor = (it.iOSWebSettings.underPageBackgroundColor ?: it.backgroundColor).toUIColor()
                    setOpaque(it.iOSWebSettings.opaque)
                    if (!it.iOSWebSettings.opaque) {
                        setBackgroundColor(backgroundColor)
                        scrollView.setBackgroundColor(scrollViewColor)
                    }
                    scrollView.pinchGestureRecognizer?.enabled = it.supportZoom
                }
                state.webSettings.iOSWebSettings.let {
                    with(scrollView) {
                        bounces = it.bounces
                        scrollEnabled = it.scrollEnabled
                        showsHorizontalScrollIndicator = it.showHorizontalScrollIndicator
                        showsVerticalScrollIndicator = it.showVerticalScrollIndicator
                    }
                }
            }.also {
                val iosWebView = IOSWebView(it, scope, webViewJsBridge)
                state.webView = iosWebView
                state.webView?.webView?.inspectable = true // TEST;
                webViewJsBridge?.webView = iosWebView
            }
        },
        modifier = modifier,
        onRelease = {
            state.webView = null
            it.removeProgressObservers(observer = observer)

            it.navigationDelegate = null
            onDispose(it)
        },
    )
}
