# Backend Migration Guide

## Prerequisites
- PostgreSQL running and reachable
- `.env` configured with `DATABASE_URL`
- Dependencies installed: `pip install -r requirements.txt`

## Migration Commands
From backend folder:

```bash
alembic upgrade head
```

Rollback one revision:

```bash
alembic downgrade -1
```

Rollback to base:

```bash
alembic downgrade base
```

Show history:

```bash
alembic history --verbose
```

## Safe Release Checklist
1. Backup database before upgrade.
2. Run `alembic upgrade head` in staging first.
3. Verify tables, constraints, and indexes.
4. Verify fixed tags are seeded in `tags` table.
5. Keep rollback target revision ready.
