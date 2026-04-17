## Plan: 后端数据库搭建与维护落地

目标是在现有 FastAPI 后端中建立可持续维护的 PostgreSQL 数据层，覆盖用户账号认证、用户身体信息与目标管理、动态标签体系、饮食摄入记录（四大营养素+常见微量营养），并同步建立迁移规范、审计字段和索引巡检机制。方案采用 SQLAlchemy 2 + AsyncSession + Alembic，先完成核心数据模型与读写链路，再补齐治理与运维规范。

**Steps**
1. Phase 1 - 数据层基建（阻塞后续）
2. 在 backend/app/core/config.py 增加 DATABASE_URL、JWT 相关配置、时区配置（如 Asia/Shanghai），并定义开发/测试环境默认值。 
3. 新建 backend/app/core/database.py，建立 AsyncEngine、async_sessionmaker、get_db 依赖、健康检查函数；在 backend/app/main.py 注册启动/关闭钩子用于连接池生命周期。 
4. 初始化 Alembic（alembic.ini + alembic/env.py），接入 SQLAlchemy metadata，确定命名约定（主键、外键、唯一索引命名规则）。
5. Phase 2 - 领域模型与约束设计（依赖 Phase 1）
6. 新建 backend/app/models/models.py，定义表：users、user_profiles、user_goals、tags、user_tags、nutrition_intakes。核心关系：users 1:1 user_profiles；users 1:1 user_goals（当前目标）；users M:N tags（经 user_tags）；users 1:N nutrition_intakes。
7. 统一审计字段策略：所有业务表加入 created_at、updated_at，可选 deleted_at（软删除）；关键枚举字段（goal_type、meal_type、sex）用数据库 Enum 或受控字符串 + Check Constraint。
8. nutrition_intakes 字段设计：intake_time、meal_type、food_name、portion_size、energy_kcal、fat_g、carb_g、protein_g，并扩展 calcium_mg、iron_mg、fiber_g、sodium_mg；保留 source_type（ai/manual）与 remark。
9. tags 采用动态字典表：可写入初始标签用于冷启动，后续支持按业务持续新增与维护。
10. Phase 3 - 迁移与初始化数据（依赖 Phase 2）
11. 生成首个 Alembic 迁移版本，确保包含表结构、外键、唯一约束、非空约束和默认值；单独迁移写入初始标签种子数据。
12. 建立迁移执行规范：本地、测试、生产三套命令与回滚策略（upgrade/downgrade 到指定 revision）；在 README.md 增补“迁移前备份、迁移后校验”流程。
13. Phase 4 - 应用层接入（与 Phase 3 部分并行，但最终联调依赖迁移完成）
14. 扩展 backend/app/models/schemas.py：新增 UserCreate、UserLogin、UserProfileUpdate、GoalUpsert、NutritionIntakeCreate/Read、TagRead 等 DTO。
15. 新增 backend/app/core/security.py：密码哈希与校验、JWT 生成与解析；新增认证依赖 current_user。
16. 新增路由 backend/app/api/v1/endpoints/auth.py、users.py、nutrition.py、tags.py，并在 backend/app/api/v1/__init__.py 聚合。最小可用接口：注册、登录、更新个人资料、查询标签、新增饮食记录、按日期查询饮食汇总。
17. Phase 5 - 维护与性能（依赖 Phase 4 上线）
18. 索引策略：users(email/phone) 唯一索引；nutrition_intakes(user_id, intake_time) 复合索引；user_tags(user_id, tag_id) 唯一复合索引；按慢查询结果迭代补充。
19. 数据维护规范：定义软删除仅用于可恢复实体（如 goals），饮食记录默认硬删除受限；制定“历史数据不直接物理删除”的治理规则。
20. 建立巡检清单：每周检查迁移链连续性、索引命中率、慢查询日志；每次发版执行 schema diff 与回归 SQL 校验。

**Relevant files**
- backend/app/core/config.py - 增加数据库/JWT/时区配置
- backend/app/core/database.py - 新建异步数据库连接与会话管理
- backend/app/main.py - 注册数据库生命周期管理
- backend/app/models/models.py - 新建 SQLAlchemy ORM 模型与关系
- backend/app/models/schemas.py - 扩展 API 入参与出参模型
- backend/app/core/security.py - 新建密码与令牌安全工具
- backend/app/api/v1/__init__.py - 聚合新增路由
- backend/app/api/v1/endpoints/auth.py - 认证接口
- backend/app/api/v1/endpoints/users.py - 用户资料接口
- backend/app/api/v1/endpoints/nutrition.py - 饮食记录接口
- backend/app/api/v1/endpoints/tags.py - 标签读取接口
- backend/README.md - 补充迁移执行与维护规范
- backend/alembic.ini - Alembic 配置
- backend/alembic/env.py - 迁移环境与 metadata 接入
- backend/alembic/versions/* - 首次建表与标签种子迁移

**Verification**
1. 执行迁移：alembic upgrade head，确认所有表与索引创建成功。
2. 运行最小回滚验证：alembic downgrade -1 后再 upgrade head，确认迁移可逆且不破坏数据约束。
3. API 验证链路：注册 -> 登录 -> 更新资料 -> 绑定标签 -> 新增饮食记录 -> 按日期查询汇总，检查权限、外键与单位一致性。
4. 数据约束验证：插入非法枚举、重复邮箱、负数营养值、空必填字段，确认被数据库或应用层正确拦截。
5. 性能基线验证：对 nutrition_intakes 的按用户+日期查询进行 explain analyze，确认命中复合索引。

**Decisions**
- 数据库选型：PostgreSQL。
- 饮食记录范围：四大营养素 + 常见微量营养（calcium、iron、fiber、sodium）。
- 标签模型：动态标签字典，支持后续扩展。
- 维护范围：包含 Alembic 迁移规范、审计字段、索引与性能巡检；暂不纳入备份恢复与数据归档自动化。

**Further Considerations**
1. 账号主键推荐使用 UUID（跨端同步更稳定）而不是自增 ID；若后续要做数据脱敏导出，UUID 更安全。
2. nutrition_intakes 建议同时保存本次摄入原始值与当日聚合结果分离计算，避免写入时过度耦合统计逻辑。
3. 若后续接入微信生态，可在 users 表预留 third_party_provider 与 third_party_sub 字段。
