import json, sys, unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from diagram_plugins.legacy import register_plugins
from web_engine import execute
from diagram_core.typography import text_units, wrap_text

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
    self.assertTrue(result.get("valid"),result.get("issues"));self.assertIn("<svg",result["svg"]);self.assertEqual(kind,result["diagramTypeKey"]);self.assertTrue(result["ok"])
 def test_missing_header(self): self.assertEqual("HEADER_MISSING",execute({"dsl":"标题：测试"})["issues"][0]["code"])
 def test_unreachable_code_normalized(self):
  dsl="@Flowchart\n标题：测试\n[节点]\nN1|start|开始\nN2|end|结束\nN3|process|孤立\n[连接]\nN1->N2"
  self.assertIn("FLOW_UNREACHABLE_NODE",[x["code"] for x in execute({"dsl":dsl})["issues"]])
 def test_function_module_acceptance_and_wrapped_function(self):
  dsl="\ufeff@FunctionModule\r\n系统:个人健康管理系统\r\n\r\n模块：管理端\r\n功能：仪表盘统计，用户管理、健康知识管理；公告管理,健康数据查看;智能服务配置\r\n\r\n模块：用户端\r\n功能：首页健康概览，健康数据管理，运动记录，饮食记录，健康目\r\n标管理，智能健康评估，智能健康对话，个人中心"
  result=execute({"command":"render","dsl":dsl})
  self.assertTrue(result["valid"],result["issues"]);self.assertEqual("function_module",result["diagramTypeKey"])
  self.assertEqual(2,len(result["structure"]["modules"]));self.assertEqual(14,sum(len(x["functions"]) for x in result["structure"]["modules"]))
  self.assertEqual(17,result["svg"].count('class="td-node-shape'))
  self.assertGreaterEqual(result["svg"].count('stroke="#111827"'),17)
 def test_comments_before_header_and_canonical_only(self):
  self.assertEqual("function_module",execute({"dsl":"# comment\n// comment\n@FunctionModule\n系统：系统\n模块：模块\n功能：功能"})["diagramType"])
  self.assertEqual("HEADER_UNKNOWN",execute({"dsl":"@Function\n系统：系统"})["issues"][0]["code"])
 def test_flowchart_nodes_have_visible_explicit_strokes_and_png_export(self):
  dsl="@Flowchart\n标题：边框测试\n[节点]\nN1|start|开始\nN2|process|处理\nN3|decision|判断\nN4|end|结束\n[连接]\nN1->N2\nN2->N3\nN3->N4|是\nN3->N2|否"
  result=execute({"command":"render","dsl":dsl})
  self.assertTrue(result["valid"],result["issues"])
  self.assertTrue(result["exports"]["png"])
  self.assertIn('fill="white" stroke="#1f2937"',result["svg"])
  self.assertGreaterEqual(result["svg"].count('stroke="#1f2937"'),4)
 def test_shared_wrap_text_keeps_all_characters_and_explicit_lines(self):
  source="中文English混合换行测试\n第二行不会丢失"
  lines=wrap_text(source,8)
  self.assertTrue(all(text_units(line)<=8 for line in lines))
  self.assertEqual(source.replace("\n",""),"".join(lines))
 def test_er_authoritative_template_counts_and_typography(self):
  dsl="@ERDiagram\n"+(ROOT/TEMPLATES["er_diagram"]).read_text(encoding="utf-8-sig")
  result=execute({"command":"render","dsl":dsl})
  self.assertTrue(result["valid"],result["issues"])
  self.assertEqual(6,len(result["structure"]["entities"]))
  self.assertEqual(5,len(result["structure"]["relationships"]))
  self.assertEqual(11,result["nodeCount"])
  self.assertEqual(27,sum(len(x["attributes"]) for x in result["structure"]["entities"]))
  self.assertEqual(6,sum(1 for x in result["structure"]["entities"] for a in x["attributes"] if a["is_primary_key"]))
  self.assertIn("Microsoft YaHei, PingFang SC, SimSun, Noto Sans CJK SC",result["svg"])
  self.assertIn("viewBox=",result["svg"])
 def test_er_explicit_entity_syntax_from_website_case(self):
  dsl="""@ERDiagram
标题：个人健康管理系统ER图

实体：角色|角色ID，角色名称，角色描述
实体：用户|用户ID，用户名，密码，手机号，邮箱，状态
实体：用户身体信息|身体信息ID，身高体重，年龄，性别，心率，血压
实体：智能助手|智能助手ID，对话内容，数据同步时间
实体：运动知识信息|运动知识ID，运动类型，适宜时间，适宜心率，适宜频率
实体：运动详情信息|运动详情ID，运动类型，注意事项，禁忌疾病，运动方法

---

角色-用户：1*n，分配权限
用户-用户身体信息：1*n，上传
用户-智能助手：1*1，对话
用户-运动知识信息：1*n，查看
运动知识信息-运动详情信息：1*n，扩展"""
  result=execute({"command":"render","dsl":dsl})
  self.assertTrue(result["valid"],result["issues"])
  self.assertEqual(6,len(result["structure"]["entities"]))
  self.assertEqual(5,len(result["structure"]["relationships"]))
  self.assertEqual(11,result["nodeCount"])
  self.assertIn("个人健康管理系统ER图",result["svg"])
 def test_er_legacy_pk_warning_and_missing_relation_entity(self):
  legacy="@ERDiagram\n标题：兼容\n[实体]\n角色：角色ID，角色名称\n用户：姓名，状态\n[关系]\n角色-用户：1×n，关联"
  result=execute({"command":"render","dsl":legacy})
  self.assertTrue(result["valid"],result["issues"])
  self.assertTrue(result["structure"]["entities"][0]["attributes"][0]["is_primary_key"])
  self.assertIn("ER_PRIMARY_KEY_NOT_FOUND",[x["code"] for x in result["issues"]])
  missing=execute({"command":"validate","dsl":"@ERDiagram\n标题：错误\n[实体]\n实体：角色|角色ID*\n实体：用户|用户ID*\n[关系]\n关系：角色|订单|创建|1|n"})
  self.assertIn("ER_RELATION_ENTITY_NOT_FOUND",[x["code"] for x in missing["issues"]])
 def test_er_too_many_attributes_is_warning_only(self):
  dsl="@ERDiagram\n标题：属性警告\n[实体]\n实体：角色|角色ID*，一，二，三，四，五，六\n实体：用户|用户ID*\n[关系]\n关系：角色|用户|关联|1|n"
  result=execute({"command":"render","dsl":dsl})
  self.assertTrue(result["valid"],result["issues"])
  self.assertIn("ER_ATTRIBUTE_LIMIT",[x["code"] for x in result["issues"]])
 def test_seven_templates_have_readable_node_fonts(self):
  import re
  for kind,name in TEMPLATES.items():
   with self.subTest(kind=kind):
    result=execute({"command":"render","dsl":HEADERS[kind]+"\n"+(ROOT/name).read_text(encoding="utf-8-sig")})
    sizes=[int(x) for x in re.findall(r'font-size="(\d+)"',result["svg"])]
    self.assertTrue(sizes)
    self.assertGreaterEqual(min(sizes),17)
if __name__=="__main__": unittest.main()
