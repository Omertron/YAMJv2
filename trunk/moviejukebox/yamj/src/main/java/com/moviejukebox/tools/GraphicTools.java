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
package com.moviejukebox.tools;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import com.jhlabs.image.PerspectiveFilter;

public class GraphicTools {
    private static Logger logger = Logger.getLogger("moviejukebox");
    private static float quality;
    private static int jpegQuality;

    
    /**
     * Load a JPG image from a file (input stream)
     * @param fis
     * @return
     */
    private static BufferedImage loadJPEGImage(InputStream fis) {
        // Create BufferedImage
        BufferedImage bi = null;
        try {
            bi = ImageIO.read(fis);
        } catch (IIOException error) {
            logger.severe("GraphicsTools: Error reading image file. Possibly corrupt image, please try another image. " + error.getMessage());
            return null;
        } catch (Exception ignore) {
            logger.severe("GraphicsTools: Error reading image file. Possibly corrupt image, please try another image. " + ignore.getMessage());
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception error) {
                    final Writer eResult = new StringWriter();
                    final PrintWriter printWriter = new PrintWriter(eResult);
                    error.printStackTrace(printWriter);
                    logger.severe(eResult.toString());
                    // ignore the error
                }
            }
        }
        return bi;
    }

    /**
     * Load a JPG image from a filename
     * @param filename
     * @return
     * @throws IOException
     */
    public static BufferedImage loadJPEGImage(String filename) throws IOException {
        return loadJPEGImage(new File(filename));
    }

    /**
     * Load a JPG image from a file
     * @param f
     * @return
     * @throws IOException
     */
    public static BufferedImage loadJPEGImage(File f) throws IOException{
        InputStream in = FileTools.createFileInputStream(f);
        BufferedImage b = loadJPEGImage(in);
        in.close();
        return b;
    }

    /**
     * Load a JPG image from an URL
     * @param url
     * @return
     */
    public static BufferedImage loadJPEGImage(URL url) {
        BufferedImage bi = null;
        try {
            bi = ImageIO.read(url);
        } catch (Exception ignore) {
            logger.severe("GraphicsTools: Error reading image file. Possibly corrupt image, please try another image. " + ignore.getMessage());
            bi = null;
        }
        return bi;
    }

    public static void saveImageAsJpeg(BufferedImage bi, String filename) {
        if (bi == null || filename == null || filename.equalsIgnoreCase("")) {
            return;
        }

        jpegQuality  = PropertiesUtil.getIntProperty("mjb.jpeg.quality", "75");
        quality = (float)jpegQuality / 100;
        // save image as JPEG
        try {
            BufferedImage bufImage = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
            bufImage.createGraphics().drawImage(bi, 0, 0, null, null);

            //ori: File outputFile = new File(filename);
            //ori: ImageIO.write(bufImage, "jpg", outputFile);
            @SuppressWarnings("rawtypes")
            Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");
            ImageWriter writer = (ImageWriter)iter.next();

            ImageWriteParam iwp = writer.getDefaultWriteParam();
            iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwp.setCompressionQuality(quality);   // float integer between 0 and 1 - with 1 specifying minimum compression and maximum quality

            //Output file:
            File outputFile = new File(filename);
            FileImageOutputStream output = new FileImageOutputStream(outputFile);
            writer.setOutput(output);
            IIOImage image = new IIOImage(bufImage, null, null);
            writer.write(null, image, iwp);
            writer.dispose();

        } catch (Exception error) {
            logger.severe("GraphicsTools: Failed Saving thumbnail file: " + filename);
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.severe(eResult.toString());
        }
    }

    public static void saveImageAsPng(BufferedImage bi, String filename) {
        if (bi == null || filename == null) {
            return;
        }

        // save image as PNG
        try {
            File outputFile = new File(filename);
            ImageIO.write(bi, "png", outputFile);
        } catch (Exception error) {
            logger.severe("GraphicsTools: Failed Saving thumbnail file: " + filename);
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.severe(eResult.toString());
        }
    }

    /**
     * Save the buffered image to disk as either a JPG or PNG
     * @param bi
     * @param filename
     */
    public static void saveImageToDisk(BufferedImage bi, String filename) {
        if (filename.toUpperCase().endsWith("JPG") || filename.toUpperCase().endsWith("JPEG")) {
            saveImageAsJpeg(bi, filename);
        } else if (filename.toUpperCase().endsWith("PNG")) {
            saveImageAsPng(bi, filename);
        } else {
            saveImageAsJpeg(bi, filename);
        }
    }

    /**
     * Bi-cubic image scaling
     * @param nMaxWidth
     * @param nMaxHeight
     * @param imgSrc
     * @return
     */
    public static BufferedImage scaleToSize(int nMaxWidth, int nMaxHeight, BufferedImage imgSrc) {
        /* determine thumbnail size from WIDTH and HEIGHT */
        int imageWidth = imgSrc.getWidth(null);
        int imageHeight = imgSrc.getHeight(null);

        int tempWidth;
        int tempHeight;
        int y = 0;

        tempWidth = nMaxWidth;
        tempHeight = (int) (((double) imageHeight * (double) nMaxWidth) / (double) imageWidth);

        if (nMaxHeight > tempHeight) {
            y = nMaxHeight - tempHeight;
        }

        Image temp1 = imgSrc.getScaledInstance(tempWidth, tempHeight, Image.SCALE_SMOOTH);
        BufferedImage bi = new BufferedImage(nMaxWidth, nMaxHeight, BufferedImage.TYPE_INT_ARGB);
        //bi.getGraphics().drawImage(temp1, 0, y, null);
        bi.createGraphics().drawImage(temp1, 0, y, null);
        return bi;
    }

    public static BufferedImage scaleToSizeBestFit(int nMaxWidth, int nMaxHeight, BufferedImage imgSrc) {
        /* determine thumbnail size from WIDTH and HEIGHT */
        int imageWidth = imgSrc.getWidth(null);
        int imageHeight = imgSrc.getHeight(null);

        int tempWidth;
        int tempHeight;

        tempWidth = nMaxWidth;
        tempHeight = (int) (((double) imageHeight * (double) nMaxWidth) / (double) imageWidth);

        Image temp1 = imgSrc.getScaledInstance(tempWidth, tempHeight, Image.SCALE_SMOOTH);
        BufferedImage bi = new BufferedImage(tempWidth, tempHeight, BufferedImage.TYPE_INT_ARGB);
        bi.createGraphics().drawImage(temp1, 0, 0, null);
        return bi;
    }

    public static BufferedImage scaleToSizeNormalized(int nMaxWidth, int nMaxHeight, BufferedImage imgSrc) {
        /* determine thumbnail size from WIDTH and HEIGHT */
        int imageWidth = imgSrc.getWidth(null);
        int imageHeight = imgSrc.getHeight(null);

        double imageRatio = (double) imageHeight / (double) imageWidth;
        double thumbnailRatio = (double) nMaxHeight / (double) nMaxWidth;

        int tempWidth;
        int tempHeight;
        
        if (imageRatio > thumbnailRatio) {
            tempWidth = nMaxWidth;
            tempHeight = (int) (((double) imageHeight * (double) nMaxWidth) / (double) imageWidth);
        } else {
            tempWidth = (int) (((double) imageWidth * (double) nMaxHeight) / (double) imageHeight);
            tempHeight = nMaxHeight;
        }

        Image temp1 = imgSrc.getScaledInstance(tempWidth, tempHeight, Image.SCALE_SMOOTH);
        BufferedImage bi = new BufferedImage(tempWidth, tempHeight, BufferedImage.TYPE_INT_ARGB);
        bi.createGraphics().drawImage(temp1, 0, 0, null);
        return cropToSize(nMaxWidth, nMaxHeight, bi);
    }

    public static BufferedImage cropToSize(int nMaxWidth, int nMaxHeight, BufferedImage imgSrc) {
        int nHeight = imgSrc.getHeight();
        int nWidth = imgSrc.getWidth();

        int x1 = 0;
        if (nWidth > nMaxWidth) {
            x1 = (nWidth - nMaxWidth) / 2;
        }

        int l = nMaxWidth;
        if (nWidth < nMaxWidth) {
            l = nWidth;
        }

        int y1 = 0;
        if (nHeight > nMaxHeight) {
            y1 = (nHeight - nMaxHeight) / 2;
        }

        int h = nMaxHeight;
        if (nHeight < nMaxHeight) {
            h = nHeight;
        }

        return imgSrc.getSubimage(x1, y1, l, h);
    }

    /*
     * Reflection effect
     * graphicType should be "posters", "thumbnails" or "videoimage" and is used 
     * to determine the settings that are extracted from the skin.properties file.
     */
    public static BufferedImage createReflectedPicture(BufferedImage avatar, String graphicType) {
        int avatarWidth = avatar.getWidth();
        int avatarHeight = avatar.getHeight();
        
        float reflectionHeight = 12.5f;
        
        reflectionHeight = getFloatProperty(graphicType + ".reflectionHeight", "12.5");
        
        BufferedImage gradient = createGradientMask(avatarWidth, avatarHeight, reflectionHeight, graphicType);
        BufferedImage buffer = createReflection(avatar, avatarWidth, avatarHeight, reflectionHeight);

        applyAlphaMask(gradient, buffer, avatarWidth, avatarHeight);

        return buffer;
    }

    /*
     * Create a gradient mask for the image
     */
    public static BufferedImage createGradientMask(int avatarWidth, int avatarHeight, float reflectionHeight, String graphicType) {
        BufferedImage gradient = new BufferedImage(avatarWidth, avatarHeight, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        Graphics2D g = gradient.createGraphics();
        
//      GradientPaint painter = new GradientPaint(0.0f, 0.0f, new Color(1.0f, 1.0f, 1.0f, 0.3f), 0.0f, avatarHeight * (reflectionHeight / 100), new Color(1.0f, 1.0f, 1.0f, 1.0f));
        
        float reflectionStart, reflectionEnd, opacityStart, opacityEnd;
        float reflectionHeightAbsolute = avatarHeight * (reflectionHeight / 100);

        reflectionStart = (getFloatProperty(graphicType + ".reflectionStart", "0.0") / 100) * reflectionHeightAbsolute;
        reflectionEnd   = (getFloatProperty(graphicType + ".reflectionEnd", "100.0") / 100) * reflectionHeightAbsolute;
        opacityStart    = getFloatProperty(graphicType + ".opacityStart", "30.0") / 100;
        opacityEnd      = getFloatProperty(graphicType + ".opacityEnd", "100.0") / 100;

        GradientPaint painter = new GradientPaint(0.0f, reflectionStart, new Color(1.0f, 1.0f, 1.0f, opacityStart), 0.0f, reflectionEnd, new Color(1.0f, 1.0f, 1.0f, opacityEnd));
        g.setPaint(painter);
        g.fill(new Rectangle2D.Double(0, 0, avatarWidth, avatarHeight));

        g.dispose();
        gradient.flush();

        return gradient;
    }
    
    /*
     * This function will load a float property and convert it to a proper float before returning it.
     * If the property errors, it will return the default value.
     */
    private static float getFloatProperty(String propertyName, String propertyDefault) {
        float propertyValue = Float.valueOf(propertyDefault);

        try {
            propertyValue = Float.valueOf(PropertiesUtil.getProperty(propertyName, propertyDefault));
        } catch (NumberFormatException nfe) {
            logger.severe("NumberFormatException " + nfe.getMessage() + " in property " + propertyName);
        }

        return propertyValue;
    }
    
    /*
     * Create the reflection effect for the image
     */
    public static BufferedImage createReflection(BufferedImage avatar, int avatarWidth, int avatarHeight, float reflectionHeight) {
        // Increase the height of the image to cater for the reflection.
        int newHeight = (int) (avatarHeight * (1 + (reflectionHeight / 100) ));
  
        BufferedImage buffer = new BufferedImage(avatarWidth, newHeight, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        Graphics2D g = buffer.createGraphics();

        g.drawImage(avatar, null, null);
        g.translate(0, (avatarHeight << 1) + 2);

        AffineTransform reflectTransform = AffineTransform.getScaleInstance(1.0, -1.0);
        g.drawImage(avatar, reflectTransform, null);
        g.translate(0, -(avatarHeight << 1));

        g.dispose();

        return buffer;
    }

    public static void applyAlphaMask(BufferedImage gradient, BufferedImage buffer, int avatarWidth, int avatarHeight) {
        Graphics2D g2 = buffer.createGraphics();
        g2.setComposite(AlphaComposite.DstOut);
        g2.drawImage(gradient, null, 0, avatarHeight);
        g2.dispose();
    }

    /*
     * 3D effect
     * graphicType should be "posters", "thumbnails" or "videoimage" and is used 
     * to determine the settings that are extracted from the skin.properties file. 
     */
    public static BufferedImage create3DPicture(BufferedImage bi, String graphicType, String perspectiveDirection) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        float perspectiveTop = 3f;
        float perspectiveBottom = 3f;

        try {
            perspectiveTop = Float.valueOf(PropertiesUtil.getProperty(graphicType + ".perspectiveTop", "3"));
        } catch (NumberFormatException nfe) {
            logger.severe("NumberFormatException " + nfe.getMessage() + " in property " + graphicType + ".perspectiveTop");
        }

        try {
            perspectiveBottom = Float.valueOf(PropertiesUtil.getProperty(graphicType + ".perspectiveBottom", "3"));
        } catch (NumberFormatException nfe) {
            logger.severe("NumberFormatException " + nfe.getMessage() + " in property " + graphicType + ".perspectiveBottom");
        }        

        int Top3d = (int) (h * perspectiveTop / 100);
        int Bot3d = (int) (h * perspectiveBottom / 100);
        
        PerspectiveFilter perspectiveFilter = new PerspectiveFilter();
        // Top Left (x/y), Top Right (x/y), Bottom Right (x/y), Bottom Left (x/y)
        
        if (perspectiveDirection.equalsIgnoreCase("right")) {
            perspectiveFilter.setCorners(0, 0, w, Top3d, w, h - Bot3d, 0, h);
        } else {
            perspectiveFilter.setCorners(0, Top3d, w, 0, w, h, 0, h - Bot3d);
        }
        return perspectiveFilter.filter(bi, null);
    }

}
