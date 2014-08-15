package org.opengeo.app;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Envelope;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.ResourceStore;
import org.geoserver.ysld.YsldHandler;
import org.geotools.data.DataUtilities;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Rule;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.geoserver.catalog.Predicates.equal;
import static org.mockito.Mockito.*;

/**
 * Helper to mock up GeoServer configuration.
 */
public class MockGeoServer {

    CatalogBuilder catalog;

    public static MockGeoServer get() {
        return new MockGeoServer();
    }

    public MockGeoServer() {
        catalog = new CatalogBuilder(this);
    }

    public GeoServer build(GeoServer geoServer) {
        catalog.apply(geoServer);
        return geoServer;
    }

    CatalogBuilder catalog() {
        return catalog;
    }

    public abstract class Builder {

        public abstract MockGeoServer geoServer();

    }

    public class ResourcesBuilder extends  Builder {

        ResourceStore resourceStore;
        CatalogBuilder catalogBuilder;

        public ResourcesBuilder(CatalogBuilder catalogBuilder) {
            this.catalogBuilder = catalogBuilder;

            Resource base = mock(Resource.class);
            when(base.dir()).thenReturn(null);

            resourceStore = mock(ResourceStore.class);
            when(resourceStore.get(Paths.BASE)).thenReturn(base);

            when(catalogBuilder.catalog.getResourceLoader())
                .thenAnswer(new Answer<GeoServerResourceLoader>() {
                    @Override
                    public GeoServerResourceLoader answer(InvocationOnMock invocation) throws Throwable {
                        return new GeoServerResourceLoader(resourceStore);
                    }
                });
        }

        public ResourcesBuilder resource(String path, String content) {
            return resource(path, new ByteArrayInputStream(content.getBytes()));
        }

        public ResourcesBuilder resource(String path, InputStream content) {
            Resource r = mock(Resource.class);
            when(r.in()).thenReturn(content);
            when(r.out()).thenReturn(new ByteArrayOutputStream());
            when(resourceStore.get(path)).thenReturn(r);
            return this;
        }

        @Override
        public MockGeoServer geoServer() {
            return catalogBuilder.geoServer();
        }
    }

    public class CatalogBuilder extends Builder {

        Catalog catalog;

        MockGeoServer geoServer;
        List<WorkspaceBuilder> workspaces = new ArrayList();

        public CatalogBuilder(MockGeoServer geoServer) {
            this.geoServer = geoServer;
            this.catalog = mock(Catalog.class);
        }

        public WorkspaceBuilder workspace(String name, String uri, boolean isDefault) {
            WorkspaceBuilder wsBuilder = new WorkspaceBuilder(name, uri, isDefault, this);
            workspaces.add(wsBuilder);
            return wsBuilder;
        }

        public ResourcesBuilder resources() {
            return new ResourcesBuilder(this);
        }

        public MockGeoServer geoServer() {
            return geoServer;
        }

