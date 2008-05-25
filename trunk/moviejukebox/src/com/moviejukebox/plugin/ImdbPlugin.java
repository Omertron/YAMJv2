package com.moviejukebox.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.StringTokenizer;

import com.moviejukebox.MovieJukeboxTools;
import com.moviejukebox.model.Movie;

public class ImdbPlugin implements MovieDatabasePlugin {
	
	public void scan(Movie mediaFile) {

		String imdbId = mediaFile.getId();
		if (imdbId == null || imdbId.equalsIgnoreCase("Unknown")) {
			imdbId = getImdbId(mediaFile.getTitle(), mediaFile.getYear());
			mediaFile.setId(imdbId);
		}

		if (!imdbId.equalsIgnoreCase("Unknown")) {
			updateImdbMediaInfo(mediaFile);
		}
	}

	
	/**
	 * retrieve the imdb matching the specified movie name and year.
	 * This routine is base on a yahoo request.
	 */
	private String getImdbIdFromYahoo(String movieName, String year) {
		try {
			StringBuffer sb = new StringBuffer("http://fr.search.yahoo.com/search;_ylt=A1f4cfvx9C1I1qQAACVjAQx.?p=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));
			
			if (year != null && !year.equalsIgnoreCase("Unknown")) {
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
				return "Unknown";
			}

		} catch (Exception e) {
			System.err.println("Failed retreiving imdb Id for movie : " + movieName);
			System.err.println("Error : " + e.getMessage());
			return "Unknown";
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year.
	 * This routine is base on a google request.
	 */
	private String getImdbIdFromGoogle(String movieName, String year) {
		try {
			StringBuffer sb = new StringBuffer("http://www.google.fr/search?hl=fr&q=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));
			
			if (year != null && !year.equalsIgnoreCase("Unknown")) {
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
				return "Unknown";
			}

		} catch (Exception e) {
			System.err.println("Failed retreiving imdb Id for movie : " + movieName);
			System.err.println("Error : " + e.getMessage());
			return "Unknown";
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year.
	 * This routine is base on a IMDb request.
	 */
	private String getImdbId(String movieName, String year) {
		try {
			StringBuffer sb = new StringBuffer("http://www.imdb.com/find?s=tt&q=");
			sb.append(URLEncoder.encode(movieName, "iso-8859-1"));
			
			if (year != null && !year.equalsIgnoreCase("Unknown")) {
				sb.append("+%28").append(year).append("%29");
			}
			sb.append(";s=tt;site=aka");
			
			String xml = request(new URL(sb.toString()));

			int beginIndex = xml.indexOf("Popular Titles");
			if (beginIndex != -1) {
				xml = xml.substring(beginIndex);
			}
			
			// Try to find an exact match first... 
			// never know... that could be ok...
			int movieIndex;
			if (year != null && !year.equalsIgnoreCase("Unknown")) {
				movieIndex = xml.indexOf(movieName +" </a> ("+year+")");
			} else {
				movieIndex = xml.indexOf(movieName);
			}
			
			// Let's consider Exact Matches first
			beginIndex = xml.indexOf("Titles (Exact Matches)");			
			if (beginIndex != -1 && movieIndex > beginIndex) {
				xml = xml.substring(beginIndex);
			}
			
			beginIndex = xml.indexOf("title/tt");
			StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 6), "/\"");
			String imdbId = st.nextToken();

			if (imdbId.startsWith("tt")) {
				return imdbId;
			} else {
				return "Unknown";
			}

		} catch (Exception e) {
			System.err.println("Failed retreiving imdb Id for movie : " + movieName);
			System.err.println("Error : " + e.getMessage());
			return "Unknown";
		}
	}

	/**
	 * Scan IMDB html page for the specified movie
	 */
	private void updateImdbMediaInfo(Movie movie) {
		try {
			String xml = request(new URL("http://www.imdb.com/title/" + movie.getId()));

			movie.setTitleSort(extractTag(xml, "<title>", 0, "()><"));
			movie.setRating(extractTag(xml, "<b>User Rating:</b>",2));
			movie.setPlot(extractTag(xml, "<h5>Plot:</h5>"));
			movie.setDirector(extractTag(xml, "<h5>Director:</h5>", 1));
			movie.setReleaseDate(extractTag(xml, "<h5>Release Date:</h5>"));
			movie.setRuntime(extractTag(xml, "<h5>Runtime:</h5>"));
			movie.setCountry(extractTag(xml, "<h5>Country:</h5>", 1));
			movie.setCompany(extractTag(xml, "<h5>Company:</h5>", 1));
			movie.addGenre(extractTag(xml, "<h5>Genre:</h5>", 1));
			movie.setPlot(extractTag(xml, "<h5>Plot:</h5>"));

			if (movie.getPlot().startsWith("a class=\"tn15more")) {
				movie.setPlot("None");
			}

			if (movie.getYear() == null 
			 || movie.getYear().isEmpty()
			 || movie.getYear().equalsIgnoreCase("Unknown")) {
			
				int beginIndex = xml.indexOf("<a href=\"/Sections/Years/");
				StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 25), "\"");
				try {
					movie.setYear(st.nextToken().trim());
				} catch (NumberFormatException e) { }
			}

			
			int castIndex = xml.indexOf("<h3>Cast</h3>");
			int beginIndex = xml.indexOf("src=\"http://ia.media-imdb.com/images");
			
			String posterURL = "Unknown";
			if (beginIndex<castIndex && beginIndex != -1) {
				
				StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 5), "\"");
				posterURL = st.nextToken();
				int index = posterURL.indexOf("_SY");
				if (index != -1) {
					posterURL = posterURL.substring(0, index) + "_SY800_SX600_.jpg";
				}
			} else { 
				// try searching yahoo
				String yahooURL = getPosterURLFromGoogle(movie.getTitle());
				if (!yahooURL.equalsIgnoreCase("Unknown")) {
					posterURL = yahooURL;
				}
			}
			
			movie.setPosterURL(posterURL);

		} catch (Exception e) {
			System.err.println("Failed retreiving imdb rating for movie : " + movie.getId());
			e.printStackTrace();
		}
	}

