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

import java.io.IOException;
import java.io.OutputStream;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

/**
* Canvas that renders an ANSI character stream and 
* sends any user input to the specified output stream.
*/ 
public class TelnetCanvas extends Canvas
{
    private CustomFont font;
    private short fontHeight;
    private short fontWidth;
    private short rows;
    private short columns;
    private int scrollX;
    private int scrollY;
    private short insetX;
    private short insetY;
    private byte[] buffer;
    private int cursor;
    private int savedCursor;
    private int bound; // outer bound: max extent of cursor
    private OutputStream output;

    Display midletDisplay; // Zane
    
    /**
    * The first element in this array is the logical size,
    * which is the index at which the next byte should be
    * placed.  (Kind of like Pascal.)
    * The array itself may be longer than the length used.  
    */
    private char[] argbuf;
    
    private byte state;
    private boolean highlight;
    private boolean scrolling;
    private byte[] move = new byte[] { 27, '[', 0 };
    private static final byte NORMAL_STATE = 0;
    private static final byte ESCAPE_STATE = 1;
    private static final byte PARAMS_STATE= 2;

    /**
    * Default constructor creates a new telnet canvas.
    * Zane - Moved to setup as apparently setFullScreenMode has some issues on S60 devices
    */
    public TelnetCanvas(Display display)
    {
	setFullScreenMode(true); // Zane
	midletDisplay = display; // Zane
    }

    public void setup() {
        int width = getWidth();
        int height = getHeight();

        // get font and metrics
        font = CustomFont.getFont( 
            "/font.png",        // the font definition file
            Font.SIZE_SMALL,    // ignored
            Font.STYLE_PLAIN ); // no styling
                
        fontHeight = (short) font.getHeight();
        fontWidth = (short) font.stringWidth( "w" ); // it's monospaced

        // calculate how many rows and columns we can display
        columns = (short) ( width / fontWidth ); 
        rows = (short) ( height / fontHeight );

        // divide any extra space evenly around edges of screen
        insetX = (short) ( ( width - columns*fontWidth ) / 2 );
        insetY = (short) ( ( height - rows*fontHeight ) / 2 );

        // initialize state
        reset();
        scrolling = false;

        // ansi arg parsing state
        argbuf = new char[2];
        state = 0;
        highlight = false;
    }
    
    /**
    * Returns the number of rows that fit on screen.
    */
    public int getRows()
    {
        return rows;
    }
    
    /**
    * Returns the number of columns that fit on screen.
    */
    public int getColumns()
    {
        return columns;
    }
    
    /**
    * Returns the supported terminal emulation type.
    */
    public String getTerminalType()
    {
        return "ansi";
    }
    
    /**
    * Resets the display to its initial state.
    */ 
    public void reset()
    {
        buffer = new byte[rows*columns*4]; // start with 4 screens of buffer
        cursor = 0;
        bound = rows * columns;
        savedCursor = -1;
        scrollX = 0;
        scrollY = 0;
    }
    
    /**
    * Returns whether the terminal is in "scroll-lock" mode:
    * arrow keys will scroll the local display instead of
    * being sent to the remote host.
    */
    public boolean isScrolling()
    {
        return scrolling;
    }
    
    /**
    * Sets whether the terminal is in "scroll-lock" mode:
    * arrow keys will scroll the local display instead of
    * being sent to the remote host.
    */
    public void setScrolling( boolean isScrolling )
    {
        scrolling = isScrolling;
    }
    
    /**
    * Sets the output stream used to receive user input.
    */
    public void setOutputStream( OutputStream stream )
    {
        output = stream;
    }
    
    /**
    * Sends the specified byte to the output stream.
    * If no output stream is set, does nothing.
    */
    public void send( byte b )
    {
        if ( output == null ) return;
        
        try
        {   
            output.write( b );
            output.flush();
        } 
        catch ( IOException exc )
        {
            System.err.println( "Error on send: " + exc );
        }
    }
    
