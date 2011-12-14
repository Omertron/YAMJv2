/*
 *      Copyright (c) 2004-2011 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *
 *      For any reuse or distribution, you must make clear to others the
 *      license terms of this work.
 */
package com.moviejukebox.plugin;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;

import junit.framework.TestCase;

import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.WebBrowser;
import org.apache.log4j.BasicConfigurator;

public class FilmwebPluginTest extends TestCase {

    private FilmwebPluginMock filmwebPlugin;
    private Movie movie = new Movie();
    private boolean offline = true;

    static {
        PropertiesUtil.setPropertiesStreamName("./properties/moviejukebox-default.properties");
        BasicConfigurator.configure();
    }

    @Override
    protected void setUp() {
        // uncomment the line below to check if tests are still up to date
        // offline = false;
        filmwebPlugin = new FilmwebPluginMock(offline);
        movie = new Movie();
    }

    public void testGetFilmwebUrlFromGoogle() {
        filmwebPlugin.filmwebPreferredSearchEngine = "google";
        filmwebPlugin.setRequestResult("<font color=\"green\">http://www.filmweb.pl/Seksmisja - 84k</font>");
        assertEquals("http://www.filmweb.pl/Seksmisja", filmwebPlugin.getFilmwebUrl("Seksmisja", null));
    }

    public void testGetFilmwebUrlFromGoogleWithId() {
        filmwebPlugin.filmwebPreferredSearchEngine = "google";
        filmwebPlugin.setRequestResult("<font color=\"green\">http://www.filmweb.pl/serial/4400-2004-122684 - 90k</font>");
        assertEquals("http://www.filmweb.pl/serial/4400-2004-122684", filmwebPlugin.getFilmwebUrl("The 4400", null));
    }

    public void testGetFilmwebUrlFromYahoo() {
        filmwebPlugin.filmwebPreferredSearchEngine = "yahoo";
        filmwebPlugin.setRequestResult("<a data-bk=\"5034.1\" href=\"http://www.filmweb.pl/Seksmisja\" class=\"yschttl spt\" dirtyhref=\"http://rds.yahoo.com/_ylt=A0oG7hxsZvVMxwEAUx9XNyoA;_ylu=X3oDMTE1azRuN3ZwBHNlYwNzcgRwb3MDMQRjb2xvA2FjMgR2dGlkA1NSVDAwMV8xODc-/SIG=11jrti008/EXP=1291237356/**http%3a//www.filmweb.pl/Seksmisja\"><b>Seksmisja</b> (1983) - Filmweb</a>");
        assertEquals("http://www.filmweb.pl/Seksmisja", filmwebPlugin.getFilmwebUrl("Seksmisja", null));
    }

    public void testGetFilmwebUrlFromYahooWithId() {
        filmwebPlugin.filmwebPreferredSearchEngine = "yahoo";
        filmwebPlugin.setRequestResult("<a href=\"http://search.yahoo.com/web/advanced?ei=UTF-8&p=4400+site%3Afilmweb.pl&y=Search\">Advanced Search</a><a class=\"yschttl\" href=\"http://rds.yahoo.com/_ylt=A0geu5RTv7FI.jUB.DtXNyoA;_ylu=X3oDMTE1aGEzbmUyBHNlYwNzcgRwb3MDMQRjb2xvA2FjMgR2dGlkA01BUDAxMV8xMDg-/SIG=11rlibf7n/EXP=1219694803/**http%3a//www.filmweb.pl/serial/4400-2004-122684\" ><b>4400</b> / <b>4400</b>, The (2004) - Film - FILMWEB.pl</a>");
        assertEquals("http://www.filmweb.pl/serial/4400-2004-122684", filmwebPlugin.getFilmwebUrl("The 4400", null));
    }

