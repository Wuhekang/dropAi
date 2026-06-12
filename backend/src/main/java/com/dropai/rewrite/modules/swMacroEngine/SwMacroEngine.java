package com.dropai.rewrite.modules.swMacroEngine;

import com.dropai.rewrite.modules.drawingEngine.DrawingArtifact;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SwMacroEngine {
    public List<DrawingArtifact> generate(DesignProject project) {
        List<DrawingArtifact> files = new ArrayList<>();
        files.add(macro("sw_macro_shell.bas", "Shell", project.number("总长", 4200), project.number("总宽", 1600), project.number("总高", 1800)));
        files.add(macro("sw_macro_base.bas", "Base", project.number("总长", 4200), project.number("总宽", 1600), project.number("壳体板厚", 4) * 8));
        files.add(macro("sw_macro_inlet.bas", "Inlet", project.number("总宽", 1600) * .45, project.number("总高", 1800) * .4, project.number("壳体板厚", 4)));
        String steps = """
                SolidWorks 辅助建模步骤
                1. 按顺序运行 sw_macro_shell.bas、sw_macro_base.bas、sw_macro_inlet.bas。
                2. 宏将新建零件、绘制中心矩形草图并拉伸形成基础实体。
                3. 根据参数表补充进出口、支撑、检修口、连接孔与圆角。
                4. 分别保存零件后，按 assembly.dxf 的部件编号建立装配体。
                5. 完成干涉检查、材料设置和关键尺寸复核后再出工程图。
                """;
        files.add(new DrawingArtifact("sw_modeling_steps.txt", steps.getBytes(StandardCharsets.UTF_8), "text/plain"));
        return files;
    }

    private DrawingArtifact macro(String fileName, String partName, double length, double width, double depth) {
        String macro = """
                Option Explicit
                Dim swApp As Object
                Dim swModel As Object
                Dim swSketchMgr As Object
                Dim swFeatureMgr As Object

                Sub main()
                  Set swApp = Application.SldWorks
                  Set swModel = swApp.NewDocument("", 0, 0, 0)
                  If swModel Is Nothing Then
                    MsgBox "请在 SolidWorks 中配置默认零件模板后重新运行。"
                    Exit Sub
                  End If
                  swModel.Extension.SelectByID2 "前视基准面", "PLANE", 0, 0, 0, False, 0, Nothing, 0
                  Set swSketchMgr = swModel.SketchManager
                  swSketchMgr.InsertSketch True
                  swSketchMgr.CreateCenterRectangle 0, 0, 0, %.6f, %.6f, 0
                  swSketchMgr.InsertSketch True
                  Set swFeatureMgr = swModel.FeatureManager
                  swFeatureMgr.FeatureExtrusion2 True, False, False, 0, 0, %.6f, 0, False, False, False, False, 0, 0, False, False, False, False, True, True, True, 0, 0, False
                  swModel.ViewZoomtofit2
                  MsgBox "%s 基础实体已生成。请按建模步骤补充孔、支撑与检修结构并另存。"
                End Sub
                """.formatted(length / 2000d, width / 2000d, depth / 1000d, partName);
        return new DrawingArtifact(fileName, macro.getBytes(StandardCharsets.UTF_8), "text/plain");
    }
}
