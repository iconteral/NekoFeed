import os
from sqlalchemy import create_engine
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

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
