package com.prutprit

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.Requests
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

data class AnichiLoadData(
    val hash: String,
    val dubStatus: String,
    val episode: String,
    val idMal: Int? = null,
)

suspend fun main() {
    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(HydraHD())
    providerTester.testAll()
}

class HydraHD() : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://hydrahd.com"
    override var name = "HydraHD"
    var apiUrl = "https://hydrahd.com/ajax/tv_0.php"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries)

    override var lang = "en"

    var headers = mutableMapOf(
        "app-version" to "android_c-247",
        "platformstr" to "android_c",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Referer" to "https://hydrahd.com")

    var cookies = mapOf("PHPSESSID" to "fj45rf9a09hn8t5jvus6eig9d5")

    // enable this when your provider has a main page
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "trendingmovz" to "Trending Movies",
        "trendingshowz" to "Trending Series",
        "latestmovz" to "Latest Movies",
        "latestshowz" to "Latest Series",
        "ratedmovz" to "Top Rated Movies",
        "ratedshowz" to "Top Rated Series"
    )


    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?menu=search&query=${query.replace(' ', '+')}"

        val requests = Requests()
        val response = requests.get(url, headers = headers)
        val soup = Jsoup.parse(response.text)

        return elementToSearchResponse(soup)
    }


    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val requests = Requests()
        val response = requests.get(mainUrl, headers=headers)
