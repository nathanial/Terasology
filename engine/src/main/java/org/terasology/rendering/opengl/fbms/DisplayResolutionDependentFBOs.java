/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.opengl.fbms;

import org.lwjgl.opengl.Display;
import org.terasology.config.RenderingConfig;
import org.terasology.engine.SimpleUri;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.rendering.nui.layers.mainMenu.videoSettings.ScreenshotSize;
import org.terasology.rendering.opengl.AbstractFBOsManager;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;
import org.terasology.rendering.opengl.ScreenGrabber;
import org.terasology.rendering.opengl.SwappableFBO;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static org.terasology.config.RenderingConfig.FBO_SCALE;
import static org.terasology.engine.subsystem.lwjgl.LwjglDisplayDevice.DISPLAY_RESOLUTION_CHANGE;
import static org.terasology.rendering.opengl.ScalingFactors.FULL_SCALE;

/**
 * TODO: Add javadocs
 * TODO: Better naming
 */
public class DisplayResolutionDependentFBOs extends AbstractFBOsManager implements PropertyChangeListener {
    public static final SimpleUri FINAL_BUFFER = new SimpleUri("engine:fbo.finalBuffer");

    private SwappableFBO gBufferPair;

    private FBO.Dimensions fullScale = new FBO.Dimensions();
    private RenderingConfig renderingConfig;
    private ScreenGrabber screenGrabber;

    private boolean wasTakingScreenshotLastFrame = false;

    public DisplayResolutionDependentFBOs(RenderingConfig renderingConfig, ScreenGrabber screenGrabber, DisplayDevice displayDevice) {
        this.renderingConfig = renderingConfig;
        this.screenGrabber = screenGrabber;

        renderingConfig.subscribe(FBO_SCALE, this);

        displayDevice.subscribe(DISPLAY_RESOLUTION_CHANGE, this);

        updateFullScale();
        generateDefaultFBOs();
    }

    private void generateDefaultFBOs() {
        FBO gBuffer1 = generateWithDimensions(new FBOConfig(new SimpleUri("engine:fbo.gBuffer1"), FULL_SCALE, FBO.Type.HDR)
                .useDepthBuffer().useNormalBuffer().useLightBuffer().useStencilBuffer(), fullScale);
        FBO gBuffer2 = generateWithDimensions(new FBOConfig(new SimpleUri("engine:fbo.gBuffer2"), FULL_SCALE, FBO.Type.HDR)
                .useDepthBuffer().useNormalBuffer().useLightBuffer().useStencilBuffer(), fullScale);
        generateWithDimensions(new FBOConfig(FINAL_BUFFER, FULL_SCALE, FBO.Type.DEFAULT), fullScale);

        gBufferPair = new SwappableFBO(gBuffer1, gBuffer2);
    }

    @Override
    public FBO request(FBOConfig fboConfig) {
        FBO fbo;
        SimpleUri fboName = fboConfig.getName();
        if (fboConfigs.containsKey(fboName)) {
            if (!fboConfig.equals(fboConfigs.get(fboName))) {
                throw new IllegalArgumentException("Requested FBO is already available with different configuration");
            }
            fbo = fboLookup.get(fboConfig.getName());
        } else {
            fbo = generateWithDimensions(fboConfig, fullScale.multiplyBy(fboConfig.getScale()));
        }
        retain(fboName);
        return fbo;
    }

    private void updateFullScale() {
        fullScale.setDimensions(Display.getWidth(), Display.getHeight());
        fullScale.multiplySelfBy(renderingConfig.getFboScale() / 100f);
    }

    /**
     * Invoked before real-rendering starts
    */
    public void update() {
        if (!screenGrabber.isTakingScreenshot()) {
            if (wasTakingScreenshotLastFrame) {
                updateFullScale();
                regenerateFbos();

                wasTakingScreenshotLastFrame = false;
            }
        } else {
            ScreenshotSize screenshotSize = renderingConfig.getScreenshotSize();
            // TODO: Remove dependency on Display
            fullScale.setDimensions(screenshotSize.getWidth(Display.getWidth()),
                    screenshotSize.getHeight(Display.getHeight()));
            regenerateFbos();

            wasTakingScreenshotLastFrame = true;
        }
    }

    private void regenerateFbos() {
        for (SimpleUri urn : fboConfigs.keySet()) {
            FBOConfig fboConfig = getFboConfig(urn);
            fboConfig.setDimensions(fullScale.multiplyBy(fboConfig.getScale()));
            FBO.recreate(get(urn), getFboConfig(urn));
        }

        notifySubscribers();
   }

    private void disposeAllFbos() {
        // TODO: This should be public, and should be called while disposing an object of this class, to prevent leaks.
        for (SimpleUri urn : fboConfigs.keySet()) {
            fboLookup.get(urn).dispose();
        }
        fboLookup.clear();
    }

    public SwappableFBO getGBufferPair() {
        return gBufferPair;
    }

    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if (propertyChangeEvent.getPropertyName().equals(DISPLAY_RESOLUTION_CHANGE) || propertyChangeEvent.getPropertyName().equals(FBO_SCALE)) {
            updateFullScale();
            regenerateFbos();
        }
    }
}
