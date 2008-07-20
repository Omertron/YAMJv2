package com.moviejukebox.plugin;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.GraphicTools;

public class DefaultThumbnailPlugin implements MovieImagePlugin {

	private static Logger logger = Logger.getLogger("moviejukebox");

	private String skinHome; 
	private boolean addReflectionEffect;
	private boolean addPerspective;
	private boolean normalizeThumbnails;
	private boolean addHDLogo;
        private boolean addLanguage;
	private int thumbWidth;
	private int thumbHeight;
        private float ratio;

	@Override
	public void init(Properties props) {
		skinHome = props.getProperty("mjb.skin.dir", "./skins/default");
		thumbWidth = Integer.parseInt(props.getProperty("thumbnails.width", "180"));
		thumbHeight = Integer.parseInt(props.getProperty("thumbnails.height", "260"));
		addReflectionEffect = Boolean.parseBoolean(props.getProperty("thumbnails.reflection", "true"));
		addPerspective = Boolean.parseBoolean(props.getProperty("thumbnails.perspective", "false"));
		normalizeThumbnails = Boolean.parseBoolean(props.getProperty("thumbnails.normalize", "false"));
		addHDLogo = Boolean.parseBoolean(props.getProperty("thumbnails.logoHD", "false"));
                addLanguage = Boolean.parseBoolean(props.getProperty("thumbnails.language", "false"));
                ratio = (float)thumbWidth/(float)thumbHeight;
	}

	@Override
	public BufferedImage generate(Movie movie, BufferedImage moviePoster) {
		BufferedImage bi = moviePoster;
		
                int origWidth = moviePoster.getWidth();
                int origHeight = moviePoster.getHeight();
                boolean skipResize = false;
                if (origWidth < thumbWidth && origHeight < thumbHeight && !addHDLogo && !addLanguage) {
                    skipResize = true;
                }
                
		if (normalizeThumbnails) {
                        if (skipResize) {
                            bi = GraphicTools.scaleToSizeNormalized((int)(origHeight*ratio), origHeight, bi);
                        } else {
                            bi = GraphicTools.scaleToSizeNormalized(thumbWidth, thumbHeight, bi);
                        }
		} else if (!skipResize) {
			bi = GraphicTools.scaleToSize(thumbWidth, thumbHeight, bi);
		}

		if (addHDLogo) {
			bi = drawLogoHD(movie, bi);
		}
                
                if (addLanguage) {
                        bi = drawLanguage(movie, bi);
                }

		if (addReflectionEffect) {
			bi = GraphicTools.createReflectedPicture(bi);
		}
		
		if (addPerspective) {
			bi = GraphicTools.create3DPicture(bi);
		}

		return bi;
	}

	private BufferedImage drawLogoHD(Movie movie, BufferedImage bi) {
		String videoOutput = movie.getVideoOutput();
		if (  videoOutput.indexOf("720") != -1   
		   || videoOutput.indexOf("1080") != -1) {
			
			try {
				InputStream in = new FileInputStream(skinHome + File.separator + "resources" + File.separator + "hd.png");
				BufferedImage biHd = ImageIO.read(in);
				Graphics g = bi.getGraphics();
				g.drawImage(biHd, bi.getWidth() / 2 - biHd.getWidth() / 2, bi.getHeight() - biHd.getHeight() - 5, null);
			} catch (IOException e) {
				logger.warning("Failed drawing HD logo to thumbnail file: Please check that hd.png is in the resources directory.");
			}
		}
		
		return bi;
	}
        
	private BufferedImage drawLanguage(Movie movie, BufferedImage bi) {
		String lang = movie.getLanguage();
		if (lang != null && !lang.isEmpty() && !lang.equalsIgnoreCase("Unknown")) {
			
			try {
				InputStream in = new FileInputStream(skinHome + File.separator + "resources" + File.separator + "languages" + File.separator + lang + ".png");
				BufferedImage biLang = ImageIO.read(in);
				Graphics g = bi.getGraphics();
				g.drawImage(biLang, 1, 1, null);
			} catch (IOException e) {
				logger.warning("Failed drawing Language logo to thumbnail file: Please check that language specific png is in the resources/languages directory.");
			}
		}
		
		return bi;
	}
        
}
