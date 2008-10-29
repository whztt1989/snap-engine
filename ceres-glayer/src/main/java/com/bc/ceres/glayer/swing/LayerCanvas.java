/*
 * $Id$
 *
 * Copyright (C) 2008 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.bc.ceres.glayer.swing;

import com.bc.ceres.core.Assert;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.support.ImageLayer;
import com.bc.ceres.glayer.support.LayerViewInvalidationListener;
import com.bc.ceres.glayer.swing.NavControl.NavControlModel;
import com.bc.ceres.grender.AdjustableView;
import com.bc.ceres.grender.InteractiveRendering;
import com.bc.ceres.grender.Viewport;
import com.bc.ceres.grender.support.DefaultViewport;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 * A Swing component capable of drawing a collection of {@link com.bc.ceres.glayer.Layer}s.
 *
 * @author Norman Fomferra
 */
public class LayerCanvas extends JPanel implements AdjustableView {

    private LayerCanvasModel model;
    private CanvasRendering canvasRendering;
    private Layer.RenderCustomizer renderCustomizer;

    private boolean navControlShown;
    private WakefulComponent navControlWrapper;
    private boolean zoomedAll;

    // AdjustableView properties
    private Rectangle2D maxVisibleModelBounds;
    private double minZoomFactor;
    private double maxZoomFactor;
    private double defaultZoomFactor;

    private ArrayList<Overlay> overlays;

    private final ModelChangeHandler modelChangeHandler;

    private boolean debug = true;

    public LayerCanvas() {
        this(new Layer());
    }

    public LayerCanvas(Layer layer) {
        this(layer, new DefaultViewport(true));
    }

    public LayerCanvas(final Layer layer, final Viewport viewport) {
        this(new DefaultLayerCanvasModel(layer, viewport));
    }

    public LayerCanvas(LayerCanvasModel model) {
        super(null);
        Assert.notNull(model, "model");
        setOpaque(false);
        this.modelChangeHandler = new ModelChangeHandler();
        this.model = model;
        this.model.addChangeListener(modelChangeHandler);
        this.canvasRendering = new CanvasRendering();
        this.overlays = new ArrayList<Overlay>(4);
        setNavControlShown(false);
        if (!model.getViewport().getViewBounds().isEmpty()) {
            setBounds(model.getViewport().getViewBounds());
        }
    }

    public LayerCanvasModel getModel() {
        return model;
    }

    public void setModel(LayerCanvasModel newModel) {
        Assert.notNull(newModel, "newModel");
        LayerCanvasModel oldModel = this.model;
        if (newModel != oldModel) {
            oldModel.removeChangeListener(modelChangeHandler);
            this.model = newModel;
            newModel.addChangeListener(modelChangeHandler);
            zoomedAll = false;
            updateAdjustableViewProperties();
            repaint();
            firePropertyChange("model", oldModel, newModel);
        }
    }

    public Layer getLayer() {
        return model.getLayer();
    }

    public Layer.RenderCustomizer getRenderCustomizer() {
        return renderCustomizer;
    }

    public void setRenderCustomizer(Layer.RenderCustomizer newRenderCustomizer) {
        Layer.RenderCustomizer oldRenderCustomizer = this.renderCustomizer;
        if (oldRenderCustomizer != newRenderCustomizer) {
            this.renderCustomizer = newRenderCustomizer;
            repaint();
            firePropertyChange("renderCustomizer", oldRenderCustomizer, newRenderCustomizer);
        }
    }

    public void dispose() {
        if (model != null) {
            model.removeChangeListener(modelChangeHandler);
        }
        model = null;
    }

    /**
     * Adds an overlay to the canvas.
     *
     * @param overlay An overlay
     */
    public void addOverlay(Overlay overlay) {
        overlays.add(overlay);
        repaint();
    }

    /**
     * Removes an overlay from the canvas.
     *
     * @param overlay An overlay
     */
    public void removeOverlay(Overlay overlay) {
        overlays.remove(overlay);
        repaint();
    }

