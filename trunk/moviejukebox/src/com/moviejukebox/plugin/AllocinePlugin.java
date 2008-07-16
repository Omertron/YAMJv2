package com.moviejukebox.plugin;

import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;

import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.HTMLTools;

public class AllocinePlugin extends ImdbPlugin {

	protected Properties storedProps;

	public static String ALLOCINE_PLUGIN_ID = "allocine";

	@Override
	public void init(Properties props) {
		super.init(props);
		storedProps = props;
		preferredCountry = props.getProperty("imdb.preferredCountry", "France");
		logger.setLevel(Level.FINER);
	}

	/**
	 * Scan Allocine html page for the specified Tv Show
	 */
	private void updateTVShowInfo(Movie movie) {
		try {
			String xml = request(new URL("http://www.allocine.fr/series/ficheserie_gen_cserie="
					+ movie.getId(ALLOCINE_PLUGIN_ID) + ".html"));

			movie.setTitleSort(extractTag(xml, "<title>", "</title>"));
			movie.setRating(parseRating(extractTag(xml, "<h4>Note moyenne :", "</h4>")));
			movie.setPlot(removeHtmlTags(extractTag(xml, "Synopsis</span> :", "</h5>")));
			movie.setDirector(removeHtmlTags(extractTag(xml, "<h4>S�rie cr��e par", "</a> en")));
			movie.setRuntime(extractTag(xml, "Format</span> : ", "."));
			movie.setCountry(extractTag(xml, "Nationalit�</span> :", "</h5>"));

			int count = 0;
			for (String genre : extractTags(xml, "Genre</span> :", "-", " ", ",")) {
				movie.addGenre(genre);
				if (++count >= maxGenres) {
					break;
				}
			}

			// movie.setCertification(getPreferredValue(extractTags(xml,
			// "<h5>Certification:</h5>", "</div>",
			// "<a href=\"/List?certificates=", "</a>")));

			if (movie.getYear() == null || movie.getYear().isEmpty() || movie.getYear().equalsIgnoreCase("Unknown")) {

				movie.setYear(extractTag(xml, "</a> en", "</h4>"));
			}

			movie.setCast(extractTags(xml, "<h4>Avec", "</h4>", "personne/fichepersonne_gen_cpersonne", "</a>"));

			updatePoster(movie);
			// start a new request for seasons details
			xml = request(new URL("http://www.allocine.fr/series/episodes_gen_cserie="
					+ movie.getId(ALLOCINE_PLUGIN_ID) + ".html"));
			for (String seasonTag : extractHtmlTags(xml, "<h4><b>Choisir une saison</b> :", "<table",
					"<a href=\"/series/episodes_gen_csaison", "</a>")) {
				try {
					int seasonId = Integer.valueOf(removeHtmlTags(seasonTag));
					logger.finest("New Season detected = " + seasonId);
					String seasonAllocineId = extractTag(seasonTag, "/series/episodes_gen_csaison=", "&");
					logger.finest("Season Id = " + seasonAllocineId);
					logger.finest("Season IdI = " + seasonId + ", movie.getSeason() = " + movie.getSeason());
					if (seasonId == movie.getSeason()) {
						// we found the right season, time to get the infos
						logger.finest("The right Season IdI = " + seasonId);
						xml = request(new URL("http://www.allocine.fr/series/episodes_gen_csaison=" + seasonAllocineId
								+ "&cserie=" + movie.getId(ALLOCINE_PLUGIN_ID) + ".html"));
						for (MovieFile file : movie.getFiles()) {
							int episode = file.getPart();
							String episodeName = removeHtmlTags(extractTag(xml, "<b>Episode " + episode
									+ "</b></h4>&nbsp;-&nbsp;", "</td>"));
							logger.finest("episodeName = " + episodeName);
							file.setTitle(episodeName);
						}
					}
				} catch (Exception e) {
					// logger.severe("Error while getting season infos " + e);
					// nothing to do, we skip this season
				}
			}
		} catch (Exception e) {
			logger.severe("Failed retreiving alloCine TVShow info for " + movie.getId(ALLOCINE_PLUGIN_ID));
			e.printStackTrace();
		}
	}

