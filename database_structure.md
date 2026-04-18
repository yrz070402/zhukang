# 筑康数据库结构说明

本文件根据 Alembic 迁移脚本整理，数据来源：
- backend/alembic/versions/20260413_0001_create_core_tables.py
- backend/alembic/versions/20260413_0002_seed_fixed_tags.py
- backend/alembic/versions/20260417_0003_update_goal_and_nutrition_constraints.py
- backend/alembic/versions/20260417_0004_add_user_nickname.py
- backend/alembic/versions/20260417_0005_add_activity_level_to_user_goals.py
- backend/alembic/versions/20260417_0006_add_macro_targets_to_user_goals.py
- backend/alembic/versions/20260418_0007_add_avatar_index_to_users.py

## 1. 枚举类型（PostgreSQL ENUM）

1. sex_type: MALE, FEMALE, OTHER
2. goal_type: MUSCLE_GAIN, FAT_LOSS, MAINTAIN, CHRONIC_DISEASE_MANAGEMENT
3. meal_type: BREAKFAST, LUNCH, DINNER, SNACK
4. source_type: AI, MANUAL

## 2. 表结构总览

当前核心业务表共 6 张：
1. users
2. tags
3. user_goals
4. user_profiles
5. user_tags
6. nutrition_intakes

---

## 3. 各表字段与约束

## 3.1 users

用途：用户账号

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | UUID | 否 | - | 主键 |
| account | VARCHAR(128) | 否 | - | 账号（唯一） |
| nickname | VARCHAR(128) | 否 | 插入时默认等于 account | 昵称（可修改） |
| avatar_index | INTEGER | 否 | 0 | 头像索引（0-11） |
| password_hash | VARCHAR(255) | 否 | - | 密码哈希 |
| is_active | BOOLEAN | 否 | true | 是否启用 |
| created_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 创建时间 |
| updated_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 更新时间 |
| deleted_at | TIMESTAMP WITH TIME ZONE | 是 | - | 软删除时间 |

约束：
1. 主键：pk_users(id)
2. 唯一约束：uq_users_account(account)
3. 检查约束：ck_users_avatar_index_range(avatar_index >= 0 AND avatar_index <= 11)

---

## 3.2 tags

用途：动态标签字典（支持新增/维护）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | INTEGER | 否 | 自增 | 主键 |
| code | VARCHAR(64) | 否 | - | 标签编码（唯一） |
| display_name | VARCHAR(64) | 否 | - | 标签展示名（唯一） |
| description | VARCHAR(255) | 是 | - | 标签描述 |
| is_active | BOOLEAN | 否 | true | 是否启用 |
| created_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 创建时间 |
| updated_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 更新时间 |
| deleted_at | TIMESTAMP WITH TIME ZONE | 是 | - | 软删除时间 |

约束：
1. 主键：pk_tags(id)
2. 唯一约束：uq_tags_code(code)
3. 唯一约束：uq_tags_display_name(display_name)

初始种子数据（可扩展，不受固定集合限制）：
1. fitness_enthusiast / 健身爱好者
2. chronic_disease / 慢病管理
3. sub_healthy / 亚健康
4. pregnant_postpartum / 孕产期
5. special_diet / 特殊饮食需求

---

## 3.3 user_goals

用途：用户目标记录（1 个用户仅保留 1 条当前目标，可更新）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | UUID | 否 | - | 主键 |
| user_id | UUID | 否 | - | 关联 users.id |
| goal_type | goal_type (ENUM) | 否 | - | 目标类型 |
| activity_level | FLOAT | 否 | 0 | 运动量等级/系数 |
| target_weight_kg | NUMERIC(5,2) | 是 | - | 目标体重 |
| target_daily_calories_kcal | NUMERIC(8,2) | 是 | - | 目标每日热量 |
| target_carb_g | NUMERIC(8,2) | 是 | - | 目标碳水(g) |
| target_protein_g | NUMERIC(8,2) | 是 | - | 目标蛋白质(g) |
| target_fat_g | NUMERIC(8,2) | 是 | - | 目标脂肪(g) |
| note | TEXT | 是 | - | 备注 |
| is_active | BOOLEAN | 否 | true | 是否启用 |
| created_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 创建时间 |
| updated_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 更新时间 |
| deleted_at | TIMESTAMP WITH TIME ZONE | 是 | - | 软删除时间 |

约束：
1. 主键：pk_user_goals(id)
2. 外键：fk_user_goals_user_id_users(user_id -> users.id, ON DELETE CASCADE)
3. 唯一约束：uq_user_goals_user_id(user_id)
4. 检查约束：ck_user_goals_activity_level_non_negative(activity_level >= 0)
5. 检查约束：ck_user_goals_target_weight_positive(target_weight_kg IS NULL OR target_weight_kg > 0)
6. 检查约束：ck_user_goals_target_daily_calories_positive(target_daily_calories_kcal IS NULL OR target_daily_calories_kcal > 0)
7. 检查约束：ck_user_goals_target_carb_non_negative(target_carb_g IS NULL OR target_carb_g >= 0)
8. 检查约束：ck_user_goals_target_protein_non_negative(target_protein_g IS NULL OR target_protein_g >= 0)
9. 检查约束：ck_user_goals_target_fat_non_negative(target_fat_g IS NULL OR target_fat_g >= 0)

