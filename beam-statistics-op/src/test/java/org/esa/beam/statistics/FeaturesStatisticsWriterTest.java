/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.statistics;

import java.util.List;
import org.esa.beam.util.FeatureUtils;
import org.esa.beam.util.io.FileUtils;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.net.URL;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class FeaturesStatisticsWriterTest {

    @Test
    public void testSingleShape() throws Exception {
        final URL originalShapefile = getClass().getResource("4_pixels.shp");
        final FeaturesStatisticsWriter featuresStatisticsWriter = FeaturesStatisticsWriter.createShapefileOutputter(originalShapefile, new BandNameCreator());
        final String[] algorithmNames = {"p90", "p95"};

        featuresStatisticsWriter.initialiseOutput(new String[]{"algal_2"}, algorithmNames);

        HashMap<String, Number> statistics = new HashMap<String, Number>();
        statistics.put("p90", 0.1);
        featuresStatisticsWriter.addToOutput("algal_2", "4_pixels.1", statistics);

        statistics.clear();
        statistics.put("p95", 0.195);
        featuresStatisticsWriter.addToOutput("algal_2", "4_pixels.1", statistics);

        final List<SimpleFeature> features = featuresStatisticsWriter.getFeatures();

        assertEquals(1, features.size());

        final SimpleFeature simpleFeature = features.get(0);

        assertNotNull(simpleFeature.getProperty("p90_lgl2"));
        assertNotNull(simpleFeature.getProperty("p95_lgl2"));

        assertEquals(0.1, (Double) simpleFeature.getProperty("p90_lgl2").getValue(), 1E-6);
        assertEquals(0.195, (Double) simpleFeature.getProperty("p95_lgl2").getValue(), 1E-6);
    }

    @Test
    public void testThreeShapes() throws Exception {
        final URL originalShapefile = getClass().getResource("polygons.shp");
        final FeaturesStatisticsWriter featuresStatisticsWriter = FeaturesStatisticsWriter.createShapefileOutputter(originalShapefile, new BandNameCreator());
        final String[] algorithmNames = {"p90", "p95"};

        featuresStatisticsWriter.initialiseOutput(new String[]{"algal_2", "algal_2"}, algorithmNames);

        HashMap<String, Number> statistics = new HashMap<String, Number>();

        statistics.put("p90", 1.90);
        featuresStatisticsWriter.addToOutput("algal_2", "polygons.1", statistics);

        statistics.clear();
        statistics.put("p90", 2.90);
        featuresStatisticsWriter.addToOutput("algal_2", "polygons.2", statistics);

        statistics.clear();
        statistics.put("p90", 3.90);
        featuresStatisticsWriter.addToOutput("algal_2", "polygons.3", statistics);

        statistics.clear();
        statistics.put("p95", 1.95);
        featuresStatisticsWriter.addToOutput("algal_2", "polygons.1", statistics);

        statistics.clear();
        statistics.put("p95", 2.95);
        featuresStatisticsWriter.addToOutput("algal_2", "polygons.2", statistics);

        statistics.clear();
        statistics.put("p95", 3.95);
        featuresStatisticsWriter.addToOutput("algal_2", "polygons.3", statistics);

        final List<SimpleFeature> features = featuresStatisticsWriter.features;

        assertEquals(3, features.size());

        for (SimpleFeature feature : features) {
            if (feature.getID().contains("1")) {
                assertNotNull(feature.getProperty("p90_lgl2"));
                assertNotNull(feature.getProperty("p95_lgl2"));

                assertEquals(1.90, (Double) feature.getProperty("p90_lgl2").getValue(), 1E-6);
                assertEquals(1.95, (Double) feature.getProperty("p95_lgl2").getValue(), 1E-6);
            } else if (feature.getID().contains("2")) {
                assertNotNull(feature.getProperty("p90_lgl2"));
                assertNotNull(feature.getProperty("p95_lgl2"));

                assertEquals(2.90, (Double) feature.getProperty("p90_lgl2").getValue(), 1E-6);
                assertEquals(2.95, (Double) feature.getProperty("p95_lgl2").getValue(), 1E-6);
            } else if (feature.getID().contains("3")) {
                assertNotNull(feature.getProperty("p90_lgl2"));
                assertNotNull(feature.getProperty("p95_lgl2"));

                assertEquals(3.90, (Double) feature.getProperty("p90_lgl2").getValue(), 1E-6);
                assertEquals(3.95, (Double) feature.getProperty("p95_lgl2").getValue(), 1E-6);
            }
        }
    }
}