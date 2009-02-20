<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" omit-xml-declaration="yes"/>

<xsl:template match="details/preferences"></xsl:template>

<xsl:template match="details/movie">
<html>
<head>
  <link rel="StyleSheet" type="text/css" href="exportdetails_item_popcorn.css"></link>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <title><xsl:value-of select="title"/></title>
</head>

<xsl:variable name="star_rating">true</xsl:variable>
<xsl:variable name="full_rating">true</xsl:variable>

<body bgproperties="fixed" background="pictures/background.jpg">
<xsl:attribute name="onloadset"><xsl:value-of select="//index[@current='true']/@name"/></xsl:attribute>

<table class="main" align="center" border="0" cellpadding="0" cellspacing="0">
  
    <tr height="30">
        <td height="50" align="center" colspan="2">
        <!-- Navigation using remote keys: Home, PageUP/PageDown (First/Last), Prev/Next & Left/Right (Previous/Next) -->
        <a><xsl:attribute name="TVID">HOME</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="//preferences/homePage"/></xsl:attribute></a>

        <a><xsl:attribute name="TVID">PGUP</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="first"/>.html</xsl:attribute></a>
        <a><xsl:attribute name="TVID">PGDN</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="last"/>.html</xsl:attribute></a>
   
        <a><xsl:attribute name="TVID">RIGHT</xsl:attribute><xsl:attribute name="href"><xsl:choose><xsl:when test="contains(next,'UNKNOWN')"><xsl:value-of select="first"/>.html</xsl:when><xsl:otherwise><xsl:value-of select="next"/>.html</xsl:otherwise></xsl:choose></xsl:attribute></a>
        <a><xsl:attribute name="TVID">LEFT</xsl:attribute><xsl:attribute name="href"><xsl:choose><xsl:when test="contains(previous,'UNKNOWN')"><xsl:value-of select="//preferences/homePage"/></xsl:when><xsl:otherwise><xsl:value-of select="last"/>.html</xsl:otherwise></xsl:choose></xsl:attribute></a>

        <a><xsl:attribute name="TVID">PREV</xsl:attribute><xsl:attribute name="href"><xsl:choose><xsl:when test="contains(next,'UNKNOWN')"><xsl:value-of select="first"/>.html</xsl:when><xsl:otherwise><xsl:value-of select="next"/>.html</xsl:otherwise></xsl:choose></xsl:attribute></a>
        <a><xsl:attribute name="TVID">NEXT</xsl:attribute><xsl:attribute name="href"><xsl:choose><xsl:when test="contains(previous,'UNKNOWN')"><xsl:value-of select="//preferences/homePage"/></xsl:when><xsl:otherwise><xsl:value-of select="last"/>.html</xsl:otherwise></xsl:choose></xsl:attribute></a>
    </td>
  </tr>
  
  <tr align="left" valign="top">
    <td width="420px">
       <img width="400"><xsl:attribute name="src"><xsl:value-of select="detailPosterFile" /></xsl:attribute></img>
    </td>
    
    <td>
      <table border="0" width="85%">
        <tr>
          <td class="title1" valign="top" colspan="2">
            <xsl:value-of select="title"/> 
            <xsl:if test="season!=-1"> Season <xsl:value-of select="season" /></xsl:if>
            <xsl:if test="year != 'UNKNOWN'">
            (<xsl:value-of select="year"/>)
            </xsl:if>
          </td>
        </tr>
        <tr>
          <td class="title2" valign="top" colspan="2">
            <xsl:if test="rating != -1">
              <xsl:if test="$star_rating = 'true'">
                <img><xsl:attribute name="src">pictures/rating_<xsl:value-of select="round(rating div 10)*10" />.png</xsl:attribute></img>
              </xsl:if>
              <xsl:if test="$full_rating = 'true'">
                <xsl:if test="$star_rating = 'true'">
                   (<xsl:value-of select="rating div 10" />/10)
                </xsl:if>
                <xsl:if test="$star_rating = 'false'">
                  IMDB Rating: <xsl:value-of select="rating div 10" />/10
                </xsl:if>
              </xsl:if>
            </xsl:if>
            <xsl:if test="top250 != -1">
                <xsl:text>&#160;&#160;</xsl:text>Top 250: #<xsl:value-of select="top250" />
            </xsl:if>
          </td>
        </tr>
        <tr>
          <td class="title2" valign="top" colspan="2">By 
            <xsl:if test="director != 'UNKNOWN'">
              <xsl:value-of select="director" /> 
            </xsl:if>
            <xsl:if test="company != 'UNKNOWN'">
              <xsl:if test="director != 'UNKNOWN'">, </xsl:if>
              <xsl:value-of select="company" /> 
            </xsl:if>
            <xsl:if test="country != 'UNKNOWN'">
              (<xsl:value-of select="country" />) 
            </xsl:if>
          </td>
        </tr>

        <xsl:if test="count(cast/actor)!=0">
        <tr>
            <td class="title2" colspan="2">With 
              <xsl:for-each select="cast/actor[position() &lt;= //preferences/actors.max]">
                 <xsl:if test="position()!=1">, </xsl:if>
                 <xsl:value-of select="." />
              </xsl:for-each>
           </td>
        </tr>
        </xsl:if>

        <tr>
          <td class="title2" valign="top" colspan="2">
            <xsl:if test="count(genres) != 0">
              <xsl:for-each select="genres/genre[position() &lt;= //preferences/genres.max]">
                <xsl:if test="position()!= 1">, </xsl:if>
                <xsl:value-of select="." />
              </xsl:for-each>
            </xsl:if>
            <xsl:if test="runtime != 'UNKNOWN'">
              <xsl:if test="count(genres) != 0">, </xsl:if>
              <xsl:value-of select="runtime" /> 
            </xsl:if>
            <xsl:if test="certification != 'UNKNOWN'">
              <xsl:if test="count(genres) != 0 and runtime != 'UNKNOWN'">, </xsl:if>
              <xsl:value-of select="certification" /> 
            </xsl:if>
            <xsl:if test="language != 'UNKNOWN'">
              <xsl:if test="count(genres) != 0 and runtime != 'UNKNOWN' and certification != 'UNKNOWN'">, </xsl:if>
              <xsl:value-of select="language" /> 
            </xsl:if>
          </td>
        </tr>

        <tr class="divider"><td colspan="2"><xsl:text> </xsl:text></td></tr>

        <tr>
          <td width="85%" class="normal" colspan="2">
            <xsl:if test="plot != 'UNKNOWN'">
              <xsl:value-of select="plot" />
            </xsl:if>
          </td>
        </tr>

        <tr class="spacer"><td><xsl:text> </xsl:text></td></tr>

        <tr>
          <td colspan="2"><center><table width="85%">
            <tr>
              <td class="title3" width="5%">Source</td>
              <td class="normal" width="45%"><xsl:value-of select="videoSource" /></td>
              <td class="title3" width="5%">Subtitles</td>
              <td class="normal" width="45%"><xsl:value-of select="subtitles" /></td>
            </tr>
            <tr>
              <td class="title3" width="5%">System</td>
              <td class="normal" width="45%"><xsl:value-of select="container" /></td>
              <td class="title3" width="5%">Dimension</td>
              <td class="normal" width="45%"><xsl:value-of select="resolution" /></td>
            </tr>
            <tr>
              <td class="title3" width="5%">Video</td>
              <td class="normal" width="45%"><xsl:value-of select="videoCodec" /></td>
              <td class="title3" width="5%">Output</td>
              <td class="normal" width="45%"><xsl:value-of select="videoOutput" /></td>
            </tr>
            <tr>
              <td class="title3" width="5%">Audio</td>
              <td class="normal" width="45%"><xsl:value-of select="audioCodec" /></td>
              <td class="title3" width="5%">FPS</td>
              <td class="normal" width="45%"><xsl:value-of select="fps" /></td>
            </tr>
            <tr>
              <td class="title3" width="5%" valign="top">Channels</td>
              <td class="normal" width="45%" valign="top"><xsl:value-of select="audioChannels" /></td>
              <td class="title3" width="5%" valign="top">
                <xsl:if test="libraryDescription != 'UNKNOWN'">
                  Library
                </xsl:if>
              </td>
              <td class="normal" width="45%" valign="top">
                <xsl:if test="libraryDescription != 'UNKNOWN'">
                  <xsl:value-of select="libraryDescription" />
                </xsl:if>
              </td>
            </tr>
          </table></center></td>
        </tr>
        
        <tr class="spacer" colspan="2"><td> </td></tr>

        <xsl:choose>                                
        <xsl:when test="count(files/file) = 1">
          <xsl:for-each select="files/file">
            <tr valign="top">
              <xsl:if test="//movie/season!=-1">
			    <td align="right" class="normal">
                  <xsl:value-of select="@firstPart"/><xsl:if test="@firstPart!=@lastPart">-<xsl:value-of select="@lastPart"/></xsl:if>.
			    </td>
			  </xsl:if>
              <td align="center">
                 <a class="link">
                   <xsl:attribute name="href"><xsl:value-of select="fileURL" /></xsl:attribute>
                   <xsl:attribute name="TVID">Play</xsl:attribute>

                   <xsl:if test="//movie/container = 'ISO' or substring(fileURL,string-length(fileURL)-3,4) = '.ISO' or substring(fileURL,string-length(fileURL)-3,4) = '.iso'">
                     <xsl:attribute name="zcd">2</xsl:attribute> 
                   </xsl:if>
                    
                   <xsl:if test="//movie/container = 'IMG' or substring(fileURL,string-length(fileURL)-3,4) = '.IMG' or substring(fileURL,string-length(fileURL)-3,4) = '.img'">
                     <xsl:attribute name="zcd">2</xsl:attribute> 
                   </xsl:if>
                    
                   <xsl:if test="substring(fileURL,string-length(fileURL)-7,8) = 'VIDEO_TS'">
                     <xsl:attribute name="zcd">2</xsl:attribute> 
                   </xsl:if>

                   <xsl:attribute name="vod"/>

                   <xsl:if test="//movie/prebuf != '-1'">
                       <xsl:attribute name="prebuf"><xsl:value-of select="//movie/prebuf" /></xsl:attribute>
                   </xsl:if>

				   <xsl:choose>
					   <xsl:when test="//movie/season=-1">
                         <img src="pictures/play.png" onfocussrc="pictures/play_selected.png"/>
					   </xsl:when>
					   <xsl:otherwise>
						 <img src="pictures/play_small.png" onfocussrc="pictures/play_selected_small.png" align="top"/>
                         <xsl:choose>
                          <xsl:when test="@title='UNKNOWN'">
						    <xsl:choose>
						      <xsl:when test="@firstPart!=@lastPart">
                                Episodes <xsl:value-of select="@firstPart"/> - <xsl:value-of select="@lastPart"/>
						      </xsl:when>	 
						      <xsl:otherwise>
						        Episode <xsl:value-of select="@firstPart"/>
						      </xsl:otherwise>	 
					        </xsl:choose>
					      </xsl:when>	 
                          <xsl:otherwise><xsl:value-of select="@title"/></xsl:otherwise>
                        </xsl:choose>						   
					  </xsl:otherwise>
				    </xsl:choose>
                  </a>
                </td>
              </tr>
            </xsl:for-each>
        </xsl:when>
        <xsl:when test="//movie/container = 'BDAV'">
          <tr>
            <td align="center">
              <a class="link">
                <xsl:attribute name="href">
                  <xsl:value-of select="concat(/details/movie/baseFilename,'.playlist.jsp')" />
                </xsl:attribute>
                <xsl:attribute name="TVID">Play</xsl:attribute>

                <xsl:attribute name="vod">playlist</xsl:attribute>
                <img src="pictures/play.png" onfocussrc="pictures/play_selected.png"/>
              </a>
            </td>
          </tr>
        </xsl:when>
        <xsl:otherwise>
          <tr>
		    <xsl:variable name="episodeSortOrder" select="if (/details/preferences/skin.reverseEpisodeOrder='true' and /details/movie/season!=-1) then 'descending' else 'ascending'" />
            <td>
              <table>
               <xsl:for-each select="files/file">
			   <xsl:sort select="@firstPart" data-type="number" order="{$episodeSortOrder}"/>
               <tr valign="top">
			     <td align="right" class="normal">
                   <xsl:value-of select="@firstPart"/><xsl:if test="@firstPart!=@lastPart">-<xsl:value-of select="@lastPart"/></xsl:if>.
                 </td>
                 <td class="normal">
                   <a class="link">
                     <xsl:attribute name="href"><xsl:value-of select="fileURL" /></xsl:attribute>
                     <xsl:choose>                    
                       <xsl:when test="position() = 1">
                         <xsl:attribute name="TVID">Play</xsl:attribute>
                       </xsl:when>
					   <xsl:otherwise>	 
                         <xsl:attribute name="TVID"><xsl:value-of select="@firstPart"/></xsl:attribute>
					   </xsl:otherwise>	   
                     </xsl:choose>                    
                     <xsl:if test="//movie/container = 'ISO' or substring(fileURL,string-length(fileURL)-3,4) = '.ISO' or substring(fileURL,string-length(fileURL)-3,4) = '.iso'">
                       <xsl:attribute name="zcd">2</xsl:attribute> 
                     </xsl:if>
                    
                     <xsl:if test="//movie/container = 'IMG' or substring(fileURL,string-length(fileURL)-3,4) = '.IMG' or substring(fileURL,string-length(fileURL)-3,4) = '.img'">
                       <xsl:attribute name="zcd">2</xsl:attribute> 
                     </xsl:if>
                    
                     <xsl:if test="substring(fileURL,string-length(fileURL)-7,8) = 'VIDEO_TS'">
                       <xsl:attribute name="zcd">2</xsl:attribute> 
                     </xsl:if>

                     <xsl:attribute name="vod"/>               

                     <xsl:if test="//movie/prebuf != '-1'">
                       <xsl:attribute name="prebuf"><xsl:value-of select="//movie/prebuf" /></xsl:attribute>
                     </xsl:if>

					 <xsl:if test="position() = 1"> 
                         <xsl:attribute name="class">firstMovie</xsl:attribute> 
                     </xsl:if>

                     <img src="pictures/play_small.png" onfocussrc="pictures/play_selected_small.png" align="top"/>
                     
                     <xsl:text>&#160;</xsl:text>
                     
                     <xsl:choose>
                       <xsl:when test="@title='UNKNOWN'">
						 <xsl:choose>
						   <xsl:when test="@firstPart!=@lastPart">
						      <xsl:choose>
                                 <xsl:when test="/details/movie/season!=-1">Episodes </xsl:when>
						         <xsl:otherwise>Parts </xsl:otherwise>
						      </xsl:choose>
							  <xsl:value-of select="@firstPart"/> - <xsl:value-of select="@lastPart"/>
						   </xsl:when>	 
						   <xsl:otherwise>
						      <xsl:choose>
                                 <xsl:when test="/details/movie/season!=-1">Episode </xsl:when>
						         <xsl:otherwise>Part </xsl:otherwise>
						      </xsl:choose>
							  <xsl:value-of select="@firstPart"/>
						   </xsl:otherwise>	 
					     </xsl:choose>
						 <xsl:value-of select="@part"/>
					   </xsl:when>	 
                       <xsl:otherwise><xsl:value-of select="@title"/></xsl:otherwise>
                     </xsl:choose>
                   </a>
                 </td>
               </tr>
               </xsl:for-each>
               <tr>
				 <td>&#160;</td>
                 <td class="normal">
                   <a class="link">
                       <xsl:attribute name="href"><xsl:value-of select="concat(/details/movie/baseFilename,'.playlist.jsp')" /></xsl:attribute>
                       <xsl:attribute name="vod">playlist</xsl:attribute>
                       <xsl:if test="//movie/prebuf != '-1'">
                           <xsl:attribute name="prebuf"><xsl:value-of select="//movie/prebuf" /></xsl:attribute>
                       </xsl:if>
                       <img src="pictures/play_small.png" onfocussrc="pictures/play_selected_small.png" align="top"/>
                       <xsl:text>&#160;</xsl:text>PLAY ALL
                   </a>
                 </td>
               </tr>
              </table>
            </td>
          </tr>
        </xsl:otherwise>
        </xsl:choose>
        
        <xsl:if test="count(trailers) != 0">
          <tr>
            <td>
              <table>
               <tr><td class="title2">Trailers</td></tr>
               <xsl:for-each select="trailers/trailer">
               <tr>
                 <td class="normal">
                   <a>
                     <xsl:attribute name="href"><xsl:value-of select="." /></xsl:attribute>

                     <xsl:if test="substring(.,string-length(.)-2) = 'ISO'">
                       <xsl:attribute name="zcd">2</xsl:attribute>
                     </xsl:if>
                     <xsl:if test="substring(.,string-length(.)-2) = 'iso'">
                       <xsl:attribute name="zcd">2</xsl:attribute>
                     </xsl:if>
                     <xsl:if test="substring(.,string-length(.)-2) = 'IMG'">
                       <xsl:attribute name="zcd">2</xsl:attribute>
                     </xsl:if>
                     <xsl:if test="substring(.,string-length(.)-2) = 'img'">
                       <xsl:attribute name="zcd">2</xsl:attribute>
                     </xsl:if>
                     <xsl:if test="substring(.,string-length(.)-7) = 'VIDEO_TS'">
                       <xsl:attribute name="zcd">2</xsl:attribute>
                     </xsl:if>

                     <xsl:if test="//movie/prebuf != '-1'">
                       <xsl:attribute name="prebuf"><xsl:value-of select="//movie/prebuf" /></xsl:attribute>
                     </xsl:if>

                     <xsl:attribute name="vod"/>
                     <img src="pictures/play_small.png" onfocussrc="pictures/play_selected_small.png" align="top">
                       <xsl:attribute name="onmouseover">this.src='pictures/play_selected_small.png';</xsl:attribute>
                       <xsl:attribute name="onmouseout">this.src='pictures/play_small.png';</xsl:attribute>
                     </img>
                     <xsl:text>&#160;</xsl:text>
                     <xsl:value-of select="@title"/>
                   </a>
                 </td>
               </tr>
               </xsl:for-each>
              </table>
            </td>
          </tr>
        </xsl:if>
        
      </table>
    </td>
  </tr>
</table>
</body>
</html>
</xsl:template>
</xsl:stylesheet>
