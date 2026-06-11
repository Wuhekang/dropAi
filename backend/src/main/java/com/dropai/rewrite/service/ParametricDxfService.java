package com.dropai.rewrite.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ParametricDxfService {
    public byte[] generate(double length, double width, double height, double wheelbase, double wheelDiameter) {
        validate(length, width, height, wheelbase, wheelDiameter);
        double wb = Math.min(wheelbase, length * .82);
        double radius = wheelDiameter / 2;
        double wheelY = radius + 40;
        double left = (length - wb) / 2;
        double right = left + wb;
        List<Entity> entities = new ArrayList<>();

        line(entities, 0, 0, length, 0);
        line(entities, length, 0, length, height * .55);
        line(entities, length, height * .55, length * .82, height * .82);
        line(entities, length * .82, height * .82, length * .28, height * .82);
        line(entities, length * .28, height * .82, length * .12, height * .58);
        line(entities, length * .12, height * .58, 0, height * .55);
        line(entities, 0, height * .55, 0, 0);
        line(entities, left - radius, wheelY - radius - 25, right + radius, wheelY - radius - 25);
        line(entities, right + radius, wheelY - radius - 25, right + radius, wheelY + radius + 25);
        line(entities, right + radius, wheelY + radius + 25, left - radius, wheelY + radius + 25);
        line(entities, left - radius, wheelY + radius + 25, left - radius, wheelY - radius - 25);
        circle(entities, left, wheelY, radius);
        circle(entities, right, wheelY, radius);
        for (int i = 1; i <= 5; i++) circle(entities, left + wb * i / 6, wheelY - radius * .18, radius * .48);
        line(entities, length * .42, height * .82, length * .42, height);
        line(entities, length * .42, height, length * .53, height);
        line(entities, length * .53, height, length * .53, height * .82);

        StringBuilder dxf = new StringBuilder();
        pair(dxf, 0, "SECTION"); pair(dxf, 2, "HEADER");
        pair(dxf, 9, "$ACADVER"); pair(dxf, 1, "AC1009");
        pair(dxf, 9, "$INSUNITS"); pair(dxf, 70, "4");
        pair(dxf, 9, "$EXTMIN"); point(dxf, -radius - 50, -radius - 50);
        pair(dxf, 9, "$EXTMAX"); point(dxf, length + radius + 50, height + radius + 50);
        pair(dxf, 0, "ENDSEC");
        pair(dxf, 0, "SECTION"); pair(dxf, 2, "TABLES");
        pair(dxf, 0, "TABLE"); pair(dxf, 2, "LTYPE"); pair(dxf, 70, "1");
        pair(dxf, 0, "LTYPE"); pair(dxf, 2, "CONTINUOUS"); pair(dxf, 70, "0");
        pair(dxf, 3, "Solid line"); pair(dxf, 72, "65"); pair(dxf, 73, "0"); pair(dxf, 40, "0.0");
        pair(dxf, 0, "ENDTAB");
        pair(dxf, 0, "TABLE"); pair(dxf, 2, "LAYER"); pair(dxf, 70, "1");
        pair(dxf, 0, "LAYER"); pair(dxf, 2, "DESIGN_OUTLINE"); pair(dxf, 70, "0"); pair(dxf, 62, "7"); pair(dxf, 6, "CONTINUOUS");
        pair(dxf, 0, "ENDTAB"); pair(dxf, 0, "ENDSEC");
        pair(dxf, 0, "SECTION"); pair(dxf, 2, "BLOCKS"); pair(dxf, 0, "ENDSEC");
        pair(dxf, 0, "SECTION"); pair(dxf, 2, "ENTITIES");
        entities.forEach(entity -> entity.write(dxf));
        pair(dxf, 0, "ENDSEC"); pair(dxf, 0, "EOF");
        return dxf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private void validate(double length, double width, double height, double wheelbase, double wheelDiameter) {
        if (length <= 0 || width <= 0 || height <= 0 || wheelbase <= 0 || wheelDiameter <= 0) {
            throw new IllegalArgumentException("CAD 尺寸参数必须大于 0");
        }
    }

    private void line(List<Entity> entities, double x1, double y1, double x2, double y2) {
        entities.add(dxf -> {
            pair(dxf, 0, "LINE"); pair(dxf, 8, "DESIGN_OUTLINE");
            point(dxf, 10, x1, y1); point(dxf, 11, x2, y2);
        });
    }

    private void circle(List<Entity> entities, double x, double y, double radius) {
        entities.add(dxf -> {
            pair(dxf, 0, "CIRCLE"); pair(dxf, 8, "DESIGN_OUTLINE");
            point(dxf, x, y); pair(dxf, 40, number(radius));
        });
    }

    private static void point(StringBuilder dxf, double x, double y) { point(dxf, 10, x, y); }
    private static void point(StringBuilder dxf, int xCode, double x, double y) {
        pair(dxf, xCode, number(x)); pair(dxf, xCode + 10, number(y)); pair(dxf, xCode + 20, "0.0");
    }
    private static void pair(StringBuilder dxf, int code, String value) { dxf.append(code).append('\n').append(value).append('\n'); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private interface Entity { void write(StringBuilder dxf); }
}
