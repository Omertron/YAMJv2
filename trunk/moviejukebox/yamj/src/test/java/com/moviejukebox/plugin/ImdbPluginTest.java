/*
 *      Copyright (c) 2004-2013 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      This file is part of the Yet Another Movie Jukebox (YAMJ).
 *
 *      The YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 */
package com.moviejukebox.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.model.Person;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;

public class ImdbPluginTest {

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Ignore
    public void testImdbMoviePlotLong() {
        PropertiesUtil.setProperty("imdb.site", "us");
        PropertiesUtil.setProperty("imdb.plot", "long");
        PropertiesUtil.setProperty("imdb.full.info", true);
        ImdbPlugin imdbPlugin = new ImdbPlugin();

        Movie movie = new Movie();
        movie.setId(ImdbPlugin.IMDB_PLUGIN_ID, "tt1515091");

        assertTrue(imdbPlugin.scan(movie));
        assertNotNull(movie.getPlot());
        assertNotEquals(Movie.UNKNOWN, movie.getPlot());
    }

    @Ignore
    public void testImdbTvShow() {
        PropertiesUtil.setProperty("imdb.site", "us");
        PropertiesUtil.setProperty("mjb.includeEpisodePlots", true);
        PropertiesUtil.setProperty("imdb.full.info", true);
        ImdbPlugin imdbPlugin = new ImdbPlugin();

        Movie movie = new Movie();
        movie.setMovieType(Movie.TYPE_TVSHOW);
        movie.setId(ImdbPlugin.IMDB_PLUGIN_ID, "tt0369179");

        MovieFile mf1 = new MovieFile();
        mf1.setSeason(8);
        mf1.setFirstPart(12);
        mf1.setLastPart(12);
        movie.addMovieFile(mf1);
        MovieFile mf2 = new MovieFile();
        mf2.setSeason(8);
        mf2.setFirstPart(14);
        mf2.setLastPart(14);
        movie.addMovieFile(mf2);

        assertTrue(imdbPlugin.scan(movie));
        assertEquals("Chocolate Diddlers or My Puppy's Dead", mf1.getTitle(12));
        assertTrue(StringUtils.startsWith(mf1.getPlot(12), "When Charlie and Courtney break up"));
        assertEquals("2010-12-13", mf1.getFirstAired(12));
        assertEquals("Lookin' for Japanese Subs", mf2.getTitle(14));
        assertTrue(StringUtils.startsWith(mf2.getPlot(14), "Charlie continues to obsess over Rose"));
        assertEquals("2011", mf2.getFirstAired(14));
    }

    @Ignore
    public void testImdbPerson() {
        PropertiesUtil.setProperty("imdb.site", "us");
        ImdbPlugin imdbPlugin = new ImdbPlugin();

        Person person = new Person();
        person.setName("Daniel Craig");

        assertTrue(imdbPlugin.scan(person));
        assertNotNull(person.getBiography());
        assertNotEquals(Movie.UNKNOWN, person.getBiography());
    }

    @Test
    public void testImdb_NewLayout() {
        PropertiesUtil.setProperty("imdb.site", "us");
        PropertiesUtil.setProperty("imdb.full.info", "false");
        PropertiesUtil.setProperty("mjb.scrapeAwards", "true");
        ImdbPlugin imdbPlugin = new ImdbPlugin();

        Movie movie = new Movie();
        movie.setId(ImdbPlugin.IMDB_PLUGIN_ID, "tt0499549");
        movie.setBaseName("Test Movie - Avatar");

        assertTrue(imdbPlugin.scan(movie));
        assertEquals("Incorrect year", "2009", movie.getYear());
        assertNotEquals("Incorrect Plot", Movie.UNKNOWN, movie.getPlot());
        assertTrue("Incorrect Rating", movie.getRating(ImdbPlugin.IMDB_PLUGIN_ID) > 0);
        assertEquals("Incorrect Country", "USA", movie.getCountry());
        assertEquals("Incorrect Company", "Twentieth Century Fox Film Corporation", movie.getCompany());
        assertTrue("Incorrect Tagline", StringTools.isValidString(movie.getTagline()));
        assertEquals("Incorrect number of cast", 10, movie.getCast().size());
        assertEquals("Incorrect Directors", 1, movie.getDirectors().size());
        assertEquals("Incorrect Writers", 1, movie.getWriters().size());
        assertTrue("No awards scraped", movie.getAwards().size() > 1);
    }

    @Ignore
    public void testImdb_Combined() {
        PropertiesUtil.setProperty("imdb.site", "us");
        PropertiesUtil.setProperty("imdb.full.info", "true");
        ImdbPlugin imdbPlugin = new ImdbPlugin();

        Movie movie = new Movie();
        movie.setId(ImdbPlugin.IMDB_PLUGIN_ID, "tt0499549");

        assertTrue(imdbPlugin.scan(movie));
        assertEquals("Incorrect year", "2009", movie.getYear());
        assertNotEquals("Incorrect Plot", Movie.UNKNOWN, movie.getPlot());
        assertTrue("Incorrect Rating", movie.getRating(ImdbPlugin.IMDB_PLUGIN_ID) > 0);
        assertEquals("Incorrect Country", "USA", movie.getCountry());
        assertEquals("Incorrect Company", "Twentieth Century Fox Film Corporation", movie.getCompany());
        assertEquals("Incorrect Tagline", "Enter the World", movie.getTagline());
        assertEquals("Incorrect number of cast", 10, movie.getCast().size());
        assertEquals("Incorrect Directors", 1, movie.getDirectors().size());
        assertEquals("Incorrect Writers", 1, movie.getWriters().size());
    }

    @Ignore
    public void testScanNFO() {
        String nfo = "\nhttp://www.imdb.com/title/tt0458339/\n";
        Movie movie = new Movie();

        ImdbPlugin imdbPlugin = new ImdbPlugin();
        imdbPlugin.scanNFO(nfo, movie);
        assertEquals("tt0458339", movie.getId(ImdbPlugin.IMDB_PLUGIN_ID));
    }
}