    /**
    * Sends the specified bytes to the output stream.
    * If no output stream is set, does nothing.
    */
    public void send( byte b[] )
    {
        if ( output == null ) return;
        
        try
        {   
            output.write( b );
            output.flush();
        } 
        catch ( IOException exc )
        {
            System.err.println( "Error on send data: " + exc );
        }
    }
    
    /**
    * Appends the specified ascii string - no unicode allowed.
    */
    public void receive( String inString )
    {
        receive( inString.toCharArray() );
    }
    
    /**
    * Appends the specified ascii character array - no unicode allowed.
    */
    public void receive( char[] c )
    {
        for ( int i = 0; i < c.length; i++ ) receive( (byte) c[i] ); 
    }
    
    /**
    * Appends the specified ascii bytes to the output.
    */
    public void receive( byte[] b )
    {
        for ( int i = 0; i < b.length; i++ ) receive( b[i] );
    }

    /**
    * Appends the specified ascii byte to the output.
    */
    public void receive( byte b )
    {
        // ignore nulls
        if ( b == 0 ) return; 

        if ( state == PARAMS_STATE )
        {
            // if still receiving parameters
            if ( b < 64 )
            {
                argbuf[0]++;
                
                // grow if needed
                if ( argbuf[0] == argbuf.length )
                {
                    char[] tmp = new char[ argbuf.length * 2 ];
                    System.arraycopy( argbuf, 0, tmp, 0, argbuf.length );
                    argbuf = tmp; 
                }
                
                argbuf[ argbuf[0] ] = (char) b;
            }
            else // final byte: process the command
            {
                processCommand( b );

                // reset for next command
                argbuf[0] = 0;
                state = NORMAL_STATE;
            }
        }
        else
        if ( state == ESCAPE_STATE )
        {
            // if a valid escape sequence
            if ( b == '[' )
            {
                state = PARAMS_STATE;
            }
            else // not an escape sequence
            { 
                // allow escape to pass through
                state = NORMAL_STATE;
                processData( (byte) 27 );
                processData( b );
            }
        }
        else // NORMAL_STATE
        {
            if ( b == 27 )
            {
                state = ESCAPE_STATE;
            }
            else
            {
                processData( b );
            }
        }
    }

    /**
    * Appends the specified byte to the display buffer.
    */
    protected void processData( byte b )
    { 
        // grow buffer as needed
        if ( cursor + columns > buffer.length )
        {
            try
            {
                // expand by sixteen screenfuls at a time
                byte[] tmp = new byte[ buffer.length + rows*columns*16 ];
                System.arraycopy( buffer, 0, tmp, 0, buffer.length );
                buffer = tmp;
            } 
            catch ( OutOfMemoryError e )
            {
                // no more memory to grow: just clear half and reuse the existing buffer
                System.err.println( "Could not allocate buffer larger than: " + buffer.length );
                int i, half = buffer.length / 2;
                for ( i = 0; i < half; i++ ) buffer[i] = buffer[i+half];
                for ( i = half; i < buffer.length; i++ ) buffer[i] = 0;
                int oldLastScreen = calcLastVisibleScreen();
                cursor = cursor - half; // start from last input
                if ( scrollY == oldLastScreen ) scrollY = calcLastVisibleScreen();
            }
       }
        
        // start with the last screen containing the cursor
        int offsetY = calcLastVisibleScreen();
        
        switch ( b )
        {
	    case 7: // Zane - bell
	    midletDisplay.vibrate(800);
	    break;

            case 8: // back space
            cursor--;
            break;
        
            case 10: // line feed
            cursor = cursor + columns - ( cursor % columns );
            break;
            
            case 13: // carriage return
            cursor = cursor - ( cursor % columns );
            break;
            
            default: 
            if ( b > 31 ) 
            { 
                // only show visible characters
                buffer[cursor++] = b;
            }
            // ignore all others
        }

        // increment bound if necessary
        while ( cursor > bound ) bound += columns;
        
        // if the user has scrolled back, don't lose
        // their position when new input comes in
        int newY = calcLastVisibleScreen();
        if ( newY != offsetY && offsetY == scrollY )
        {
            // otherwise, make the latest input visible
            scrollY = (short) newY;
        }

        repaint();
    }
    