	/**
	 * Scan Allocine html page for the specified movie
	 */
	private void updateMovieInfo(Movie movie) {
		try {
			String xml = request(new URL("http://www.allocine.fr/film/fichefilm_gen_cfilm="
					+ movie.getId(ALLOCINE_PLUGIN_ID) + ".html"));

			movie.setTitleSort(extractTag(xml, "<title>", "</title>"));
			movie.setRating(parseRating(extractTag(xml, "<h4>Note moyenne :", "</h4>")));
			movie.setPlot(removeHtmlTags(extractTag(xml, "<h3><b>Synopsis</b></h3>", "</h4>")));
			movie.setDirector(removeHtmlTags(extractTag(xml, "<h4>R�alis� par ", "</h4>")));
			movie.setReleaseDate(extractTag(xml, "<h4>Date de sortie : <b>", "</b>"));
			movie.setRuntime(extractTag(xml, "<h4>Dur�e : ", "</h4>"));
			movie.setCountry(extractTag(xml, "<h4>Film", "."));
			movie.setCompany(removeHtmlTags(extractTag(xml, "<h4>Distribu� par ", "</h4>")));

			int count = 0;
			for (String genre : extractTags(xml, "<h4>Genre : ", "</h4>", "film/alaffiche_genre_gen_genre", "</a>")) {
				movie.addGenre(genre);
				if (++count >= maxGenres) {
					break;
				}
			}

			// movie.setCertification(getPreferredValue(extractTags(xml,
			// "<h5>Certification:</h5>", "</div>",
			// "<a href=\"/List?certificates=", "</a>")));

			if (movie.getYear() == null || movie.getYear().isEmpty() || movie.getYear().equalsIgnoreCase("Unknown")) {
				movie.setYear(extractTag(xml, "<h4>Ann�e de production : ", "</h4>"));
			}

			movie.setCast(extractTags(xml, "<h4>Avec", "</h4>", "personne/fichepersonne_gen_cpersonne", "</a>"));

			updatePoster(movie);

		} catch (Exception e) {
			logger.severe("Failed retreiving allocine infos for movie : " + movie.getId(ALLOCINE_PLUGIN_ID));
			e.printStackTrace();
		}
	}

