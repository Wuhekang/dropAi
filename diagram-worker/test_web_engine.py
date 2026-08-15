import json, sys, unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from diagram_plugins.legacy import register_plugins
from web_engine import execute

ROOT=Path(__file__).parent
TEMPLATES={"function_module":"三级功能模块图_输入模板.txt","flowchart":"标准程序流程图_输入模板.txt","er_diagram":"Chen_ER图_输入模板.txt","architecture":"系统架构图_输入模板.txt","use_case":"UML用例图_输入模板.txt","block_diagram":"系统框图_输入模板.txt","sequence":"UML时序图_输入模板.txt"}
HEADERS={"function_module":"@FunctionModule","flowchart":"@Flowchart","er_diagram":"@ERDiagram","architecture":"@ArchitectureDiagram","use_case":"@UseCaseDiagram","block_diagram":"@BlockDiagram","sequence":"@SequenceDiagram"}
class WebEngineTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls): register_plugins()
 def test_seven_templates_render_svg(self):
  for kind,name in TEMPLATES.items():
   with self.subTest(kind=kind):
    dsl=HEADERS[kind]+"\n"+(ROOT/name).read_text(encoding="utf-8-sig")
    result=execute({"command":"render","dsl":dsl})
    self.assertTrue(result.get("valid"),result.get("issues"));self.assertIn("<svg",result["svg"]);self.assertEqual(kind,result["diagramType"])
 def test_missing_header(self): self.assertEqual("HEADER_MISSING",execute({"dsl":"标题：测试"})["issues"][0]["code"])
 def test_unreachable_code_normalized(self):
  dsl="@Flowchart\n标题：测试\n[节点]\nN1|start|开始\nN2|end|结束\nN3|process|孤立\n[连接]\nN1->N2"
  self.assertIn("FLOW_UNREACHABLE_NODE",[x["code"] for x in execute({"dsl":dsl})["issues"]])
if __name__=="__main__": unittest.main()
