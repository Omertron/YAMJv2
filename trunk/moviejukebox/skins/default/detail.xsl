<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" omit-xml-declaration="yes"/>

<xsl:template match="details/preferences"></xsl:template>

<xsl:template match="details/movie">
<html>
<head>
  <link rel="StyleSheet" type="text/css" href="exportdetails_item_popcorn.css"></link>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <title><xsl:value-of select="titleSort"/></title>
</head>

<body bgproperties="fixed" background="pictures/background.jpg" onloadset="1">

<table class="main" align="center" border="0" cellpadding="0" cellspacing="0">
  
  <tr height="30">
    <td heigth="50" align="center" colspan="2">
      <!-- Navigation using remote keys PageUP/PageDown and Prev/Next -->
      <a><xsl:attribute name="TVID">RIGHT</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="next"/>.html</xsl:attribute></a>
      <a><xsl:attribute name="TVID">LEFT</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="previous"/>.html</xsl:attribute></a>
      <a><xsl:attribute name="TVID">HOME</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="//preferences/homePage"/></xsl:attribute></a>
      <a><xsl:attribute name="TVID">PGUP</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="first"/>.html</xsl:attribute></a>
      <a><xsl:attribute name="TVID">PGDN</xsl:attribute><xsl:attribute name="href"><xsl:value-of select="last"/>.html</xsl:attribute></a>
    </td>
  </tr>
  
  <tr align="left" valign="top">
    <td width="420px">
       <img width="400"><xsl:attribute name="src"><xsl:value-of select="detailPosterFile" /></xsl:attribute></img>
    </td>
    
    <td>
      <table border="0" width="85%">
        <tr>
          <td class="title1" valign="top" colspan="4">
            <xsl:value-of select="titleSort"/> 
            <xsl:if test="season!=-1"> Season <xsl:value-of select="season" /></xsl:if>
            <xsl:if test="year != 'UNKNOWN'">
            (<xsl:value-of select="year"/>)
            </xsl:if>
          </td>
        </tr>
        <tr>
          <td>
            <xsl:if test="rating != -1">
              <img><xsl:attribute name="src">pictures/rating_<xsl:value-of select="round(rating div 10)*10" />.png</xsl:attribute></img>
            </xsl:if>
          </td>
        </tr>
        <tr>
          <td class="title2" valign="top" colspan="4">By 
            <xsl:if test="director != 'UNKNOWN'">
              <xsl:value-of select="director" /> 
            </xsl:if>
            <xsl:if test="company != 'UNKNOWN'">
              <xsl:if test="director != 'UNKNOWN'">, </xsl:if>
              <xsl:value-of select="company" /> 
            </xsl:if>
            <xsl:if test="company != 'UNKNOWN'">
              (<xsl:value-of select="country" />) 
            </xsl:if>
          </td>
        </tr>

        <xsl:if test="count(cast/actor)!=0">
        <tr>
            <td class="title2" colspan="4">With 
              <xsl:for-each select="cast/actor[position()&lt;3]">
                 <xsl:if test="position()!=1">, </xsl:if>
                 <xsl:value-of select="." />
              </xsl:for-each>
           </td>
        </tr>
        </xsl:if>

        <tr>
          <td class="title2" valign="top" colspan="3">
            <xsl:if test="count(genres) != 0">
              <xsl:for-each select="genres/genre">
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

        <tr class="divider"><td> </td></tr>

        <tr>
          <td width="85%" class="normal" colspan="4">
            <xsl:if test="plot != 'UNKNOWN'">
              <xsl:value-of select="plot" />
            </xsl:if>
          </td>
        </tr>

        <tr class="spacer"><td> </td></tr>

        <tr>
          <td colspan="4"><center><table width="85%">
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
          </table></center></td>
        </tr>
        
        <tr class="spacer"><td> </td></tr>

        <xsl:choose>                                
        <xsl:when test="count(files/file) = 1">
          <tr>
            <td>
                <center>
                 <a class="link">
                   <xsl:attribute name="href"><xsl:value-of select="files/file[1]" /></xsl:attribute>
                   <xsl:attribute name="TVID">Play</xsl:attribute>

                   <xsl:if test="//movie/container = 'ISO' or substring(.,string-length(.)-3,4) = '.ISO' or substring(.,string-length(.)-3,4) = '.iso'">
                     <xsl:attribute name="zcd">2</xsl:attribute> 
                   </xsl:if>
                    
                   <xsl:if test="substring(//movie/files/file[1],string-length(//movie/files/file[1])-7,8) = 'VIDEO_TS'">
                     <xsl:attribute name="zcd">2</xsl:attribute> 
                   </xsl:if>

                   <xsl:attribute name="vod"/>               
                   <img src="pictures/play.png" onfocussrc="pictures/play_selected.png"/>
                 </a>
                </center>
            </td>
          </tr>
        </xsl:when>
        <xsl:otherwise>
          <tr>
            <td>
              <table>
               <xsl:for-each select="files/file">
               <tr>
                 <td class="normal">
                   <a class="link">
                     <xsl:attribute name="href"><xsl:value-of select="." /></xsl:attribute>
                    
                     <xsl:choose>                                
                       <xsl:when test="position() = 1">
                         <xsl:attribute name="TVID">Play</xsl:attribute>
                       </xsl:when>
                       <xsl:otherwise>
                         <xsl:attribute name="TVID"><xsl:value-of select="position()"/></xsl:attribute>
                       </xsl:otherwise>            
                     </xsl:choose> 
                    
                     <xsl:if test="//movie/container = 'ISO' or substring(.,string-length(.)-3,4) = '.ISO' or substring(.,string-length(.)-3,4) = '.iso'">
                       <xsl:attribute name="zcd">2</xsl:attribute> 
                     </xsl:if>
                    
                     <xsl:if test="substring(//movie/files/file[1],string-length(//movie/files/file[1])-7,8) = 'VIDEO_TS'">
                       <xsl:attribute name="zcd">2</xsl:attribute> 
                     </xsl:if>

                     <xsl:attribute name="vod"/>               
                     <img src="pictures/play_small.png" onfocussrc="pictures/play_selected_small.png" align="top"/>
                     
                     <xsl:text>&#160;</xsl:text>
                     
                     <xsl:choose>
                       <xsl:when test="position() = 1"> 
                         <xsl:attribute name="class">firstMovie</xsl:attribute> 
                       </xsl:when>
                     </xsl:choose>

                     <xsl:choose>
                       <xsl:when test="/details/movie/season!=-1">
                         Episode <xsl:value-of select="@part"/>
                         <xsl:if test="@title!='UNKNOWN'">
                           - <xsl:value-of select="@title"/>
                         </xsl:if>
                       </xsl:when>
                       <xsl:otherwise>
                         <xsl:value-of select="/details/movie/titleSort"/> (Part <xsl:value-of select="@part"/>)
                       </xsl:otherwise>
                     </xsl:choose>
                   </a>
                 </td>
               </tr>
               </xsl:for-each>
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
                     <xsl:if test="substring(.,string-length(.)-7) = 'VIDEO_TS'">
                       <xsl:attribute name="zcd">2</xsl:attribute>
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
