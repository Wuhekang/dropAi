# @ERDiagram 规则（v1.6）

统一生成器支持 `@FunctionModule、@Flowchart、@ERDiagram、@ArchitectureDiagram、@UseCaseDiagram、@BlockDiagram、@SequenceDiagram`。

生成 ER DSL 时只能输出以下标准格式，禁止输出旧版“实体名：属性”简写：

```text
@ERDiagram
标题：系统ER图

[实体]
实体：用户|用户ID*，用户名
实体：订单|订单ID*，金额

[关系]
关系：用户|订单|创建|1|n
```

- 实体格式固定为 `实体：实体中文名称|属性1*，属性2`。
- `*` 表示主键，正式输出统一把星号写在主键属性末尾。
- 关系格式固定为 `关系：实体A|实体B|关系名称|实体A基数|实体B基数`。
- 基数只使用 `1`、`m`、`n`。
- 每个实体建议不超过 6 个属性。
- 不输出旧版 `角色：角色ID，角色名称` 或 `角色-用户：1*n，关系名`。