	private void updatePoster(Movie movie) {
		// make an Imdb request for poster
		if (movie.getPosterURL() != null && !movie.getPosterURL().equalsIgnoreCase("Unknown")) {
			// we already have a poster URL
			logger.finest("Movie already has PosterURL : " + movie.getPosterURL());
			return;
		}
		try {
			String posterURL = "Unknown";
			String xml;
			// Check alloCine first only for movies because TV Show posters are
			// wrong.
			if (!movie.isTVShow()) {
				String baseUrl = "http://www.allocine.fr/film/galerievignette_gen_cfilm=";
				xml = request(new URL(baseUrl + movie.getId(ALLOCINE_PLUGIN_ID) + ".html"));
				posterURL = extractTag(xml, "img id='imgNormal' class='photo' src='", "'");
				if (!posterURL.equalsIgnoreCase("Unknown")) {
					logger.finest("Movie PosterURL : " + posterURL);
					movie.setPosterURL(posterURL);
					return;
				}
			}
			// Check posters.motechnet.com
			if (this.testMotechnetPoster(movie.getId(IMDB_PLUGIN_ID))) {
				posterURL = "http://posters.motechnet.com/covers/" + movie.getId(IMDB_PLUGIN_ID) + "_largeCover.jpg";
				logger.finest("Movie PosterURL : " + posterURL);
				movie.setPosterURL(posterURL);
				return;
			} // Check www.impawards.com
			else if (!(posterURL = this.testImpawardsPoster(movie.getId(IMDB_PLUGIN_ID))).equalsIgnoreCase("Unknown")) {
				logger.finest("Movie PosterURL : " + posterURL);
				movie.setPosterURL(posterURL);
				return;
			} // Check www.moviecovers.com (if set in property file)
			else if ("moviecovers".equals(preferredPosterSearchEngine)
					&& !(posterURL = this.getPosterURLFromMoviecoversViaGoogle(movie.getTitle()))
							.equalsIgnoreCase("Unknown")) {
				logger.finest("Movie PosterURL : " + posterURL);
				movie.setPosterURL(posterURL);
				return;
			} else {
				xml = request(new URL("http://www.imdb.com/title/" + movie.getId(IMDB_PLUGIN_ID)));
				int castIndex = xml.indexOf("<h3>Cast</h3>");
				int beginIndex = xml.indexOf("src=\"http://ia.media-imdb.com/images");
				if (beginIndex < castIndex && beginIndex != -1) {

					StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 5), "\"");
					posterURL = st.nextToken();
					int index = posterURL.indexOf("_SY");
					if (index != -1) {
						posterURL = posterURL.substring(0, index) + "_SY800_SX600_.jpg";
					}
				} else {
					// try searching an alternate search engine
					String alternateURL = "Unknown";
					if ("google".equalsIgnoreCase(preferredPosterSearchEngine)) {
						alternateURL = getPosterURLFromGoogle(movie.getTitle());
					} else if ("yahoo".equalsIgnoreCase(preferredPosterSearchEngine)) {
						alternateURL = getPosterURLFromYahoo(movie.getTitle());
					}

					if (!alternateURL.equalsIgnoreCase("Unknown")) {
						posterURL = alternateURL;
					}
				}
			}
			logger.finest("Movie PosterURL : " + posterURL);
			movie.setPosterURL(posterURL);
		} catch (Exception e) {
			logger.severe("Failed retreiving poster for movie : " + movie.getId(ALLOCINE_PLUGIN_ID));
			e.printStackTrace();
		}

	}

	private int parseRating(String rating) {
		int index = rating.indexOf("etoile_");
		try {
			return (int) (Float.parseFloat(rating.substring(index + 7, index + 8)) / 4.0 * 100);
		} catch (Exception e) {
			return -1;
		}
	}

	@Override
	public void scan(Movie mediaFile) {
		try {
			String allocineId = mediaFile.getId(ALLOCINE_PLUGIN_ID);
			if (allocineId.equalsIgnoreCase(Movie.UNKNOWN)) {
				allocineId = getAllocineId(mediaFile.getTitle(), mediaFile.getYear(), mediaFile);
			}
			// we also get imdb Id for extra infos
			if (mediaFile.getId(IMDB_PLUGIN_ID).equalsIgnoreCase(Movie.UNKNOWN)) {
				mediaFile.setId(IMDB_PLUGIN_ID, getImdbId(mediaFile.getTitle(), mediaFile.getYear()));
				logger.finest("Found imdbId = " + mediaFile.getId(IMDB_PLUGIN_ID));
			}
			if (!allocineId.equalsIgnoreCase(Movie.UNKNOWN)) {
				mediaFile.setId(ALLOCINE_PLUGIN_ID, allocineId);
				if (mediaFile.isTVShow()) {
					updateTVShowInfo(mediaFile);
				} else {
					updateMovieInfo(mediaFile);
				}
			}
		} catch (ParseException e) {
			// If no AllocineId found fallback to Imdb
			ImdbPlugin imdbPlugin = new ImdbPlugin();
			imdbPlugin.init(storedProps);
			imdbPlugin.scan(mediaFile);
		}
	}

	/**
	 * retrieve the allocineId matching the specified movie name. This routine is base on a alloCine search.
	 * 
	 * @throws ParseException
	 */
	private String getAllocineId(String movieName, String year, Movie mediaFile) throws ParseException {
		String allocineId = Movie.UNKNOWN;
		try {
			StringBuffer sb = new StringBuffer("http://www.allocine.fr/recherche/?motcle=");
			sb.append(URLEncoder.encode(movieName.replace(' ', '+'), "iso-8859-1"));

			String xml = request(new URL(sb.toString()));

			int beginIndex;
			String alloCineMediaPrefix;
			if (mediaFile.isTVShow()) {
				beginIndex = xml.indexOf("<h3><b>S�ries TV <h4>");
				alloCineMediaPrefix = "/series/ficheserie_gen_cserie=";
			} else {
				beginIndex = xml.indexOf("<h3><b>Films <h4>");
				alloCineMediaPrefix = "/film/fichefilm_gen_cfilm=";
			}
			if (beginIndex != -1) {
				xml = xml.substring(beginIndex);
			}

			for (String searchResult : extractTags(xml,
					"<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width: 445;\">",
					"<script type=\"text/javascript\">", "<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">",
					"</table>")) {
				logger.finest("AlloCine SearchResult = " + searchResult);
				String searchResultYear = extractTag(searchResult, "<h4 style=\"color: #808080\">", "<");
				logger.finest("AlloCine searchResultYear = " + searchResultYear);
				if (year == null || year.equalsIgnoreCase(Movie.UNKNOWN) || year.equalsIgnoreCase(searchResultYear)) {
					int allocineIndexBegin = searchResult.indexOf(alloCineMediaPrefix) + alloCineMediaPrefix.length();
					int allocineIndexEnd = searchResult.indexOf(".html");

					allocineId = searchResult.substring(allocineIndexBegin, allocineIndexEnd);
					// validate that allocineId is an int
					Integer.valueOf(allocineId);
					logger.finer("Found AllocineId = " + allocineId);
					return allocineId;
				}
			}

			return Movie.UNKNOWN;
		} catch (Exception e) {
			logger.severe("Failed to retrieve alloCine Id for movie : " + movieName);
			// logger.severe("Error : " + e.getMessage());
			logger.severe("We fall back to ImdbPlugin");
			throw new ParseException(allocineId, 0);
		}
	}

	protected String extractTag(String src, String tagStart, String tagEnd) {
		int beginIndex = src.indexOf(tagStart);
		if (beginIndex < 0) {
			logger.finest("extractTag value= Unknown");
			return Movie.UNKNOWN;
		}
		try {
			String subString = src.substring(beginIndex + tagStart.length());
			int endIndex = subString.indexOf(tagEnd);
			if (endIndex < 0) {
				logger.finest("extractTag value= Unknown");
				return Movie.UNKNOWN;
			}
			subString = subString.substring(0, endIndex);

			String value = HTMLTools.decodeHtml(subString.trim());
			logger.finest("extractTag value=" + value);
			return value;
		} catch (Exception e) {
			logger.severe("extractTag an exception occurred during tag extraction : " + e);
			return Movie.UNKNOWN;
		}
	}

	protected String removeHtmlTags(String src) {
		return src.replaceAll("\\<.*?>", "");
	}

	@Override
	protected ArrayList<String> extractTags(String src, String sectionStart, String sectionEnd, String startTag,
			String endTag) {
		ArrayList<String> tags = new ArrayList<String>();
		int index = src.indexOf(sectionStart);
		if (index == -1) {
			logger.finest("extractTags no sectionStart Tags found");
			return tags;
		}
		index += sectionStart.length();
		int endIndex = src.indexOf(sectionEnd, index);
		if (endIndex == -1) {
			logger.finest("extractTags no sectionEnd Tags found");
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
		logger.finest("extractTags sectionText = " + sectionText);
		logger.finest("extractTags startTag = " + startTag);
		logger.finest("extractTags startTag index = " + index);
		while (index != -1) {
			index += startLen;
			int close = sectionText.indexOf('>', index);
			if (close != -1) {
				index = close + 1;
			}
			endIndex = sectionText.indexOf(endTag, index);
			if (endIndex == -1) {
				logger.finest("extractTags no endTag found");
				endIndex = lastIndex;
			}
			String text = sectionText.substring(index, endIndex);
			logger.finest("extractTags Tag found text = " + text);

			tags.add(HTMLTools.decodeHtml(text.trim()).trim());
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

	protected ArrayList<String> extractHtmlTags(String src, String sectionStart, String sectionEnd, String startTag,
			String endTag) {
		ArrayList<String> tags = new ArrayList<String>();
		int index = src.indexOf(sectionStart);
		if (index == -1) {
			logger.finest("extractTags no sectionStart Tags found");
			return tags;
		}
		index += sectionStart.length();
		int endIndex = src.indexOf(sectionEnd, index);
		if (endIndex == -1) {
			logger.finest("extractTags no sectionEnd Tags found");
			return tags;
		}

		String sectionText = src.substring(index, endIndex);
		int lastIndex = sectionText.length();
		index = 0;
		int endLen = endTag.length();

		if (startTag != null) {
			index = sectionText.indexOf(startTag);
		}
		logger.finest("extractTags sectionText = " + sectionText);
		logger.finest("extractTags startTag = " + startTag);
		logger.finest("extractTags startTag index = " + index);
		while (index != -1) {
			endIndex = sectionText.indexOf(endTag, index);
			if (endIndex == -1) {
				endIndex = lastIndex;
			}
			endIndex += endLen;
			String text = sectionText.substring(index, endIndex);
			logger.finest("extractTags Tag found text = " + text);
			tags.add(text);
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

}