    /**
    * Executes the specified ANSI command, obtaining arguments
    * as needed from the getArgument() and getArgumentCount() methods.
    */
    protected void processCommand( byte command )
    { //System.out.println( "processCommand: " + (char) command + " : " + new String( argbuf, 1, argbuf[0] ) );
        try 
        {
            int arg = 0;
            switch ( command )
            {
                case 'H': // cursor position to x, y or home
                case 'f': // cursor position to x, y or home
                    if ( argbuf[0] > 0 )
                    {
                        cursor = bound-1 - ((rows-getArgument( 0 ))*columns) - (columns-getArgument( 1 ));
                    }
                    else
                    { // return to top-left position
                        cursor = bound - ( rows * columns );
                    }
                    break;
                
                case 'A': // cursor up by x
                    if ( argbuf[0] == 0 ) 
                    {
                        cursor = cursor - columns;
                    }
                    else
                    {
                        cursor = cursor - columns * getArgument( 0 );
                    }
                    break;
                
                case 'B': // cursor down by x
                    if ( argbuf[0] == 0 ) 
                    {
                        cursor = cursor + columns;
                    }
                    else
                    {
                        cursor = cursor + columns * getArgument( 0 );
                    }
                    break;
                
                case 'C': // cursor forward by x
                    if ( argbuf[0] == 0 ) 
                    {
                        cursor = cursor + 1;
                    }
                    else
                    {
                        cursor = cursor + getArgument( 0 );
                    }
                    break;
                
                case 'D': // cursor backward by x
                    if ( argbuf[0] == 0 ) 
                    {
                        cursor = cursor - 1;
                    }
                    else
                    {
                        cursor = cursor - getArgument( 0 );
                    }
                    break;
                
                case 'd': // cursor to row x (preserve column position)
                    if ( argbuf[0] > 0 ) 
                    {
                        cursor = bound - ((rows-getArgument( 0 )+1)*columns) + ( cursor % columns );
                    }
                    break;
                
                case 'G': // cursor to column x (preserve row position)
                    if ( argbuf[0] > 0 ) 
                    {
                        cursor = cursor - ( cursor % columns ) + getArgument( 0 );
                    }
                    break;
                
                case '@': // insert x blank spaces
                    arg = getArgument( 0 );
                    for ( int i = cursor + columns-(cursor%columns); i > cursor+arg; i-- )
                    {
                        buffer[i] = buffer[i-arg];
                    }
                    for ( int i = cursor; i < cursor+arg; i++ )
                    {
                        buffer[i] = ' ';
                    }
                    break;
                
                case 'L': // insert x blank lines (not from cursor?)
                    arg = getArgument( 0 ) * columns;
                    {
                        int origin = cursor - (cursor%columns);
                        for ( int i = bound; i >= origin+arg; i-- )
                        {
                            buffer[i] = buffer[i-arg];
                        }
                        for ( int i = origin; i < origin+arg; i++ )
                        {
                            buffer[i] = 0;
                        }
                    }
                    break;
                
                case 'M': // delete x lines from cursor
                    arg = getArgument( 0 ) * columns;
                    for ( int i = cursor; i < bound; i++ )
                    {
                        if ( i < cursor + arg )
                        {
                            buffer[i] = buffer[i+arg];
                        }
                        else
                        {
                            buffer[i] = 0;
                        }
                    }
                    break;
                
                case 'P': // delete x characters from cursor
                    arg = getArgument( 0 );
                    for ( int i = cursor; i%columns!=0; i++ )
                    {
                        if ( i < cursor + arg )
                        {
                            buffer[i] = buffer[i+arg];
                        }
                        else
                        {
                            buffer[i] = 0;
                        }
                    }
                    break;
                
                case 's': // save cursor position
                    savedCursor = cursor;
                    break;

                case 'u': // restore cursor position
                    cursor = savedCursor;
                    break;
                
                case 'J': // clear region
                    if ( argbuf[0] > 0 ) arg = getArgument( 0 );
                    switch ( arg )
                    {
                        case 1: // from beginning of screen to cursor
                            for ( int i = bound - rows * columns; i <= cursor; i++ )
                            {
                                buffer[i] = 0;
                            }
                            break;
                        case 2: // clear all screen
                            cursor = bound - rows * columns;
                            for ( int i = cursor; i < bound; i++ )
                            {
                                buffer[i] = 0;
                            }
                            break;
                        default: // from cursor to end of screen
                            for ( int i = cursor; i < bound; i++ )
                            {
                                buffer[i] = 0;
                            }
                            break;
                    }
                    break;
                
                case 'K': // erase rest of line
                    if ( argbuf[0] > 0 ) arg = getArgument( 0 );
                    switch ( arg )
                    {
                        case 1: // from beginning of line to cursor
                            buffer[cursor] = 0;
                            for ( int i = cursor - (cursor % columns); i < cursor; i++ )
                            {
                                buffer[i] = 0;
                            }
                            break;
                        case 2: // clear all line
                            buffer[cursor - (cursor % columns)] = 0;
                            for ( int i = cursor - (cursor % columns) + 1; i % columns != 0; i++ )
                            {
                                buffer[i] = 0;
                            }
                            break;
                        default: // from cursor to end of line
                            buffer[cursor] = 0;
                            for ( int i = cursor+1; i % columns != 0; i++ )
                            {
                                buffer[i] = 0;
                            }
                            break;
                    }
                    break;
                
                case 'm': // set graphics mode
                    int argc = getArgumentCount();
                    for ( int i = 0; i < argc; i++ )
                    {
                        highlight = getArgument( i ) > 0;
                    }
                    break;
                
                case 'h': // set emulation option
                    System.err.println( "h: emulation mode not supported" 
                        + " : " + new String( argbuf, 1, argbuf[0] ) );
                    break;
                
                case 'l': // unset emulation option
                    System.err.println( "l: reset emulation mode not supported" 
                        + " : " + new String( argbuf, 1, argbuf[0] ) );
                    break;
                
                case 'p': // define keyboard mappings
                    System.err.println( "p: keyboard mappings not supported" 
                        + " : " + new String( argbuf, 1, argbuf[0] ) );
                    break;
                    
                default:
                    System.err.println( "unsupported command: " + (char) command 
                        + " : " + new String( argbuf, 1, argbuf[0] ) );
                    
            }
        }
        catch ( Throwable t ) 
        {
            // probably a parse exception or wrong number of args
            System.err.println( "Error in processCommand: " );
            t.printStackTrace();
        }
    }

