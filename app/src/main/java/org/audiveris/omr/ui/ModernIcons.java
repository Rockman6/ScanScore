//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                    M o d e r n I c o n s                                       //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
package org.audiveris.omr.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

/**
 * Flat modern toolbar icons drawn with Java2D.
 * No external files needed — all icons are rendered programmatically.
 */
public class ModernIcons
{
    //~ Static fields ------------------------------------------------------------------------------

    private static final int SIZE = 40;

    // Primary blue used for filled icons
    private static final Color BLUE = new Color(59, 130, 246);

    // Darker shade for outlines / depth
    private static final Color BLUE_DARK = new Color(37, 99, 205);

    // Stroke for outlines
    private static final BasicStroke STROKE2 = new BasicStroke(
            2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private static final BasicStroke STROKE25 = new BasicStroke(
            2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    //~ Constructors -------------------------------------------------------------------------------

    private ModernIcons ()
    {
    }

    //~ Static Methods -----------------------------------------------------------------------------

    //---------//
    // getIcon //
    //---------//
    /**
     * Return a modern flat icon for the given action method name, or null if not mapped.
     *
     * @param methodName the action method name (e.g. "openImageFiles")
     * @return the icon, or null
     */
    public static ImageIcon getIcon (String methodName)
    {
        return switch (methodName) {
            case "openImageFiles"  -> folder();
            case "transcribeBook"  -> play();
            case "exportBook"      -> export();
            case "saveBook"        -> save();
            case "zoomWidth"       -> zoomWidth();
            case "zoomHeight"      -> zoomHeight();
            case "toggleDarkMode"  -> moon();
            case "toggleEditorMode" -> pencil();
            default -> null;
        };
    }

    //--------//
    // folder //
    //--------//
    /** Open folder icon — represents "open file". */
    private static ImageIcon folder ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        // Folder body (light fill)
        g.setColor(new Color(219, 234, 254));
        g.fillRoundRect(2, 10, 24, 14, 4, 4);

        // Folder tab (top-left)
        g.setColor(BLUE);
        int[] tabX = {2, 2, 11, 13};
        int[] tabY = {13, 7,  7, 10};
        g.fillPolygon(tabX, tabY, 4);

        // Folder body outline
        g.setColor(BLUE);
        g.setStroke(STROKE2);
        g.drawRoundRect(2, 10, 24, 14, 4, 4);

        // Tab outline
        Path2D tab = new Path2D.Float();
        tab.moveTo(2, 13);
        tab.lineTo(2, 7);
        tab.lineTo(11, 7);
        tab.lineTo(13, 10);
        g.draw(tab);

        g.dispose();
        return new ImageIcon(img);
    }

    //------//
    // play //
    //------//
    /** Play triangle — represents "transcribe". */
    private static ImageIcon play ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        int[] xs = {6, 6, 23};
        int[] ys = {4, 24, 14};

        g.setColor(BLUE);
        g.fillPolygon(xs, ys, 3);

        g.setColor(BLUE_DARK);
        g.setStroke(STROKE2);
        g.drawPolygon(xs, ys, 3);

        g.dispose();
        return new ImageIcon(img);
    }

    //--------//
    // export //
    //--------//
    /** Document with arrow — represents "export to MusicXML". */
    private static ImageIcon export ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        // Document body
        g.setColor(new Color(219, 234, 254));
        g.fillRoundRect(3, 3, 14, 19, 3, 3);
        g.setColor(BLUE);
        g.setStroke(STROKE2);
        g.drawRoundRect(3, 3, 14, 19, 3, 3);

        // Arrow shaft (pointing up-right)
        g.setStroke(STROKE25);
        g.drawLine(14, 19, 24, 9);

        // Arrow head
        Path2D head = new Path2D.Float();
        head.moveTo(24, 9);
        head.lineTo(17, 9);
        head.moveTo(24, 9);
        head.lineTo(24, 16);
        g.draw(head);

