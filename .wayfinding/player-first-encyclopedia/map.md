# 玩家优先的星露谷离线工具

## 目的地

形成一条可直接交给后续实施技能的跨仓库路线：`valley-dex` 默认快速回答玩家的“去哪、何时、多少钱、怎么获得、需要什么”，同时让新手看得懂、普通玩家查得快、资深玩家能继续展开；`stardew-offline-data-builder` 提供可靠、可追溯、面向玩家语义的数据契约。

## 非目标

- 本地图不实施应用、构建器、数据包或 UI 改动。
- 不把应用改成联网 Wiki，不在运行时抓取 Wiki。
- 不读取玩家存档，不在本次目的地内设计农场模拟器或完整收益规划器。
- 不追求展示所有官方内部参数；不能帮助普通玩家判断或行动的技术字段默认不进入主信息层。
- 不用中文 Wiki 替代官方游戏资产成为游戏事实来源。

## 约束

- 两个仓库可以同时调整，但各自保留独立的 `.codestable`、架构边界、验证与发布流程。
- 游戏事实以官方资产为依据；中文 Wiki 用于理解玩家问题、中文术语和信息优先级。来源必须可区分。
- 未知、动态、条件化和缺失信息不得猜测为固定结论。
- 应用继续完全离线；`stardew.db` 只读，个人数据继续独立保存。
- 不同实体类型可以采用不同信息结构，不再强行套统一详情模板。
- 图片只能来自发布包内经校验的本地资源。

## 完成判断

- 每个主要玩家任务和实体类别都有已确认的默认信息、次级信息、隐藏信息与缺失表达。
- 图片、买价/卖价、来源/获得方式、地点/层数、水域、时间/季节/天气、制作/升级条件、人物关系等核心语义都有唯一规范表达和可信来源策略。
- builder 的官方数据提取边界、跨实体关系、条件表达、schema/manifest 契约和质量门禁均已决定。
- 应用的列表、详情、关系、搜索、筛选、占位与渐进展开方式经过低成本原型验证。
- 两仓库的实施顺序、迁移方式、真实数据验收和回归范围明确，后续无需补做目的地范围内的产品、领域、架构或运维裁决。

## 覆盖面

- 玩家核心任务与信息优先级 -> [玩家问题矩阵](decisions/01-player-question-matrix.md)
- 官方资产与当前数据链路的真实能力 -> [官方数据能力矩阵](decisions/02-official-data-capability.md)
- 买/卖、来源/用途、地点/水域/矿井层、条件/未知等通用语言 -> [玩家语义词汇表](decisions/03-player-semantics.md)
- 商店、作物、大型可制作物、工具、怪物、鱼类、武器等分类契约 -> [分类信息契约](decisions/04-category-information-contracts.md)
- 村民亲属、朋友、监护、同住、恋爱与婚配等关系 -> [人物关系模型](decisions/05-character-relationship-model.md)
- 固定、动态、条件化、未知和不适用的表达及来源追踪 -> [条件与来源可信度](decisions/06-condition-and-provenance.md)
- 官方资产无法直接回答核心问题时的处理 -> [关键事实缺口策略](decisions/07-critical-fact-gap-policy.md)
- 图片完整性、辨识度与占位质量 -> [图片质量门槛](decisions/08-image-quality-gate.md)
- builder 到应用的 schema、关系和 manifest 演进 -> [跨仓库数据契约](decisions/09-cross-repo-data-contract.md)
- 列表、详情、主次层级和渐进展开 -> [玩家信息层级原型](decisions/10-information-hierarchy-prototype.md)
- 按地点、季节、价格、来源和需求查找 -> [搜索与浏览模型](decisions/11-search-and-browse-model.md)
- 数据与 UI 是否真的可供玩家使用的验收 -> [发布质量与真实数据验收](decisions/12-release-quality-and-validation.md)
- 两仓库实施依赖、切片和迁移 -> [实施交付图](decisions/13-delivery-slices.md)
- 离线、隐私、只读内容库 -> 已确认事实，沿用两个仓库当前 `.codestable` 约束。

## 迄今决定

- [玩家问题矩阵](decisions/01-player-question-matrix.md) - 八类内容均按玩家行动阻断与同类比较分层：地点、时间、价格、来源、条件和所需材料优先，深度机制次级展开，原始技术字段默认隐藏。
- [官方数据能力矩阵](decisions/02-official-data-capability.md) - 当前只闭合了部分静态事实链路；购买价、售价、获得方式和地点尚未统一，动态条件不能伪装成当前结论，未识别官方资产必须保留为待调查。
- [玩家语义词汇表](decisions/03-player-semantics.md) - 统一区分购买价/兑换成本/出售价格、获得方式/用途、地点/水域/矿井层、制作/加工/升级，以及固定、条件、动态、未知、暂未收录和不适用。
- [分类信息契约](decisions/04-category-information-contracts.md) - 八类条目分别以自身核心玩家行动组织列表与详情；大型可制作物再按机器、设施/容器、装饰/照明和不可制作物细分，当前缺口不得由相似技术字段顶替。
- [人物关系模型](decisions/05-character-relationship-model.md) - 人物关系采用有来源、有方向的闭集；住所和婚配资格不是关系，`LoveInterest` 不是正在恋爱，文森特与贾斯当前只显示“亲友关联（具体关系未注明）”。

## 打开决策项

- [条件与来源可信度](decisions/06-condition-and-provenance.md)
- [关键事实缺口策略](decisions/07-critical-fact-gap-policy.md)
- [图片质量门槛](decisions/08-image-quality-gate.md)
- [跨仓库数据契约](decisions/09-cross-repo-data-contract.md)
- [玩家信息层级原型](decisions/10-information-hierarchy-prototype.md)
- [搜索与浏览模型](decisions/11-search-and-browse-model.md)
- [发布质量与真实数据验收](decisions/12-release-quality-and-validation.md)
- [实施交付图](decisions/13-delivery-slices.md)

## 迷雾

无。扫描中出现的地点层级、动态条件、关系语义和非官方补充边界均已能准确写成决策问题。

## 范围外

- 在线同步、账号、云端收藏：违反完全离线与隐私目的。
- 玩家存档读取和自动进度追踪：属于独立产品能力，不是修复图鉴信息价值的必要条件。
- 完整农场布局、收益模拟和日程规划：可在核心查阅体验稳定后另建地图，本次只保留支撑未来规划所需的可靠事实与查询能力。
- 模组数据与非官方内容包：当前发布契约只面向官方中文数据，混入会改变来源和兼容性边界。
