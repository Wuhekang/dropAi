package com.dropai.rewrite.service.impl;

/** Rival-style light spoken phrasing: break template families, then densely rewrite each clause. */
final class OrdinaryPlatformMethods {
 private OrdinaryPlatformMethods() { }
 static String instruction(String type) {
  String code=type!=null && type.contains("@") ? type.substring(type.indexOf('@')+1) : "GENERAL";
  String method=switch(NativeHumanizeProfiles.normalize(code)) {
   case "CNKI" -> "知网：所有句组密换平常的内容词和句组，保留必要学术指标，不把背景再解释一遍。";
   case "WEIPU" -> "维普：用配合、对接、等候、监控等短同义词连续换写，少量的、了、以及作连接，原有研究—数据—限制—建议顺序可留。";
   case "GEZIDA" -> "格子达：短动词与普通状态词替换抽象说法，例如共享不足可说共享不够，衔接不充分可说对接不到位；不补意义句。";
   case "PAPERPASS" -> "PaperPass：维普式短词组密换覆盖摘要和正文各句，后半段不能照搬。优先冷链配合、标准对接、监控不足、设施安排等问题中的动作与判断词，不靠加长定义提高密度。";
   case "PAPERYY" -> "PaperYY：逐句换内容词，用短动宾表达接住原意，去掉同义复述，不扩写每个对象的背景。";
   case "ZHUQUE" -> "朱雀：性质、动作、限制用短而日常的近义词替换，保持长短句差异，不加口语噪声。";
   case "WANFANG" -> "万方：用普通短语交代原有对象与动作，不添复杂词，不把每个数据句再讲一遍。";
   default -> "开场/收尾改断模板串法，中间密换内容词，用少量并且/从而/使得自然衔接。";
  };
  return "\n【平台轻口语句组方式】"+method+" 原文专名、数字、单位与指标含义保留；持续提高可说不断加大/不断提高并按原义接结果，协同可说加强配合，等待可说等候，衔接可说对接；以期可改以便提供借鉴。经研究发现、因此本文等须按实际研究内容使用，无调查不能说经过调查发现。不是逐项硬词典，不套用示例里的领域事实。";
 }
 static String instruction(String type,String source) {
  String common=instruction(type);
  if(!OrdinaryRewriteAmplitude.applies(type)||source==null) return common;
  String focus="";
  if(source.matches("(?s).*(冷链|温度|温湿度|操作指引|查验后|成熟度).*"))
   focus=" 本段定义/标准/冷链动作逐句密换，尤其处理后半段的设备、条件、异常处置、记录和依据等表达；只换开头不算完成，不能缩短成原句。温度异常仍是异常，建议仍是建议，不改指标或新增步骤。";
  else if(source.matches("(?s).*(监测|监控|基础设施|查验车位|应急).*"))
   focus=" 本段监测/设施问题中的动作、限制和措施都要短词组换写，不让术语周围整句照搬，也不把通行能力换通行效率。";
  return common+"\n【所有摘要与正文必须改写】不能只换段首；平常说法与密集换词同时成立，裸起句研究发现、据此/以期的旧串法不能留下；短了但仍贴骨架不算完成。不用一直都在……当中、相关工作、有用的参考这类重水包装，不用新书面升档词；改断原开场和收尾，用一点并且/从而/使得接起内容，不只贴同义词。"+focus;
 }
}
