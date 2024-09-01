package com.prutprit

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class HydraHD(val plugin: HydraHDPlugin) : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://hydrahd.com"
    override var name = "HydraHD"
    var apiUrl = "https://hydrahd.com/ajax/tv_0.php"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime)

    override var lang = "en"

    val headers = mapOf(
        "app-version" to "android_c-247",
        "platformstr" to "android_c",
        "User-Agent" to "justfoolingaround/1",
        "Referer" to "https://example.com/")

    // enable this when your provider has a main page
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "trendingmovz" to "Trending Movies",
        "trendingshowz" to "Trending Series",
        "latestmovz" to "Latest Movies",
        "latestshowz" to "Latest Series"
    )


    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?menu=search&query=${query.replace(' ', '+')}"

        val response = khttp.get(url, headers = headers)
        val soup = Jsoup.parse(response.text)

        return elementToSearchResponse(soup)
    }

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val response = khttp.get(mainUrl, headers = headers)
        val soup = Jsoup.parse(response.text)
        val section = soup.select("div.${request.data}")[0]

        var genre = TvType.Movie
        if ("movz" !in request.data) {
            genre = TvType.TvSeries
        }

        // Return a list of search responses mapped to the request name defined earlier.
        return newHomePageResponse(request.name, elementToSearchResponse(section, genre))
    }

    fun elementToSearchResponse(element: Element, genre: TvType=TvType.Movie): List<SearchResponse>{
        val showList = element.select("figure.figured")

        var results: MutableList<SearchResponse> = mutableListOf()
        for (show in showList) {
            val title = show.select("div.title")[0].text().trim()
            val year = show.select("div.year")[0].text().trim()
            val poster = show.select("img.img-responsive.hoverZoomLink.lazy-image")[0].attr("src")
            val showLink = mainUrl + show.select("a")[0].attr("href")

            val infos: Elements = show.select("span")
            val quality = infos[0].text().trim()

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
}