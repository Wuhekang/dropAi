package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanHasher;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.ConnectorElementRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.ElementRenderContext;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.ElementRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.ImageElementRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.ShapeElementRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.TableElementRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.element.TextElementRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Apache POI implementation that executes the frozen element stream without interpretation. */
public final class PurePptxRendererImpl implements PurePptxRenderer {
    private final PptxDocumentFactory documentFactory;
    private final Map<String, ElementRenderer> elementRenderers;
    private final RenderPlanHasher planHasher = new RenderPlanHasher();

    public PurePptxRendererImpl() {
        this(new PptxDocumentFactory());
    }

    public PurePptxRendererImpl(PptxDocumentFactory documentFactory) {
        this(documentFactory, List.of(
                new TextElementRenderer(),
                new ImageElementRenderer(),
                new ShapeElementRenderer(),
                new TableElementRenderer(),
                new ConnectorElementRenderer()
        ));
    }

    public PurePptxRendererImpl(
            PptxDocumentFactory documentFactory,
            List<ElementRenderer> elementRenderers
    ) {
        this.documentFactory = Objects.requireNonNull(documentFactory, "documentFactory");
        Objects.requireNonNull(elementRenderers, "elementRenderers");
        LinkedHashMap<String, ElementRenderer> index = new LinkedHashMap<>();
        for (ElementRenderer renderer : elementRenderers) {
            Objects.requireNonNull(renderer, "elementRenderer");
            if (index.putIfAbsent(renderer.elementType(), renderer) != null) {
                throw new IllegalArgumentException("Duplicate element renderer: " + renderer.elementType());
            }
        }
        this.elementRenderers = Map.copyOf(index);
    }

    @Override
    public RenderedPptx render(
            FrozenSlideRenderPlan plan,
            AssetBinaryResolver assetResolver,
            OutputStream output
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(assetResolver, "assetResolver");
        Objects.requireNonNull(output, "output");
        String hashBefore = planHasher.hash(plan);
        ObjectNode documentNode = plan.document();
        ArrayNode slides = requiredArray(documentNode, "slides");
        ObjectNode slideSize = requiredObject(documentNode, "slideSize");
        Map<String, ObjectNode> assets = assetIndex(requiredArray(documentNode, "assets"));
        CountingOutputStream counting = new CountingOutputStream(output);

        try (XMLSlideShow document = documentFactory.create(
                requiredLong(slideSize, "widthEmu"),
                requiredLong(slideSize, "heightEmu"))) {
            while (!document.getSlides().isEmpty()) {
                document.removeSlide(0);
            }
            for (JsonNode node : slides) {
                if (!node.isObject()) {
                    throw new IllegalArgumentException("Each slide must be an object");
                }
                ObjectNode slidePlan = (ObjectNode) node;
                String slideId = requiredText(slidePlan, "slideId");
                XSLFSlide slide = documentFactory.createBlankSlide(document);
                renderElements(document, slide, slideId,
                        requiredArray(slidePlan, "elements"), assets, assetResolver);
            }
            if (document.getSlides().size() != slides.size()) {
                throw new RendererExecutionException(
                        PptQualityCode.SLIDE_COUNT_MISMATCH,
                        "Renderer did not create exactly one slide per plan slide",
                        null,
                        null);
            }
            document.write(counting);
        } catch (RendererExecutionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new RendererExecutionException(
                    PptQualityCode.OOXML_PACKAGE_INVALID,
                    "Failed to write the frozen RenderPlan as PPTX",
                    null,
                    null,
                    exception);
        }

        String hashAfter = planHasher.hash(plan);
        if (!hashBefore.equals(hashAfter)) {
            throw new RendererExecutionException(
                    PptQualityCode.RENDER_PLAN_HASH_MISMATCH,
                    "RenderPlan changed during rendering",
                    null,
                    null);
        }
        return new RenderedPptx(RendererVersion.VERSION, hashBefore, slides.size(), counting.count());
    }

    private void renderElements(
            XMLSlideShow document,
            XSLFSlide slide,
            String slideId,
            ArrayNode elements,
            Map<String, ObjectNode> assets,
            AssetBinaryResolver assetResolver
    ) {
        ElementRenderContext context = new ElementRenderContext(
                document, slide, slideId, assets, assetResolver);
        int previousZIndex = -1;
        for (JsonNode node : elements) {
            if (!node.isObject()) {
                throw new RendererExecutionException(
                        PptQualityCode.SCHEMA_INVALID,
                        "Slide element must be an object",
                        slideId,
                        null);
            }
            ObjectNode element = (ObjectNode) node;
            String elementId = requiredText(element, "elementId");
            int zIndex = requiredInt(element, "zIndex");
            if (zIndex < previousZIndex) {
                throw new RendererExecutionException(
                        PptQualityCode.SCHEMA_INVALID,
                        "Element zIndex order is not monotonic; Renderer will not reorder it",
                        slideId,
                        elementId);
            }
            previousZIndex = zIndex;
            String elementType = requiredText(element, "elementType");
            ElementRenderer renderer = elementRenderers.get(elementType);
            if (renderer == null) {
                throw new RendererExecutionException(
                        PptQualityCode.UNKNOWN_ELEMENT_TYPE,
                        "No executor registered for elementType " + elementType,
                        slideId,
                        elementId);
            }
            renderer.render(element.deepCopy(), context);
        }
    }

    private static Map<String, ObjectNode> assetIndex(ArrayNode assets) {
        LinkedHashMap<String, ObjectNode> index = new LinkedHashMap<>();
        for (JsonNode node : assets) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("Each asset must be an object");
            }
            ObjectNode asset = (ObjectNode) node;
            String assetId = requiredText(asset, "assetId");
            if (index.putIfAbsent(assetId, asset.deepCopy()) != null) {
                throw new IllegalArgumentException("Duplicate assetId: " + assetId);
            }
        }
        return Map.copyOf(index);
    }

    private static ObjectNode requiredObject(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (ObjectNode) node;
    }

    private static ArrayNode requiredArray(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return (ArrayNode) node;
    }

    private static String requiredText(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return node.textValue();
    }

    private static long requiredLong(ObjectNode owner, String field) {
        JsonNode node = owner.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new IllegalArgumentException(field + " must be a long integer");
        }
        return node.longValue();
    }

    private static int requiredInt(ObjectNode owner, String field) {
        long value = requiredLong(owner, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is outside integer range");
        }
        return (int) value;
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            count += length;
        }

        private long count() {
            return count;
        }
    }
}
