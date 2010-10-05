/* License
 * 
 * Copyright 1994-2004 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  
 *  * Redistribution of source code must retain the above copyright notice,
 *	this list of conditions and the following disclaimer.
 * 
 *  * Redistribution in binary form must reproduce the above copyright notice,
 *	this list of conditions and the following disclaimer in the
 *	documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *  
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *  
 * You acknowledge that this software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any
 * nuclear facility. 
 */

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
* A custom bitmapped font read from a png file.
* 
* Font is a final class, so we are unable to subclass it.
* Therefore, this class implements many of the same methods
* for compatibility purposes.  However, to render this font
* you need to call the drawChar, drawChars, drawString, and
* drawSubstring methods from your paint method.
*/
public class CustomFont 
{
    private int style;
    private int size;
    private int baseline;
    private int height;
    private int width;
    private Image image;
    
    /**
    * Returns a soft font based on the specified 
    * resource name and the specified size and style.
    * The resource name must refer to a png file.
    * The size and style constants are defined
    * on the Font class.
    */
    public static CustomFont getFont( 
        String inName, int inStyle, int inSize )
    {
        Image i;
        String filename = inName;
        try
        {
            i = Image.createImage( filename );
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            throw new IllegalArgumentException(
                "Could not locate font: " + filename + " : " +  t );
        }
        
        return new CustomFont( i, inSize, inStyle );
    }

    private CustomFont( 
        Image inImage, int inStyle, int inSize )
    {
        image = inImage;
        style = inStyle;
        size  = inSize;
        
        try
        {
            height = image.getHeight();
            width  = image.getWidth() / 128;
            baseline = calculateBaseline();
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            throw new IllegalArgumentException(
                "Specified font is invalid: " + t );
        }
    }
    
    private int calculateBaseline()
    {
        // get baseline: defaults to last row
        int result = height;
        int imageWidth = image.getWidth();
        int max = 0;
        int total;
        int[] row = new int[ imageWidth ];
        int background;
        
        // determine background color: assume it's at 0, 0
        image.getRGB( row, 0, 1, 0, 0, 1, 1 );
        background = row[0];

        // here's the heuristic: find the row on the bottom
        // half of the image with the most non-background pixels
        for ( int y = height/2; y < height; y++ )
        {
            total = 0;
            image.getRGB( row, 0, imageWidth, 0, y, imageWidth, 1 );
            for ( int x = 0; x < imageWidth; x++ )
            {
                if ( row[x] != background ) total++;
            }
            if ( total > max ) 
            {
                max = total;
                result = y;
            }
        }
        
        return result;
    }
    
    public int charsWidth( char[] ch, int offset, int length )
    {
        // monospaced font makes this an easy calculation
        return length * width;
    }
    
    public int charWidth( char ch )
    {
        // we're monospaced so all are equal
        return width;
    }
    
    public int getBaselinePosition()
    {
        return baseline;
    }
    
    public int getHeight()
    {
        return height;
    }
    
    public int stringWidth( String str )
    {
        return charsWidth( str.toCharArray(), 0, str.length() );
    }
    
    public int substringWidth( String str, int offset, int len )
    {
        return charsWidth( str.toCharArray(), offset, len );
    }
    
    public int getSize()
    {
        return size;
    }
    
    public int getStyle()
    {
        return style;
    }
    
    public boolean isBold()
    {
        return ( ( style & Font.STYLE_BOLD ) != 0 );
    }
    
    public boolean isItalic()
    {
        return ( ( style & Font.STYLE_ITALIC ) != 0 );
    }
    
    public boolean isPlain()
    {
        return ( style == 0 );
    }
    
    public boolean isUnderlined()
    {
        return ( ( style & Font.STYLE_UNDERLINED ) != 0 );
    }
    
    /**
    * Paints the specified character at the specified coordinates
    * on the Graphics instance using Graphics' anchoring constants.
    */
    public void drawChar( 
        Graphics g, char character, int x, int y, int anchor )
    {
        int clipX = g.getClipX();
        int clipY = g.getClipY();
        int clipW = g.getClipWidth();
        int clipH = g.getClipHeight();

        drawCharInternal( g, character, x, y, anchor );
        
        g.setClip( clipX, clipY, clipW, clipH );
    }
    
    /**
    * Paints the characters as specified.
    */
    public void drawChars( 
        Graphics g, char[] data, 
        int offset, int length, int x, int y, int anchor )
    {
        if ( (anchor & g.RIGHT) != 0 )
        {
            x -= charsWidth( data, offset, length );
        }
        else
        if ( (anchor & g.HCENTER) != 0 )
        {
            x -= ( charsWidth( data, offset, length ) / 2 );
        }
        
        if ( (anchor & g.BOTTOM) != 0 )
        {
            y -= height;
        }
        else
        if ( (anchor & g.VCENTER) != 0 )
        {
            y -= height/2;
        }
        
        int clipX = g.getClipX();
        int clipY = g.getClipY();
        int clipW = g.getClipWidth();
        int clipH = g.getClipHeight();

        char c;
        for ( int i = 0; i < length; i++ )
        {  
            c = data[offset+i];
            drawCharInternal( g, c, x, y, g.TOP|g.LEFT );
            x += width;
        }
        
        g.setClip( clipX, clipY, clipW, clipH );
    }
    
    /**
    * Draws the actual characters without worrying about
    * saving and restoring the existing clip region.
    */
    private void drawCharInternal( 
        Graphics g, char character, int x, int y, int anchor )
    {
        if ( ( style & Font.STYLE_ITALIC ) != 0 )
        {
            // draw italicized: top half is shifted right
            g.setClip( x + 1, y, width, height/2 );
            g.drawImage( 
                image, x - width*character + 1, y, anchor );
            g.setClip( x, y+height/2, width, height/2 );
            g.drawImage( 
                image, x - width*character, y, anchor );
            
            if ( ( style & Font.STYLE_BOLD ) != 0 )
            {
            g.setClip( x, y, width, height/2 );
            g.drawImage( 
                image, x - width*character + 2, y, anchor );
            g.setClip( x, y+height/2, width, height/2 );
            g.drawImage( 
                image, x - width*character + 1, y, anchor );
            }
        }
        else 
        {
            // draw normally
            g.setClip( x, y, width, height );
            g.drawImage( 
                image, x - width*character, y, anchor );
            
            if ( ( style & Font.STYLE_BOLD ) != 0 )
            {
                g.drawImage( 
                    image, x - width*character + 1, y, anchor );
            }
        }
            
        if ( ( style & Font.STYLE_UNDERLINED ) != 0 )
        {
            g.drawLine( 
                x, y + baseline + 2, x + width, y + baseline + 2 );
        }
    }
    
    /**
    * Paints the string as specified.
    */
    public void drawString(
        Graphics g, String str, int x, int y, int anchor )
    {
        drawChars( g, str.toCharArray(), 0, str.length(), x, y, anchor );
    }
    
    /**
    * Paints the substring as specified.
    */
    public void drawSubstring( 
        Graphics g, String str, 
        int offset, int len, int x, int y, int anchor )
    {
        drawChars( g, str.toCharArray(), offset, len, x, y, anchor );
    }
    
    
}