    /**
    * Returns the argument at the specified index from 
    * the argument buffer, throwing an IndexOutOfBounds
    * if there are not enough arguments, or NumberFormatException
    * if the individual argument is not parseable as an int.
    */
    private int getArgument( int index ) 
        throws IndexOutOfBoundsException, NumberFormatException
    {
        int a = 1, b = 1; 
        int c = argbuf[0]+1;
        for ( int i = 0; i < index; i++ )
        {
            while ( argbuf[a] != ';' )
            {
                a++;
                if ( a >= c ) throw new IndexOutOfBoundsException();
            }
            a++;
        }
        b = a+1;
        while ( b < c && argbuf[b] != ';' )
        {
            b++;
        }
        return Integer.parseInt( new String( argbuf, a, b-a ) );
    }
    
    private int getArgumentCount()
    {
        if ( argbuf[0] == 0 ) return 0;
        
        int result = 1;
        for ( int i = 1; i < argbuf[0]; i++ )
        {
            if ( argbuf[i] == ';' ) result++;
        }
        return result;
    }
    
    public void paint( Graphics g )
    {
        // clear screen
        g.setGrayScale( 0 ); // black
        g.fillRect( 0, 0, getWidth(), getHeight() );
    
        // draw content from buffer
        g.setGrayScale( 255 ); // white
        
        // before: g.setFont( font );
        
        int i;
        byte b;
        
        for ( int y = 0; y < rows; y++ )
        {
            for ( int x = 0; x < columns; x++ )
            {
                i = (y+scrollY)*columns+(x+scrollX);
                if ( i < buffer.length )
                {
                    b = buffer[i];
                    if ( b != 0 )
                    {
                        font.drawChar( g, (char) b, 
                        insetX + x*fontWidth, insetY + y*fontHeight, 
                        g.TOP | g.LEFT );
                    }
                    if ( cursor == i )
                    {
                        g.drawRect( 
                            insetX + x*fontWidth, insetY + y*fontHeight,
                            fontWidth, fontHeight );
                    }
                }
            }
        }
    }
    