    /**
     * None API. Don't use this method!
     *
     * @return true, if this canvas uses a {@link NavControl}.
     */
    public boolean isNavControlShown() {
        return navControlShown;
    }

    /**
     * None API. Don't use this method!
     *
     * @param navControlShown true, if this canvas uses a {@link NavControl}.
     */
    public void setNavControlShown(boolean navControlShown) {
        boolean oldValue = this.navControlShown;
        if (oldValue != navControlShown) {
            if (navControlShown) {
                final NavControl navControl = new NavControl(new NavControlModelImpl(getViewport()));
                navControlWrapper = new WakefulComponent(navControl);
                add(navControlWrapper);
            } else {
                remove(navControlWrapper);
                navControlWrapper = null;
            }
            validate();
            this.navControlShown = navControlShown;
        }
    }

    public void zoomAll() {
        getViewport().zoom(getMaxVisibleModelBounds());
    }

    /////////////////////////////////////////////////////////////////////////
    // AdjustableView implementation

    @Override
    public Viewport getViewport() {
        return model.getViewport();
    }

    @Override
    public Rectangle2D getMaxVisibleModelBounds() {
        return maxVisibleModelBounds;
    }

    @Override
    public double getMinZoomFactor() {
        return minZoomFactor;
    }

    @Override
    public double getMaxZoomFactor() {
        return maxZoomFactor;
    }

    @Override
    public double getDefaultZoomFactor() {
        return defaultZoomFactor;
    }

    private void updateAdjustableViewProperties() {
        maxVisibleModelBounds = computeMaxVisibleModelBounds(getLayer().getModelBounds(), getViewport().getOrientation());
        minZoomFactor = computeMinZoomFactor(getViewport().getViewBounds(), maxVisibleModelBounds);
        Layer layer = getLayer();
        double minScale = computeMinImageToModelScale(layer);
        if (minScale > 0.0) {
            defaultZoomFactor = 1.0 / minScale;
            maxZoomFactor = 32.0 / minScale; // empiric!
        } else {
            defaultZoomFactor = minZoomFactor;
            maxZoomFactor = 1000.0 * minZoomFactor;
        }
        if (debug) {
            System.out.println("LayerCanvas.updateAdjustableViewProperties():");
            System.out.println("  zoomFactor            = " + getViewport().getZoomFactor());
            System.out.println("  minZoomFactor         = " + minZoomFactor);
            System.out.println("  maxZoomFactor         = " + maxZoomFactor);
            System.out.println("  defaultZoomFactor     = " + defaultZoomFactor);
            System.out.println("  maxVisibleModelBounds = " + maxVisibleModelBounds);
        }
    }

    static double computeMinZoomFactor(Rectangle2D viewBounds, Rectangle2D maxVisibleModelBounds) {
        double vw = viewBounds.getWidth();
        double vh = viewBounds.getHeight();
        double mw = maxVisibleModelBounds.getWidth();
        double mh = maxVisibleModelBounds.getHeight();
        double sw = mw > 0.0 ? vw / mw : 0.0;
        double sh = mh > 0.0 ? vh / mh : 0.0;
        double s;
        if (sw > 0.0 && sh > 0.0) {
            s = Math.min(sw, sh);
        } else if (sw > 0.0) {
            s = sw;
        } else if (sh > 0.0) {
            s = sh;
        } else {
            s = 0.0;
        }
        return 0.5 * s;
    }

    static double computeMinImageToModelScale(Layer layer) {
        return computeMinImageToModelScale(layer, 0.0);
    }

    private static double computeMinImageToModelScale(Layer layer, double minScale) {
        if (layer instanceof ImageLayer) {
            ImageLayer imageLayer = (ImageLayer) layer;
            double scale = Math.sqrt(Math.abs(imageLayer.getImageToModelTransform().getDeterminant()));
            if (scale > 0.0 && (minScale <= 0.0 || scale < minScale)) {
                minScale = scale;
            }
        }
        for (Layer childLayer : layer.getChildren()) {
            minScale = computeMinImageToModelScale(childLayer, minScale);
        }
        return minScale;
    }