	private String extractTag(String src, String findStr) {
		return this.extractTag(src, findStr, 0);
	}

	private String extractTag(String src, String findStr, int skip) {
		return this.extractTag(src, findStr, skip, "><");
	}

	private String extractTag(String src, String findStr, int skip,
			String separator) {
		int beginIndex = src.indexOf(findStr);
		StringTokenizer st = new StringTokenizer(src.substring(beginIndex
				+ findStr.length()), separator);
		for (int i = 0; i < skip; i++)
			st.nextToken();
		
		String value = MovieJukeboxTools.decodeHtml(st.nextToken().trim());
		if (   (value.indexOf("uiv=\"content-ty")!= -1) 
			    || (value.indexOf("cast")!= -1)
		    	|| (value.indexOf("title")!= -1)
			    || (value.indexOf("<")!= -1)) {
			value = "Unknown";
		}
		
		return value;
	}

	public String request(URL url) throws IOException {
		StringWriter content = null;

		try {
			content = new StringWriter();

			BufferedReader in = null;
			try {
				URLConnection cnx = url.openConnection();
				cnx.setRequestProperty(
					"User-Agent",
					"Mozilla/5.25 Netscape/5.0 (Windows; I; Win95)");

				in = new BufferedReader(new InputStreamReader(cnx.getInputStream()));

				String line;
				while ((line = in.readLine()) != null) {
					content.write(line);
				}
				
			} finally {
				if (in != null)
					in.close();
			}

			return content.toString();
		} finally {
			if (content != null)
				content.close();
		}
	}

	static final int BUFF_SIZE = 100000;
	static final byte[] buffer = new byte[BUFF_SIZE];

	/**
	 * Try to download the movie poster for the specified media file from the
	 * imdb site. This method uses the actual posterURL of the movie.
	 */
	public void downloadPoster(String jukeboxDetailsRoot, Movie mediaFile) {
		String posterFilename = jukeboxDetailsRoot + File.separator + mediaFile.getBaseName() + ".jpg";
		File posterFile = new File(posterFilename);		

		// Do not overwrite existing posters
		if (posterFile.exists())
			return;

		posterFile.getParentFile().mkdirs();

		if (mediaFile.getPosterURL() == null || mediaFile.getPosterURL().equalsIgnoreCase("Unknown")) {
			MovieJukeboxTools.copyResource("dummy.jpg", jukeboxDetailsRoot, mediaFile.getBaseName() + ".jpg");
			return;
		}

		InputStream in = null;
		OutputStream out = null;

		try {
			URL url = new URL(mediaFile.getPosterURL());
			URLConnection cnx = url.openConnection();

			// Let's pretend we're Firefox...
			cnx.setRequestProperty(
				"User-Agent",
				"Mozilla/5.0 (X11; U; Linux x86_64; en-GB; rv:1.8.1.5) Gecko/20070719 Iceweasel/2.0.0.5 (Debian-2.0.0.5-0etch1)");

			try {
				in = cnx.getInputStream();
				out = new FileOutputStream(posterFile);

				while (true) {
					synchronized (buffer) {
						int amountRead = in.read(buffer);
						if (amountRead == -1) {
							break;
						}
						out.write(buffer, 0, amountRead);
					}
				}
			} finally {
				if (in != null) {
					in.close();
				}

				if (out != null) {
					out.close();
				}
			}
		} catch (Exception e) {
			System.err.println("Failed downloading movie poster : " + mediaFile.getPosterURL());
			MovieJukeboxTools.copyResource("dummy.jpg", jukeboxDetailsRoot, mediaFile.getBaseName() + ".jpg");
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year.
	 * This routine is base on a yahoo request.
	 */
	private String getPosterURLFromYahoo(String movieName) {
		try {
			StringBuffer sb = new StringBuffer("	http://fr.images.search.yahoo.com/search/images?p=");
			sb.append(URLEncoder.encode(movieName, "UTF-8"));			
			sb.append("+poster&fr=&ei=utf-8&js=1&x=wrt");

			String xml = request(new URL(sb.toString()));
			int beginIndex = xml.indexOf("imgurl=");
			int endIndex = xml.indexOf("%26",beginIndex);

			if (beginIndex != -1 && endIndex>beginIndex) {
				return URLDecoder.decode(xml.substring(beginIndex + 7, endIndex), "UTF-8");
			} else {
				return "Unknown";
			}

		} catch (Exception e) {
			System.err.println("Failed retreiving poster URL from yahoo images : " + movieName);
			System.err.println("Error : " + e.getMessage());
			return "Unknown";
		}
	}

	/**
	 * retrieve the imdb matching the specified movie name and year.
	 * This routine is base on a yahoo request.
	 */
	private String getPosterURLFromGoogle(String movieName) {
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
				return "Unknown";
			}
		} catch (Exception e) {
			System.err.println("Failed retreiving poster URL from yahoo images : " + movieName);
			System.err.println("Error : " + e.getMessage());
			return "Unknown";
		}
	}
}