    public void keyPressed( int keyCode )
    {
        switch ( getGameAction( keyCode ) )
        {
            case LEFT:
                // move cursor left one column
                move[2] = 'D';
                send( move );
                break;
            case RIGHT:
                // move cursor right one column
                move[2] = 'C';
                send( move );
                break;
            case DOWN:
                if ( scrolling )
                {
                    // scroll down one row
                    scrollY++;
                    if ( scrollY > calcLastVisibleScreen() )
                    {
                        scrollY = calcLastVisibleScreen();
                    }
                    repaint();
                }
                else 
                {
                    // move cursor down one row
                    move[2] = 'B';
                    send( move );
                }
                break;
            case UP:
                if ( scrolling )
                {
                    // scroll up one row
                    scrollY--;
                    if ( scrollY < 0 ) scrollY = 0;
                    repaint();
                }
                else 
                {
                    // move cursor down one row
                    move[2] = 'A';
                    send( move );
                }
                break;
            case FIRE:
                // send a line feed:
                send( (byte) '\n' );
                break;
	}

	switch ( keyCode ) {
	    case Canvas.KEY_NUM1: // Zane
		send( (byte) '\r' );
		break;
	    case Canvas.KEY_NUM7: // Zane
		send( (byte) 0x08 ); // back space
		break;
            case Canvas.KEY_NUM3: // Zane
                byte[] pageUp = new byte[] { 27, '[', '5', '~' };
		send ( pageUp );
		break;
            case Canvas.KEY_NUM9: // Zane
                byte[] pageDown = new byte[] { 27, '[', '6', '~' };
		send ( pageDown );
		break;
            case Canvas.KEY_NUM0: // Zane
		send ( (byte) ' ' );
		break;
        }
    }

    public void keyRepeated( int keyCode )
    {
        switch ( getGameAction( keyCode ) )
        {
            case LEFT:
                // move cursor left one column
                move[2] = 'D';
                //receive( move );
                send( move );
                repaint();
                break;
            case RIGHT:
                // move cursor right one column
                move[2] = 'C';
                //receive( move );
                send( move );
                repaint();
                break;
            case DOWN:
                if ( scrolling )
                {
                    // scroll down by half a screen
                    scrollY += rows/2;
                    if ( scrollY > calcLastVisibleScreen() )
                    {
                        scrollY = calcLastVisibleScreen();
                    }
                }
                else 
                {
                    // move cursor down one row
                    move[2] = 'B';
                    //receive( move );
                    send( move );
                }
                repaint();
                break;
            case UP:
                if ( scrolling )
                {
                    // scroll up by half a screen
                    scrollY -= rows/2;
                    if ( scrollY < 0 ) scrollY = 0;
                }
                else 
                {
                    // move cursor down one row
                    move[2] = 'A';
                    //receive( move );
                    send( move );
                }
                repaint();
                break;
	}

	switch ( keyCode ) {
	    case Canvas.KEY_NUM1: // Zane
		send( (byte) '\r' );
		break;
	    case Canvas.KEY_NUM7: // Zane
		send( (byte) 0x08 ); // back space
		break;
            case Canvas.KEY_NUM3: // Zane
                byte[] pageUp = new byte[] { 27, '[', '5', '~' };
		send ( pageUp );
		break;
            case Canvas.KEY_NUM9: // Zane
                byte[] pageDown = new byte[] { 27, '[', '6', '~' };
		send ( pageDown );
		break;
            case Canvas.KEY_NUM0: // Zane
		send ( (byte) ' ' );
		break;
        }
    }
    
    /**
    * For clarity: calculates the row offset so we don't
    * scroll below the most recent visible input.
    */
    private int calcLastVisibleScreen()
    {
        return Math.max( 0, bound / columns - rows );
    }
    
}
