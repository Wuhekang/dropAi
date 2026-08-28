package com.dropai.rewrite.service.ppt.rendering.contract.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualitySeverity;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.QualityStage;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PptQualityCode {
    SCHEMA_INVALID("PPT-RC-001", QualitySeverity.ERROR),
    UNSUPPORTED_SCHEMA_VERSION("PPT-RC-002", QualitySeverity.ERROR),
    UNKNOWN_ENUM_VALUE("PPT-RC-003", QualitySeverity.ERROR),
    INVALID_HASH("PPT-RC-004", QualitySeverity.ERROR),
    DUPLICATE_ID("PPT-RC-005", QualitySeverity.ERROR),
    INVALID_REFERENCE("PPT-RC-006", QualitySeverity.ERROR),

    SLIDE_COUNT_MISMATCH("PPT-QA-101", QualitySeverity.ERROR),
    SLIDE_ORDER_MISMATCH("PPT-QA-102", QualitySeverity.ERROR),
    SOURCE_PAGE_MAPPING_INVALID("PPT-QA-103", QualitySeverity.ERROR),
    UNKNOWN_LAYOUT("PPT-QA-104", QualitySeverity.ERROR),
    UNKNOWN_ELEMENT_TYPE("PPT-QA-105", QualitySeverity.ERROR),
    UNRENDERABLE_PAGE("PPT-QA-106", QualitySeverity.ERROR),

    TEXT_OVERFLOW("PPT-QA-201", QualitySeverity.ERROR),
    TEXT_TRUNCATED("PPT-QA-202", QualitySeverity.ERROR),
    FONT_BELOW_MINIMUM("PPT-QA-203", QualitySeverity.ERROR),
    FORBIDDEN_TEXT_FOUND("PPT-QA-204", QualitySeverity.ERROR),

    ELEMENT_OUT_OF_BOUNDS("PPT-QA-221", QualitySeverity.ERROR),
    ILLEGAL_OVERLAP("PPT-QA-222", QualitySeverity.ERROR),
    SAFE_AREA_VIOLATION("PPT-QA-223", QualitySeverity.ERROR),

    MANDATORY_ASSET_MISSING("PPT-QA-241", QualitySeverity.ERROR),
    ASSET_HASH_MISMATCH("PPT-QA-242", QualitySeverity.ERROR),
    IMAGE_ASPECT_DISTORTION("PPT-QA-243", QualitySeverity.ERROR),
    CROP_NOT_ALLOWED("PPT-QA-244", QualitySeverity.ERROR),
    IMAGE_RESOLUTION_LOW("PPT-QA-245", QualitySeverity.WARNING),

    TABLE_CAPACITY_EXCEEDED("PPT-QA-261", QualitySeverity.ERROR),

    FONT_UNAVAILABLE("PPT-QA-301", QualitySeverity.ERROR),
    FONT_SUBSTITUTED("PPT-QA-302", QualitySeverity.ERROR),

    OOXML_PACKAGE_INVALID("PPT-QA-401", QualitySeverity.ERROR),
    SLIDE_RELATIONSHIP_BROKEN("PPT-QA-402", QualitySeverity.ERROR),
    MEDIA_RELATIONSHIP_BROKEN("PPT-QA-403", QualitySeverity.ERROR),
    DEFAULT_MASTER_TEXT_FOUND("PPT-QA-404", QualitySeverity.ERROR),
    INTERNAL_FIELD_LEAKED("PPT-QA-405", QualitySeverity.ERROR),
    HIDDEN_CONTENT_FOUND("PPT-QA-406", QualitySeverity.ERROR),

    PREVIEW_RENDER_FAILED("PPT-QA-501", QualitySeverity.ERROR),
    PREVIEW_SLIDE_COUNT_MISMATCH("PPT-QA-502", QualitySeverity.ERROR),

    RENDER_PLAN_HASH_MISMATCH("PPT-QA-601", QualitySeverity.ERROR),
    NON_DETERMINISTIC_RENDER_PLAN("PPT-QA-602", QualitySeverity.ERROR),

    EDITABILITY_VIOLATION("PPT-QA-701", QualitySeverity.ERROR);

    private static final Map<String, PptQualityCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PptQualityCode::code, Function.identity()));

    private final String code;
    private final QualitySeverity defaultSeverity;
    private final boolean deprecated;

    PptQualityCode(String code, QualitySeverity defaultSeverity) {
        this(code, defaultSeverity, false);
    }

    PptQualityCode(String code, QualitySeverity defaultSeverity, boolean deprecated) {
        this.code = code;
        this.defaultSeverity = defaultSeverity;
        this.deprecated = deprecated;
    }

    public String code() {
        return code;
    }

    public QualitySeverity defaultSeverity() {
        return defaultSeverity;
    }

    public QualityStage defaultStage() {
        return switch (this) {
            case SCHEMA_INVALID, UNSUPPORTED_SCHEMA_VERSION, UNKNOWN_ENUM_VALUE, INVALID_HASH,
                    DUPLICATE_ID, INVALID_REFERENCE -> QualityStage.RENDER_PLAN;
            case SLIDE_COUNT_MISMATCH, SLIDE_ORDER_MISMATCH, SOURCE_PAGE_MAPPING_INVALID,
                    UNRENDERABLE_PAGE -> QualityStage.PAGE_RENDERABILITY;
            case UNKNOWN_LAYOUT -> QualityStage.LAYOUT_SELECTION;
            case UNKNOWN_ELEMENT_TYPE, TEXT_OVERFLOW, TEXT_TRUNCATED, FONT_BELOW_MINIMUM,
                    ELEMENT_OUT_OF_BOUNDS, ILLEGAL_OVERLAP, SAFE_AREA_VIOLATION,
                    MANDATORY_ASSET_MISSING, ASSET_HASH_MISMATCH, IMAGE_ASPECT_DISTORTION,
                    CROP_NOT_ALLOWED, IMAGE_RESOLUTION_LOW, TABLE_CAPACITY_EXCEEDED -> QualityStage.RENDER_PLAN;
            case FONT_UNAVAILABLE -> QualityStage.THEME_RESOLUTION;
            case FORBIDDEN_TEXT_FOUND -> QualityStage.QUALITY_GATE;
            case FONT_SUBSTITUTED -> QualityStage.PREVIEW;
            case OOXML_PACKAGE_INVALID, SLIDE_RELATIONSHIP_BROKEN, MEDIA_RELATIONSHIP_BROKEN,
                    DEFAULT_MASTER_TEXT_FOUND, INTERNAL_FIELD_LEAKED,
                    HIDDEN_CONTENT_FOUND -> QualityStage.PPTX_PACKAGE;
            case PREVIEW_RENDER_FAILED, PREVIEW_SLIDE_COUNT_MISMATCH -> QualityStage.PREVIEW;
            case RENDER_PLAN_HASH_MISMATCH, NON_DETERMINISTIC_RENDER_PLAN -> QualityStage.DETERMINISM;
            case EDITABILITY_VIOLATION -> QualityStage.EDITABILITY;
        };
    }

    public boolean deprecated() {
        return deprecated;
    }

    public static Set<String> codes() {
        return BY_CODE.keySet();
    }

    public static PptQualityCode fromCode(String code) {
        PptQualityCode value = BY_CODE.get(code);
        if (value == null) {
            throw new IllegalArgumentException("Unknown PPT quality code: " + code);
        }
        return value;
    }
}