    public static Rectangle2D computeMaxVisibleModelBounds(Rectangle2D modelBounds, double orientation) {
        if (modelBounds == null) {
            return new Rectangle();
        }
        if (orientation == 0.0) {
            return modelBounds;
        } else {
            final AffineTransform t = new AffineTransform();
            t.rotate(orientation, modelBounds.getCenterX(), modelBounds.getCenterY());
            return t.createTransformedShape(modelBounds).getBounds2D();
        }
    }

    // AdjustableView implementation
    /////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////
    // JComponent overrides

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        getViewport().setViewBounds(new Rectangle(x, y, width, height));
    }

    @Override
    public void doLayout() {
        if (navControlShown && navControlWrapper != null) {
            // Use the following code to align the nav. control to the RIGHT (nf, 18.09,.2008)
            //            navControlWrapper.setLocation(getWidth() - navControlWrapper.getWidth() - 4, 4);
            navControlWrapper.setLocation(4, 4);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {

        long t0 = debug ? System.nanoTime() : 0L;

        super.paintComponent(g);

        if (!zoomedAll && maxVisibleModelBounds != null && !maxVisibleModelBounds.isEmpty()) {
            zoomedAll = true;
            zoomAll();
        }

        canvasRendering.setGraphics2D((Graphics2D) g);
        getLayer().render(canvasRendering, renderCustomizer);

        if (!isPaintingForPrint()) {
            for (Overlay overlay : overlays) {
                overlay.paintOverlay(this, (Graphics2D) g);
            }
        }

        if (debug) {
            double dt = (System.nanoTime() - t0) / (1000.0 * 1000.0);
            System.out.println("LayerCanvas.paintComponent() took " + dt + " ms");
        }
    }

    // JComponent overrides
    /////////////////////////////////////////////////////////////////////////

    private class CanvasRendering implements InteractiveRendering {
        private Graphics2D graphics2D;

        public CanvasRendering() {
        }

        @Override
        public Graphics2D getGraphics() {
            return graphics2D;
        }

        void setGraphics2D(Graphics2D graphics2D) {
            this.graphics2D = graphics2D;
        }

        @Override
        public Viewport getViewport() {
            return getModel().getViewport();
        }

        @Override
        public void invalidateRegion(Rectangle region) {
            repaint(region.x, region.y, region.width, region.height);
        }

        @Override
        public void invokeLater(Runnable runnable) {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private static class NavControlModelImpl implements NavControlModel {
        private final Viewport viewport;

        public NavControlModelImpl(Viewport viewport) {
            this.viewport = viewport;
        }

        @Override
        public double getCurrentAngle() {
            return Math.toDegrees(viewport.getOrientation());
        }

        @Override
        public void handleRotate(double rotationAngle) {
            viewport.setOrientation(Math.toRadians(rotationAngle));
        }

        @Override
        public void handleMove(double moveDirX, double moveDirY) {
            viewport.moveViewDelta(16 * moveDirX, 16 * moveDirY);
        }

        @Override
        public void handleScale(double scaleDir) {
            final double oldZoomFactor = viewport.getZoomFactor();
            final double newZoomFactor = (1.0 + 0.1 * scaleDir) * oldZoomFactor;
            viewport.setZoomFactor(newZoomFactor);
        }

    }

    public interface Overlay {
        void paintOverlay(LayerCanvas canvas, Graphics2D graphics);
    }

    private class ModelChangeHandler extends LayerViewInvalidationListener implements LayerCanvasModel.ChangeListener {

        @Override
        public void handleViewInvalidation(Layer layer, Rectangle2D modelRegion) {
            updateAdjustableViewProperties();
            if (modelRegion != null) {
                AffineTransform m2v = getViewport().getModelToViewTransform();
                Rectangle viewRegion = m2v.createTransformedShape(modelRegion).getBounds();
                repaint(viewRegion);
            } else {
                repaint();
            }
        }

        @Override
        public void handleViewportChanged(Viewport viewport, boolean orientationChanged) {
            updateAdjustableViewProperties();
            repaint();
        }
    }
}