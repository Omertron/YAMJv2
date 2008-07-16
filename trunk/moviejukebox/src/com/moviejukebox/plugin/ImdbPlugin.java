package com.moviejukebox.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.HTMLTools;

public class ImdbPlugin implements MovieDatabasePlugin {

	public static String IMDB_PLUGIN_ID = "imdb";

	protected static Logger logger = Logger.getLogger("moviejukebox");
	protected String preferredSearchEngine;
	protected String preferredPosterSearchEngine;
	protected boolean perfectMatch;
	protected int maxGenres;
	protected String preferredCountry;
	protected String imdbPlot;

	@Override
	public void init(Properties props) {
		preferredSearchEngine = props.getProperty("imdb.id.search", "imdb");
		preferredPosterSearchEngine = props.getProperty("imdb.alternate.poster.search", "google");
		perfectMatch = Boolean.parseBoolean(props.getProperty("imdb.perfect.match", "true"));
		preferredCountry = props.getProperty("imdb.preferredCountry", "USA");
		imdbPlot = props.getProperty("imdb.plot", "short");
		try {
			String temp = props.getProperty("imdb.genres.max", "9");
			System.out.println("imdb.genres.max=" + temp);
			maxGenres = Integer.parseInt(temp);
		} catch (NumberFormatException ex) {
			maxGenres = 9;
		}
	}

	public void scan(Movie mediaFile) {

		String imdbId = mediaFile.getId(IMDB_PLUGIN_ID);
		if (imdbId == null || imdbId.equalsIgnoreCase(Movie.UNKNOWN)) {
			imdbId = getImdbId(mediaFile.getTitle(), mediaFile.getYear());
			mediaFile.setId(IMDB_PLUGIN_ID, imdbId);
		}

		if (!imdbId.equalsIgnoreCase(Movie.UNKNOWN)) {
			updateImdbMediaInfo(mediaFile);
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year. This routine is base on a IMDb request.
	 */
	protected String getImdbId(String movieName, String year) {
		if ("google".equalsIgnoreCase(preferredSearchEngine)) {
			return getImdbIdFromGoogle(movieName, year);
		} else if ("yahoo".equalsIgnoreCase(preferredSearchEngine)) {
			return getImdbIdFromYahoo(movieName, year);
		} else if ("none".equalsIgnoreCase(preferredSearchEngine)) {
			return Movie.UNKNOWN;
		} else {
			return getImdbIdFromImdb(movieName, year);
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year. This routine is base on a yahoo request.
	 */
	private String getImdbIdFromYahoo(String movieName, String year) {
		try {
			StringBuffer sb = new StringBuffer("http://fr.search.yahoo.com/search;_ylt=A1f4cfvx9C1I1qQAACVjAQx.?p=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));

			if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
				sb.append("+%28").append(year).append("%29");
			}

			sb.append("+site%3Aimdb.com&fr=yfp-t-501&ei=UTF-8&rd=r1");

			String xml = request(new URL(sb.toString()));
			int beginIndex = xml.indexOf("/title/tt");
			StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 7), "/\"");
			String imdbId = st.nextToken();

			if (imdbId.startsWith("tt")) {
				return imdbId;
			} else {
				return Movie.UNKNOWN;
			}

		} catch (Exception e) {
			logger.severe("Failed retreiving imdb Id for movie : " + movieName);
			logger.severe("Error : " + e.getMessage());
			return Movie.UNKNOWN;
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year. This routine is base on a google request.
	 */
	private String getImdbIdFromGoogle(String movieName, String year) {
		try {
			StringBuffer sb = new StringBuffer("http://www.google.fr/search?hl=fr&q=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));

			if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
				sb.append("+%28").append(year).append("%29");
			}

			sb.append("+site%3Awww.imdb.com&meta=");

			String xml = request(new URL(sb.toString()));
			int beginIndex = xml.indexOf("/title/tt");
			StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 7), "/\"");
			String imdbId = st.nextToken();

			if (imdbId.startsWith("tt")) {
				return imdbId;
			} else {
				return Movie.UNKNOWN;
			}

		} catch (Exception e) {
			logger.severe("Failed retreiving imdb Id for movie : " + movieName);
			logger.severe("Error : " + e.getMessage());
			return Movie.UNKNOWN;
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year. This routine is base on a IMDb request.
	 */
	private String getImdbIdFromImdb(String movieName, String year) {
		try {
			StringBuffer sb = new StringBuffer("http://www.imdb.com/find?s=tt&q=");
			sb.append(URLEncoder.encode(movieName, "iso-8859-1"));

			if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
				sb.append("+%28").append(year).append("%29");
			}
			sb.append(";s=tt;site=aka");

			String xml = request(new URL(sb.toString()));

			// Try to have a more accurate search result
			// by considering "exact matches" categories
			if (perfectMatch) {
				int beginIndex = xml.indexOf("Popular Titles");
				if (beginIndex != -1) {
					xml = xml.substring(beginIndex);
				}

				// Try to find an exact match first...
				// never know... that could be ok...
				int movieIndex;
				if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
					movieIndex = xml.indexOf(movieName + " </a> (" + year + ")");
				} else {
					movieIndex = xml.indexOf(movieName);
				}

				// Let's consider Exact Matches first
				beginIndex = xml.indexOf("Titles (Exact Matches)");
				if (beginIndex != -1 && movieIndex > beginIndex) {
					xml = xml.substring(beginIndex);
				}
			}

			int beginIndex = xml.indexOf("title/tt");
			StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 6), "/\"");
			String imdbId = st.nextToken();

			if (imdbId.startsWith("tt")) {
				return imdbId;
			} else {
				return Movie.UNKNOWN;
			}

		} catch (Exception e) {
			logger.severe("Failed retreiving imdb Id for movie : " + movieName);
			logger.severe("Error : " + e.getMessage());
			return Movie.UNKNOWN;
		}
	}