---

## 3.4 user_profiles

用途：用户身体信息（与 users 1:1）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | UUID | 否 | - | 主键 |
| user_id | UUID | 否 | - | 关联 users.id（唯一） |
| height_cm | NUMERIC(5,2) | 否 | - | 身高（cm） |
| weight_kg | NUMERIC(5,2) | 否 | - | 体重（kg） |
| age | INTEGER | 否 | - | 年龄 |
| sex | sex_type (ENUM) | 是 | - | 性别 |
| created_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 创建时间 |
| updated_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 更新时间 |
| deleted_at | TIMESTAMP WITH TIME ZONE | 是 | - | 软删除时间 |

约束：
1. 主键：pk_user_profiles(id)
2. 唯一约束：uq_user_profiles_user_id(user_id)
3. 外键：fk_user_profiles_user_id_users(user_id -> users.id, ON DELETE CASCADE)
4. 检查约束：ck_user_profiles_height_cm_positive(height_cm > 0)
5. 检查约束：ck_user_profiles_weight_kg_positive(weight_kg > 0)
6. 检查约束：ck_user_profiles_age_valid_range(age >= 1 AND age <= 120)

---

## 3.5 user_tags

用途：用户与标签的多对多关联表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| user_id | UUID | 否 | - | 关联 users.id |
| tag_id | INTEGER | 否 | - | 关联 tags.id |
| created_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 创建时间 |
| updated_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 更新时间 |
| deleted_at | TIMESTAMP WITH TIME ZONE | 是 | - | 软删除时间 |

约束：
1. 复合主键：pk_user_tags(user_id, tag_id)
2. 外键：fk_user_tags_user_id_users(user_id -> users.id, ON DELETE CASCADE)
3. 外键：fk_user_tags_tag_id_tags(tag_id -> tags.id, ON DELETE CASCADE)

---

## 3.6 nutrition_intakes

用途：饮食摄入记录

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | UUID | 否 | - | 主键 |
| user_id | UUID | 否 | - | 关联 users.id |
| intake_time | TIMESTAMP WITH TIME ZONE | 否 | - | 摄入时间 |
| meal_type | meal_type (ENUM) | 否 | - | 餐次 |
| food_name | VARCHAR(128) | 否 | - | 食物名称 |
| portion_size | VARCHAR(64) | 是 | - | 份量描述 |
| source_type | source_type (ENUM) | 否 | 'AI' | 数据来源 |
| energy_kcal | NUMERIC(8,2) | 否 | - | 热量 |
| fat_g | NUMERIC(8,2) | 否 | - | 脂肪 |
| carb_g | NUMERIC(8,2) | 否 | - | 碳水 |
| protein_g | NUMERIC(8,2) | 否 | - | 蛋白质 |
| calcium_mg | NUMERIC(8,2) | 是 | - | 钙 |
| iron_mg | NUMERIC(8,2) | 是 | - | 铁 |
| fiber_g | NUMERIC(8,2) | 是 | - | 膳食纤维 |
| sodium_mg | NUMERIC(8,2) | 是 | - | 钠 |
| remark | TEXT | 是 | - | 备注 |
| created_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 创建时间 |
| updated_at | TIMESTAMP WITH TIME ZONE | 否 | now() | 更新时间 |
| deleted_at | TIMESTAMP WITH TIME ZONE | 是 | - | 软删除时间 |

约束：
1. 主键：pk_nutrition_intakes(id)
2. 外键：fk_nutrition_intakes_user_id_users(user_id -> users.id, ON DELETE CASCADE)
3. 检查约束：ck_nutrition_intakes_energy_kcal_non_negative(energy_kcal >= 0)
4. 检查约束：ck_nutrition_intakes_fat_g_non_negative(fat_g >= 0)
5. 检查约束：ck_nutrition_intakes_carb_g_non_negative(carb_g >= 0)
6. 检查约束：ck_nutrition_intakes_protein_g_non_negative(protein_g >= 0)
7. 检查约束：ck_nutrition_intakes_calcium_mg_non_negative(calcium_mg >= 0)
8. 检查约束：ck_nutrition_intakes_iron_mg_non_negative(iron_mg >= 0)
9. 检查约束：ck_nutrition_intakes_fiber_g_non_negative(fiber_g >= 0)
10. 检查约束：ck_nutrition_intakes_sodium_mg_non_negative(sodium_mg >= 0)

索引：
1. ix_nutrition_intakes_user_id_intake_time(user_id, intake_time)

---

## 4. 关系摘要

1. users 1 : 1 user_profiles（通过 user_profiles.user_id 唯一约束保证）
2. users 1 : 1 user_goals（当前目标）
3. users M : N tags（通过 user_tags 实现）
4. users 1 : N nutrition_intakes

外键删除策略：所有关联外键均为 ON DELETE CASCADE。

## 5. 审计字段约定

业务表统一包含：
1. created_at（默认 now()）
2. updated_at（默认 now()）
3. deleted_at（可空，软删除标记）

## 6. 命名约定（来自 ORM Metadata）

1. 主键：pk_<table_name>
2. 外键：fk_<table_name>_<column_0_name>_<referred_table_name>
3. 唯一约束：uq_<table_name>_<column_0_name>
4. 检查约束：ck_<table_name>_<constraint_name>
5. 索引：ix_<column_0_label>
