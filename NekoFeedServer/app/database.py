import os
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import sessionmaker, declarative_base

# Ensure data directory exists
os.makedirs("data", exist_ok=True)
os.makedirs("data/media/images", exist_ok=True)
os.makedirs("data/media/videos", exist_ok=True)

SQLALCHEMY_DATABASE_URL = "sqlite:///./data/feed.db"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

def ensure_feed_metric_columns():
    columns = {column["name"] for column in inspect(engine).get_columns("feed_items")}
    statements = []
    if "base_click_count" not in columns:
        statements.append(
            "ALTER TABLE feed_items ADD COLUMN base_click_count INTEGER NOT NULL DEFAULT 0"
        )
    if "base_like_count" not in columns:
        statements.append(
            "ALTER TABLE feed_items ADD COLUMN base_like_count INTEGER NOT NULL DEFAULT 0"
        )
    if "base_collect_count" not in columns:
        statements.append(
            "ALTER TABLE feed_items ADD COLUMN base_collect_count INTEGER NOT NULL DEFAULT 0"
        )

    if statements:
        with engine.begin() as connection:
            for statement in statements:
                connection.execute(text(statement))

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