	protected String getPreferredValue(ArrayList<String> values) {
		String value = null;
		for (String text : values) {
			String country = null;

			int pos = text.indexOf(':');
			if (pos != -1) {
				country = text.substring(0, pos);
				text = text.substring(pos + 1);
			}
			if (country == null) {
				if (value == null) {
					value = text;
				}
			} else {
				if (country.equals(preferredCountry)) {
					return text;
				}
			}
		}
		return value;
	}

	/**
	 * Scan IMDB html page for the specified movie
	 */
	private void updateImdbMediaInfo(Movie movie) {
		try {
			String xml = request(new URL("http://www.imdb.com/title/" + movie.getId(IMDB_PLUGIN_ID)));

			movie.setTitleSort(extractTag(xml, "<title>", 0, "()><"));
			movie.setRating(parseRating(extractTag(xml, "<b>User Rating:</b>", 2)));
			// movie.setPlot(extractTag(xml, "<h5>Plot:</h5>"));
			movie.setDirector(extractTag(xml, "<h5>Director:</h5>", 1));
			movie.setReleaseDate(extractTag(xml, "<h5>Release Date:</h5>"));
			movie.setRuntime(getPreferredValue(extractTags(xml, "<h5>Runtime:</h5>", "</div>")));

			movie.setCountry(extractTag(xml, "<h5>Country:</h5>", 1));
			movie.setCompany(extractTag(xml, "<h5>Company:</h5>", 1));
			int count = 0;
			for (String genre : extractTags(xml, "<h5>Genre:</h5>", "</div>", "<a href=\"/Sections/Genres/", "</a>")) {
				movie.addGenre(genre);
				if (++count >= maxGenres) {
					break;
				}
			}

			String plot = "None";
			if (imdbPlot.equalsIgnoreCase("long")) {
				plot = getLongPlot(movie);
			}
			// even if "long" is set we will default to the "short" one if none
			// was found
			if (imdbPlot.equalsIgnoreCase("short") || plot.equals("None")) {
				plot = extractTag(xml, "<h5>Plot:</h5>");
				if (plot.startsWith("a class=\"tn15more")) {
					plot = "None";
				}
			}
			movie.setPlot(plot);

			movie.setCertification(getPreferredValue(extractTags(xml, "<h5>Certification:</h5>", "</div>",
					"<a href=\"/List?certificates=", "</a>")));

			if (movie.getYear() == null || movie.getYear().isEmpty() || movie.getYear().equalsIgnoreCase(Movie.UNKNOWN)) {

				int beginIndex = xml.indexOf("<a href=\"/Sections/Years/");
				StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 25), "\"");
				try {
					movie.setYear(st.nextToken().trim());
				} catch (NumberFormatException e) {
				}
			}