        g.dispose();
        return new ImageIcon(img);
    }

    //------//
    // save //
    //------//
    /** Cloud with down-arrow — represents "save". */
    private static ImageIcon save ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        // Floppy-disk style save icon
        // Outer square
        g.setColor(new Color(219, 234, 254));
        g.fillRoundRect(3, 3, 22, 22, 3, 3);
        g.setColor(BLUE);
        g.setStroke(STROKE2);
        g.drawRoundRect(3, 3, 22, 22, 3, 3);

        // Label area (top strip)
        g.setColor(BLUE);
        g.fillRoundRect(7, 3, 10, 7, 1, 1);

        // Bottom pocket
        g.setColor(new Color(219, 234, 254));
        g.fillRoundRect(6, 14, 16, 10, 2, 2);
        g.setColor(BLUE);
        g.setStroke(STROKE2);
        g.drawRoundRect(6, 14, 16, 10, 2, 2);

        // Down arrow inside pocket
        g.setStroke(STROKE25);
        g.drawLine(14, 16, 14, 21);
        Path2D arr = new Path2D.Float();
        arr.moveTo(14, 22);
        arr.lineTo(11, 18);
        arr.moveTo(14, 22);
        arr.lineTo(17, 18);
        g.draw(arr);

        g.dispose();
        return new ImageIcon(img);
    }

    //-----------//
    // zoomWidth //
    //-----------//
    /** Horizontal arrows — fit to width. */
    private static ImageIcon zoomWidth ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        g.setColor(BLUE);
        g.setStroke(STROKE25);

        // Left arrow ←
        g.drawLine(2, 14, 12, 14);
        Path2D left = new Path2D.Float();
        left.moveTo(2, 14);
        left.lineTo(6, 10);
        left.moveTo(2, 14);
        left.lineTo(6, 18);
        g.draw(left);

        // Right arrow →
        g.drawLine(16, 14, 26, 14);
        Path2D right = new Path2D.Float();
        right.moveTo(26, 14);
        right.lineTo(22, 10);
        right.moveTo(26, 14);
        right.lineTo(22, 18);
        g.draw(right);

        // Center vertical bar
        g.setStroke(STROKE2);
        g.drawLine(14, 8, 14, 20);

        g.dispose();
        return new ImageIcon(img);
    }

    //------------//
    // zoomHeight //
    //------------//
    /** Vertical arrows — fit to height. */
    private static ImageIcon zoomHeight ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        g.setColor(BLUE);
        g.setStroke(STROKE25);

        // Up arrow ↑
        g.drawLine(14, 2, 14, 12);
        Path2D up = new Path2D.Float();
        up.moveTo(14, 2);
        up.lineTo(10, 6);
        up.moveTo(14, 2);
        up.lineTo(18, 6);
        g.draw(up);

        // Down arrow ↓
        g.drawLine(14, 16, 14, 26);
        Path2D down = new Path2D.Float();
        down.moveTo(14, 26);
        down.lineTo(10, 22);
        down.moveTo(14, 26);
        down.lineTo(18, 22);
        g.draw(down);

        // Center horizontal bar
        g.setStroke(STROKE2);
        g.drawLine(8, 14, 20, 14);

        g.dispose();
        return new ImageIcon(img);
    }

    //------//
    // moon //
    //------//
    /** Crescent moon — represents dark mode. */
    private static ImageIcon moon ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        // Full circle (will be masked)
        g.setColor(BLUE);
        g.fillOval(2, 3, 20, 22);

        // Mask out inner circle to create crescent effect
        g.setColor(new Color(0, 0, 0, 0));
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.DST_OUT));
        g.fillOval(8, 2, 20, 22);

        // Restore composite
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER));

        g.dispose();
        return new ImageIcon(img);
    }

    //--------//
    // pencil //
    //--------//
    /** Pencil icon — represents editor mode. */
    private static ImageIcon pencil ()
    {
        Graphics2D g = g2d();
        BufferedImage img = (BufferedImage) g.getDeviceConfiguration()
                .createCompatibleImage(SIZE, SIZE, java.awt.Transparency.TRANSLUCENT);
        g.dispose();
        g = setup(img);

        g.setColor(BLUE);
        g.setStroke(STROKE2);

        // Pencil body — diagonal rectangle from top-right to bottom-left
        Path2D body = new Path2D.Float();
        body.moveTo(26, 5);
        body.lineTo(30, 9);
        body.lineTo(11, 28);
        body.lineTo(7,  24);
        body.closePath();
        g.fill(body);

        g.setColor(BLUE_DARK);
        g.draw(body);

        // Eraser tip (small rectangle at top-right)
        g.setColor(new Color(248, 200, 200));
        Path2D eraser = new Path2D.Float();
        eraser.moveTo(26, 5);
        eraser.lineTo(30, 9);
        eraser.lineTo(28, 11);
        eraser.lineTo(24, 7);
        eraser.closePath();
        g.fill(eraser);
        g.setColor(BLUE_DARK);
        g.draw(eraser);

        // Pencil tip triangle at bottom-left
        g.setColor(new Color(240, 210, 160));
        Path2D tip = new Path2D.Float();
        tip.moveTo(7,  24);
        tip.lineTo(11, 28);
        tip.lineTo(7,  32);
        tip.closePath();
        g.fill(tip);

        g.dispose();
        return new ImageIcon(img);
    }

    //~ Private helpers ----------------------------------------------------------------------------

    private static Graphics2D g2d ()
    {
        // Temporary graphics just to get device configuration
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return tmp.createGraphics();
    }

    private static Graphics2D setup (BufferedImage img)
    {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }
}
