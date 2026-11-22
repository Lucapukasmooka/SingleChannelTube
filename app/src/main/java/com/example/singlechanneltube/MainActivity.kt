package com.example.singlechanneltube

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val channelId = "UCxYCsSGQwJ3Q9tZLfUJt0bw"
    private lateinit var webView: WebView
    private lateinit var playerContainer: FrameLayout
    private lateinit var listView: ListView
    private lateinit var spinner: ProgressBar

    data class VideoItem(val id: String, val title: String, val published: String)
    private val videos = mutableListOf<VideoItem>()
    private var currentAllowedVideoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val header = TextView(this).apply {
            text = "@Cherrycatxo — Locked Mode"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        root.addView(header)

        playerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                650
            )
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }

        webView = WebView(this)
        setupLockedWebView()
        playerContainer.addView(webView)

        val closeBtn = Button(this).apply {
            text = "Close"
            setOnClickListener {
                webView.loadUrl("about:blank")
                playerContainer.visibility = View.GONE
                currentAllowedVideoId = null
            }
        }
        playerContainer.addView(closeBtn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            setMargins(16, 16, 16, 16)
        })

        root.addView(playerContainer)

        spinner = ProgressBar(this).apply {
            setPadding(0, 64, 0, 0)
        }
        root.addView(spinner)

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            playLockedVideo(videos[position].id)
        }
        root.addView(listView)

        setContentView(root)
        loadChannelVideos()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLockedWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                val allowed = currentAllowedVideoId
                return !(allowed != null && url.startsWith("https://www.youtube.com/embed/$allowed"))
            }
        }
    }

    private fun playLockedVideo(videoId: String) {
        currentAllowedVideoId = videoId
        playerContainer.visibility = View.VISIBLE

        val html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background:#000;">
                <iframe 
                    width="100%" 
                    height="100%" 
                    src="https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1&playsinline=1" 
                    frameborder="0" 
                    allow="autoplay; encrypted-media" 
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun loadChannelVideos() {
        thread {
            try {
                val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                val conn = URL(rssUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                val xmlText = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseRssFeed(xmlText)

                runOnUiThread {
                    spinner.visibility = View.GONE
                    videos.clear()
                    videos.addAll(parsed)
                    val titles = videos.map { "${it.title}\n${it.published.take(10)}" }
                    listView.adapter = object : ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_list_item_1,
                        titles
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = super.getView(position, convertView, parent) as TextView
                            view.setTextColor(Color.WHITE)
                            view.setPadding(36, 28, 36, 28)
                            return view
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    spinner.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseRssFeed(xml: String): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inEntry = false
        var currentTag = ""
        var videoId = ""
        var title = ""
        var published = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "entry") {
                        inEntry = true
                        videoId = ""
                        title = ""
                        published = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inEntry) {
                        when (currentTag) {
                            "yt:videoId" -> videoId = parser.text
                            "title" -> title = parser.text
                            "published" -> published = parser.text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        if (videoId.isNotEmpty()) {
                            result.add(VideoItem(videoId, title, published))
                        }
                        inEntry = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return result
    }
}