//        Log.d("d_response", response.toString())

        val soup = Jsoup.parse(response.text)
        val section = soup.select("div.${request.data}")[0]

        var genre = TvType.Movie
        if ("movz" !in request.data) {
            genre = TvType.TvSeries
        }

        // Return a list of search responses mapped to the request name defined earlier.
        return newHomePageResponse(request.name, elementToSearchResponse(section, genre))
    }


    override suspend fun load(url: String): LoadResponse {
        val requests = Requests()
        val response = requests.get(url, headers = headers)
        cookies = response.cookies
        val soup = Jsoup.parse(response.text)

        val details = soup.select("div.col-sm-12.col-md-12.title-jumbo-text")[0]

        val posterUrl = details.select("img.hidden-xs")[0].attr("src")
        val title = details.select("h1")[0].text() ?: throw ErrorLoadingException("No Title")
        val plot = details.select("div.ploting")[0].text().trim()

        var cast: MutableList<String> = mutableListOf()
        val actorsList = details.select("div[itemprop=\"actor\"] > a")
        for (actor in actorsList){
            cast.add(actor.text())
        }

        val youtubeTrailer = details.selectFirst("button.watch-now-btn")?.attr("data-embed-url")

        val variousData = mutableMapOf<String, String>()
        val keys = details.select("div.row.diz-title > span > b")
        val values = details.select("div.row.diz-title > span > a")
        for (i in 0..<keys.size){
            variousData[keys[i].text().lowercase()] = values[i].text()
        }

        val year = variousData["year"]?.toInt()


        val videoPlaceholder = soup.select("span.ajaxlink_mov0")[0]
        val duration = videoPlaceholder.select("span")[1].text().trim()

        // This is required to know which sort of LoadResponse to return.
        // If the page is a movie it needs different metadata and will be displayed differently
        val isMovie = url.contains("/movie/")

        if (isMovie) {
            // retrieve metadata
            val regex = """\{"i":"(?<i>.*)","t":"(?<t>.*)"\}""".toRegex()
            val matchResult = regex.find(response.text)!!
            val i = matchResult.groups["i"]?.value
            val t = matchResult.groups["t"]?.value

            val sourceIds = getMovieSources(i, t, url)

//            var sourceIds = mutableMapOf(
//                "Server1" to mutableMapOf(
//                    "url" to "https://s-delivery34.mxcontent.net/v2/elr94dr1uqw4dr.mp4?s=XQR-Y6yDhWidPxPdPLtSkA&e=1725848288&_t=1725831337",
//                    "quality" to 1080
//                ),
//                "Server2" to mutableMapOf(
//                    "url" to "https://aa.bigtimedelivery.net/_v13/425dc3594411c58c16d6f3106fe7712267d93aad2846aaf272f643dddc3cf1146c1cac083115c580748c94648131b5a0cc73977feac837bcab767aaaa81226b74c3cfe13ce066ac4fdd42fed994b86d195b57a66ccdaae0573979a42c67ea75ff2acb1567df62e19779ec013599377deb926c5f3317d5baec060f91bef167423/1080/index.m3u8",
//                    "quality" to 420
//                )
//            )

//            val resolver = WebViewResolver(Regex("https://.*vidsrc"))
//            val webViewResponse = resolver.resolveUsingWebView(serverResponse)
//            val webViewSoup = Jsoup.parse(webViewResponse)
//            Log.d("WEB_VIEW", webViewSoup.text)


            // Movies
            return newMovieLoadResponse(title, url, TvType.Movie, sourceIds) {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                addDuration(duration)
                addActors(cast)
                addTrailer(youtubeTrailer)
            }
        } else{
            return newMovieLoadResponse(title, url, TvType.Movie, "") {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                addDuration(duration)
                addActors(cast)
                addTrailer(youtubeTrailer)
            }
        }
    }


    fun elementToSearchResponse(element: Element, genre: TvType=TvType.Movie): List<SearchResponse>{
        var showList = element.select("figure.figured")
        if (showList.isEmpty()){
            showList = element.select("div.swiper-slide")
        }

        var results: MutableList<SearchResponse> = mutableListOf()
        for (show in showList) {
            val title = show.select("div.title")[0].text().trim()
            val poster = show.select("img.lazy-image")[0].attr("data-src")
            val showLink = mainUrl + show.select("a")[0].attr("href")

            val infos: Elements = show.select("span")
            val quality = infos[0].text().trim()
            val year = infos[3].text().trim()

            var type = genre
            if (infos.size > 1) {
                val parsedType: String = infos[1].text().trim()
                if (parsedType.lowercase().replace(" ", "") == "tv") {
                    type = TvType.TvSeries
                }
            }

            val response = newMovieSearchResponse(
                name=title,
                url=showLink,
                type=type
            )
            response.addPoster(poster)
            response.addQuality(quality)
            response.year = year.toInt()

            results.add(response)
        }

        return results
    }


    suspend fun getMovieSources(i: String?, t: String?, url: String): Map<String, Map<String, Any>>{
        var sourceIds: Map<String, Map<String, Any>> = mutableMapOf()

        // scrape server
        headers["i"] = i!!
        headers["t"] = t!!
        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0"
        headers["Host"] = "hydrahd.com"
        headers["Referer"] = url
        headers["Accept"] = "*/*"
        headers["DNT"] = "1"
        headers["Sec-GPC"] = "1"
        headers["Sec-Fetch-Dest"] = "empty"
        headers["Sec-Fetch-Mode"] = "cors"
        headers["Sec-Fetch-Site"] = "same-origin"
        headers["Connection"] = "keep-alive"
        headers["X-Requested-With"] = "XMLHttpRequest"

        val requests = Requests()
        var response = requests.get(apiUrl, headers=headers, cookies=cookies).text
        var soup = Jsoup.parse(response)

        for (serverButton in soup.select("div.iframe-server-button")){
            // get embed link
            var embedLink = serverButton.attr("data-link")
            if (embedLink.isEmpty()){
                continue
            }

            // parse embed link
            var website = "https://" + embedLink.split("/")[2]
            Log.d("d_WEBSITE", website)
            embedLink = when {
                "ythd" in website || "2embed" in website -> "$website/embed/$i"
                "vidsrc.vip" in website || "player.autoembed" in website -> "$website/embed/movie/$i"
                "vidsrc.pro" in website  -> "$website/embed/movie/$t"
                "moviesapi" in website -> "$website/movie/$t"
                "multiembed" in website -> "$website/directstream.php?video_id=$i"
                "primewire" in website -> "$website/embed/movie?imdb=$i"
                "streamsito" in website -> "$website/video/$i"
                "frembed" in website -> "$website/api/film.php?id=$i"
                else -> ""
            }
            if (embedLink.isEmpty()){
                continue
            }

            var found = false
            while (!found){
                response = requests.get(embedLink).text
                soup = Jsoup.parse(response)
                val iframes = soup.select("iframe")
                if (! iframes.isEmpty()) {
                    val src = iframes[0].attr("src")
                    if (src.isNotEmpty()) {
                        embedLink = fixUrl(src, website)
                        Log.d("    d_FRAMES", embedLink)
                        website = "https://" + embedLink.split("/")[2]
                    } else{
                        found = true
                    }
                } else {
                    found = true
                }
            }
            Log.d("  d_EMBED", embedLink)

        }

        return sourceIds
    }


    fun fixUrl(url: String, source: String): String {
        if (url.startsWith("http") ||
            // Do not fix JSON objects when passed as urls.
            url.startsWith("{\"")
        ) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }

        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return source + url
            }
            return "$source/$url"
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val serverList = parseJson<Map<String, Map<String, Any>>>(data)
        for (server in serverList.keys) {
            val link: String = serverList[server]!!["url"].toString()
            val quality: Int = serverList[server]!!["quality"].toString().toInt()
            var linkType = ExtractorLinkType.VIDEO
            if ("m3u8" in link){
                linkType = ExtractorLinkType.M3U8
            }
            Log.d("SERVER", "$server $quality $link")
            val extractor = ExtractorLink("source", server, link, mainUrl, quality, linkType)
            callback.invoke(extractor)
        }

        return true
    }
}