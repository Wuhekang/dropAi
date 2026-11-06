# Dokiai Spring Boot + Vue 黄金模板设计

## 1. 模板目标

黄金模板固定使用 Java 17、Spring Boot 3.3.x、MyBatis Plus、Spring Security、JWT、MySQL 8，以及 Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router。模板面向毕业设计后台管理系统，首要保证可编译、可启动、CRUD 契约一致、ER 关系一致和权限有效。

模板只消费 `GenerationModel`，不读取 AI 原始文本，也不重复推导关系和命名。

## 2. 固定部分与动态部分

固定部分直接复制：

```text
backend/
├── mvnw, mvnw.cmd, .mvn/, pom.xml
└── src/main/java/__BASE_PACKAGE__/
    ├── common/api/{Result,PageResult}.java
    ├── common/exception/{BusinessException,GlobalExceptionHandler}.java
    ├── config/{MybatisPlusConfig,CorsConfig,JacksonConfig}.java
    └── security/{SecurityConfig,JwtTokenProvider,JwtAuthenticationFilter,
                  UserDetailsServiceImpl,SecurityUser}.java

frontend/
├── package.json, package-lock.json, vite.config.ts, tsconfig*.json
└── src/
    ├── main.ts, App.vue
    ├── components/{AppTable,AppPagination,FileUploader,ImageUploader}.vue
    ├── stores/user.ts
    ├── utils/{request,auth}.ts
    ├── layout/AdminLayout.vue
    └── views/system/{Login,Dashboard,NotFound}.vue
```

动态部分渲染：

```text
backend: entity、DTO、QueryDTO、VO、Mapper、Service、ServiceImpl、Controller
frontend: api、types、list、form、detail、route entry、menu entry、permissions
sql: business tables、indexes、foreign keys、join tables、RBAC seeds
root: README.md、manifest.json
```

固定文件只允许一次全局 token substitution（basePackage、artifactId、databaseName 等），业务实体内容不得进入固定模板。

允许人工扩展的源码必须包含稳定的保护区：

```java
// ===== USER CODE START: imports =====
// ===== USER CODE END: imports =====
// ===== USER CODE START: methods =====
// ===== USER CODE END: methods =====
```

`ProtectedRegionMerger` 在重新生成时按区域 id 提取并回填旧内容。标记缺失、重复、交叉或损坏时停止覆盖并报告冲突。保护区不能修改 Controller path、DTO 字段、权限注解等生成契约。

## 3. 模板仓库目录

```text
templates/springboot-vue-template/1.0.0/
├── template.yaml
├── static/{backend,frontend}/...
├── dynamic/
│   ├── backend/{entity,dto,query-dto,vo,mapper,service,service-impl,controller}.java.ftl
│   ├── frontend/{api,types,index,form,detail,route-entry,menu-entry}.*.ftl
│   └── sql/{table,index,foreign-key,join-table,rbac-seed}.sql.ftl
└── aggregates/{routes,menus,permissions,database}.ftl
```

`template.yaml` 声明模板版本、Blueprint schema 兼容范围、最低引擎版本、文件 ownership、构建命令和健康检查。发布时计算整包 SHA-256，运行期只读加载。

## 4. 类型映射

| Blueprint | Java | MySQL | TypeScript | 表单控件 |
|---|---|---|---|---|
| STRING | String | varchar(255) | string | el-input |
| TEXT | String | text | string | textarea |
| INTEGER | Integer | int | number | el-input-number |
| DECIMAL | BigDecimal | decimal(12,2) | number | el-input-number |
| BOOLEAN | Boolean | tinyint(1) | boolean | el-switch |
| DATE | LocalDate | date | string | date picker |
| DATETIME | LocalDateTime | datetime | string | datetime picker |
| ENUM | String | varchar(32) | string/union | el-select |
| IMAGE | String | varchar(500) | string | ImageUploader |
| FILE | String | varchar(500) | string | FileUploader |

`id` 固定为 BIGINT AUTO_INCREMENT/Long；created_time、updated_time 由 MyBatis Plus 自动填充；deleted 使用 `@TableLogic`。DATE/DATETIME API 统一采用 ISO-8601。

## 5. Backend CRUD 模板

每个实体固定生成：

```text
entity/Device.java
dto/DeviceDTO.java
dto/DeviceQueryDTO.java
vo/DeviceVO.java
mapper/DeviceMapper.java
service/DeviceService.java
service/impl/DeviceServiceImpl.java
controller/DeviceController.java
```

固定接口：

```java
GET    /api/device/page
GET    /api/device/{id}
POST   /api/device
PUT    /api/device
DELETE /api/device/{id}
```

- Page 入参固定 `pageNum/pageSize`，返回 `PageResult(records,total,pageNum,pageSize)`。
- DTO 只包含可编辑字段；审计和逻辑删除字段不接受客户端赋值。
- VO 包含列表/详情字段和关系展示字段，例如 `laboratoryName`。
- Mapper 继承 BaseMapper；存在关系展示时生成显式分页/详情查询。
- ServiceImpl 负责事务、多对多关联表差量同步和逻辑删除。
- Controller 只做校验、鉴权和调用 Service，不生成业务推理代码。