        private void apply(GeoServer geoServer) {
            List<WorkspaceInfo> allWorkspaces = Lists.transform(workspaces,
                    new Function<WorkspaceBuilder, WorkspaceInfo>() {
                        @Nullable
                        @Override
                        public WorkspaceInfo apply(@Nullable WorkspaceBuilder builder) {
                            return builder.workspace;
                        }
                    });
            List<NamespaceInfo> allNamespaces = Lists.transform(workspaces,
                    new Function<WorkspaceBuilder, NamespaceInfo>() {
                        @Nullable
                        @Override
                        public NamespaceInfo apply(@Nullable WorkspaceBuilder builder) {
                            return builder.namespace;
                        }
                    });

            when(catalog.getWorkspaces()).thenReturn(allWorkspaces);
            when(catalog.list(WorkspaceInfo.class, Predicates.acceptAll()))
                .thenReturn(new CloseableIteratorAdapter<WorkspaceInfo>(allWorkspaces.iterator()));
            when(catalog.getNamespaces()).thenReturn(allNamespaces);
            when(catalog.list(NamespaceInfo.class, Predicates.acceptAll()))
                .thenReturn(new CloseableIteratorAdapter<NamespaceInfo>(allNamespaces.iterator()));

            List<LayerInfo> allLayers = new ArrayList<LayerInfo>();
            for (WorkspaceBuilder wsBuilder : workspaces) {
                List<LayerInfo> layers = Lists.transform(wsBuilder.layers, new Function<LayerBuilder, LayerInfo>() {
                    @Nullable
                    @Override
                    public LayerInfo apply(@Nullable LayerBuilder layerBuilder) {
                        return layerBuilder.layer;
                    }
                });
                allLayers.addAll(layers);

                when(catalog.list(LayerInfo.class, Predicates.equal("resource.namespace.prefix",
                    wsBuilder.workspace.getName()))).thenReturn(new CloseableIteratorAdapter<LayerInfo>(layers.iterator()));
            }

            when(catalog.getLayers()).thenReturn(allLayers);
            when(catalog.list(LayerInfo.class, Predicates.acceptAll())).thenReturn(
                new CloseableIteratorAdapter<LayerInfo>(allLayers.iterator()));
            when(geoServer.getCatalog()).thenReturn(catalog);
        }
    }

    public class WorkspaceBuilder extends Builder {

        CatalogBuilder catalogBuilder;
        WorkspaceInfo workspace;
        NamespaceInfo namespace;
        List<StoreBuilder> stores = new ArrayList<StoreBuilder>();
        List<LayerBuilder> layers = new ArrayList<LayerBuilder>();

        public WorkspaceBuilder(String name, String uri, boolean isDefault, CatalogBuilder catalogBuilder) {
            this.catalogBuilder = catalogBuilder;
            Catalog cat = catalogBuilder.catalog;

            workspace = mock(WorkspaceInfo.class);
            when(workspace.getName()).thenReturn(name);

            namespace = mock(NamespaceInfo.class);
            when(namespace.getName()).thenReturn(name);
            when(namespace.getURI()).thenReturn(uri);

            when(cat.getWorkspaceByName(name)).thenReturn(workspace);
            when(cat.getNamespaceByPrefix(name)).thenReturn(namespace);
            when(cat.getNamespaceByURI(uri)).thenReturn(namespace);

            if (isDefault) {
                when(cat.getDefaultWorkspace()).thenReturn(workspace);
                when(cat.getDefaultNamespace()).thenReturn(namespace);
            }
        }

        public LayerBuilder layer(String name) {
            LayerBuilder lBuilder = new LayerBuilder(name, this);
            layers.add(lBuilder);
            return lBuilder;
        }

        public CatalogBuilder catalog() {
            return catalogBuilder;
        }

        public MockGeoServer geoServer() {
            return catalogBuilder.geoServer();
        }

    }

    public class StoreBuilder {

    }

    public class LayerBuilder extends Builder {

        String name;
        LayerInfo layer;

        WorkspaceBuilder workspaceBuilder;
        ResourceBuilder resourceBuilder;

        public LayerBuilder(String name, WorkspaceBuilder workspaceBuilder) {
            this.name = name;
            this.workspaceBuilder = workspaceBuilder;

            String wsName = workspaceBuilder.workspace.getName();
            layer = mock(LayerInfo.class);
            when(layer.getName()).thenReturn(name);
            when(layer.prefixedName()).thenReturn(wsName+":"+name);

            Catalog catalog = workspaceBuilder.catalogBuilder.catalog;
            when(catalog.getLayerByName(name)).thenReturn(layer);
            when(catalog.getLayerByName(wsName+":"+name)).thenReturn(layer);
            when(catalog.getLayerByName(new NameImpl(wsName, name))).thenReturn(layer);
        }

        public StyleBuilder style() {
            return new StyleBuilder(this);
        }

