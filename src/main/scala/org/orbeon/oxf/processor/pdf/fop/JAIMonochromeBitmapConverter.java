/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id: JAIMonochromeBitmapConverter.java 738453 2009-01-28 11:10:51Z jeremias $ */

package org.orbeon.oxf.processor.pdf.fop;

import javax.media.jai.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;

/**
 * Implementation of the MonochromeBitmapConverter which uses Java Advanced Imaging (JAI)
 * to convert grayscale bitmaps to monochrome bitmaps. JAI provides better dithering options
 * including error diffusion dithering.
 *
 * If you call setHint("quality", "true") on the instance you can enabled error diffusion
 * dithering which produces a nicer result but is also a lot slower.
 */
public class JAIMonochromeBitmapConverter {

    private boolean isErrorDiffusion = false;

    /** {@inheritDoc} */
    public void setHint(String name, String value) {
        if ("quality".equalsIgnoreCase(name)) {
            isErrorDiffusion = "true".equalsIgnoreCase(value);
        }
    }

    public RenderedImage convertToMonochrome(BufferedImage img) {
        return convertToMonochromePlanarImage(img);
    }

    private PlanarImage convertToMonochromePlanarImage(BufferedImage img) {
        if (img.getColorModel().getColorSpace().getNumComponents() != 1) {
            img = convertToGrayscale(img);
        }

        // Load the ParameterBlock for the dithering operation
        // and set the operation name.
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(img);
        String opName = null;
        if (isErrorDiffusion) {
            opName = "errordiffusion";
            LookupTableJAI lut = new LookupTableJAI(new byte[] {(byte)0x00, (byte)0xff});
            pb.add(lut);
            pb.add(KernelJAI.ERROR_FILTER_FLOYD_STEINBERG);
        } else {
            opName = "ordereddither";
            //Create the color cube.
            ColorCube colorMap = ColorCube.createColorCube(DataBuffer.TYPE_BYTE,
                    0, new int[] {2});
            pb.add(colorMap);
            pb.add(KernelJAI.DITHER_MASK_441);
        }

        //Create an image layout for a monochrome b/w image
        ImageLayout layout = new ImageLayout();
        byte[] map = new byte[] {(byte)0x00, (byte)0xff};
        ColorModel cm = new IndexColorModel(1, 2, map, map, map);
        layout.setColorModel(cm);

        // Create a hint containing the layout.
        RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);

        // Dither the image.
        return JAI.create(opName, pb, hints);
    }

    /**
     * Converts an image to a grayscale (8 bits) image. Optionally, the image can be scaled.
     * @param img the image to be converted
     * @return the grayscale image
     */
    private static BufferedImage convertToGrayscale(RenderedImage img) {
        return convertAndScaleImage(img, BufferedImage.TYPE_BYTE_GRAY);
    }

    private static BufferedImage convertAndScaleImage(RenderedImage img, int imageType) {
        final Dimension bmpDimension = new Dimension(img.getWidth(), img.getHeight());
        BufferedImage target = new BufferedImage(bmpDimension.width, bmpDimension.height, imageType);
        transferImage(img, target);
        return target;
    }

    private static void transferImage(RenderedImage source, BufferedImage target) {
        Graphics2D g2d = target.createGraphics();
        try {
            g2d.setBackground(Color.white);
            g2d.setColor(Color.black);
            g2d.clearRect(0, 0, target.getWidth(), target.getHeight());

            AffineTransform at = new AffineTransform();
            if (source.getWidth() != target.getWidth()
                    || source.getHeight() != target.getHeight()) {
                double sx = target.getWidth() / (double)source.getWidth();
                double sy = target.getHeight() / (double)source.getHeight();
                at.scale(sx, sy);
            }
            g2d.drawRenderedImage(source, at);
        } finally {
            g2d.dispose();
        }
    }
}