## 6. Frontend CRUD 模板

每个模块固定生成：

```text
src/api/device.ts
src/types/device.ts
src/views/device/index.vue
src/views/device/form.vue
src/views/device/detail.vue
```

列表页统一包含搜索、表格、分页、新增、编辑、删除、详情。搜索字段取 `searchable`，表格字段取 `listVisible`，表单字段取 `formVisible`。required 同时生成后端 Bean Validation 和前端 rules。

API 文件只能消费 `CrudContract`，不得自行拼另一套路由。创建和编辑共用 form，通过 id 判断模式。路由和菜单先按模块并行生成 entry，最后按 `order,code` 聚合成唯一文件。

## 7. ER 关系规则

### 一对多与多对一

`Laboratory 1:N Device` 固定生成：

- SQL：`device.laboratory_id`、普通索引、外键；
- Entity/DTO：`Long laboratoryId`；
- VO：`laboratoryId` 和 `laboratoryName`；
- Mapper：LEFT JOIN 查询关系显示名；
- Form：单选关联选择器，value=id、label=目标实体 displayField；
- List/Detail：展示名称而非裸 id。

### 一对一

在 owner 侧生成外键和唯一索引。归一化后必须有明确 owner；未显式指定时固定 source 为 owner，并把结果写回冻结快照。

### 多对多

`Student N:M Course` 固定生成：

- `student_course(student_id,course_id,created_time)`；
- `(student_id,course_id)` 联合唯一索引及两个外键索引；
- DTO 为 `List<Long> courseIds`；
- VO 为 `List<IdNameVO> courses`；
- Service 事务内差量同步 join table；
- Form 为 `el-select multiple`，显示名称标签。

join table 名采用排序稳定的表名组合，Blueprint 显式名称优先。首期外键删除策略默认 RESTRICT，不自动生成物理级联删除。

## 8. SQL 规则

内部并行生成 SQL 片段，最终稳定排序聚合为 `sql/database.sql`：

1. CREATE DATABASE / USE。
2. 用户、角色、权限及关联表。
3. 按 tableName 排序的业务表。
4. join tables。
5. indexes 和 foreign keys。
6. ADMIN、普通角色、菜单、权限和管理员种子数据。

标识符必须经过白名单校验并使用反引号，数据值由 SQL literal encoder 编码，不能拼接 AI 原始文本。管理员密码存 BCrypt hash，README 要求首次登录修改。

索引只采用确定性规则：unique 字段唯一索引、外键普通索引、适合索引的 searchable 字段索引；TEXT 不自动建普通索引，不让 AI 做性能判断。

## 9. 权限规则

权限 code 固定为 `{moduleCode}:{action}`：

```text
device:view device:create device:update device:delete
device:export device:import device:approve
```

同一 code 同时用于 Controller `@PreAuthorize`、route meta、按钮权限和 SQL seed。ADMIN 绑定全部权限；普通角色只绑定 Blueprint 授权。菜单关联 view permission，但隐藏菜单不能替代后端鉴权。

## 10. 文件级并行

Generator 先把每个 Entity、DTO、Controller、API、Vue 页面和 SQL 片段规划为独立 `FileGenerationTask`，再提交有界线程池。建议初始配置：

```yaml
generator.file-executor:
  core-pool-size: 8
  max-pool-size: 16
  queue-capacity: 100
  task-timeout: 30s
```

任务提交前完成路径唯一性检查。每个任务只写自己的临时文件，完成后原子改名。聚合任务消费内存片段结果，不轮询其他任务的输出文件。

## 11. Manifest 和增量基础

从首版开始为每个文件记录 `path/sha256/sourceKey/modelHash/ownership/lastGeneratedHash/userModified`，同时记录 Blueprint、模板和引擎版本 hash。生成前通过当前文件 hash 与 `lastGeneratedHash` 计算人工修改状态。首期可以全量生成，但未来只重生成 modelHash 变化的 sourceKey；人工修改只能通过保护区合并或显式冲突处理，不能静默覆盖；模板或引擎主版本变化时全量生成。

## 12. 黄金测试与开发顺序

模板发布门禁必须包含三个固定 Blueprint：单实体 CRUD、一对多、多对多，并执行：

```text
./mvnw -DskipTests package
npm ci && npm run build
MySQL 8 执行 database.sql
Controller path == frontend api path
SQL relation == DTO/VO relation == Vue relation component
启动后健康检查成功
```

开发顺序：

1. 建立模板目录、template.yaml 和固定基础工程。
2. 实现 NamingStrategy、TypeMappingRegistry、GenerationModel。
3. 实现 RelationExpansionService 和四种关系测试。
4. 实现 Backend CRUD 模板。
5. 实现 Frontend CRUD、路由和菜单聚合模板。
6. 实现 SQL、关系和 RBAC 模板。
7. 实现文件任务线程池、Manifest Merge 和 Build Check。
8. 三个黄金 Blueprint 端到端构建通过后，再接入真实 AI Blueprint。
