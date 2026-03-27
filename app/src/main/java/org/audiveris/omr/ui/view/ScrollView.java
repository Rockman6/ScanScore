//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                      S c r o l l V i e w                                       //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2026. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr.ui.view;

import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.ViewportLayout;

/**
 * Class <code>ScrollView</code> contains a JScrollPane, which provides a comprehensive
 * combination of the following entities.
 * <dl>
 * <dt><b>view:</b></dt>
 * <dd>The display of a {@link RubberPanel}, a component potentially
 * wired to a {@link Zoom} and a mouse adapter {@link Rubber}</dd>
 * <dt><b>zoom:</b></dt>
 * <dd>The {@link Zoom}, whose ratio is to be used when the component is rendered</dd>
 * </dl>
 *
 * @author Hervé Bitteur
 * @author Brenton Partridge
 */
public class ScrollView
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(ScrollView.class);

    //~ Instance fields ----------------------------------------------------------------------------

    /** Current view inside the scrolled pane. */
    protected RubberPanel view;

    /** The concrete UI component. */
    private final JScrollPane component = new JScrollPane();

    //~ Constructors -------------------------------------------------------------------------------

    /**
     * Create a bare view pane.
     * <p>
     * Other related entities, such as view, pixel monitor or zoom, can be added later
     * programmatically via setXXX() methods).
     * <p>
     * The increment related to mouse wheel movement can be adapted via the 'unitIncrement' class
     * constant.
     */
    public ScrollView ()
    {
        // Use a centering viewport so the sheet is centred when smaller than the window
        component.setViewport(new CenteredViewport());

        JScrollBar vertical = component.getVerticalScrollBar();
        vertical.setUnitIncrement(constants.unitIncrement.getValue());

        JScrollBar horizontal = component.getHorizontalScrollBar();
        horizontal.setUnitIncrement(constants.unitIncrement.getValue());

        // Install smooth-scroll handler for trackpad (replaces the default integer-based one).
        // Adapted from FlatLaf (Apache 2.0) and NetBeans SmoothScrollPaneUI (Apache 2.0).
        // See: https://github.com/JFormDesigner/FlatLaf  (FlatScrollPaneUI.java)
        //      https://github.com/apache/netbeans  (SmoothScrollPaneUI.java)
        //      Pavel Fatin — "Scrolling with Pleasure"
        component.setWheelScrollingEnabled(false); // we handle it ourselves
        component.addMouseWheelListener(new SmoothWheelListener());

        bindKeys(component);
    }

    /**
     * Create a view pane, with pre-built view.
     *
     * @param view the pre-built panel
     */
    public ScrollView (RubberPanel view)
    {
        this();
        setView(view);
    }

    //~ Methods ------------------------------------------------------------------------------------

    //----------//
    // bindKeys //
    //----------//
    private void bindKeys (JComponent component)
    {
        InputMap inputMap;
        //        inputMap = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        //        inputMap = component.getInputMap(JComponent.WHEN_FOCUSED); // Default
        inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        ActionMap actionMap = component.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("UP"), "UpAction");
        actionMap.put("UpAction", new UpAction());

        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "DownAction");
        actionMap.put("DownAction", new DownAction());

        inputMap.put(KeyStroke.getKeyStroke("shift UP"), "ShiftUpAction");
        actionMap.put("ShiftUpAction", new ShiftUpAction());

        inputMap.put(KeyStroke.getKeyStroke("shift DOWN"), "ShiftDownAction");
        actionMap.put("ShiftDownAction", new ShiftDownAction());

        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "LeftAction");
        actionMap.put("LeftAction", new LeftAction());

        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "RightAction");
        actionMap.put("RightAction", new RightAction());

        inputMap.put(KeyStroke.getKeyStroke("shift LEFT"), "ShiftLeftAction");
        actionMap.put("ShiftLeftAction", new ShiftLeftAction());

        inputMap.put(KeyStroke.getKeyStroke("shift RIGHT"), "ShiftRightAction");
        actionMap.put("ShiftRightAction", new ShiftRightAction());
    }

    //-----------//
    // fitHeight //
    //-----------//
    /**
     * Define the zoom ratio so that the full height of the model can be visible.
     * We force the zoom ratio to stand within the range of the slider.
     */
    public void fitHeight ()
    {
        final Rectangle vr = view.getVisibleRect();
        final Dimension dim = view.getModelSize();
        logger.debug("fitHeight vr={} dim={}", vr, dim);

        if ((vr != null) && (dim != null)) {
            setZoomRatio(vr.height / (double) dim.height);
        }
    }

    //----------//
    // fitWhole //
    //----------//
    /**
     * Define the zoom ratio so that the full model can be visible.
     * We force the zoom ratio to stand within the range of the slider.
     */
    public void fitWhole ()
    {
        final Rectangle vr = view.getVisibleRect();
        final Dimension dim = view.getModelSize();
        logger.debug("fitWhole vr={} dim={}", vr, dim);

        if ((vr != null) && (dim != null)) {
            setZoomRatio(Math.min(vr.width / (double) dim.width, vr.height / (double) dim.height));
        }
    }

    //----------//
    // fitWidth //
    //----------//
    /**
     * Define the zoom ratio so that the full width of the model can be visible.
     * We force the zoom ratio to stand within the range of the slider.
     *
     * @return true if we have been able to fit the display width, false otherwise which happens
     *         when the display width is zero
     */
    public boolean fitWidth ()
    {
        final Rectangle vr = view.getVisibleRect();
        final Dimension dim = view.getModelSize();
        logger.debug("fitWidth vr={} dim={}", vr, dim);

        if ((vr != null) && (dim != null) && (vr.width > 0) && (dim.width > 0)) {
            setZoomRatio(vr.width / (double) dim.width);

            return true;
        }

        return false;
    }

    //--------------//
    // getComponent //
    //--------------//
    /**
     * Report the UI component
     *
     * @return the concrete component
     */
    public JScrollPane getComponent ()
    {
        return component;
    }

    //----------------//
    // getRubberFocus //
    //----------------//
    /**
     * Retrieve the coordinates of what is currently the focus point of the display.
     * Typically, this is the center of the rubber rectangle.
     *
     * @return the focus point, or null if the view is null
     */
    public Point getRubberFocus ()
    {
        if (view != null) {
            Point center = view.rubber.getCenter();

            if (center != null) {
                logger.debug("getRubberFocus rubber center={}", center);

                return center; // Of rubber band
            } else {
                logger.debug("getRubberFocus panelcenter={}", view.getPanelCenter());

                return view.getPanelCenter(); // Of visible rectangle
            }
        } else {
            logger.warn("getRubberFocus : no rubber, no view ???");

            return null;
        }
    }

    //--------------------//
    // getRubberSelection //
    //--------------------//
    /**
     * Retrieve a copy of the rubber rectangle, or a zero-height, zero-width rectangle
     * at <code>getRubberFocus()</code> if the rubber rectangle does not exist.
     *
     * @return the rubber selection, or null if the view is null
     */
    public Rectangle getRubberSelection ()
    {
        Rectangle rect = view.rubber.getRectangle();

        if (rect != null) {
            return new Rectangle(rect);
        } else {
            Point focus = getRubberFocus();

            if (focus != null) {
                return new Rectangle(focus);
            }
        }

        return null;
    }

    //---------//
    // getView //
    //---------//
    /**
     * Report the view displayed in the view pane
     *
     * @return the view
     */
    public RubberPanel getView ()
    {
        return view;
    }

    //---------//
    // setView //
    //---------//
    /**
     * Allow to set the panel to be displayed
     *
     * @param view the pre-built panel
     */
    public final void setView (RubberPanel view)
    {
        // Display the view in the scrollpane
        component.setViewportView(view);

        // At the end, to avoid too early setting of the zoom ratio
        this.view = view;
    }

    //--------------//
    // setZoomRatio //
    //--------------//
    /**
     * A convenient method which allows to set another zoom ratio,
     * programmatically.
     *
     * @param zoomRatio the new display zoom ratio
     */
    public void setZoomRatio (double zoomRatio)
    {
        logger.debug("setZoomRatio zoomRatio={}", zoomRatio);

        if (view.getZoom() != null) {
            view.getZoom().setRatio(zoomRatio);
        } else {
            logger.warn("setZoomRatio. No zoom assigned");
        }
    }

    //~ Inner Classes ------------------------------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {
        private final Constant.Integer unitIncrement = new Constant.Integer(
                "Pixels",
                64,
                "Size of mouse wheel increment for ScrollView");
    }

    private class DownAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar vertical = component.getVerticalScrollBar();
            vertical.setValue(vertical.getValue() + vertical.getUnitIncrement());
        }
    }

    private class LeftAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar horizontal = component.getHorizontalScrollBar();
            horizontal.setValue(horizontal.getValue() - horizontal.getUnitIncrement());
        }
    }

    private class RightAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar horizontal = component.getHorizontalScrollBar();
            horizontal.setValue(horizontal.getValue() + horizontal.getUnitIncrement());
        }
    }

    private class ShiftDownAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar vertical = component.getVerticalScrollBar();
            vertical.setValue(vertical.getValue() + 1);
        }
    }

    private class ShiftLeftAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar horizontal = component.getHorizontalScrollBar();
            horizontal.setValue(horizontal.getValue() - 1);
        }
    }

    private class ShiftRightAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar horizontal = component.getHorizontalScrollBar();
            horizontal.setValue(horizontal.getValue() + 1);
        }
    }

    private class ShiftUpAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar vertical = component.getVerticalScrollBar();
            vertical.setValue(vertical.getValue() - 1);
        }
    }

    private class UpAction
            extends AbstractAction
    {
        @Override
        public void actionPerformed (ActionEvent e)
        {
            final JScrollBar vertical = component.getVerticalScrollBar();
            vertical.setValue(vertical.getValue() - vertical.getUnitIncrement());
        }
    }

    //---------------------//
    // SmoothWheelListener //
    //---------------------//
    /**
     * Pixel-precise mouse-wheel handler for smooth trackpad scrolling.
     * <p>
     * Adapted from FlatLaf (Apache 2.0, JFormDesigner/FlatLaf) and
     * NetBeans SmoothScrollPaneUI (Apache 2.0, apache/netbeans).
     * Uses {@code getPreciseWheelRotation()} so that fractional trackpad
     * deltas (e.g. 0.1) produce proportional pixel movement instead of
     * being rounded to 0 or 1 by the default Swing handler.
     */
    private class SmoothWheelListener
            implements MouseWheelListener
    {
        @Override
        public void mouseWheelMoved (MouseWheelEvent e)
        {
            // Let Rubber handle zoom (Ctrl/Cmd + scroll)
            if (e.isControlDown() || e.isMetaDown()) {
                return; // not consumed — Rubber will pick it up
            }

            JScrollBar scrollbar = e.isShiftDown()
                    ? component.getHorizontalScrollBar()
                    : component.getVerticalScrollBar();

            if (scrollbar == null || !scrollbar.isVisible()) {
                // Try the other axis
                scrollbar = e.isShiftDown()
                        ? component.getVerticalScrollBar()
                        : component.getHorizontalScrollBar();
                if (scrollbar == null || !scrollbar.isVisible()) {
                    return;
                }
            }

            double rotation = e.getPreciseWheelRotation();
            int unitIncrement = scrollbar.getUnitIncrement(rotation < 0 ? -1 : 1);
            int scrollAmount = e.getScrollAmount(); // typically 3

            double delta = rotation * scrollAmount * unitIncrement;

            // Cap to viewport size (prevents overshoot on sub-notch trackpad events)
            JViewport viewport = component.getViewport();
            if (viewport != null && Math.abs(rotation) < 1.0 + 1e-5) {
                int viewportSize = (scrollbar.getOrientation() == SwingConstants.VERTICAL)
                        ? viewport.getHeight()
                        : viewport.getWidth();
                delta = Math.max(-viewportSize, Math.min(delta, viewportSize));
            }

            int idelta = (int) Math.round(delta);

            // Force at least 1px movement to prevent stalling (FlatLaf technique)
            if (idelta == 0) {
                if (rotation > 0) {
                    idelta = 1;
                } else if (rotation < 0) {
                    idelta = -1;
                }
            }

            int value = scrollbar.getValue();
            int maxValue = scrollbar.getMaximum() - scrollbar.getVisibleAmount();
            int newValue = Math.max(scrollbar.getMinimum(), Math.min(value + idelta, maxValue));

            if (newValue != value) {
                scrollbar.setValue(newValue);
            }

            e.consume();
        }
    }

    //------------------//
    // CenteredViewport //
    //------------------//
    /**
     * A JViewport that centres its content when the content is smaller than the viewport.
     * When the sheet is zoomed out enough to fit inside the window, it appears in the
     * middle rather than anchored to the top-left corner.
     * <p>
     * IMPORTANT: Only re-centres on viewport resize, NOT on every layout pass.
     * The previous version re-centred on every scroll event, causing the view to
     * snap back to the centre.
     */
    private static class CenteredViewport
            extends JViewport
    {
        /** Last known viewport size — re-centre only when this changes. */
        private Dimension lastViewportSize = new Dimension(0, 0);

        CenteredViewport ()
        {
            setLayout(new ViewportLayout()
            {
                @Override
                public void layoutContainer (Container parent)
                {
                    super.layoutContainer(parent);

                    JViewport vp = (JViewport) parent;
                    Component v = vp.getView();

                    if (v == null) {
                        return;
                    }

                    Dimension vs = vp.getSize();
                    Dimension cs = v.getPreferredSize();

                    // Only centre when the content is smaller than the viewport
                    boolean needsCenterX = vs.width > cs.width;
                    boolean needsCenterY = vs.height > cs.height;

                    if (!needsCenterX && !needsCenterY) {
                        lastViewportSize.setSize(vs);
                        return; // content fills viewport, no centering needed
                    }

                    // Re-centre only when viewport size changed (window resize / zoom),
                    // not on scroll-triggered relayouts.
                    boolean sizeChanged = !vs.equals(lastViewportSize);
                    lastViewportSize.setSize(vs);

                    if (sizeChanged || needsCenterX || needsCenterY) {
                        int x = needsCenterX ? (vs.width - cs.width) / 2 : v.getX();
                        int y = needsCenterY ? (vs.height - cs.height) / 2 : v.getY();
                        v.setLocation(x, y);
                    }
                }
            });
        }
    }
}