        public FeatureTypeBuilder featureType() {
            return new FeatureTypeBuilder(this);
        }

        public MockGeoServer geoServer() {
            return workspaceBuilder.geoServer();
        }
    }

    public class ResourceBuilder<T extends ResourceInfo> extends Builder {

        protected T resource;
        protected LayerBuilder layerBuilder;

        protected ResourceBuilder(T resource, LayerBuilder layerBuilder) {
            this.resource = resource;
            this.layerBuilder = layerBuilder;

            LayerInfo layer = layerBuilder.layer;
            when(resource.getName()).thenReturn(layerBuilder.name);
            when(resource.getNativeName()).thenReturn(layerBuilder.name);
            when(layer.getResource()).thenReturn(resource);
        }

        public ResourceBuilder<T> proj(String srs, CoordinateReferenceSystem crs) {
            when(resource.getSRS()).thenReturn(srs);
            when(resource.getCRS()).thenReturn(crs);
            return this;
        }

        public ResourceBuilder<T> bbox(double x1, double y1, double x2, double y2, CoordinateReferenceSystem crs) {
            when(resource.getNativeBoundingBox()).thenReturn(new ReferencedEnvelope(x1,x2,y1,y2,crs));
            when(resource.getNativeCRS()).thenReturn(crs);
            return this;
        }

        public ResourceBuilder<T> latLonBbox(double x1, double y1, double x2, double y2) {
            when(resource.getLatLonBoundingBox()).thenReturn(new ReferencedEnvelope(x1,x2,y1,y2, DefaultGeographicCRS.WGS84));
            return this;
        }

        public WorkspaceBuilder workspace() {
            return layerBuilder.workspaceBuilder;
        }

        public MockGeoServer geoServer() {
            return workspace().catalog().geoServer();
        }
    }

    public class FeatureTypeBuilder extends ResourceBuilder<FeatureTypeInfo> {

        public FeatureTypeBuilder(LayerBuilder layerBuilder) {
            super(mock(FeatureTypeInfo.class), layerBuilder);
        }

        public FeatureTypeBuilder schema(String spec) {
            try {
                when(resource.getFeatureType()).thenReturn(DataUtilities.createType(layerBuilder.name, spec));
                return this;
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        public FeatureTypeBuilder defaults() {
            proj("EPSG:4326", DefaultGeographicCRS.WGS84)
              .bbox(-180, -90, 180, 90, DefaultGeographicCRS.WGS84)
              .latLonBbox(-180, -90, 180, 90);

            return schema("geom:Point,name:String");
        }
    }

    public class StyleBuilder extends Builder {

        StyleInfo style;
        LayerBuilder layerBuilder;
        StyleFactory styleFactory;

        public StyleBuilder(LayerBuilder layerBuilder) {
            this.layerBuilder = layerBuilder;
            this.styleFactory = CommonFactoryFinder.getStyleFactory();

            style = mock(StyleInfo.class);
            when(style.getWorkspace()).thenReturn(layerBuilder.workspaceBuilder.workspace);
            when(layerBuilder.layer.getDefaultStyle()).thenReturn(style);
        }

        public StyleBuilder ysld(String filename) {
            when(style.getFormat()).thenReturn(YsldHandler.FORMAT);
            when(style.getFilename()).thenReturn(filename);
            return this;
        }

        public StyleBuilder point() {
            Rule rule = styleFactory.createRule();
            rule.symbolizers().add(styleFactory.createPointSymbolizer());

            FeatureTypeStyle featureTypeStyle = styleFactory.createFeatureTypeStyle();
            featureTypeStyle.rules().add(rule);

            Style style = styleFactory.createStyle();
            style.featureTypeStyles().add(featureTypeStyle);

            try {
                when(this.style.getStyle()).thenReturn(style);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        public LayerBuilder layer() {
            return layerBuilder;
        }

        public MockGeoServer geoServer() {
            return layerBuilder.geoServer();
        }
    }
}