    public void testGetFilmwebUrlFromFilmweb() {
        filmwebPlugin.filmwebPreferredSearchEngine = "filmweb";
        filmwebPlugin.setRequestResult("<a class=\"searchResultTitle\" href=\"/John.Rambo\"><b>John</b> <b>Rambo</b> / <b>Rambo</b> </a>");
        assertEquals("http://www.filmweb.pl/John.Rambo", filmwebPlugin.getFilmwebUrl("john rambo", null));
    }

    // This needs work, tv shows aren't supported in this code search (although they are on the site)
//    public void testGetFilmwebUrlFromFilmwebWithId() {
//        filmwebPlugin.filmwebPreferredSearchEngine = "filmweb";
//        filmwebPlugin.setRequestResult("<a class=\"searchResultTitle\" href=\"/serial/4400-2004-122684\"><b>4400</b> / <b>4400</b>, The </a>");
//        assertEquals("http://www.filmweb.pl/serial/4400-2004-122684", filmwebPlugin.getFilmwebUrl("The 4400", null));
//    }

    public void testScanNFONoUrl() {
        filmwebPlugin.scanNFO("", movie);
        assertEquals(Movie.UNKNOWN, movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));
    }

    public void testScanNFO() {
        filmwebPlugin.scanNFO("txt\ntxt\nfilmweb url: http://john.rambo.filmweb.pl - txt\ntxt", movie);
        assertEquals("http://john.rambo.filmweb.pl", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, Movie.UNKNOWN);
        filmwebPlugin.scanNFO("<url>http://john.rambo.filmweb.pl</url>", movie);
        assertEquals("http://john.rambo.filmweb.pl", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, Movie.UNKNOWN);
        filmwebPlugin.scanNFO("[url]http://john.rambo.filmweb.pl[/url]", movie);
        assertEquals("http://john.rambo.filmweb.pl", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, Movie.UNKNOWN);
        filmwebPlugin.scanNFO("http://www.filmweb.pl/f336379/Death+Sentence,2007", movie);
        assertEquals("http://www.filmweb.pl/f336379/Death+Sentence,2007", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, Movie.UNKNOWN);
        filmwebPlugin.scanNFO("http://4.pila.filmweb.pl\thttp://www.imdb.com/title/tt0890870/", movie);
        assertEquals("http://4.pila.filmweb.pl", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, Movie.UNKNOWN);
        filmwebPlugin.scanNFO("http://4.pila.filmweb.pl\nhttp://www.imdb.com/title/tt0890870/", movie);
        assertEquals("http://4.pila.filmweb.pl", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));
    }

    public void testScanNFOWithId() {
        filmwebPlugin.scanNFO("txt\ntxt\nfilmweb url: http://www.filmweb.pl/f122684/4400,2004 - txt\ntxt", movie);
        assertEquals("http://www.filmweb.pl/f122684/4400,2004", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));
    }

    public void testScanNFOWithPoster() {
        filmwebPlugin.scanNFO("txt\ntxt\nimg: http://gfx.filmweb.pl/po/18/54/381854/7131155.3.jpg - txt\ntxt", movie);
        assertEquals(Movie.UNKNOWN, movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

        filmwebPlugin.scanNFO("txt\ntxt\nimg: http://gfx.filmweb.pl/po/18/54/381854/7131155.3.jpg - txt\nurl: http://www.filmweb.pl/f122684/4400,2004", movie);
        assertEquals("http://www.filmweb.pl/f122684/4400,2004", movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));
    }

    public void testUpdateMediaInfoTitle() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Seksmisja");
        filmwebPlugin.setRequestResult("<title>Seksmisja (1984)  - Film - FILMWEB.pl</title>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("Seksmisja", movie.getTitle());
        assertEquals("Seksmisja", movie.getOriginalTitle());
    }

    public void testUpdateMediaInfoTitleWithOriginalTitle() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Ojciec.Chrzestny");
        filmwebPlugin.setRequestResult("<title>Ojciec chrzestny / Godfather, The (1972)  - Film - FILMWEB.pl</title>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("Ojciec chrzestny", movie.getTitle());
        assertEquals("The Godfather", movie.getOriginalTitle());
    }

    public void testUpdateMediaInfoRating() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Ojciec.Chrzestny");
        filmwebPlugin.setRequestResult("<span class=\"average\">            8,8      </span>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals(88, movie.getRating());
    }

    public void testUpdateMediaInfoTop250() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Ojciec.Chrzestny");
        filmwebPlugin.setRequestResult("<span class=worldRanking>2. <a href=\"/rankings/film/world#Ojciec chrzestny\">w rankingu światowym</a></span>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals(2, movie.getTop250());
    }

    public void testUpdateMediaInfoDirector() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/John.Rambo");
        filmwebPlugin.setRequestResult("<tr>            \t\t            \t\t\t<th>reżyseria:</th>            \t\t\t<td>            \t\t\t            \t\t\t\t                              \t\t\t\t                        \t\t\t\t\t<a href=\"/person/Sylvester.Stallone\" title=\"Sylvester Stallone\">Sylvester Stallone</a>            \t\t\t\t            \t\t\t            \t\t\t            \t\t\t</td>            \t\t\t\t\t\t\t</tr>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("Sylvester Stallone", movie.getDirector());
    }

    public void testUpdateMediaInfoReleaseDate() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/John.Rambo");
        filmwebPlugin.setRequestResult("<span id =\"filmPremierePoland\" style=\"display:none\">2008-03-07</span><span style=\"display: none;\" id=\"filmPremiereWorld\">2008-01-23</span>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("2008-01-23", movie.getReleaseDate());
    }

    public void testUpdateMediaInfoRuntime() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/John.Rambo");
        filmwebPlugin.setRequestResult("<table><tr><th>czas trwania:</th><td> 1 godz. 32 min. </td></tr><tr><th>gatunek:</th>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("92", movie.getRuntime());
    }

    public void testUpdateMediaInfoCountry() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/John.Rambo");
        filmwebPlugin.setRequestResult("<dt>kraje:</dt><dd><a href=\"/search/film?countryIds=38\">Niemcy</a>, <a href=\"/search/film?countryIds=53\">USA</a></dd>/dl></div>                                                <div class=\"topicsList lastTopicsList\"><div class=comBox><h2><a href=\"/John.Rambo/discussion\" class=\"hdrBig icoBig icoBigDiscuss\">dyskusja</a>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("Niemcy, USA", movie.getCountry());
    }

    public void testUpdateMediaInfoGenre() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/John.Rambo");
        filmwebPlugin.setRequestResult("<th>gatunek:</th><td><a href=\"/search/film?genreIds=26\">Wojenny</a>, <a href=\"/search/film?genreIds=28\">Akcja</a></td></tr><tr><th>premiera:</th>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals(Arrays.asList(new String[]{"Akcja", "Wojenny"}).toString(), movie.getGenres().toString());
    }

    public void testUpdateMediaInfoOutline() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Seksmisja");
        filmwebPlugin.setRequestResult("<span class=imgRepInNag>Seksmisja</span></h2><p class=cl><span class=filmDescrBg property=\"v:summary\">9 sierpnia 1991 roku telewizja transmituje epokowy eksperyment: dwóch śmiałków - Maks (Jerzy Stuhr) i Albert (Olgierd Łukaszewicz), zostaje poddanych hibernacji. Podczas ich snu wybucha wojna nuklearna. Uczestnicy eksperymentu budzą się w 2044 roku. Od opiekującej się nimi doktor Lamii dowiadują się, że w ciągu ostatnich kilkudziesięciu lat geny męskie zostały całkowicie zniszczone promieniowaniem, a oni są prawdopodobnie jedynymi osobnikami płci męskiej, którzy przetrwali kataklizm. Niezwykła<span> społeczność kobiet, w jakiej znaleźli się bohaterowie, egzystuje w całkowicie sztucznych warunkach, głęboko pod powierzchnią ziemi. Władzę dyktatorską pełni tu Jej Ekscelencja, która darzy męskich osobników szczególnym zainteresowaniem. Maks i Albert znajdują się pod stałą obserwacją i ścisłą kontrolą. Takie życie na dłuższą metę wydaje im się jednak niemożliwe. Zdesperowani postanawiają więc uciec. </span> <a href=\"#\" class=see-more>więcej </a></span>");
        filmwebPlugin.updateMediaInfo(movie);

        assertEquals(
               StringTools.trimToLength(
                   "9 sierpnia 1991 roku telewizja transmituje epokowy eksperyment: dwóch śmiałków - Maks (Jerzy Stuhr) i Albert (Olgierd Łukaszewicz), zostaje poddanych hibernacji. Podczas ich snu wybucha wojna nuklearna. Uczestnicy eksperymentu budzą się w 2044 roku. Od opiekującej się nimi doktor Lamii dowiadują się, że w ciągu ostatnich kilkudziesięciu lat geny męskie zostały całkowicie zniszczone promieniowaniem, a oni są prawdopodobnie jedynymi osobnikami płci męskiej, którzy przetrwali kataklizm. Niezwykła<span> społeczność kobiet, w jakiej znaleźli się bohaterowie, egzystuje w całkowicie sztucznych warunkach, głęboko pod powierzchnią ziemi. Władzę dyktatorską pełni tu Jej Ekscelencja, która darzy męskich osobników szczególnym zainteresowaniem. Maks i Albert znajdują się pod stałą obserwacją i ścisłą kontrolą. Takie życie na dłuższą metę wydaje im się jednak niemożliwe. Zdesperowani postanawiają więc uciec. ",
                   filmwebPlugin.preferredOutlineLength),
                movie.getOutline());
    }

    public void testUpdateMediaInfoPlot() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Waleczne.Serce");
        filmwebPlugin.preferredPlotLength = Integer.MAX_VALUE;
        filmwebPlugin.setRequestResult("<div class=\"filmDescription comBox\">\n  \t<h2>\n  \t\t<a href=\"/Waleczne.Serce/descs\" class=\"hdrBig icoBig icoBigArticles\">\n  \t\t\t opisy filmu   \t\t</a>\t\t\n\t\t\t\t\t<span class=\"hdrAddInfo\">(10)</span>\n\t\t\t\t\n  \t\t  \t\t<a href=\"\t\t\t/Waleczne.Serce/contribute/descriptions\t\" class=\"add-button\" title=\"dodaj  opis filmu \" rel=\"nofollow\">  \t\t\t<span>dodaj  opis filmu </span>\n\n  \t\t</a>\n\t\t<span class=\"imgRepInNag\">Braveheart - Waleczne Serce</span>\n  \t</h2>\n\t\n\t\t\t\t\t   \t   \t\t<p class=\"cl\"><span class=\"filmDescrBg\" property=\"v:summary\">Pod koniec XIII wieku Szkocja dostaje się pod panowanie angielskiego króla, Edwarda I. Przejęcie władzy odbywa się w wyjątkowo krwawych okolicznościach. Jednym ze świadków gwałtów i morderstw jest kilkunastoletni chłopak, William Wallace. Po latach spędzonych pod opieką wuja dorosły William wraca do rodzinnej wioski. Jedną z pierwszych osób, które spotyka, jest Murron - przyjaciółka z lat dzieciństwa. Dawne uczucie przeradza się w wielką i szczerą miłość. Niestety wkrótce dziewczyna ginie z rąk<span> angielskich żołnierzy. Wydarzenie to staje się to momentem przełomowym w życiu młodego Szkota. William decyduje się bowiem na straceńczą walkę z okupantem i po brawurowym ataku zdobywa warownię wroga. Dzięki ogromnej odwadze zostaje wykreowany na przywódcę powstania przeciw angielskiej tyranii...</span> <a href=\"#\" class=\"see-more\">więcej </a></span></p>\n   \t\t  </div>");
        filmwebPlugin.updateMediaInfo(movie);

        assertEquals(
            StringTools.trimToLength(
                "Pod koniec XIII wieku Szkocja dostaje się pod panowanie angielskiego króla, Edwarda I. Przejęcie władzy odbywa się w wyjątkowo krwawych okolicznościach. Jednym ze świadków gwałtów i morderstw jest kilkunastoletni chłopak, William Wallace. Po latach spędzonych pod opieką wuja dorosły William wraca do rodzinnej wioski. Jedną z pierwszych osób, które spotyka, jest Murron - przyjaciółka z lat dzieciństwa. Dawne uczucie przeradza się w wielką i szczerą miłość. Niestety wkrótce dziewczyna ginie z rąk angielskich żołnierzy. Wydarzenie to staje się to momentem przełomowym w życiu młodego Szkota. William decyduje się bowiem na straceńczą walkę z okupantem i po brawurowym ataku zdobywa warownię wroga. Dzięki ogromnej odwadze zostaje wykreowany na przywódcę powstania przeciw angielskiej tyranii...",
                filmwebPlugin.preferredPlotLength),
                movie.getPlot());
    }

    public void testUpdateMediaInfoYear() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Seksmisja");
        filmwebPlugin.setRequestResult("<title>Seksmisja (1983)  - Film - FILMWEB.pl</title>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("1983", movie.getYear());
    }

    public void testUpdateMediaInfoCast() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Seksmisja");
        filmwebPlugin.setRequestResult("<div class=\"castListWrapper cl\">\n    <ul class=\"list\">\n\n    \t\t<li class=\"clear_li\">\n\t\t\t<h3>\n        <a href=\"/person/Jerzy.Stuhr\" rel=\"v:starring\">\n\t\t\t\t<span class=\"pNoImg05\">\n                     \t\t<img width=\"38\" height=\"50\" src=\"http://gfx.filmweb.pl/p/01/10/110/145434.0.jpg\" alt=\"Jerzy Stuhr\">\n            \t\t\t\t</span>\n\t\t\t\tJerzy Stuhr\n        \t</a>\n\t\t\t</h3>\n\n        \t<div>\n                           Maks            \n            \t\t\t</div>        </li>\n    \t\t<li>\n\t\t\t<h3>\n        <a href=\"/person/Olgierd+%C5%81ukaszewicz-405\" rel=\"v:starring\">\n\t\t\t\t<span class=\"pNoImg05\">\n                     \t\t<img width=\"38\" height=\"50\" src=\"http://gfx.filmweb.pl/p/04/05/405/146158.0.jpg\" alt=\"Olgierd Łukaszewicz\">\n\n            \t\t\t\t</span>\n\t\t\t\tOlgierd Łukaszewicz\n        \t</a>\n\t\t\t</h3>\n\n        \t<div>\n                           Albert            \n            \t\t\t</div>        </li></ul>\n\n    </div>");
        filmwebPlugin.updateMediaInfo(movie);

        LinkedHashSet<String> testCast = new LinkedHashSet<String>();
        // These need to be in the same order as the web page
        testCast.add("Jerzy Stuhr");
        testCast.add("Olgierd Łukaszewicz");

        assertEquals(Arrays.asList(testCast.toArray()).toString(), Arrays.asList(Arrays.copyOf(movie.getCast().toArray(), 2)).toString());
    }

    public void testUpdateMediaInfoUpdateTVShowInfo() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/Prison.Break");
        MovieFile episode = new MovieFile();
        episode.setSeason(4);
        episode.setPart(1);
        movie.addMovieFile(episode);
        episode = new MovieFile();
        episode.setSeason(4);
        episode.setPart(2);
        movie.addMovieFile(episode);
        filmwebPlugin.setRequestResult("<h3>sezon 4</h3>\n   \t\t\t\t\t\t\t\t<a href=\"#\" class=\"seasonWatched common-button-third\" style=\"display:none\">\n\n   \t\t\t\t\t\t\t\t\t<span class=\"lBtbL\"></span>\n                                       <span class=\"lBtbR\"></span>\n                                       <span>Oglądałem</span>\n   \t\t\t\t\t\t\t\t</a>\n               \t\t\t\t</th>\n       \t\t\t\t\t</tr>\n               \t\t\t               \t\t\t\t   \t<tr>\n   \t\t<td>\n\n   \t\t\t   \t\t\t\todcinek&nbsp;1\n   \t\t\t   \t\t\t   \t\t</td>\n   \t\t<td>\n   \t\t\t   \t\t\t   \t\t\t\t   \t\t\t\t\t   \t\t\t\t\t   \t\t\t\t\t<div>\t\t\t\t\t\t\t\t\t\t1 września\t\t\t\t\t\t\t2008\n\t<br><span class=\"countryName\">(USA)</span></div>\n   \t\t\t\t   \t\t\t   \t\t</td>\n   \t\t<td>\n   \t\t\t   \t\t\t\tScylla\n   \t\t\t   \t\t</td>\n\n   \t\t   \t\t   \t\t   \t</tr>\n               \t\t\t               \t\t\t\t   \t<tr>\n   \t\t<td>\n   \t\t\t   \t\t\t\todcinek&nbsp;2\n   \t\t\t   \t\t\t   \t\t</td>\n   \t\t<td>\n   \t\t\t   \t\t\t   \t\t\t\t   \t\t\t\t\t   \t\t\t\t\t   \t\t\t\t\t<div>\t\t\t\t\t\t\t\t\t\t1 września\t\t\t\t\t\t\t2008\n\t<br><span class=\"countryName\">(USA)</span></div>\n\n   \t\t\t\t   \t\t\t   \t\t</td>\n   \t\t<td>\n   \t\t\t   \t\t\t\tBreaking and Entering\n   \t\t\t   \t\t</td>\n   \t\t   \t\t   \t\t   \t</tr>");
        filmwebPlugin.updateMediaInfo(movie);

        Iterator<MovieFile> episodesIt = movie.getFiles().iterator();
        assertEquals("Scylla", episodesIt.next().getTitle());
        assertEquals("Breaking and Entering", episodesIt.next().getTitle());
    }

    public void testUpdateMediaInfoNotOverwrite() {
        movie.setId(FilmwebPlugin.FILMWEB_PLUGIN_ID, "http://www.filmweb.pl/John.Rambo");
        movie.addDirector("John Doe");
        movie.addRating(FilmwebPlugin.FILMWEB_PLUGIN_ID, 30);
        movie.setPlot("Ble ble ble");
        filmwebPlugin.setRequestResult("<span class=\"average\">            8,9      </span><tr>            \t\t            \t\t\t<th>reżyseria:</th>            \t\t\t<td>            \t\t\t            \t\t\t\t                              \t\t\t\t                        \t\t\t\t\t<a href=\"/person/Sylvester.Stallone\" title=\"Sylvester Stallone\">Sylvester Stallone</a>            \t\t\t\t            \t\t\t            \t\t\t            \t\t\t</td>            \t\t\t\t\t\t\t</tr>");
        filmwebPlugin.updateMediaInfo(movie);
        assertEquals("John Doe", movie.getDirector());
        assertEquals(30, movie.getRating());
        assertEquals("Ble ble ble", movie.getPlot());
    }

    class FilmwebPluginMock extends FilmwebPlugin {

        private String requestResult;
        private boolean offline;

        public FilmwebPluginMock(boolean offline) {
            this.offline = offline;
            super.init();
        }

        @Override
        public void init() {
            webBrowser = new WebBrowser() {

                @Override
                public String request(URL url) throws IOException {
                    if (offline) {
                        return getRequestResult(url);
                    } else {
                        return super.request(url);
                    }
                }
            };
        }

        public String getRequestResult(URL url) {
            return requestResult;
        }

        public void setRequestResult(String requestResult) {
            this.requestResult = requestResult;
        }
    }
}