			movie.setCast(extractTags(xml, "<table class=\"cast\">", "</table>", "<td class=\"nm\"><a href=\"/name/",
					"</a>"));

			if (movie.getPosterURL() != null && !movie.getPosterURL().equalsIgnoreCase(Movie.UNKNOWN)) {
				// we already have a poster URL
				// logger.info("Movie already has PosterURL : " +
				// movie.getPosterURL());
				return;
			}
			int castIndex = xml.indexOf("<h3>Cast</h3>");
			int beginIndex = xml.indexOf("src=\"http://ia.media-imdb.com/images");

			String posterURL = Movie.UNKNOWN;

			// Check posters.motechnet.com
			if (this.testMotechnetPoster(movie.getId(IMDB_PLUGIN_ID))) {
				posterURL = "http://posters.motechnet.com/covers/" + movie.getId(IMDB_PLUGIN_ID) + "_largeCover.jpg";
			} // Check www.impawards.com
			else if (!(posterURL = this.testImpawardsPoster(movie.getId(IMDB_PLUGIN_ID))).equals(Movie.UNKNOWN)) {
				// Cover Found
			} // Check www.moviecovers.com (if set in property file)
			else if ("moviecovers".equals(preferredPosterSearchEngine)
					&& !(posterURL = this.getPosterURLFromMoviecoversViaGoogle(movie.getTitle())).equals(Movie.UNKNOWN)) {
				// Cover Found
			} else if (beginIndex < castIndex && beginIndex != -1) {

				StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 5), "\"");
				posterURL = st.nextToken();
				int index = posterURL.indexOf("_SY");
				if (index != -1) {
					posterURL = posterURL.substring(0, index) + "_SY800_SX600_.jpg";
				}
			} else {
				// try searching an alternate search engine
				String alternateURL = Movie.UNKNOWN;
				if ("google".equalsIgnoreCase(preferredPosterSearchEngine)) {
					alternateURL = getPosterURLFromGoogle(movie.getTitle());
				} else if ("yahoo".equalsIgnoreCase(preferredPosterSearchEngine)) {
					alternateURL = getPosterURLFromYahoo(movie.getTitle());
				}

				if (!alternateURL.equalsIgnoreCase(Movie.UNKNOWN)) {
					posterURL = alternateURL;
				}
			}

			movie.setPosterURL(posterURL);

			if (movie.isTVShow()) {
				updateTVShowInfo(movie);
			}

		} catch (Exception e) {
			logger.severe("Failed retreiving imdb rating for movie : " + movie.getId(IMDB_PLUGIN_ID));
			e.printStackTrace();
		}
	}

	private int parseRating(String rating) {
		StringTokenizer st = new StringTokenizer(rating, "/ ()");
		try {
			return (int) Float.parseFloat(st.nextToken()) * 10;
		} catch (Exception e) {
			return -1;
		}
	}

	protected String extractTag(String src, String findStr) {
		return this.extractTag(src, findStr, 0);
	}

	protected String extractTag(String src, String findStr, int skip) {
		return this.extractTag(src, findStr, skip, "><");
	}

	protected String extractTag(String src, String findStr, int skip, String separator) {
		int beginIndex = src.indexOf(findStr);
		StringTokenizer st = new StringTokenizer(src.substring(beginIndex + findStr.length()), separator);
		for (int i = 0; i < skip; i++) {
			st.nextToken();
		}

		String value = HTMLTools.decodeHtml(st.nextToken().trim());
		if (value.indexOf("uiv=\"content-ty") != -1 || value.indexOf("cast") != -1 || value.indexOf("title") != -1
				|| value.indexOf("<") != -1) {
			value = Movie.UNKNOWN;
		}

		return value;
	}

	protected ArrayList<String> extractTags(String src, String sectionStart, String sectionEnd) {
		return extractTags(src, sectionStart, sectionEnd, null, "|");
	}

	protected ArrayList<String> extractTags(String src, String sectionStart, String sectionEnd, String startTag,
			String endTag) {
		ArrayList<String> tags = new ArrayList<String>();
		int index = src.indexOf(sectionStart);
		if (index == -1) {
			return tags;
		}
		index += sectionStart.length();
		int endIndex = src.indexOf(sectionEnd, index);
		if (endIndex == -1) {
			return tags;
		}

		String sectionText = src.substring(index, endIndex);
		int lastIndex = sectionText.length();
		index = 0;
		int startLen = 0;
		int endLen = endTag.length();

		if (startTag != null) {
			index = sectionText.indexOf(startTag);
			startLen = startTag.length();
		}

		while (index != -1) {
			index += startLen;
			int close = sectionText.indexOf('>', index);
			if (close != -1) {
				index = close + 1;
			}
			endIndex = sectionText.indexOf(endTag, index);
			if (endIndex == -1) {
				endIndex = lastIndex;
			}
			String text = sectionText.substring(index, endIndex);

			tags.add(HTMLTools.decodeHtml(text.trim()));
			endIndex += endLen;
			if (endIndex > lastIndex) {
				break;
			}
			if (startTag != null) {
				index = sectionText.indexOf(startTag, endIndex);
			} else {
				index = endIndex;
			}
		}
		return tags;
	}

	public String request(URL url) throws IOException {
		StringWriter content = null;

		try {
			content = new StringWriter();

			BufferedReader in = null;
			try {
				URLConnection cnx = url.openConnection();
				cnx.setRequestProperty("User-Agent", "Mozilla/5.25 Netscape/5.0 (Windows; I; Win95)");

				in = new BufferedReader(new InputStreamReader(cnx.getInputStream()));

				String line;
				while ((line = in.readLine()) != null) {
					content.write(line);
				}

			} finally {
				if (in != null) {
					in.close();
				}
			}

			return content.toString();
		} finally {
			if (content != null) {
				content.close();
			}
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year. This routine is base on a yahoo request.
	 */
	protected String getPosterURLFromYahoo(String movieName) {
		try {
			StringBuffer sb = new StringBuffer("http://fr.images.search.yahoo.com/search/images?p=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));
			sb.append("+poster&fr=&ei=utf-8&js=1&x=wrt");

			String xml = request(new URL(sb.toString()));
			int beginIndex = xml.indexOf("imgurl=");
			int endIndex = xml.indexOf("%26", beginIndex);

			if (beginIndex != -1 && endIndex > beginIndex) {
				return URLDecoder.decode(xml.substring(beginIndex + 7, endIndex), "UTF-8");
			} else {
				return Movie.UNKNOWN;
			}

		} catch (Exception e) {
			logger.severe("Failed retreiving poster URL from yahoo images : " + movieName);
			logger.severe("Error : " + e.getMessage());
			return Movie.UNKNOWN;
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year. This routine is base on a yahoo request.
	 */
	protected String getPosterURLFromGoogle(String movieName) {
		try {
			StringBuffer sb = new StringBuffer("http://images.google.fr/images?q=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));
			sb.append("&gbv=2");

			String xml = request(new URL(sb.toString()));
			int beginIndex = xml.indexOf("imgurl=") + 7;

			if (beginIndex != -1) {
				StringTokenizer st = new StringTokenizer(xml.substring(beginIndex), "\"&");
				return st.nextToken();
			} else {
				return Movie.UNKNOWN;
			}
		} catch (Exception e) {
			logger.severe("Failed retreiving poster URL from yahoo images : " + movieName);
			logger.severe("Error : " + e.getMessage());
			return Movie.UNKNOWN;
		}
	}

	public boolean testMotechnetPoster(String movieId) {
		String content = null;
		try {
			content = request((new URL("http://posters.motechnet.com/title/" + movieId + "/")));
		} catch (Exception e) {
		}

		return content != null && content.contains("/covers/" + movieId + "_largeCover.jpg");
	}

	public String testImpawardsPoster(String movieId) throws IOException {
		String returnString = Movie.UNKNOWN;
		String content = null;
		try {
			content = request((new URL(
					"http://search.yahoo.com/search?fr=yfp-t-501&ei=UTF-8&rd=r1&p=site:impawards.com+link:http://www.imdb.com/title/"
							+ movieId)));
		} catch (Exception e) {
		}

		if (content != null) {
			int indexMovieLink = content.indexOf("<span class=url>www.<b>impawards.com</b>/");
			if (indexMovieLink != -1) {
				String finMovieUrl = content.substring(indexMovieLink + 41, content.indexOf("</span>", indexMovieLink));
				finMovieUrl = finMovieUrl.replaceAll("<wbr />", "");

				int indexLastRep = finMovieUrl.lastIndexOf('/');
				String imgRepUrl = "http://www.impawards.com/" + finMovieUrl.substring(0, indexLastRep) + "/posters";
				returnString = imgRepUrl + finMovieUrl.substring(indexLastRep, finMovieUrl.lastIndexOf('.')) + ".jpg";
			}
		}

		return returnString;
	}

	protected String getPosterURLFromMoviecoversViaGoogle(String movieName) {
		try {
			String returnString = Movie.UNKNOWN;
			StringBuffer sb = new StringBuffer("http://www.google.com/search?meta=&q=site%3Amoviecovers.com+");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));

			String content = request(new URL(sb.toString()));
			if (content != null) {
				int indexMovieLink = content.indexOf("<a href=\"http://www.moviecovers.com/film/titre_");
				if (indexMovieLink != -1) {
					String finMovieUrl = content.substring(indexMovieLink + 47, content.indexOf("\" class=l>",
							indexMovieLink));
					returnString = "http://www.moviecovers.com/getjpg.html/"
							+ finMovieUrl.substring(0, finMovieUrl.lastIndexOf('.')).replace("+", "%20") + ".jpg";
				}
			}
			// System.out.println(returnString);
			return returnString;

		} catch (Exception e) {
			logger.severe("Failed retreiving moviecovers poster URL from google : " + movieName);
			logger.severe("Error : " + e.getMessage());
			return Movie.UNKNOWN;
		}
	}

	/**
	 * Get the TV show information from IMDb
	 * 
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	private void updateTVShowInfo(Movie movie) throws MalformedURLException, IOException {
		if (!movie.isTVShow()) {
			return;
		}

		/*
		 * String title = movie.getTitleSort(); if (title.startsWith("\"")) { StringTokenizer st = new
		 * StringTokenizer(title, "\""); movie.setTitleSort(st.nextToken()); }
		 */

		String xml = request(new URL("http://www.imdb.com/title/" + movie.getId(IMDB_PLUGIN_ID) + "/episodes"));

		int season = movie.getSeason();
		for (MovieFile file : movie.getFiles()) {
			int episode = file.getPart();
			String episodeName = extractTag(xml, "<h4>Season " + season + ", Episode " + episode + ":", 2);

			if (episodeName.indexOf("Episode #") == -1) {
				file.setTitle(episodeName);
			} else {
				file.setTitle(Movie.UNKNOWN);
			}
		}
	}

	/**
	 * Retrieves the long plot description from IMDB if it exists, else "None"
	 * 
	 * @param movie
	 * @return long plot
	 */
	private String getLongPlot(Movie movie) {
		String plot = "None";

		try {
			String xml = request(new URL("http://www.imdb.com/title/" + movie.getId(IMDB_PLUGIN_ID) + "/plotsummary"));

			String result = extractTag(xml, "<p class=\"plotpar\">");
			if (!result.equalsIgnoreCase(Movie.UNKNOWN) && result.indexOf("This plot synopsis is empty") < 0) {
				plot = result;
			}
		} catch (Exception e) {
			plot = "None";
		}

		return plot;
	}
}
